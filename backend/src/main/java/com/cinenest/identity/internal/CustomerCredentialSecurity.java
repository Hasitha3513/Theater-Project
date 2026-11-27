package com.cinenest.identity.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Shared qualified cryptography only; no account, capability issuer or live policy. */
final class CustomerCredentialSecurity {
    private CustomerCredentialSecurity() {}
    static String encode(String password) {
        return "{pbkdf2-v1}" + LocalCustomerIdentity.credentialEncoder().encode(LocalCustomerIdentity.password(password));
    }
    static boolean encodedShape(String encoded) { return encoded != null && encoded.matches("\\{pbkdf2-v1\\}[0-9a-f]{96}"); }
    static boolean matches(String password, String encoded) {
        if (!encodedShape(encoded)) return false;
        try { return LocalCustomerIdentity.credentialEncoder().matches(LocalCustomerIdentity.password(password),
            encoded.substring("{pbkdf2-v1}".length())); }
        catch (RuntimeException invalid) { return false; }
    }
    static String digest(String value) { return LocalCustomerIdentity.digest(value); }
    static boolean tokenMatches(String raw, String digest) {
        return raw != null && raw.matches("[0-9a-f]{64}") && digest != null && digest.matches("[0-9a-f]{64}")
            && MessageDigest.isEqual(digest(raw).getBytes(StandardCharsets.US_ASCII), digest.getBytes(StandardCharsets.US_ASCII));
    }
}
