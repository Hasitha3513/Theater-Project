package com.cinenest.pricing.internal;

import static com.cinenest.pricing.internal.CommercialPolicyTrustVerifier.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class CommercialPolicyTrustVerifierTest {
    private static final Instant NOW = Instant.parse("2030-02-01T00:00:00Z");
    private final UUID actor = UUID.randomUUID();
    private KeyPair appointing;
    private KeyPair identity;
    private KeyPair custody;
    private KeyPair revocation;
    private Scope scope;
    private CommercialPolicyTrustVerifier verifier;
    private List<Approval> approvals;

    @BeforeEach
    void fixtures() throws Exception {
        appointing = key();
        identity = key();
        custody = key();
        revocation = key();
        scope = new Scope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            1, "a".repeat(64), NOW, NOW.plusSeconds(3600));
        verifier = new CommercialPolicyTrustVerifier(new Authorities(
            appointing.getPublic(), identity.getPublic(), custody.getPublic(), revocation.getPublic()));
        approvals = List.of(approval(Domain.FINANCE, scope), approval(Domain.LEGAL, scope),
            approval(Domain.OPERATIONS, scope));
    }

    @Test
    void admitsThreeIndependentFixtureApprovalsWithBoundedValidity() throws Exception {
        assertThat(verifier.verifyFixture(scope, approvals, revocations(3, NOW, Set.of()), 3, actor, Set.of(), NOW))
            .isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    void deniesForgedDecisionsAndMissingOrDuplicateSpecialists() throws Exception {
        var original = approvals.getFirst();
        var forged = new Approval(original.appointment(), original.appointmentSignature(),
            original.identitySignature(), original.custodySignature(), original.issuedAt(), original.expiresAt(),
            sign(key(), original.payload()));
        rejects(List.of(forged, approvals.get(1), approvals.get(2)), revocations(3, NOW, Set.of()), Set.of());
        rejects(approvals.subList(0, 2), revocations(3, NOW, Set.of()), Set.of());
        rejects(List.of(original, original, approvals.get(2)), revocations(3, NOW, Set.of()), Set.of());
    }

    @Test
    void deniesWrongScopeAndKnownReviewerConflict() throws Exception {
        var wrongRoom = new Scope(scope.venue(), UUID.randomUUID(), scope.packageVersion(), scope.policy(),
            scope.revision(), scope.hash(), scope.effectiveFrom(), scope.effectiveUntil());
        rejects(List.of(approval(Domain.FINANCE, wrongRoom), approvals.get(1), approvals.get(2)),
            revocations(3, NOW, Set.of()), Set.of());
        rejects(approvals, revocations(3, NOW, Set.of()), Set.of(approvals.getFirst().appointment().reviewer()));
    }

    @Test
    void deniesExpiredDecisionsAndUnverifiedIdentityProvenance() throws Exception {
        var later = NOW.plusSeconds(180);
        var checkpoint = revocations(3, later, Set.of());
        assertThatThrownBy(() -> verifier.verifyFixture(scope, approvals, checkpoint, 3, actor, Set.of(), later))
            .isInstanceOf(DraftFailure.class);
        var a = approvals.getFirst();
        var wrongIdentity = new Approval(a.appointment(), a.appointmentSignature(), a.custodySignature(),
            a.custodySignature(), a.issuedAt(), a.expiresAt(), a.signature());
        rejects(List.of(wrongIdentity, approvals.get(1), approvals.get(2)), revocations(3, NOW, Set.of()), Set.of());
    }

    @Test
    void deniesStaleRolledBackOrRevokedAuthority() throws Exception {
        rejects(approvals, revocations(3, NOW.minusSeconds(901), Set.of()), Set.of());
        rejects(approvals, revocations(2, NOW, Set.of()), Set.of());
        rejects(approvals, revocations(3, NOW, Set.of(approvals.getFirst().appointment().id())), Set.of());
    }

    private void rejects(List<Approval> decisions, Revocations checkpoint, Set<UUID> conflicts) {
        assertThatThrownBy(() -> verifier.verifyFixture(scope, decisions, checkpoint, 3, actor, conflicts, NOW))
            .isInstanceOf(DraftFailure.class);
    }

    private Approval approval(Domain domain, Scope target) throws Exception {
        var reviewerKey = key();
        var appointment = new Appointment(UUID.randomUUID(), UUID.randomUUID(), domain, target,
            reviewerKey.getPublic(), NOW.minusSeconds(60), NOW.plusSeconds(180));
        var a = new Approval(appointment, sign(appointing, canonical("APPOINTMENT", appointment.canonical())),
            sign(identity, canonical("IDENTITY", appointment.canonical())),
            sign(custody, canonical("CUSTODY", appointment.canonical())), NOW, NOW.plusSeconds(120), "");
        return new Approval(appointment, a.appointmentSignature(), a.identitySignature(), a.custodySignature(),
            a.issuedAt(), a.expiresAt(), sign(reviewerKey, a.payload()));
    }

    private Revocations revocations(long sequence, Instant observedAt, Set<UUID> revoked) throws Exception {
        var checkpoint = new Revocations(sequence, observedAt, revoked, "");
        return new Revocations(sequence, observedAt, revoked, sign(revocation, checkpoint.payload()));
    }

    private static KeyPair key() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static String sign(KeyPair key, byte[] payload) throws Exception {
        var signature = Signature.getInstance("Ed25519");
        signature.initSign(key.getPrivate());
        signature.update(payload);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }
}
