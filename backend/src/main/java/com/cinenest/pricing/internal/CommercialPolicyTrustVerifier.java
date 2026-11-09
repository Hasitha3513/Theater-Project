package com.cinenest.pricing.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.EdECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Fixture-only cryptographic qualification. Operational trust has no admission path. */
final class CommercialPolicyTrustVerifier {
    enum Domain { FINANCE, LEGAL, OPERATIONS }

    record Scope(UUID venue, UUID room, UUID packageVersion, UUID policy, long revision, String hash,
                 Instant effectiveFrom, Instant effectiveUntil) {
        Scope {
            if (venue == null || room == null || packageVersion == null || policy == null
                    || revision < 1 || hash == null || !hash.matches("[0-9a-f]{64}")
                    || effectiveFrom == null || effectiveUntil == null || !effectiveFrom.isBefore(effectiveUntil)) {
                throw new IllegalArgumentException("Invalid activation scope");
            }
        }

        String canonical() {
            return String.join("\n", venue.toString(), room.toString(), packageVersion.toString(), policy.toString(),
                Long.toString(revision), hash, effectiveFrom.toString(), effectiveUntil.toString());
        }
    }

    record Appointment(UUID id, UUID reviewer, Domain domain, Scope scope, PublicKey key, Instant from, Instant until) {
        String canonical() {
            return String.join("\n", id.toString(), reviewer.toString(), domain.name(), scope.canonical(),
                fingerprint(key), from.toString(), until.toString());
        }
    }

    record Approval(Appointment appointment, String appointmentSignature, String identitySignature,
                    String custodySignature, Instant issuedAt, Instant expiresAt, String signature) {
        byte[] payload() {
            return canonical("APPROVE", appointment.canonical(), issuedAt.toString(), expiresAt.toString());
        }
    }

    record Revocations(long sequence, Instant observedAt, Set<UUID> revokedAppointments, String signature) {
        Revocations {
            revokedAppointments = Set.copyOf(revokedAppointments);
        }

        byte[] payload() {
            return canonical("REVOCATIONS", Long.toString(sequence), observedAt.toString(),
                String.join("\n", revokedAppointments.stream().map(UUID::toString).sorted().toList()));
        }
    }

    record Authorities(PublicKey appointments, PublicKey identities, PublicKey custody, PublicKey revocations) {
        Authorities {
            var keys = List.of(appointments, identities, custody, revocations);
            if (keys.stream().map(CommercialPolicyTrustVerifier::fingerprint).distinct().count() != keys.size()) {
                throw new IllegalArgumentException("Fixture authorities must be distinct");
            }
        }

        Set<String> fingerprints() {
            return Set.of(fingerprint(appointments), fingerprint(identities), fingerprint(custody), fingerprint(revocations));
        }
    }

    private static final Duration MAX_REVOCATION_AGE = Duration.ofMinutes(15);
    private final Authorities fixtureAuthorities;

    CommercialPolicyTrustVerifier(Authorities fixtureAuthorities) {
        this.fixtureAuthorities = fixtureAuthorities;
    }

    Instant verifyFixture(Scope scope, List<Approval> approvals, Revocations revocations, long minimumSequence,
                          UUID actor, Set<UUID> conflictedReviewers, Instant now) {
        if (actor == null || approvals.size() != Domain.values().length || revocations.sequence() < 1
                || revocations.sequence() < minimumSequence || revocations.observedAt().isAfter(now)
                || !now.isBefore(revocations.observedAt().plus(MAX_REVOCATION_AGE))
                || !signed(fixtureAuthorities.revocations(), revocations.payload(), revocations.signature())
                || !now.isBefore(scope.effectiveUntil())) {
            throw denied();
        }
        var reviewerIds = new HashSet<UUID>();
        var appointmentIds = new HashSet<UUID>();
        var domains = new HashSet<Domain>();
        var keys = new HashSet<>(fixtureAuthorities.fingerprints());
        Instant currentThrough = revocations.observedAt().plus(MAX_REVOCATION_AGE);
        for (var approval : approvals) {
            var appointment = approval.appointment();
            var attestation = appointment.canonical();
            if (!scope.equals(appointment.scope()) || actor.equals(appointment.reviewer())
                    || conflictedReviewers.contains(appointment.reviewer())
                    || !reviewerIds.add(appointment.reviewer()) || !appointmentIds.add(appointment.id())
                    || !domains.add(appointment.domain()) || !keys.add(fingerprint(appointment.key()))
                    || revocations.revokedAppointments().contains(appointment.id())
                    || !validAt(appointment, approval.issuedAt()) || !validAt(appointment, now)
                    || approval.issuedAt().isAfter(now) || !approval.issuedAt().isBefore(approval.expiresAt())
                    || !now.isBefore(approval.expiresAt()) || approval.expiresAt().isAfter(appointment.until())
                    || !signed(fixtureAuthorities.appointments(), canonical("APPOINTMENT", attestation), approval.appointmentSignature())
                    || !signed(fixtureAuthorities.identities(), canonical("IDENTITY", attestation), approval.identitySignature())
                    || !signed(fixtureAuthorities.custody(), canonical("CUSTODY", attestation), approval.custodySignature())
                    || !signed(appointment.key(), approval.payload(), approval.signature())) {
                throw denied();
            }
            if (approval.expiresAt().isBefore(currentThrough)) {
                currentThrough = approval.expiresAt();
            }
        }
        return currentThrough;
    }

    void verifyFixtureRevocations(Revocations revocations, Instant now) {
        if (revocations.sequence() < 1 || revocations.observedAt().isAfter(now)
                || !now.isBefore(revocations.observedAt().plus(MAX_REVOCATION_AGE))
                || !signed(fixtureAuthorities.revocations(), revocations.payload(), revocations.signature())) {
            throw denied();
        }
    }

    private static boolean validAt(Appointment appointment, Instant instant) {
        return !instant.isBefore(appointment.from()) && instant.isBefore(appointment.until());
    }

    static byte[] canonical(String kind, String... fields) {
        return ("CINENEST-V39-FIXTURE-V1\n" + kind + "\n" + String.join("\n", fields))
            .getBytes(StandardCharsets.US_ASCII);
    }

    private static String fingerprint(PublicKey key) {
        if (!(key instanceof EdECPublicKey ed) || !"Ed25519".equals(ed.getParams().getName())) {
            throw denied();
        }
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    private static boolean signed(PublicKey key, byte[] payload, String encoded) {
        try {
            var signature = Base64.getUrlDecoder().decode(encoded);
            if (signature.length != 64 || !Base64.getUrlEncoder().withoutPadding().encodeToString(signature).equals(encoded)) {
                return false;
            }
            var verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(payload);
            return verifier.verify(signature);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    private static DraftFailure denied() {
        return new DraftFailure(403, "Trusted fixture approvals denied");
    }
}
