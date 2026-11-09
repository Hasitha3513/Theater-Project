package com.cinenest.pricing.internal;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

record CommercialPolicyApproval(
        UUID reviewer, UUID venue, UUID room, UUID packageVersion, UUID policy, UUID appointmentId, UUID nonce,
        long revision, long revocationVersion, String contentHash, String domain, String outcome, String keyId,
        Instant issuedAt, Instant expiresAt) {
    private static final String FORMAT = "CINENEST-COMMERCIAL-POLICY-APPROVAL-V1";
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9_-]{1,80}");
    private static final Set<String> OUTCOMES = Set.of("APPROVE", "REJECT", "REVOKE");

    CommercialPolicyApproval {
        if (revision < 0 || revocationVersion < 0 || !HASH.matcher(contentHash).matches()
                || !CommercialPolicyDraft.DOMAINS.contains(domain) || !OUTCOMES.contains(outcome)
                || !KEY_ID.matcher(keyId).matches() || !issuedAt.isBefore(expiresAt)) {
            throw new IllegalArgumentException("Invalid trusted approval payload");
        }
    }

    byte[] canonicalPayload() {
        return String.join("\n", FORMAT, reviewer.toString(), venue.toString(), room.toString(),
                packageVersion.toString(), policy.toString(), appointmentId.toString(), nonce.toString(),
                Long.toString(revision), Long.toString(revocationVersion), contentHash, domain, outcome, keyId,
                issuedAt.toString(), expiresAt.toString()).getBytes(StandardCharsets.US_ASCII);
    }

    static CommercialPolicyApproval parse(byte[] payload) {
        String[] fields = new String(payload, StandardCharsets.US_ASCII).split("\n", -1);
        if (fields.length != 16 || !FORMAT.equals(fields[0])) {
            throw new IllegalArgumentException("Invalid trusted approval format");
        }
        var approval = new CommercialPolicyApproval(
                UUID.fromString(fields[1]), UUID.fromString(fields[2]), UUID.fromString(fields[3]),
                UUID.fromString(fields[4]), UUID.fromString(fields[5]), UUID.fromString(fields[6]),
                UUID.fromString(fields[7]), Long.parseLong(fields[8]), Long.parseLong(fields[9]),
                fields[10], fields[11], fields[12], fields[13], Instant.parse(fields[14]), Instant.parse(fields[15]));
        if (!Arrays.equals(payload, approval.canonicalPayload())) {
            throw new IllegalArgumentException("Noncanonical trusted approval payload");
        }
        return approval;
    }
}
