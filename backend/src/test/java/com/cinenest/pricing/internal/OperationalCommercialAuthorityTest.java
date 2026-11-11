package com.cinenest.pricing.internal;

import static com.cinenest.pricing.internal.OperationalCommercialAuthorityAdapter.appointmentPayload;
import static com.cinenest.pricing.internal.OperationalCommercialAuthorityAdapter.digest;
import static com.cinenest.pricing.internal.OperationalCommercialAuthorityAdapter.payload;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cinenest.pricing.internal.OperationalCommercialAuthority.Scope;
import com.cinenest.pricing.internal.OperationalCommercialAuthorityAdapter.Approval;
import com.cinenest.pricing.internal.OperationalCommercialAuthorityAdapter.Bundle;
import com.cinenest.pricing.internal.OperationalCommercialAuthorityAdapter.Proof;
import com.cinenest.pricing.internal.OperationalCommercialAuthorityAdapter.Purpose;
import com.cinenest.pricing.internal.OperationalCommercialAuthorityAdapter.Revocations;
import com.cinenest.pricing.internal.OperationalCommercialAuthorityAdapter.Root;
import com.cinenest.pricing.internal.OperationalCommercialAuthorityAdapter.Specialist;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperationalCommercialAuthorityTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final Instant FROM = NOW.minusSeconds(60);
    private static final Instant UNTIL = NOW.plusSeconds(60);
    private static final String NAMESPACE = "v42-synthetic";
    private final Scope scope = new Scope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        UUID.randomUUID(), 1, "a".repeat(64));

    @Test
    void acceptsThreeDistinctSpecialistsAndPurposeScopedRoots() throws GeneralSecurityException {
        var evidence = fixture(false, false).verify(scope, NOW);
        assertEquals(NAMESPACE, evidence.namespace());
        assertEquals(2, evidence.sequence());
        assertEquals(UNTIL, evidence.validUntil());
    }

    @Test
    void deniesExpiredAndWrongVenueScope() throws GeneralSecurityException {
        var verifier = fixture(false, false);
        var wrongVenue = new Scope(UUID.randomUUID(), scope.room(), scope.version(), scope.policy(),
            scope.revision(), scope.policyHash());
        assertThrows(DraftFailure.class, () -> verifier.verify(wrongVenue, NOW));
        assertThrows(DraftFailure.class, () -> verifier.verify(scope, UNTIL));
    }

    @Test
    void deniesRevokedSpecialist() throws GeneralSecurityException {
        var verifier = fixture(true, false);
        assertEquals(2, verifier.verifyRevocations(NOW).sequence());
        assertThrows(DraftFailure.class, () -> verifier.verify(scope, NOW));
    }

    @Test
    void deniesApprovalSignatureFromDifferentKey() throws GeneralSecurityException {
        var verifier = fixture(false, true);
        assertThrows(DraftFailure.class, () -> verifier.verify(scope, NOW));
    }

    @Test
    void deniesMissingAuthority() {
        var verifier = new OperationalCommercialAuthorityAdapter(Map.of(), NAMESPACE, null);
        assertThrows(DraftFailure.class, () -> verifier.verify(scope, NOW));
    }

    @Test
    void bundleLoaderRejectsMismatchedRootPinAndDuplicateJsonFields(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
        throws java.io.IOException {
        var roots = directory.resolve("roots.json");
        var bundle = directory.resolve("bundle.json");
        java.nio.file.Files.writeString(roots, "{\"version\":1,\"version\":1,\"roots\":[]}");
        java.nio.file.Files.writeString(bundle, "{}");
        var permissions = java.nio.file.attribute.PosixFilePermissions.fromString("rw-------");
        java.nio.file.Files.setPosixFilePermissions(roots, permissions);
        java.nio.file.Files.setPosixFilePermissions(bundle, permissions);
        var configuration = new OperationalCommercialAuthorityAdapter.BundleConfiguration();
        var unpinned = configuration.authority(directory.toString(), NAMESPACE, "0".repeat(64), true);
        assertThrows(DraftFailure.class, () -> unpinned.verifyRevocations(NOW));
        var invalid = configuration.authority(directory.toString(), NAMESPACE,
            digest(java.nio.file.Files.readAllBytes(roots)), true);
        assertThrows(DraftFailure.class, () -> invalid.verifyRevocations(NOW));
    }

    private OperationalCommercialAuthority fixture(boolean revokeFinance, boolean wrongSigningKey)
        throws GeneralSecurityException {
        var generator = KeyPairGenerator.getInstance("Ed25519");
        var roots = new HashMap<UUID, Root>();
        var rootKeys = new EnumMap<Purpose, KeyPair>(Purpose.class);
        var rootIds = new EnumMap<Purpose, UUID>(Purpose.class);
        for (var purpose : Purpose.values()) {
            var key = generator.generateKeyPair();
            var id = UUID.randomUUID();
            rootKeys.put(purpose, key);
            rootIds.put(purpose, id);
            roots.put(id, new Root(purpose, key.getPublic(), FROM, UNTIL));
        }
        var approvals = new ArrayList<Approval>();
        var finance = UUID.randomUUID();
        for (var role : Specialist.values()) {
            var key = generator.generateKeyPair();
            var subject = role == Specialist.FINANCE ? finance : UUID.randomUUID();
            var appointment = UUID.randomUUID();
            byte[] claim = appointmentPayload(NAMESPACE, scope, role, appointment, subject,
                digest(key.getPublic().getEncoded()), FROM, UNTIL);
            approvals.add(new Approval(role, appointment, subject, key.getPublic(), FROM, UNTIL,
                sign(rootIds.get(Purpose.APPOINTMENT), rootKeys.get(Purpose.APPOINTMENT), claim),
                sign(rootIds.get(Purpose.IDENTITY), rootKeys.get(Purpose.IDENTITY), payload("identity", digest(claim))),
                sign(rootIds.get(Purpose.CUSTODY), rootKeys.get(Purpose.CUSTODY), payload("custody", digest(claim))),
                sign(subject, wrongSigningKey ? generator.generateKeyPair() : key, payload("approve", digest(claim)))));
        }
        Set<UUID> revoked = revokeFinance ? Set.of(finance) : Set.of();
        byte[] statement = payload("revocation", NAMESPACE, 2L, FROM, UNTIL, revoked.stream().sorted().toList());
        var revocations = new Revocations(NAMESPACE, 2, FROM, UNTIL, revoked,
            sign(rootIds.get(Purpose.REVOCATION), rootKeys.get(Purpose.REVOCATION), statement));
        return new OperationalCommercialAuthorityAdapter(roots, NAMESPACE, new Bundle(scope, revocations, approvals));
    }

    private static Proof sign(UUID id, KeyPair key, byte[] bytes) throws GeneralSecurityException {
        var signer = Signature.getInstance("Ed25519");
        signer.initSign(key.getPrivate());
        signer.update(bytes);
        return new Proof(id, Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
    }
}
