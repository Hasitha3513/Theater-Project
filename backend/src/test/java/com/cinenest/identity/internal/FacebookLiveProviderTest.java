package com.cinenest.identity.internal;

import java.nio.file.*;import java.nio.file.attribute.PosixFilePermissions;import java.time.Instant;import java.util.*;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;import org.springframework.mock.web.*;
import org.springframework.test.web.client.MockRestServiceServer;import org.springframework.web.client.RestClient;
import org.springframework.security.oauth2.core.*;import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import static org.assertj.core.api.Assertions.*;import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FacebookLiveProviderTest {
 @TempDir Path directory;String app=""+Math.abs(new java.security.SecureRandom().nextLong()),secret=UUID.randomUUID().toString();
 MockEnvironment env(boolean execution)throws Exception{
  Files.setPosixFilePermissions(directory,PosixFilePermissions.fromString("rwx------"));Path p=directory.resolve("credential");Files.writeString(p,secret);Files.setPosixFilePermissions(p,PosixFilePermissions.fromString("rw-------"));
  return new MockEnvironment().withProperty("cinenest.identity.manual.enabled","true").withProperty("cinenest.identity.facebook.mode","LIVE_QUALIFICATION").withProperty("cinenest.identity.facebook.client-id",app).withProperty("cinenest.identity.facebook.client-secret-file",p.toString()).withProperty("cinenest.identity.facebook.frontend-origin","https://localhost:42137").withProperty("cinenest.identity.facebook.live-execution",Boolean.toString(execution)).withProperty("cinenest.identity.facebook.callback-verified",Boolean.toString(execution));
 }
 Map<String,Object> token(){return Map.of("access_token",UUID.randomUUID().toString(),"token_type","bearer","expires_in",60);}
 Map<String,Object> identity(){return new HashMap<>(Map.of("app_id",app,"user_id","123456789","type","USER","is_valid",true,"expires_at",Instant.now().plusSeconds(60).getEpochSecond(),"data_access_expires_at",Instant.now().plusSeconds(60).getEpochSecond()));}
 void response(MockRestServiceServer server,String endpoint,Object value){server.expect(request->{assertThat(request.getURI().getScheme()+"://"+request.getURI().getHost()+request.getURI().getPath()).isEqualTo(endpoint);assertThat(request.getMethod()).isEqualTo(org.springframework.http.HttpMethod.GET);}).andRespond(withSuccess(com.nimbusds.jose.util.JSONObjectUtils.toJSONString((Map<String,?>)value),org.springframework.http.MediaType.APPLICATION_JSON));}
 @Test void fixedEndpointsPrivateCredentialsAndDefaultDeny()throws Exception{
  var e=env(false);var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();var p=new FacebookLiveProvider(e,builder.build());var r=p.registration();
  assertThat(r.getProviderDetails().getAuthorizationUri()).isEqualTo(FacebookLiveProvider.AUTHORIZATION);assertThat(r.getProviderDetails().getTokenUri()).isEqualTo(FacebookLiveProvider.TOKEN);assertThat(r.getScopes()).containsExactly("public_profile");assertThat(r.getProviderDetails().getIssuerUri()).isNull();assertThat(r.getRedirectUri()).isEqualTo("https://localhost:42137/api/auth/facebook/callback");
  assertThat(p.execution).isFalse();assertThat(p.callbackVerified).isFalse();assertThat(p.toString()).doesNotContain(secret,app);assertThatThrownBy(()->p.inspect("fixture-token")).isInstanceOf(OAuth2AuthenticationException.class).hasNoCause();server.verify();
 }
 @Test void confidentialCodeExchangeAndExactApplicationBoundIdentity()throws Exception{
  var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();var p=new FacebookLiveProvider(env(true),builder.build());response(server,FacebookLiveProvider.TOKEN,token());response(server,FacebookLiveProvider.INSPECTION,Map.of("data",identity()));
  assertThat(p.exchange("fixture-code",p.frontend+"/api/auth/facebook/callback").getAccessToken().getTokenType()).isEqualTo(OAuth2AccessToken.TokenType.BEARER);assertThat(p.inspect("fixture-token")).isEqualTo("123456789");server.verify();
 }
 @Test void wrongAppRevocationExpiryTypeAndMissingIdentityNeverAdmit()throws Exception{
  for(String field:List.of("app_id","is_valid","expires_at","data_access_expires_at","type","user_id")){
   var data=identity();data.put(field,switch(field){case "app_id"->"wrong-app";case "is_valid"->false;case "expires_at","data_access_expires_at"->1;case "type"->"APP";default->"";});
   var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();response(server,FacebookLiveProvider.INSPECTION,Map.of("data",data));var p=new FacebookLiveProvider(env(true),builder.build());assertThatThrownBy(()->p.inspect("fixture-token")).isInstanceOf(OAuth2AuthenticationException.class).hasNoCause();server.verify();
  }
 }
 @Test void providerFailuresRedirectOversizeAndDuplicateKeysAreSanitized()throws Exception{
  for(int n=0;n<6;n++){
   var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();var expected=server.expect(request->assertThat(request.getURI().getHost()).isEqualTo("graph.facebook.com"));
   if(n==0)expected.andRespond(withStatus(org.springframework.http.HttpStatus.FOUND).location(java.net.URI.create("https://evil.invalid")));
   else if(n==1)expected.andRespond(withException(new java.net.SocketTimeoutException("private diagnostic")));
   else expected.andRespond(withSuccess(n==2?"private diagnostic":n==3?" ".repeat(65537):n==4?"{\"data\":{},\"data\":{}}":"{}{}",org.springframework.http.MediaType.APPLICATION_JSON));
   var p=new FacebookLiveProvider(env(true),builder.build());assertThatThrownBy(()->p.inspect("fixture-token")).isInstanceOf(OAuth2AuthenticationException.class).hasNoCause().hasMessageNotContaining(secret).hasMessageNotContaining("private diagnostic").hasMessageNotContaining("fixture-token");server.verify();
  }
 }
 @Test void callbackAndUnsafeConfigurationAreRejectedBeforeNetwork()throws Exception{
  var e=env(false);for(var pair:Map.of("cinenest.identity.facebook.client-id","synthetic-invalid","cinenest.identity.facebook.contract-origin","http://127.0.0.1:1234","cinenest.identity.facebook.client-secret","inline-secret","cinenest.identity.facebook.frontend-origin","https://user:password@localhost:42137/callback").entrySet()){
   var copy=env(false);copy.setProperty(pair.getKey(),pair.getValue());assertThatThrownBy(()->new FacebookLiveProvider(copy)).isInstanceOf(IllegalStateException.class).hasNoCause();
  }
  e.setProperty("cinenest.identity.facebook.live-execution","true");assertThatThrownBy(()->new FacebookLiveProvider(e)).isInstanceOf(IllegalStateException.class);
  var p=new FacebookLiveProvider(env(false));assertThatThrownBy(()->p.exchange("code","https://evil.invalid/callback")).isInstanceOf(OAuth2AuthenticationException.class);
 }
 @Test void existingEligibleAssociationOnlyNoAccountWrites()throws Exception{
  for(boolean associated:List.of(true,false)){
   var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();response(server,FacebookLiveProvider.INSPECTION,Map.of("data",identity()));var provider=new FacebookLoginContract.Provider(env(true),builder.build());var store=mock(IdentityStore.class);UUID account=UUID.randomUUID();when(store.associatedEligibleAccount("urn:cinenest:identity:facebook:app:"+app,"123456789")).thenReturn(associated?Optional.of(account):Optional.empty());var request=new OAuth2UserRequest(provider.registration(),new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"fixture-token",Instant.now(),Instant.now().plusSeconds(60)));
   if(associated)assertThat(provider.authenticate(request,store).getName()).isEqualTo(account.toString());else assertThatThrownBy(()->provider.authenticate(request,store)).isInstanceOf(OAuth2AuthenticationException.class);
   verify(store).associatedEligibleAccount("urn:cinenest:identity:facebook:app:"+app,"123456789");verifyNoMoreInteractions(store);server.verify();
  }
 }
 @Test void liveGateDeniesStartCallbackWithoutSessions()throws Exception{
  for(String path:List.of("/oauth2/authorization/facebook","/login/oauth2/code/facebook")){
   var request=new MockHttpServletRequest("GET",path);request.setServletPath(path);var response=new MockHttpServletResponse();new FacebookLiveExecutionGate(false).doFilter(request,response,(a,b)->{throw new AssertionError("Gate escaped");});assertThat(response.getStatus()).isEqualTo(503);assertThat(request.getSession(false)).isNull();
  }
 }
 @Test void manualGoogleLiveAndFacebookLiveComposeWithoutProviderExecution()throws Exception{
  var e=env(false).withProperty("cinenest.identity.facebook.frontend-origin","http://127.0.0.1:42137").withProperty("cinenest.identity.google.enabled","true").withProperty("cinenest.identity.google.mode","LIVE_QUALIFICATION").withProperty("cinenest.identity.google.client-id","controlled-fixture.apps.googleusercontent.com").withProperty("cinenest.identity.google.client-secret-file",directory.resolve("credential").toString()).withProperty("cinenest.identity.google.frontend-origin","http://127.0.0.1:42137");
  var clients=new FacebookLoginContract().configuredClients(new FacebookLoginContract.Provider(e),e,GoogleLiveProviderTest::metadata);
  assertThat(clients.findByRegistrationId("facebook").getProviderDetails().getAuthorizationUri()).isEqualTo(FacebookLiveProvider.AUTHORIZATION);assertThat(clients.findByRegistrationId("google").getProviderDetails().getIssuerUri()).isEqualTo(GoogleLiveProvider.ISSUER);
 }
}
