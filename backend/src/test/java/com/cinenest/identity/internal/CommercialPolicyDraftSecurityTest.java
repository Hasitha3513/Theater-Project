package com.cinenest.identity.internal;
import jakarta.servlet.Filter;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class CommercialPolicyDraftSecurityTest {
 static final String ROOT="/api/admin/venues/"+UUID.randomUUID()+"/commercial-policies";
 AnnotationConfigWebApplicationContext context;MockMvc mvc;
 @Configuration @EnableWebSecurity @EnableWebMvc static class SecurityConfig {
  @Bean IdentityStore store(){return mock(IdentityStore.class);}
  @Bean Endpoint endpoint(){return new Endpoint();}
  @Bean @SuppressWarnings({"unchecked","rawtypes"}) SecurityFilterChain chain(HttpSecurity http,IdentityStore store,Environment env)throws Exception{
   var empty=new DefaultListableBeanFactory();return new IdentitySecurity().identityBoundary(http,store,env,empty.getBeanProvider(FacebookLoginContract.Provider.class),
    (org.springframework.beans.factory.ObjectProvider)empty.getBeanProvider(org.springframework.security.oauth2.client.web.AuthorizationRequestRepository.class));
  }
 }
 @RestController static class Endpoint {
  @GetMapping({"/api/admin/venues/{v}/commercial-policies","/api/admin/venues/{v}/commercial-policies/{p}","/api/admin/venues/{v}/commercial-policies/{p}/history","/api/session/csrf"}) String read(){return "ok";}
  @PostMapping({"/api/admin/venues/{v}/commercial-policies","/api/admin/venues/{v}/commercial-policies/{p}/reviews"}) String create(){return "ok";}
 }
 @BeforeEach void setup(){var env=new MockEnvironment().withProperty("cinenest.identity.manual.enabled","true");env.setActiveProfiles("local-synthetic");context=new AnnotationConfigWebApplicationContext();context.setEnvironment(env);context.setServletContext(new MockServletContext());context.register(SecurityConfig.class);context.refresh();mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",Filter.class)).build();}
 @AfterEach void close(){context.close();SecurityContextHolder.clearContext();}
 MockHttpSession authenticated(){var session=new MockHttpSession();var security=SecurityContextHolder.createEmptyContext();security.setAuthentication(CredentialEpochSessions.authentication(UUID.randomUUID(),0));session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,security);return session;}
 @Test void anonymousAndUnapprovedPathsDenied()throws Exception{mvc.perform(get(ROOT)).andExpect(status().isUnauthorized());mvc.perform(get(ROOT).session(authenticated())).andExpect(status().isOk());mvc.perform(post(ROOT+"/"+UUID.randomUUID()+"/activate").session(authenticated())).andExpect(status().isForbidden());}
 @Test void epochlessSessionsDenied()throws Exception{var session=authenticated();var security=SecurityContextHolder.createEmptyContext();security.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(UUID.randomUUID().toString(),null,List.of()));session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,security);mvc.perform(get(ROOT).session(session)).andExpect(status().isUnauthorized());}
 @Test void staleEpochDenied()throws Exception{when(context.getBean(IdentityStore.class).credentialEpoch(any(),eq(false))).thenReturn(1L);mvc.perform(get(ROOT).session(authenticated())).andExpect(status().isUnauthorized());}
 @Test void csrfProtectsBothWrites()throws Exception{var session=authenticated();for(String path:List.of(ROOT,ROOT+"/"+UUID.randomUUID()+"/reviews")){mvc.perform(post(path).session(session)).andExpect(status().isForbidden());var request=mvc.perform(get("/api/session/csrf").session(session)).andReturn().getRequest();var token=(CsrfToken)request.getAttribute(CsrfToken.class.getName());mvc.perform(post(path).session(session).header(token.getHeaderName(),token.getToken())).andExpect(status().isOk());}}
 @Test void fivePermissionsHaveNoWriteEquivalentEscalation()throws Exception{var method=DatabaseScopedAuthorization.class.getDeclaredMethod("writeEquivalent",String.class);method.setAccessible(true);var auth=new DatabaseScopedAuthorization(mock(org.springframework.jdbc.core.simple.JdbcClient.class));for(String p:List.of("commercial.policy.read","commercial.policy.edit","commercial.policy.review.finance","commercial.policy.review.legal","commercial.policy.review.operations"))assertThat(method.invoke(auth,p)).isEqualTo(p);}
 @Test void sessionExpiryMustBeExplicitAndPositive(){
  var security=new IdentitySecurity();
  assertThatThrownBy(()->security.sessionPolicy(0)).isInstanceOf(IllegalStateException.class);
  var repository=mock(org.springframework.session.jdbc.JdbcIndexedSessionRepository.class);
  security.sessionPolicy(120).customize(repository);
  verify(repository).setDefaultMaxInactiveInterval(java.time.Duration.ofSeconds(120));
 }

}
