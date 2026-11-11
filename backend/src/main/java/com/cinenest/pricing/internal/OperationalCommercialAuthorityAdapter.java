package com.cinenest.pricing.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Verifies an immutable authority snapshot. The application has no configured operational roots. */
final class OperationalCommercialAuthorityAdapter implements OperationalCommercialAuthority {
    enum Purpose { APPOINTMENT, IDENTITY, CUSTODY, REVOCATION }
    enum Specialist { FINANCE, LEGAL, OPERATIONS }

    record Root(Purpose purpose, PublicKey key, Instant from, Instant until) {}
    record Proof(UUID signer, String signature) {}
    record Approval(Specialist role, UUID appointment, UUID subject, PublicKey key,
                    Instant from, Instant until, Proof appointmentProof, Proof identityProof,
                    Proof custodyProof, Proof approvalProof) {}
    record Revocations(String namespace, long sequence, Instant observedAt, Instant expiresAt,
                       Set<UUID> revoked, Proof proof) {
        Revocations {
            revoked = Set.copyOf(revoked);
        }
    }
    record Bundle(Scope scope, Revocations revocations, List<Approval> approvals) {
        Bundle {
            approvals = List.copyOf(approvals);
        }
    }

    private final Map<UUID, Root> roots;
    private final String namespace;
    private final Bundle bundle;
    private final boolean qualificationOnly;

    OperationalCommercialAuthorityAdapter(Map<UUID, Root> roots, String namespace, Bundle bundle) {
        this(roots, namespace, bundle, true);
    }

    private OperationalCommercialAuthorityAdapter(Map<UUID, Root> roots, String namespace, Bundle bundle,
                                                   boolean qualificationOnly) {
        this.roots = Map.copyOf(roots);
        this.namespace = namespace;
        this.bundle = bundle;
        this.qualificationOnly = qualificationOnly;
    }

