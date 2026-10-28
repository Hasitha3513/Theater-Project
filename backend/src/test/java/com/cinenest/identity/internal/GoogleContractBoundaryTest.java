package com.cinenest.identity.internal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class GoogleContractBoundaryTest {
 @Test void developmentTlsFrontendDoesNotExpandProviderOrArbitraryOriginTrust(){
  assertThat(GoogleOidcContract.frontendOrigin("https://localhost:4443").getScheme()).isEqualTo("https");
  assertThat(GoogleOidcContract.frontendOrigin("http://127.0.0.1:42137").getScheme()).isEqualTo("http");
  for(String origin:new String[]{"https://localhost:4444","https://evil.invalid:4443","https://localhost:4443/path","https://localhost:4443?x=1","https://user@localhost:4443","https://localhost:4443.evil.invalid"})
   assertThatThrownBy(()->GoogleOidcContract.frontendOrigin(origin)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->GoogleOidcContract.loopback("https://localhost:4443")).isInstanceOf(IllegalArgumentException.class);
  var client=new GoogleOidcContract().googleContractClient(true,"CONTRACT_TEST","http://127.0.0.1:32123","fixture","fixture-secret","https://localhost:4443").findByRegistrationId("google");
  assertThat(client.getRedirectUri()).isEqualTo("https://localhost:4443/api/auth/google/callback");
  assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
 }
 @Test void onlyExplicitLoopbackContractOriginsAreAdmitted(){
  assertThat(GoogleOidcContract.loopback("http://127.0.0.1:32123").getPort()).isEqualTo(32123);
  for(String origin:new String[]{"https://accounts.google.com","http://evil.invalid:123","http://user:secret@127.0.0.1:123","http://127.0.0.1:123/private","http://127.0.0.1:123?secret=1"})assertThatThrownBy(()->GoogleOidcContract.loopback(origin)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void liveActivationAndStandaloneReplacementAreRejectedBeforeAnyNetworkCall(){
  var config=new GoogleOidcContract();
  assertThatThrownBy(()->config.googleContractClient(true,"LIVE","http://127.0.0.1:123","fixture","fixture-secret","http://127.0.0.1:321")).isInstanceOf(IllegalStateException.class);
  assertThatThrownBy(()->config.googleContractClient(false,"CONTRACT_TEST","http://127.0.0.1:123","fixture","fixture-secret","http://127.0.0.1:321")).isInstanceOf(IllegalStateException.class);
 }
}
