package com.cinenest.identity.internal;
import com.cinenest.FacebookContractIssuer;import java.time.*;import java.util.Map;
import org.junit.jupiter.api.*;import org.springframework.mock.env.MockEnvironment;import org.springframework.mock.web.*;import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import static org.assertj.core.api.Assertions.*;
class FacebookContractBoundaryTest {
 @Test void featureIsDisabledByDefaultAndOutsideLocalProfile() {
  new org.springframework.boot.test.context.runner.ApplicationContextRunner().withUserConfiguration(FacebookLoginContract.class).withPropertyValues("cinenest.identity.facebook.enabled=true").run(c->assertThat(c).doesNotHaveBean(FacebookLoginContract.Provider.class));
  new org.springframework.boot.test.context.runner.ApplicationContextRunner().withUserConfiguration(FacebookLoginContract.class).withInitializer(c->c.getEnvironment().setActiveProfiles("local-synthetic")).run(c->assertThat(c).doesNotHaveBean(FacebookLoginContract.Provider.class));
 }
 @Test void transactionsExpireAndConsumeExactlyOnce() {
  class MutableClock extends Clock {Instant now=Instant.parse("2026-10-27T00:00:00Z");public Instant instant(){return now;}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}}
  var clock=new MutableClock();var repository=new FacebookLoginContract.Transactions("http://127.0.0.1:1234/dialog",30,clock);var request=new MockHttpServletRequest();var response=new MockHttpServletResponse();request.setParameter("state","synthetic-state");
  var authorization=OAuth2AuthorizationRequest.authorizationCode().authorizationUri("http://127.0.0.1:1234/dialog").clientId("synthetic").redirectUri("http://127.0.0.1:1235/callback").state("synthetic-state").build();repository.saveAuthorizationRequest(authorization,request,response);assertThat(repository.loadAuthorizationRequest(request)).isNotNull();clock.now=clock.now.plusSeconds(30);assertThat(repository.removeAuthorizationRequest(request,response)).isNull();assertThat(repository.removeAuthorizationRequest(request,response)).isNull();
  repository.saveAuthorizationRequest(authorization,request,response);assertThat(repository.removeAuthorizationRequest(request,response)).isNotNull();assertThat(repository.removeAuthorizationRequest(request,response)).isNull();
 }
 @Test void liveOriginsModesCredentialsAndUnboundedStateAreRejected()throws Exception {
  try(var fixture=new FacebookContractIssuer()){
   var env=new MockEnvironment().withProperty("cinenest.identity.manual.enabled","true").withProperty("cinenest.identity.facebook.mode","CONTRACT_TEST").withProperty("cinenest.identity.facebook.contract-origin",fixture.origin()).withProperty("cinenest.identity.facebook.frontend-origin","http://127.0.0.1:12345").withProperty("cinenest.identity.facebook.client-id",fixture.appId).withProperty("cinenest.identity.facebook.client-secret-file",fixture.secretFile.toString());assertThat(new FacebookLoginContract.Provider(env).toString()).doesNotContain(fixture.secret);
   env.setProperty("cinenest.identity.facebook.frontend-origin","https://localhost:4443");
   assertThat(new FacebookLoginContract.Provider(env).registration().getRedirectUri()).isEqualTo("https://localhost:4443/api/auth/facebook/callback");
   for(var pair:Map.of("cinenest.identity.facebook.mode","LIVE_QUALIFICATION","cinenest.identity.facebook.contract-origin","https://www.facebook.com","cinenest.identity.facebook.client-id","123456789","cinenest.identity.facebook.transaction-seconds","0","cinenest.identity.facebook.client-secret","forbidden-inline").entrySet()){
    String previous=env.getProperty(pair.getKey());env.setProperty(pair.getKey(),pair.getValue());assertThatThrownBy(()->new FacebookLoginContract.Provider(env)).isInstanceOf(IllegalStateException.class).hasMessageNotContaining(fixture.secret);if(previous==null)env.getPropertySources().get("mockProperties");env.setProperty(pair.getKey(),previous==null?"":previous);
   }
  }
 }
}