    @Override
    public boolean qualificationOnly() { return qualificationOnly; }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "cinenest.commercial.operational.bundle-enabled", havingValue = "true")
    static class BundleConfiguration {
        @org.springframework.context.annotation.Bean
        OperationalCommercialAuthority authority(
            @org.springframework.beans.factory.annotation.Value("${cinenest.commercial.operational.trust-directory}") String directory,
            @org.springframework.beans.factory.annotation.Value("${cinenest.commercial.operational.namespace}") String namespace,
            @org.springframework.beans.factory.annotation.Value("${cinenest.commercial.operational.roots-sha256}") String rootsDigest,
            @org.springframework.beans.factory.annotation.Value("${cinenest.commercial.operational.qualification-only:true}") boolean fixture) {
            return new OperationalCommercialAuthority() {
                @Override public boolean qualificationOnly() { return fixture; }
                @Override public Evidence verifyRevocations(Instant now) {
                    return load(directory, namespace, rootsDigest, fixture).verifyRevocations(now);
                }
                @Override public Evidence verify(Scope scope, Instant now) {
                    return load(directory, namespace, rootsDigest, fixture).verify(scope, now);
                }
            };
        }
    }

    private record RootDocument(UUID id, Purpose purpose, String key, Instant from, Instant until) {}
    private record ApprovalDocument(Specialist role, UUID appointment, UUID subject, String key,
                                    Instant from, Instant until, Proof appointmentProof, Proof identityProof,
                                    Proof custodyProof, Proof approvalProof) {}
    private record BundleDocument(int version, Scope scope, Revocations revocations, List<ApprovalDocument> approvals) {}
    private record RootsDocument(int version, List<RootDocument> roots) {}

    private static OperationalCommercialAuthorityAdapter load(String directory, String namespace, String rootsDigest, boolean fixture) {
        try {
            var root = java.nio.file.Path.of(directory);
            if (!root.isAbsolute() || !root.equals(root.toRealPath())) throw denied();
            var mapper = tools.jackson.databind.json.JsonMapper.builder()
                .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
            byte[] rootsContent = readProtected(root.resolve("roots.json"));
            if (!digest(rootsContent).equals(rootsDigest)) throw denied();
            var config = mapper.readValue(rootsContent, RootsDocument.class);
            var document = mapper.readValue(readProtected(root.resolve("bundle.json")), BundleDocument.class);
            if (config.version() != 1 || document.version() != 1 || config.roots().size() > 32
                || document.approvals().size() != Specialist.values().length) throw denied();
            var keys = new java.util.HashMap<UUID, Root>();
            for (var item : config.roots()) {
                if (item.id() == null || item.purpose() == null || keys.putIfAbsent(item.id(),
                    new Root(item.purpose(), publicKey(item.key()), item.from(), item.until())) != null) throw denied();
            }
            var approvals = new java.util.ArrayList<Approval>();
            for (var item : document.approvals()) {
                approvals.add(new Approval(item.role(), item.appointment(), item.subject(), publicKey(item.key()),
                    item.from(), item.until(), item.appointmentProof(), item.identityProof(), item.custodyProof(),
                    item.approvalProof()));
            }
            return new OperationalCommercialAuthorityAdapter(keys, namespace,
                new Bundle(document.scope(), document.revocations(), approvals), fixture);
        } catch (java.io.IOException | GeneralSecurityException | RuntimeException invalid) {
            throw denied();
        }
    }

    private static PublicKey publicKey(String text) throws GeneralSecurityException {
        if (text == null || text.length() != 59) throw denied();
        byte[] bytes = Base64.getUrlDecoder().decode(text);
        if (!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(text)) throw denied();
        return java.security.KeyFactory.getInstance("Ed25519")
            .generatePublic(new java.security.spec.X509EncodedKeySpec(bytes));
    }

    private static byte[] readProtected(java.nio.file.Path path) throws java.io.IOException {
        var noLinks = java.nio.file.LinkOption.NOFOLLOW_LINKS;
        var attrs = java.nio.file.Files.readAttributes(path, java.nio.file.attribute.PosixFileAttributes.class, noLinks);
        if (!attrs.isRegularFile() || !attrs.owner().equals(java.nio.file.Files.getOwner(path.getParent(), noLinks))
            || attrs.permissions().stream().anyMatch(permission -> permission.name().startsWith("GROUP_")
                || permission.name().startsWith("OTHERS_"))) throw denied();
        try (var stream = java.nio.file.Files.newInputStream(path, noLinks)) {
            byte[] content = stream.readNBytes(65537);
            if (content.length > 65536) throw denied();
            return content;
        }
    }

    @Override
    public Evidence verifyRevocations(Instant now) {
        if (bundle == null || now == null || namespace == null || !namespace.matches("[a-zA-Z0-9._-]{1,64}")) {
            throw denied();
        }
        var revocations = bundle.revocations();
        if (revocations == null || !namespace.equals(revocations.namespace()) || revocations.sequence() <= 0
            || revocations.revoked().size() > 4096 || revocations.observedAt() == null || revocations.expiresAt() == null
            || revocations.observedAt().isAfter(now) || !now.isBefore(revocations.expiresAt())
            || Duration.between(revocations.observedAt(), revocations.expiresAt()).compareTo(Duration.ofHours(24)) > 0) {
            throw denied();
        }

        byte[] revocationPayload = payload("revocation", namespace, revocations.sequence(),
            revocations.observedAt(), revocations.expiresAt(), revocations.revoked().stream().sorted().toList());
        verifyRoot(Purpose.REVOCATION, revocations.proof(), revocationPayload, now, revocations.revoked());
        Instant validUntil = earliest(revocations.expiresAt(), roots.get(revocations.proof().signer()).until());
        return new Evidence(namespace, revocations.sequence(), digest(revocationPayload), validUntil);
    }

    @Override
    public Evidence verify(Scope scope, Instant now) {
        var observed = verifyRevocations(now);
        if (scope == null || !scope.equals(bundle.scope()) || bundle.approvals().size() != Specialist.values().length) {
            throw denied();
        }
        var revocations = bundle.revocations();
        Instant validUntil = observed.validUntil();
        var roles = java.util.EnumSet.noneOf(Specialist.class);
        var subjects = new java.util.HashSet<UUID>();
        var appointments = new java.util.HashSet<UUID>();
        var keys = new java.util.HashSet<String>();
        for (var approval : bundle.approvals()) {
            if (approval.role() == null || approval.subject() == null || approval.appointment() == null
                || approval.key() == null || approval.key().getEncoded() == null
                || !roles.add(approval.role()) || !subjects.add(approval.subject())
                || !appointments.add(approval.appointment()) || roots.containsKey(approval.subject())
                || !current(approval.from(), approval.until(), now)
                || revocations.revoked().contains(approval.subject())
                || revocations.revoked().contains(approval.appointment())) {
                throw denied();
            }
            String keyHash = digest(approval.key().getEncoded());
            if (!keys.add(keyHash) || roots.values().stream().anyMatch(root ->
                MessageDigest.isEqual(root.key().getEncoded(), approval.key().getEncoded()))) {
                throw denied();
            }
            byte[] appointment = appointmentPayload(namespace, scope, approval.role(), approval.appointment(),
                approval.subject(), keyHash, approval.from(), approval.until());
            verifyRoot(Purpose.APPOINTMENT, approval.appointmentProof(), appointment, now, revocations.revoked());
            verifyRoot(Purpose.IDENTITY, approval.identityProof(), payload("identity", digest(appointment)),
                now, revocations.revoked());
            verifyRoot(Purpose.CUSTODY, approval.custodyProof(), payload("custody", digest(appointment)),
                now, revocations.revoked());
            var signature = approval.approvalProof();
            if (signature == null || !approval.subject().equals(signature.signer())) {
                throw denied();
            }
            verifySignature(approval.key(), signature, payload("approve", digest(appointment)));
            validUntil = earliest(validUntil, approval.until());
            for (var proof : List.of(approval.appointmentProof(), approval.identityProof(), approval.custodyProof())) {
                validUntil = earliest(validUntil, roots.get(proof.signer()).until());
            }
        }
        return new Evidence(namespace, observed.sequence(), observed.revocationDigest(), validUntil);
    }

    private void verifyRoot(Purpose purpose, Proof proof, byte[] payload, Instant now, Set<UUID> revoked) {
        Root root = proof == null || proof.signer() == null ? null : roots.get(proof.signer());
        if (root == null || root.purpose() != purpose || !current(root.from(), root.until(), now)
            || revoked.contains(proof.signer())
            || roots.values().stream().anyMatch(other -> other.purpose() != purpose
                && MessageDigest.isEqual(root.key().getEncoded(), other.key().getEncoded()))) {
            throw denied();
        }
        verifySignature(root.key(), proof, payload);
    }

    private static void verifySignature(PublicKey key, Proof proof, byte[] payload) {
        if (proof.signature() == null || proof.signature().length() != 86) {
            throw denied();
        }
        try {
            byte[] signature = Base64.getUrlDecoder().decode(proof.signature());
            if (signature.length != 64
                || !Base64.getUrlEncoder().withoutPadding().encodeToString(signature).equals(proof.signature())) {
                throw denied();
            }
            var verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(payload);
            if (!verifier.verify(signature)) {
                throw denied();
            }
        } catch (GeneralSecurityException | IllegalArgumentException rejected) {
            throw denied();
        }
    }

    private static boolean current(Instant from, Instant until, Instant now) {
        return from != null && until != null && !now.isBefore(from) && now.isBefore(until);
    }

    private static Instant earliest(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    static byte[] appointmentPayload(String namespace, Scope scope, Specialist role, UUID appointment,
                                     UUID subject, String keyHash, Instant from, Instant until) {
        return payload("appointment", namespace, scope.venue(), scope.room(), scope.version(),
            scope.policy(), scope.revision(), scope.policyHash(), role, appointment, subject, keyHash, from, until);
    }

    static byte[] payload(String purpose, Object... fields) {
        var result = new StringBuilder("CINENEST-V42-1:").append(purpose);
        for (Object field : fields) {
            String value = field.toString();
            result.append(':').append(value.getBytes(StandardCharsets.UTF_8).length).append(':').append(value);
        }
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private static DraftFailure denied() {
        return new DraftFailure(503, "Commercial authority unavailable");
    }
}
