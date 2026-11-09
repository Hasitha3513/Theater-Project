package com.cinenest.pricing.internal;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(CommercialPolicyApprovalTrustProperties.class)
final class CommercialPolicyApprovalVerifier {
    private static final Set<Set<String>> SYNTHETIC_PROFILES = Set.of(
            Set.of("local-synthetic"), Set.of("local-synthetic", "synthetic-quotation"));
    private static final Duration MAX_REVOCATION_AGE = Duration.ofMinutes(15);
    private static final Duration MAX_APPROVAL_LIFETIME = Duration.ofHours(24);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final CommercialPolicyApprovalTrustProperties trust;
    private final Environment environment;

    CommercialPolicyApprovalVerifier(CommercialPolicyApprovalTrustProperties trust, Environment environment) {
        this.trust = trust;
        this.environment = environment;
    }

    CommercialPolicyApproval verify(String envelope, Instant now) {
        verifyTrustAvailability(now);
        byte[] payload;
        byte[] signature;
        CommercialPolicyApproval approval;
        try {
            if (envelope == null || envelope.length() > 4096) {
                throw new IllegalArgumentException();
            }
            String[] parts = envelope.split("\\.", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException();
            }
            payload = decode(parts[0]);
            signature = decode(parts[1]);
            if (signature.length != 64) {
                throw new IllegalArgumentException();
            }
            approval = CommercialPolicyApproval.parse(payload);
        } catch (IllegalArgumentException | DateTimeException e) {
            throw new CommercialPolicyDraft.Failure(400, "Invalid signed commercial approval envelope");
        }
        if (approval.revocationVersion() != trust.revocationVersion() || approval.issuedAt().isAfter(now)
                || approval.expiresAt().isAfter(trust.revocationObservedAt().plus(MAX_REVOCATION_AGE))
                || !now.isBefore(approval.expiresAt())
                || Duration.between(approval.issuedAt(), approval.expiresAt()).compareTo(MAX_APPROVAL_LIFETIME) > 0) {
            throw denied();
        }
        verifyAppointment(approval, now);
        verifySignature(approval, now, payload, signature);
        return approval;
    }

    UUID signer(CommercialPolicyApproval approval) {
        return "REVOKE".equals(approval.outcome()) ? trust.revocationAuthority() : approval.reviewer();
    }

    private void verifyTrustAvailability(Instant now) {
        var observed = trust.revocationObservedAt();
        if (!trust.enabled() || !SYNTHETIC_PROFILES.contains(Set.of(environment.getActiveProfiles()))
                || trustAuthorities().filter(Objects::nonNull).distinct().count() != 4 || trust.revocationVersion() < 1
                || observed == null || observed.isAfter(now)
                || Duration.between(observed, now).compareTo(MAX_REVOCATION_AGE) > 0) {
            throw new CommercialPolicyDraft.Failure(503, "Trusted commercial approval intake unavailable");
        }
    }

    private Stream<UUID> trustAuthorities() {
        return Stream.of(trust.appointmentAuthority(), trust.identityVerifier(), trust.keyCustodian(),
                trust.revocationAuthority());
    }

    private void verifyAppointment(CommercialPolicyApproval approval, Instant now) {
        var matches = trust.appointments().stream().filter(entry -> approval.appointmentId().equals(entry.id())).toList();
        if (matches.size() != 1) {
            throw denied();
        }
        var appointment = matches.getFirst();
        if (appointment.conflicted() || appointment.revoked() || !trust.appointmentAuthority().equals(appointment.appointedBy())
                || !trust.identityVerifier().equals(appointment.verifiedBy())
                || trustAuthorities().anyMatch(approval.reviewer()::equals)
                || !approval.reviewer().equals(appointment.reviewer()) || !approval.venue().equals(appointment.venue())
                || !approval.room().equals(appointment.room()) || !approval.packageVersion().equals(appointment.packageVersion())
                || !appointment.domains().contains(approval.domain())
                || !validAt(approval.issuedAt(), appointment.validFrom(), appointment.validUntil())
                || !validAt(now, appointment.validFrom(), appointment.validUntil())
                || approval.expiresAt().isAfter(appointment.validUntil())) {
            throw denied();
        }
    }

    private void verifySignature(CommercialPolicyApproval approval, Instant now, byte[] payload, byte[] signature) {
        var matches = trust.keys().stream().filter(key -> approval.keyId().equals(key.id())).toList();
        if (matches.size() != 1) {
            throw denied();
        }
        var key = matches.getFirst();
        if (key.revoked() || !signer(approval).equals(key.owner()) || !trust.keyCustodian().equals(key.custodian())
                || key.publicKey() == null || key.publicKey().length() > 128
                || trust.keys().stream().anyMatch(other -> other != key && key.publicKey().equals(other.publicKey()))
                || !validAt(approval.issuedAt(), key.validFrom(), key.validUntil())
                || !validAt(now, key.validFrom(), key.validUntil()) || approval.expiresAt().isAfter(key.validUntil())) {
            throw denied();
        }
        try {
            var publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(key.publicKey())));
            if (!(publicKey instanceof EdECPublicKey edKey) || !"Ed25519".equals(edKey.getParams().getName())
                    || !key.publicKey().equals(Base64.getEncoder().encodeToString(publicKey.getEncoded()))) {
                throw denied();
            }
            var verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(payload);
            if (!verifier.verify(signature)) {
                throw denied();
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw denied();
        }
    }

    private static boolean validAt(Instant instant, Instant start, Instant end) {
        return start != null && end != null && !instant.isBefore(start) && instant.isBefore(end);
    }

    private static byte[] decode(String value) {
        byte[] bytes = DECODER.decode(value);
        if (!ENCODER.encodeToString(bytes).equals(value)) {
            throw new IllegalArgumentException();
        }
        return bytes;
    }

    private static CommercialPolicyDraft.Failure denied() {
        return new CommercialPolicyDraft.Failure(403, "Trusted commercial approval denied");
    }
}
