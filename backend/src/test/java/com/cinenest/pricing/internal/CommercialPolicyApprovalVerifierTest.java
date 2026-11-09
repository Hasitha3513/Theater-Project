package com.cinenest.pricing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class CommercialPolicyApprovalVerifierTest {
    private static final Instant NOW = Instant.parse("2030-06-01T12:00:00Z");
    private final UUID reviewer = UUID.randomUUID();
    private final UUID venue = UUID.randomUUID();
    private final UUID room = UUID.randomUUID();
    private final UUID packageVersion = UUID.randomUUID();
    private final UUID policy = UUID.randomUUID();
    private final UUID appointmentId = UUID.randomUUID();
    private final UUID nonce = UUID.randomUUID();
    private final UUID appointingAuthority = UUID.randomUUID();
    private final UUID identityVerifier = UUID.randomUUID();
    private final UUID keyCustodian = UUID.randomUUID();
    private final UUID revocationAuthority = UUID.randomUUID();
    private final Instant start = NOW.minusSeconds(600);
    private final Instant end = NOW.plusSeconds(600);
    private final MockEnvironment environment = new MockEnvironment();
    private KeyPair signer;
    private KeyPair revoker;
    private CommercialPolicyApprovalTrustProperties.Key signingKey;
    private CommercialPolicyApprovalTrustProperties.Key revokingKey;

    @BeforeEach
    void setup() throws Exception {
        var generator = KeyPairGenerator.getInstance("Ed25519");
        signer = generator.generateKeyPair();
        revoker = generator.generateKeyPair();
        signingKey = key("reviewer-v2", reviewer, signer, false);
        revokingKey = key("revoke-v1", revocationAuthority, revoker, false);
        environment.setActiveProfiles("local-synthetic");
    }

    @Test
    void acceptsExactSyntheticScopeAndTrustedSignatures() throws Exception {
        var oldSigner = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var oldKey = key("reviewer-v1", reviewer, oldSigner, true);
        var verifier = verifier(true, NOW, List.of(appointment(false, false)), List.of(oldKey, signingKey, revokingKey));
        var approve = approval(venue, "FINANCE", "APPROVE", signingKey.id(), NOW, 2);
        var reject = approval(venue, "FINANCE", "REJECT", signingKey.id(), NOW, 2);
        var revoke = approval(venue, "FINANCE", "REVOKE", revokingKey.id(), NOW, 2);

        assertThat(verifier.verify(signed(approve.canonicalPayload(), signer), NOW)).isEqualTo(approve);
        assertThat(verifier.verify(signed(reject.canonicalPayload(), signer), NOW)).isEqualTo(reject);
        assertThat(verifier.verify(signed(revoke.canonicalPayload(), revoker), NOW)).isEqualTo(revoke);
        denied(verifier, signed(approval(venue, "FINANCE", "APPROVE", oldKey.id(), NOW, 2).canonicalPayload(), oldSigner), 403);
        denied(verifier, signed(approval(venue, "FINANCE", "REVOKE", signingKey.id(), NOW, 2).canonicalPayload(), signer), 403);
        denied(verifier, signed(approval(venue, "FINANCE", "APPROVE", revokingKey.id(), NOW, 2).canonicalPayload(), revoker), 403);
    }

    @Test
    void failsClosedWhenDisabledNonSyntheticOrRevocationEvidenceIsStale() throws Exception {
        var approved = approval(venue, "FINANCE", "APPROVE", signingKey.id(), NOW, 2);
        var envelope = signed(approved.canonicalPayload(), signer);
        denied(verifier(false, NOW, List.of(appointment(false, false)), List.of(signingKey)), envelope, 503);
        denied(verifier(true, NOW.minusSeconds(901), List.of(appointment(false, false)), List.of(signingKey)), envelope, 503);
        denied(verifier(true, NOW.plusSeconds(1), List.of(appointment(false, false)), List.of(signingKey)), envelope, 503);
        var verifier = verifier(true, NOW, List.of(appointment(false, false)), List.of(signingKey));
        environment.setActiveProfiles("local-synthetic", "production");
        denied(verifier, envelope, 503);
    }

    @Test
    void rejectsNoncanonicalOrForgedEnvelope() throws Exception {
        var approved = approval(venue, "FINANCE", "APPROVE", signingKey.id(), NOW, 2);
        var verifier = verifier(true, NOW, List.of(appointment(false, false)), List.of(signingKey));
        var payload = approved.canonicalPayload();
        var canonical = new String(payload, StandardCharsets.US_ASCII);
        var leadingZero = canonical.replace("\n0\n2\n", "\n00\n2\n").getBytes(StandardCharsets.US_ASCII);
        denied(verifier, signed(leadingZero, signer), 400);
        denied(verifier, signed(payload, signer) + "=", 400);
        denied(verifier, signed(payload, revoker), 403);
        denied(verifier, signed(payload, signer).replace(".", ".."), 400);
    }

    @Test
    void rejectsWrongScopeVersionAndTime() throws Exception {
        var verifier = verifier(true, NOW, List.of(appointment(false, false)), List.of(signingKey));
        for (var deniedApproval : List.of(
                approval(UUID.randomUUID(), "FINANCE", "APPROVE", signingKey.id(), NOW, 2),
                approval(venue, "LEGAL", "APPROVE", signingKey.id(), NOW, 2),
                approval(venue, "FINANCE", "APPROVE", signingKey.id(), NOW, 1),
                approval(venue, "FINANCE", "APPROVE", signingKey.id(), NOW, 3),
                approval(venue, "FINANCE", "APPROVE", signingKey.id(), NOW.plusSeconds(1), 2),
                approval(venue, "FINANCE", "APPROVE", signingKey.id(), NOW.minusSeconds(60), 2))) {
            denied(verifier, signed(deniedApproval.canonicalPayload(), signer), 403);
        }
    }

    @Test
    void rejectsConflictedRevokedOrAmbiguousTrust() throws Exception {
        var approved = approval(venue, "FINANCE", "APPROVE", signingKey.id(), NOW, 2);
        var envelope = signed(approved.canonicalPayload(), signer);
        var valid = appointment(false, false);
        denied(verifier(true, NOW, List.of(appointment(true, false)), List.of(signingKey)), envelope, 403);
        denied(verifier(true, NOW, List.of(appointment(false, true)), List.of(signingKey)), envelope, 403);
        denied(verifier(true, NOW, List.of(valid, valid), List.of(signingKey)), envelope, 403);
        denied(verifier(true, NOW, List.of(valid), List.of(signingKey, signingKey)), envelope, 403);
        var unverified = new CommercialPolicyApprovalTrustProperties.Appointment(appointmentId, appointingAuthority,
                reviewer, reviewer, venue, room, packageVersion, Set.of("FINANCE"), start, end, false, false);
        var wrongCustodian = new CommercialPolicyApprovalTrustProperties.Key(signingKey.id(), reviewer, reviewer,
                signingKey.publicKey(), start, end, false);
        denied(verifier(true, NOW, List.of(unverified), List.of(signingKey)), envelope, 403);
        denied(verifier(true, NOW, List.of(valid), List.of(wrongCustodian)), envelope, 403);
        var alias = key("duplicate-material", revocationAuthority, signer, true);
        denied(verifier(true, NOW, List.of(valid), List.of(signingKey, alias)), envelope, 403);
    }

    private CommercialPolicyApproval approval(UUID scope, String domain, String outcome, String keyId,
                                               Instant issued, long version) {
        return new CommercialPolicyApproval(reviewer, scope, room, packageVersion, policy, appointmentId, nonce,
                0, version, "a".repeat(64), domain, outcome, keyId, issued, issued.plusSeconds(60));
    }

    private CommercialPolicyApprovalTrustProperties.Appointment appointment(boolean conflicted, boolean revoked) {
        return new CommercialPolicyApprovalTrustProperties.Appointment(appointmentId, appointingAuthority, identityVerifier,
                reviewer, venue, room, packageVersion, Set.of("FINANCE"), start, end, conflicted, revoked);
    }

    private CommercialPolicyApprovalTrustProperties.Key key(String id, UUID owner, KeyPair pair, boolean revoked) {
        return new CommercialPolicyApprovalTrustProperties.Key(
                id, owner, keyCustodian, Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()), start, end, revoked);
    }

    private CommercialPolicyApprovalVerifier verifier(boolean enabled, Instant observed,
            List<CommercialPolicyApprovalTrustProperties.Appointment> appointments,
            List<CommercialPolicyApprovalTrustProperties.Key> keys) {
        var trust = new CommercialPolicyApprovalTrustProperties(
                enabled, appointingAuthority, identityVerifier, keyCustodian, revocationAuthority, 2, observed, appointments, keys);
        return new CommercialPolicyApprovalVerifier(trust, environment);
    }

    private static String signed(byte[] payload, KeyPair pair) throws Exception {
        var signature = Signature.getInstance("Ed25519");
        signature.initSign(pair.getPrivate());
        signature.update(payload);
        var encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(payload) + "." + encoder.encodeToString(signature.sign());
    }

    private static void denied(CommercialPolicyApprovalVerifier verifier, String envelope, int status) {
        assertThatThrownBy(() -> verifier.verify(envelope, NOW))
                .isInstanceOfSatisfying(CommercialPolicyDraft.Failure.class, failure -> assertThat(failure.status).isEqualTo(status));
    }
}
