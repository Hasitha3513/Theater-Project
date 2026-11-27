package com.cinenest.identity.internal;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CustomerCredentialSecurityTest {
    @Test void usesQualifiedSaltedPasswordEncodingAndNeverStoresRawPassword() {
        String password = "Synthetic password sample 2026!";
        String one = CustomerCredentialSecurity.encode(password), two = CustomerCredentialSecurity.encode(password);
        assertThat(one).startsWith("{pbkdf2-v1}").doesNotContain(password).isNotEqualTo(two);
        assertThat(CustomerCredentialSecurity.matches(password, one)).isTrue();
        assertThat(CustomerCredentialSecurity.matches("Wrong password sample 2026!", one)).isFalse();
        assertThat(CustomerCredentialSecurity.matches(password, "{unapproved}hash")).isFalse();
        assertThat(CustomerCredentialSecurity.matches(password, "{pbkdf2-v1}not-hex")).isFalse();
    }
    @Test void sharedPasswordPolicyRemainsEnforced() {
        for (String p : new String[] {"short", "passwordpassword", "line\nbreak secret password", "a".repeat(257)})
            assertThatThrownBy(() -> CustomerCredentialSecurity.encode(p)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void capabilityComparisonRequiresExactBoundedDigestAndToken() {
        String token = "a".repeat(64), digest = CustomerCredentialSecurity.digest(token);
        assertThat(CustomerCredentialSecurity.tokenMatches(token, digest)).isTrue();
        assertThat(CustomerCredentialSecurity.tokenMatches("b".repeat(64), digest)).isFalse();
        assertThat(CustomerCredentialSecurity.tokenMatches(token, token)).isFalse();
        assertThat(CustomerCredentialSecurity.tokenMatches(null, digest)).isFalse();
        assertThat(CustomerCredentialSecurity.tokenMatches(token + "a", digest)).isFalse();
        assertThat(CustomerCredentialSecurity.tokenMatches(token, null)).isFalse();
    }
}
