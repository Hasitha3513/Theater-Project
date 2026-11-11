package com.cinenest.pricing.internal;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/** Exact commercial scope shared by approval and issuance. */
interface OperationalCommercialAuthority {
    Pattern SHA256 = Pattern.compile("[a-f0-9]{64}");

    record Scope(UUID venue, UUID room, UUID version, UUID policy, long revision, String policyHash) {
        public Scope {
            if (venue == null || room == null || version == null || policy == null || revision < 0
                || policyHash == null || !SHA256.matcher(policyHash).matches()) {
                throw new IllegalArgumentException("Invalid commercial authority scope");
            }
        }
    }

    record Evidence(String namespace, long sequence, String revocationDigest, Instant validUntil) {}

    default boolean qualificationOnly() { return true; }

    /** Admit signed revocation observations independently of command approval. */
    Evidence verifyRevocations(Instant now);

    Evidence verify(Scope scope, Instant now);
}
