package com.cinenest.identity.internal;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/** Explicitly imported test instrumentation. Never reads query strings, principals or credentials. */
@TestConfiguration(proxyBeanMethods = false)
public class GoogleAuditCorrelationSupport {
    public static final String ATTEMPT_HEADER = "X-CineNest-Test-Attempt";
    public enum Route { START, CALLBACK, OTHER }
    public enum Kind { AUTHENTICATION, ACCOUNT_READ, LOGOUT, HTTP_DENIAL }
    public enum Outcome { ALLOW, DENY }
    public record Audit(UUID eventId, Kind kind, Outcome outcome, boolean committed) { }
    public record Request(UUID requestId, UUID attemptId, Route route, int status, List<Audit> audits) { }

    public static final class Recorder {
        private final ThreadLocal<Pending> current = new ThreadLocal<>();
        private final Queue<Request> completed = new ConcurrentLinkedQueue<>();
        private volatile DataSource database;
        private static final class Pending {
            final UUID requestId = UUID.randomUUID();
            final UUID attemptId;
            final Route route;
            final List<Audit> candidates = new ArrayList<>();
            Pending(UUID attemptId, Route route) { this.attemptId=attemptId; this.route=route; }
        }
        public void reset() { completed.clear(); }
        public List<Request> callbacks(UUID attempt) {
            return completed.stream().filter(r -> r.attemptId().equals(attempt) && r.route()==Route.CALLBACK).toList();
        }
        private void candidate(UUID id, Kind kind, Outcome outcome) {
            var pending=current.get();
            if(pending!=null) pending.candidates.add(new Audit(id,kind,outcome,false));
        }
        private void finish(int status) throws SQLException {
            var pending=current.get();
            try {
                List<Audit> audits=new ArrayList<>();
                // A separate connection verifies visibility after the INSERT, rather than assuming commit.
                for(var candidate:pending.candidates) {
                    boolean committed;
                    try(var connection=database.getConnection();var statement=connection.prepareStatement(
                            "SELECT event_kind,outcome FROM cinenest_identity.security_audit WHERE event_id=?")) {
                        connection.setReadOnly(true);
                        statement.setObject(1,candidate.eventId());
                        try(var row=statement.executeQuery()) {
                            committed=row.next() && candidate.kind().name().equals(row.getString(1))
                                && candidate.outcome().name().equals(row.getString(2));
                        }
                    }
                    audits.add(new Audit(candidate.eventId(),candidate.kind(),candidate.outcome(),committed));
                }
                completed.add(new Request(pending.requestId,pending.attemptId,pending.route,status,List.copyOf(audits)));
            } finally { current.remove(); }
        }
    }

    @Bean static Recorder googleAuditRecorder() { return new Recorder(); }

    @Bean static BeanPostProcessor googleTestAuditConnections(Recorder recorder) {
        return new BeanPostProcessor() {
            @Override public Object postProcessAfterInitialization(Object bean,String name) {
                if(!(bean instanceof DataSource source) || !name.equals("dataSource")) return bean;
                recorder.database=source;
                return new DelegatingDataSource(source) {
                    @Override public Connection getConnection() throws SQLException { return connection(super.getConnection(),recorder); }
                    @Override public Connection getConnection(String user,String password) throws SQLException {
                        return connection(super.getConnection(user,password),recorder);
                    }
                };
            }
        };
    }

    private static Object invoke(Object target,Method method,Object[] args) throws Throwable {
        try { return method.invoke(target,args); }
        catch(InvocationTargetException failure) { throw failure.getCause(); }
    }
    private static Connection connection(Connection target,Recorder recorder) {
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)-> {
            if(method.getName().equals("toString")) return "Test-only audit connection";
            Object result=invoke(target,method,args);
            if(method.getName().equals("prepareStatement") && args[0] instanceof String sql
                    && sql.equals("INSERT INTO cinenest_identity.security_audit(event_id,actor_id,target_id,event_kind,outcome) VALUES (?,?,?,?,?)"))
                return statement((PreparedStatement)result,recorder);
            return result;
        });
    }
    private static PreparedStatement statement(PreparedStatement target,Recorder recorder) {
        return (PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},new InvocationHandler() {
            UUID eventId; Kind kind; Outcome outcome;
            @Override public Object invoke(Object proxy,Method method,Object[] args) throws Throwable {
                if(method.getName().equals("toString")) return "Test-only audit statement";
                if(method.getName().startsWith("set") && args!=null && args.length>=2 && args[0] instanceof Integer index) {
                    // Deliberately ignore actor/target parameters 2/3 and every other SQL statement.
                    if(index==1 && args[1] instanceof UUID id) eventId=id;
                    if(index==4 && args[1] instanceof String value) kind=Kind.valueOf(value);
                    if(index==5 && args[1] instanceof String value) outcome=Outcome.valueOf(value);
                }
                Object result=GoogleAuditCorrelationSupport.invoke(target,method,args);
                if(method.getName().equals("executeUpdate") && result.equals(1) && eventId!=null && kind!=null && outcome!=null)
                    recorder.candidate(eventId,kind,outcome);
                return result;
            }
        });
    }

    @Bean FilterRegistrationBean<Filter> googleTestRequestCorrelation(Recorder recorder) {
        Filter filter=new Filter() {
            @Override public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain) throws IOException,ServletException {
                var http=(HttpServletRequest)request;
                UUID attempt;
                try { attempt=UUID.fromString(Objects.toString(http.getHeader(ATTEMPT_HEADER),"")); }
                catch(IllegalArgumentException absent) { attempt=UUID.randomUUID(); }
                Route route=switch(http.getRequestURI()) {
                    case "/oauth2/authorization/google" -> Route.START;
                    case "/login/oauth2/code/google" -> Route.CALLBACK;
                    default -> Route.OTHER;
                };
                recorder.current.set(new Recorder.Pending(attempt,route));
                try { chain.doFilter(request,response); }
                finally {
                    try { recorder.finish(((HttpServletResponse)response).getStatus()); }
                    catch(SQLException failure) { throw new ServletException("Test audit commit observation failed"); }
                }
            }
        };
        var registration=new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        return registration;
    }
}
