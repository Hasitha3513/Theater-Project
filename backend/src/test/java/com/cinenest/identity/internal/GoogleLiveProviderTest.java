package com.cinenest.identity.internal;

import com.nimbusds.jose.util.JSONObjectUtils;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GoogleLiveProviderTest {
 @TempDir Path privateDir;
 static Map<String,Object> metadata(){return Map.of("issuer",GoogleLiveProvider.ISSUER,"authorization_endpoint",GoogleLiveProvider.AUTHORIZATION,"token_endpoint",GoogleLiveProvider.TOKEN,"jwks_uri",GoogleLiveProvider.JWKS,"response_types_supported",List.of("code"),"code_challenge_methods_supported",List.of("S256"),"id_token_signing_alg_values_supported",List.of("RS256"),"token_endpoint_auth_methods_supported",List.of("client_secret_basic"));}
 Path secret() throws Exception {Files.setPosixFilePermissions(privateDir,PosixFilePermissions.fromString("rwx------"));Path p=privateDir.resolve("credential");Files.writeString(p,"controlled-test-secret\n");Files.setPosixFilePermissions(p,PosixFilePermissions.fromString("rw-------"));return p;}
 @Test void fixedDiscoveryAndValidatedRegistrationHaveNoArbitraryEndpoints(){
  var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();server.expect(requestTo(GoogleLiveProvider.DISCOVERY)).andRespond(withSuccess(JSONObjectUtils.toJSONString(metadata()),org.springframework.http.MediaType.APPLICATION_JSON));
  var r=GoogleLiveProvider.registration("controlled-fixture.apps.googleusercontent.com","controlled-test-secret","http://127.0.0.1:42137",GoogleLiveProvider.discover(builder.build()));server.verify();
  assertThat(r.getRedirectUri()).isEqualTo("http://127.0.0.1:42137/api/auth/google/callback");assertThat(r.getClientSettings().isRequireProofKey()).isTrue();assertThat(r.getScopes()).containsExactly("openid");assertThat(r.getProviderDetails().getIssuerUri()).isEqualTo(GoogleLiveProvider.ISSUER);assertThat(r.getProviderDetails().getUserInfoEndpoint().getUri()).isNull();assertThat(r.getClientAuthenticationMethod()).isEqualTo(org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
 }
 @Test void maliciousMissingOrUnsupportedDiscoveryFailsClosed(){
  for(String key:List.of("issuer","authorization_endpoint","token_endpoint","jwks_uri"))for(Object bad:List.of("https://evil.invalid/steal","http://accounts.google.com","https://accounts.google.com@evil.invalid/","https://accounts.google.com/o/oauth2/v2/auth?secret=1",List.of("ambiguous"))){var m=new HashMap<>(metadata());m.put(key,bad);assertThatThrownBy(()->GoogleLiveProvider.validateMetadata(m)).isInstanceOf(IllegalStateException.class);}
  for(String key:metadata().keySet()){var m=new HashMap<>(metadata());m.remove(key);assertThatThrownBy(()->GoogleLiveProvider.validateMetadata(m)).isInstanceOf(IllegalStateException.class);}
 }
 @Test void discoveryRedirectMalformedOversizeAndUnavailableAreSanitized(){
  for(int n=0;n<5;n++){
   var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();var request=server.expect(requestTo(GoogleLiveProvider.DISCOVERY));
   if(n==0)request.andRespond(withStatus(org.springframework.http.HttpStatus.FOUND).location(java.net.URI.create("https://evil.invalid")));
   else if(n==1)request.andRespond(withSuccess("private diagnostic",org.springframework.http.MediaType.TEXT_PLAIN));
   else if(n==2)request.andRespond(withSuccess("private diagnostic",org.springframework.http.MediaType.APPLICATION_JSON));
   else if(n==3)request.andRespond(withSuccess(" ".repeat(65537),org.springframework.http.MediaType.APPLICATION_JSON));
   else request.andRespond(withException(new java.net.SocketTimeoutException("private diagnostic")));
   assertThatThrownBy(()->GoogleLiveProvider.discover(builder.build())).isInstanceOf(IllegalStateException.class).hasMessage("Google qualification discovery unavailable or invalid").hasNoCause();server.verify();
  }
 }
 @Test void privateSecretIsBoundedAndNeverLeakedThroughErrors() throws Exception {
  Path p=secret();assertThat(GoogleLiveProvider.secret(p.toString())).isEqualTo("controlled-test-secret");
  for(String permissions:List.of("rw-r--r--","rw-rw----")){Files.setPosixFilePermissions(p,PosixFilePermissions.fromString(permissions));assertThatThrownBy(()->GoogleLiveProvider.secret(p.toString())).hasMessage("Private Google qualification secret file required").hasNoCause();}
  Files.setPosixFilePermissions(p,PosixFilePermissions.fromString("rw-------"));Files.writeString(p,"x".repeat(8193));assertThatThrownBy(()->GoogleLiveProvider.secret(p.toString())).hasMessage("Private Google qualification secret file required");
  Files.writeString(p,"bad\nsecret");assertThatThrownBy(()->GoogleLiveProvider.secret(p.toString())).hasMessage("Private Google qualification secret file required");
  Path link=privateDir.resolve("link");Files.createSymbolicLink(link,p);assertThatThrownBy(()->GoogleLiveProvider.secret(link.toString())).hasMessage("Private Google qualification secret file required");
  assertThatThrownBy(()->GoogleLiveProvider.secret("relative-secret")).hasMessage("Private Google qualification secret file required");
 }
 @Test void enabledLiveConfigurationUsesPrivateCredentialsButRequiresSeparateExecution() throws Exception {
  var env=new MockEnvironment().withProperty("cinenest.identity.manual.enabled","true").withProperty("cinenest.identity.google.mode","LIVE_QUALIFICATION").withProperty("cinenest.identity.google.client-id","controlled-fixture.apps.googleusercontent.com").withProperty("cinenest.identity.google.frontend-origin","http://127.0.0.1:42137").withProperty("cinenest.identity.google.client-secret-file",secret().toString());
  {
   assertThat(new GoogleOidcContract().configuredClient(env,GoogleLiveProviderTest::metadata).findByRegistrationId("google").getClientId()).isEqualTo("controlled-fixture.apps.googleusercontent.com");
   env.setProperty("cinenest.identity.google.client-secret","must-not-inline");assertThatThrownBy(()->new GoogleOidcContract().configuredClient(env,GoogleLiveProviderTest::metadata)).isInstanceOf(IllegalStateException.class);env.setProperty("cinenest.identity.google.client-secret","");
   env.setProperty("cinenest.identity.google.contract-issuer","http://127.0.0.1:1234");assertThatThrownBy(()->new GoogleOidcContract().configuredClient(env,GoogleLiveProviderTest::metadata)).isInstanceOf(IllegalStateException.class);
  }
  assertThatThrownBy(()->new GoogleOidcContract().googleClient(new MockEnvironment())).isInstanceOf(IllegalStateException.class);
 }
 @Test void liveExecutionDefaultDenyCoversStartAndCallback() throws Exception {
  var gate=new GoogleLiveExecutionGate(false);
  for(String path:List.of("/oauth2/authorization/google","/login/oauth2/code/google")){
   var request=new MockHttpServletRequest("GET",path);request.setServletPath(path);var response=new MockHttpServletResponse();boolean[] invoked={false};gate.doFilter(request,response,(a,b)->invoked[0]=true);assertThat(response.getStatus()).isEqualTo(503);assertThat(invoked[0]).isFalse();assertThat(response.getContentAsString()).isEmpty();
  }
  var status=new GoogleOidcContract.Status("LIVE_QUALIFICATION",false).status(new MockHttpServletRequest());assertThat(status.getBody().toString()).contains("executionEnabled=false","liveProviderQualified=false");
 }
 @Test void invalidClientAndNonlocalCallbackAreRejected(){
  for(String client:List.of("", "arbitrary", "secret@evil.invalid"))assertThatThrownBy(()->GoogleLiveProvider.registration(client,"fixture","http://127.0.0.1:42137",metadata())).isInstanceOf(IllegalStateException.class);
  assertThatThrownBy(()->GoogleLiveProvider.registration("controlled-fixture.apps.googleusercontent.com","fixture","https://evil.invalid",metadata())).isInstanceOf(IllegalArgumentException.class);
 }
}
