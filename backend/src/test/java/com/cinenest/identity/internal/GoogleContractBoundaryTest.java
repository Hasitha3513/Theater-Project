package com.cinenest.identity.internal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class GoogleContractBoundaryTest {
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
