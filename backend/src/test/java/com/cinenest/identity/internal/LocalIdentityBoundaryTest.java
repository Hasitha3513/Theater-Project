package com.cinenest.identity.internal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalIdentityBoundaryTest {
 @Test void enabledManualAuthenticationRefusesNonLocalProfile() {
  var env=new MockEnvironment().withProperty("cinenest.identity.manual.enabled","true");
  assertThatThrownBy(()->new IdentitySecurity().identityBoundary(mock(HttpSecurity.class),mock(IdentityStore.class),env,mock(org.springframework.beans.factory.ObjectProvider.class),mock(org.springframework.beans.factory.ObjectProvider.class))).isInstanceOf(IllegalStateException.class);
 }
 @Test void emailNormalizationNeverAppliesProviderSpecificAliases() {
  assertThat(LocalCustomerIdentity.email(" Test+One@EXAMPLE.TEST ")).isEqualTo("test+one@example.test");
  assertThat(LocalCustomerIdentity.email("test.one@example.test")).isNotEqualTo(LocalCustomerIdentity.email("testone@example.test"));
  for(String value:new String[]{"real@gmail.com","staff@example.test.evil","a\n@example.test","@example.test"})assertThatThrownBy(()->LocalCustomerIdentity.email(value)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void unicodePasswordsHaveCodePointBoundsAndNoSilentTruncation() {
  String good="\uD83C\uDFAC".repeat(128);assertThat(LocalCustomerIdentity.password(good)).isEqualTo(good);
  assertThatThrownBy(()->LocalCustomerIdentity.password(good+"x")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->LocalCustomerIdentity.password("\uD83C\uDFAC".repeat(14))).isInstanceOf(IllegalArgumentException.class);
  assertThat(LocalCustomerIdentity.password("e\u0301".repeat(15))).isEqualTo("é".repeat(15));
 }
 @Test void credentialDiagnosticIsRedacted() {
  assertThat(new LocalCustomerIdentity.Credential(java.util.UUID.randomUUID(),"PRIVATE_HASH",true,"ELIGIBLE",true).toString()).isEqualTo("Credential[REDACTED]");
 }
}
