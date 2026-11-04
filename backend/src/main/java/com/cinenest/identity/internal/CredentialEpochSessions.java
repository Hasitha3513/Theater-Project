package com.cinenest.identity.internal;

import java.io.Serializable;
import java.sql.Connection;
import java.util.*;
import java.util.function.Consumer;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.jdbc.config.annotation.web.http.JdbcHttpSessionConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

/** Existing Spring Session only. No epoch cache, principal deletion shortcut or bearer auth. */
final class CredentialEpochSessions {
    static final String KEY=HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
    record Binding(UUID account,long epoch) implements Serializable {
        private static final long serialVersionUID=1L;
        Binding {if(account==null||epoch<0)throw new Denied();}
    }
    static final class Denied extends RuntimeException {Denied(){super("Identity session unavailable");}}
    private record Access(Session session,Consumer<Session> save) {}
    private static final ThreadLocal<Access> CURRENT=new ThreadLocal<>();
    static Authentication authentication(UUID account,long epoch) {
        var result=UsernamePasswordAuthenticationToken.authenticated(account.toString(),null,List.of());
        result.setDetails(new Binding(account,epoch));return result;
    }
    static Binding binding(Authentication authentication) {
        if(authentication==null||!authentication.isAuthenticated()||!(authentication.getDetails() instanceof Binding b)
                || !b.account().toString().equals(authentication.getName()))throw new Denied();
        return b;
    }
    static void check(Authentication authentication,IdentityStore store,boolean lock) {
        var b=binding(authentication);if(store.credentialEpoch(b.account(),lock)!=b.epoch())throw new Denied();
    }
    static HttpSessionSecurityContextRepository contexts() {
        return new HttpSessionSecurityContextRepository(){
            @Override public void saveContext(SecurityContext context,HttpServletRequest request,HttpServletResponse response) {
                var a=context.getAuthentication();
                // Provider authentication is transient; only the existing success handler's
                // locally associated, epoch-bound principal may enter durable application state.
                if(a instanceof org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken)return;
                if(a!=null&&a.isAuthenticated())binding(a);
                super.saveContext(context,request,response);
                if(a!=null&&a.isAuthenticated()) {
                    var session=request.getSession(false);var access=CURRENT.get();
                    if(session==null||access==null||!session.getId().equals(access.session().getId()))throw new Denied();
                    access.save().accept(access.session()); // Persist before reporting authentication success.
                }
            }
        };
    }
    static OncePerRequestFilter requests(IdentityStore store) {
        return new OncePerRequestFilter(){
            @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws java.io.IOException,ServletException {
                try {
                    var a=SecurityContextHolder.getContext().getAuthentication();
                    if(a!=null&&a.isAuthenticated()&&!(a instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
                        try {check(a,store,false);}
                        catch(RuntimeException denied){SecurityContextHolder.clearContext();var s=request.getSession(false);if(s!=null)s.invalidate();response.setHeader("Cache-Control","no-store");response.setStatus(401);return;}
                    }
                    chain.doFilter(request,response);
                } finally {clear();}
            }
        };
    }
    static void clear(){CURRENT.remove();}
    static TransactionTemplate transaction(PlatformTransactionManager manager) {
        var tx=new TransactionTemplate(manager);tx.setIsolationLevel(Connection.TRANSACTION_READ_COMMITTED);
        tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRED);return tx;
    }
    static BeanPostProcessor infrastructure(ObjectProvider<PlatformTransactionManager> managers,ObjectProvider<IdentityStore> stores,ObjectProvider<JdbcClient> clients) {
        return new BeanPostProcessor(){
            TransactionTemplate tx;
            TransactionTemplate tx(){if(tx==null)tx=transaction(managers.getObject());return tx;}
            @Override public Object postProcessAfterInitialization(Object bean,String name) {
                if(bean instanceof JdbcHttpSessionConfiguration config)config.setTransactionOperations(tx());
                if(!(bean instanceof JdbcIndexedSessionRepository))return bean;
                var proxy=new ProxyFactory(bean);proxy.setProxyTargetClass(true);
                var advice=new RepositoryGuard(stores.getObject(),clients.getObject(),tx());
                proxy.addAdvice(advice);Object wrapped=proxy.getProxy();advice.repository=(SessionRepository<Session>)(SessionRepository<?>)wrapped;return wrapped;
            }
        };
    }
    /** Tracks only actual delegate-created/loaded Session objects, never client session flags. */
    static final class RepositoryGuard implements org.aopalliance.intercept.MethodInterceptor {
        final IdentityStore store;final JdbcClient jdbc;final TransactionTemplate tx;
        final Map<Session,String> persisted=Collections.synchronizedMap(new WeakHashMap<>());
        SessionRepository<Session> repository;
        RepositoryGuard(IdentityStore store,JdbcClient jdbc,TransactionTemplate tx){this.store=store;this.jdbc=jdbc;this.tx=tx;}
        @Override public Object invoke(MethodInvocation invocation)throws Throwable {
            String method=invocation.getMethod().getName();
            if(method.equals("save")&&invocation.getArguments().length==1&&invocation.getArguments()[0] instanceof Session session) {
                synchronized(session){return tx.execute(status->{
                    if(!persisted.containsKey(session))throw new Denied();
                    var context=session.<Object>getAttribute(KEY);
                    if(context instanceof SecurityContext security&&security.getAuthentication()!=null&&security.getAuthentication().isAuthenticated())check(security.getAuthentication(),store,true);
                    String previous=persisted.get(session);
                    if(previous!=null && !existing(previous))throw new Denied();
                    Object result=proceed(invocation);persisted.put(session,session.getId());return result;
                });}
            }
            if(method.equals("createSession")||method.equals("findById")) {
                Object result=proceed(invocation);
                if(result instanceof Session session) {
                    persisted.put(session,method.equals("createSession")?null:session.getId());
                    CURRENT.set(new Access(session,s->repository.save(s)));
                }
                return result;
            }
            // Existing principal-index lookups are not an authentication path. Register real
            // returned objects so any later save is still subject to the same guard.
            Object result=proceed(invocation);
            if(method.equals("findByIndexNameAndIndexValue")&&result instanceof Map<?,?> map)
                for(Object value:map.values())if(value instanceof Session s)persisted.put(s,s.getId());
            return result;
        }
        boolean existing(String id) {
            return jdbc.sql("SELECT session_id FROM cinenest_identity.http_session WHERE session_id=:id AND expiry_time>floor(extract(epoch FROM clock_timestamp())*1000) FOR UPDATE")
                .param("id",id).query(String.class).list().size()==1;
        }
        Object proceed(MethodInvocation invocation) {
            try{return invocation.proceed();}catch(RuntimeException e){throw e;}catch(Throwable e){throw new Denied();}
        }
    }
}
