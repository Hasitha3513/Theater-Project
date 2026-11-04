package com.cinenest.identity.internal;

import java.lang.reflect.Method;
import java.util.*;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CredentialEpochSessionsTest {
    final UUID account=UUID.randomUUID();
    IdentityStore store;PlatformTransactionManager manager;CredentialEpochSessions.RepositoryGuard guard;
    @BeforeEach void setup(){store=mock(IdentityStore.class);manager=mock(PlatformTransactionManager.class);when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());guard=spy(new CredentialEpochSessions.RepositoryGuard(store,mock(JdbcClient.class),CredentialEpochSessions.transaction(manager)));}
    @AfterEach void clear(){SecurityContextHolder.clearContext();CredentialEpochSessions.clear();}
    MapSession session(long epoch){var s=new MapSession();var c=SecurityContextHolder.createEmptyContext();c.setAuthentication(CredentialEpochSessions.authentication(account,epoch));s.setAttribute(CredentialEpochSessions.KEY,c);return s;}
    MethodInvocation invocation(String method,Object argument,Object result)throws Throwable {
        var call=mock(MethodInvocation.class);Method m=method.equals("save")?SessionRepository.class.getMethod(method,Session.class):SessionRepository.class.getMethod(method);
        when(call.getMethod()).thenReturn(m);when(call.getArguments()).thenReturn(argument==null?new Object[0]:new Object[]{argument});when(call.proceed()).thenReturn(result);return call;
    }
    @Test void onlyAccountBoundNonnegativeEpochIsValid(){var a=CredentialEpochSessions.authentication(account,3);assertThat(CredentialEpochSessions.binding(a)).isEqualTo(new CredentialEpochSessions.Binding(account,3));assertThatThrownBy(()->new CredentialEpochSessions.Binding(account,-1)).isInstanceOf(CredentialEpochSessions.Denied.class);}
    @Test void missingAndSubstitutedBindingsAreDenied(){var a=org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(account.toString(),null,List.of());assertThatThrownBy(()->CredentialEpochSessions.binding(a)).isInstanceOf(CredentialEpochSessions.Denied.class);a.setDetails(new CredentialEpochSessions.Binding(UUID.randomUUID(),0));assertThatThrownBy(()->CredentialEpochSessions.binding(a)).isInstanceOf(CredentialEpochSessions.Denied.class);}
    @Test void currentEpochPassesButBothManualAndOidcLocalSessionsBecomeStale(){when(store.credentialEpoch(account,false)).thenReturn(1L,2L,2L);var a=CredentialEpochSessions.authentication(account,1);assertThatCode(()->CredentialEpochSessions.check(a,store,false)).doesNotThrowAnyException();assertThatThrownBy(()->CredentialEpochSessions.check(a,store,false)).isInstanceOf(CredentialEpochSessions.Denied.class);assertThatThrownBy(()->CredentialEpochSessions.check(a,store,false)).isInstanceOf(CredentialEpochSessions.Denied.class);}
    @Test void protectedRequestInvalidatesMissingEpochSession()throws Exception {var request=new MockHttpServletRequest();var response=new MockHttpServletResponse();var session=new MockHttpSession();request.setSession(session);SecurityContextHolder.getContext().setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(account.toString(),null,List.of()));var chain=mock(jakarta.servlet.FilterChain.class);CredentialEpochSessions.requests(store).doFilter(request,response,chain);assertThat(response.getStatus()).isEqualTo(401);assertThat(session.isInvalid()).isTrue();verifyNoInteractions(chain);}
    @Test void databaseFailureCannotAdmitProtectedRequest()throws Exception {SecurityContextHolder.getContext().setAuthentication(CredentialEpochSessions.authentication(account,0));when(store.credentialEpoch(account,false)).thenThrow(new IllegalStateException("unavailable"));var response=new MockHttpServletResponse();var chain=mock(jakarta.servlet.FilterChain.class);CredentialEpochSessions.requests(store).doFilter(new MockHttpServletRequest(),response,chain);assertThat(response.getStatus()).isEqualTo(401);verifyNoInteractions(chain);}
    @Test void saveChecksEpochUnderLockBeforeDelegateAndCommitsTogether()throws Throwable {var s=session(2);guard.persisted.put(s,null);when(store.credentialEpoch(account,true)).thenReturn(2L);var call=invocation("save",s,null);guard.invoke(call);var order=inOrder(store,call,manager);order.verify(store).credentialEpoch(account,true);order.verify(call).proceed();order.verify(manager).commit(any());}
    @Test void loginRotationSaveRaceRejectsPreviouslyVerifiedEpoch()throws Throwable {var s=session(1);guard.persisted.put(s,null);when(store.credentialEpoch(account,true)).thenReturn(2L);var call=invocation("save",s,null);assertThatThrownBy(()->guard.invoke(call)).isInstanceOf(CredentialEpochSessions.Denied.class);verify(call,never()).proceed();verify(manager).rollback(any());}
    @Test void deletedSessionCannotBeResurrected()throws Throwable {var s=session(1);guard.persisted.put(s,s.getId());when(store.credentialEpoch(account,true)).thenReturn(1L);doReturn(false).when(guard).existing(s.getId());var call=invocation("save",s,null);assertThatThrownBy(()->guard.invoke(call)).isInstanceOf(CredentialEpochSessions.Denied.class);verify(call,never()).proceed();}
    @Test void expiredSessionCannotBeSaved()throws Throwable {var s=session(1);s.setLastAccessedTime(java.time.Instant.EPOCH);guard.persisted.put(s,s.getId());when(store.credentialEpoch(account,true)).thenReturn(1L);doReturn(false).when(guard).existing(s.getId());assertThatThrownBy(()->guard.invoke(invocation("save",s,null))).isInstanceOf(CredentialEpochSessions.Denied.class);}
    @Test void unobservedSessionObjectCannotBeIntroduced()throws Throwable {var call=invocation("save",session(0),null);assertThatThrownBy(()->guard.invoke(call)).isInstanceOf(CredentialEpochSessions.Denied.class);verify(call,never()).proceed();}
    @Test void delegateFailureRollsBackSave()throws Throwable {var s=session(0);guard.persisted.put(s,null);when(store.credentialEpoch(account,true)).thenReturn(0L);var call=invocation("save",s,null);when(call.proceed()).thenThrow(new IllegalStateException("save failed"));assertThatThrownBy(()->guard.invoke(call)).isInstanceOf(IllegalStateException.class);verify(manager).rollback(any());}
    @Test void configuredTransactionsJoinRatherThanSuspendTheEpochCheck(){var tx=CredentialEpochSessions.transaction(manager);assertThat(tx.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);assertThat(tx.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);}
    @Test void authenticationSuccessWithoutGuardedRepositoryCannotBeReported(){var request=new MockHttpServletRequest();var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(CredentialEpochSessions.authentication(account,0));assertThatThrownBy(()->CredentialEpochSessions.contexts().saveContext(context,request,new MockHttpServletResponse())).isInstanceOf(CredentialEpochSessions.Denied.class);}
    @Test @SuppressWarnings({"unchecked","rawtypes"}) void installedSpringRepositoryCanBeGuardedWithoutReferencingPrivateJdbcSessionType() {
        var managers=mock(org.springframework.beans.factory.ObjectProvider.class);when(managers.getObject()).thenReturn(manager);
        var stores=mock(org.springframework.beans.factory.ObjectProvider.class);when(stores.getObject()).thenReturn(store);
        var clients=mock(org.springframework.beans.factory.ObjectProvider.class);when(clients.getObject()).thenReturn(mock(JdbcClient.class));
        var processor=CredentialEpochSessions.infrastructure(managers,stores,clients);
        var config=new org.springframework.session.jdbc.config.annotation.web.http.JdbcHttpSessionConfiguration();processor.postProcessAfterInitialization(config,"jdbcConfig");
        var operations=(TransactionTemplate)org.springframework.test.util.ReflectionTestUtils.getField(config,"transactionOperations");
        assertThat(operations.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
        var delegate=new org.springframework.session.jdbc.JdbcIndexedSessionRepository(mock(org.springframework.jdbc.core.JdbcOperations.class),operations);
        delegate.setTableName("cinenest_identity.http_session");Object wrapped=processor.postProcessAfterInitialization(delegate,"sessionRepository");
        assertThat(wrapped).isInstanceOf(org.springframework.session.jdbc.JdbcIndexedSessionRepository.class);assertThat(org.springframework.aop.support.AopUtils.isCglibProxy(wrapped)).isTrue();
        var repo=(SessionRepository<Session>)wrapped;Session session=repo.createSession();var c=SecurityContextHolder.createEmptyContext();c.setAuthentication(CredentialEpochSessions.authentication(account,0));session.setAttribute(CredentialEpochSessions.KEY,c);
        when(store.credentialEpoch(account,true)).thenReturn(0L);assertThatCode(()->repo.save(session)).doesNotThrowAnyException();verify(store).credentialEpoch(account,true);
    }
    @org.springframework.context.annotation.Configuration
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    @org.springframework.web.servlet.config.annotation.EnableWebMvc
    static class SecurityConfig {
        @org.springframework.context.annotation.Bean IdentityStore store(){return mock(IdentityStore.class);}
        @org.springframework.context.annotation.Bean LocalCredentialRecoveryService service(){var s=mock(LocalCredentialRecoveryService.class);when(s.issue(any(),any(),any(),any())).thenReturn(new LocalCredentialRecoveryService.Issued(UUID.randomUUID(),"a".repeat(64),java.time.Instant.now().plusSeconds(60)));return s;}
        @org.springframework.context.annotation.Bean LocalCredentialRecoveryController controller(LocalCredentialRecoveryService s){return new LocalCredentialRecoveryController(s);}
        @org.springframework.context.annotation.Bean TokenEndpoint token(){return new TokenEndpoint();}
        @org.springframework.context.annotation.Bean @SuppressWarnings({"rawtypes","unchecked"}) org.springframework.security.web.SecurityFilterChain chain(org.springframework.security.config.annotation.web.builders.HttpSecurity http,IdentityStore store,org.springframework.core.env.Environment env)throws Exception {
            var empty=new org.springframework.beans.factory.support.DefaultListableBeanFactory();return new IdentitySecurity().identityBoundary(http,store,env,empty.getBeanProvider(FacebookLoginContract.Provider.class),(org.springframework.beans.factory.ObjectProvider)empty.getBeanProvider(org.springframework.security.oauth2.client.web.AuthorizationRequestRepository.class));
        }
    }
    @org.springframework.web.bind.annotation.RestController static class TokenEndpoint {
        @org.springframework.web.bind.annotation.GetMapping("/api/session/csrf") String read(){return "ok";}
    }
    @Test void boundSessionRetainsCsrfAndStrictInputSecurityInTheActualChain()throws Exception {
        var context=new org.springframework.web.context.support.AnnotationConfigWebApplicationContext();var env=new org.springframework.mock.env.MockEnvironment().withProperty("cinenest.identity.manual.enabled","true").withProperty("cinenest.identity.manual.recovery.enabled","true");env.setActiveProfiles("local-synthetic");context.setEnvironment(env);context.setServletContext(new MockServletContext());context.register(SecurityConfig.class);context.refresh();
        try {
            var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",jakarta.servlet.Filter.class)).build();
            var session=new MockHttpSession();var security=SecurityContextHolder.createEmptyContext();security.setAuthentication(CredentialEpochSessions.authentication(account,0));session.setAttribute(CredentialEpochSessions.KEY,security);
            String path="/api/admin/venues/"+UUID.randomUUID()+"/accounts/"+UUID.randomUUID()+"/credential-recovery/capabilities";
            String body=com.nimbusds.jose.util.JSONObjectUtils.toJSONString(Map.of("commandId",UUID.randomUUID(),"issuerGrantId",UUID.randomUUID(),"expectedIssuerGrantRevision",0,"expectedAccountRevision",1,"expectedCredentialRevision",0,"lifetimeSeconds",60,"approvalReference","Synthetic reference"));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).session(session).contentType("application/json").content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
            var request=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/session/csrf").session(session)).andReturn().getRequest();var token=(org.springframework.security.web.csrf.CsrfToken)request.getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).session(session).header(token.getHeaderName(),token.getToken()).contentType("application/json").content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).session(session).header(token.getHeaderName(),token.getToken()).contentType("application/json").content("{\"commandId\":1,\"commandId\":2}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path+"/unknown").session(session).header(token.getHeaderName(),token.getToken()).contentType("application/json").content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        }finally{context.close();}
    }
}
