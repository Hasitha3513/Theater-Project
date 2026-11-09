package com.cinenest.pricing.internal;

import com.cinenest.identity.ScopedAuthorization.MatchedGrant;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;

final class JdbcCommercialPolicyActivationRepository {
    private final JdbcClient jdbc;

    JdbcCommercialPolicyActivationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Instant now() {
        return jdbc.sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single().toInstant();
    }

    void lockTrust() {
        if (!"read committed".equals(jdbc.sql("SHOW transaction_isolation").query(String.class).single())) {
            throw new DraftFailure(409, "Commercial fixture requires read committed");
        }
        jdbc.sql("SELECT pg_advisory_xact_lock(39840)").query((rs, row) -> true).single();
    }

    void checkpoint(CommercialPolicyTrustVerifier.Revocations evidence) {
        var hash = PublicationService.hash(Map.of("checkpoint", evidence.signature(),
            "payload", java.util.Base64.getEncoder().encodeToString(evidence.payload())));
        var previous = jdbc.sql("""
            SELECT sequence, payload_hash, revoked, observed_at
            FROM cinenest_pricing.fixture_policy_revocations ORDER BY sequence DESC LIMIT 1
            """).query((rs, row) -> new Checkpoint(rs.getLong(1), rs.getString(2),
                Arrays.stream((Object[]) rs.getArray(3).getArray()).map(Object::toString)
                    .map(UUID::fromString).collect(Collectors.toUnmodifiableSet()),
                rs.getObject(4, OffsetDateTime.class).toInstant())).optional();
        if (previous.isPresent()) {
            var p = previous.get();
            if (p.sequence() == evidence.sequence() && p.hash().equals(hash)) {
                return;
            }
            if (evidence.sequence() <= p.sequence() || evidence.observedAt().isBefore(p.observed())
                    || !evidence.revokedAppointments().containsAll(p.revoked())) {
                throw new DraftFailure(403, "Revocation checkpoint regressed or conflicts");
            }
        }
        jdbc.sql("""
            INSERT INTO cinenest_pricing.fixture_policy_revocations(sequence,observed_at,payload_hash,revoked)
            VALUES(:seq,:observed,:hash,CAST(:revoked AS uuid[]))
            """).param("seq", evidence.sequence()).param("observed", time(evidence.observedAt()))
            .param("hash", hash).param("revoked", uuidArray(evidence.revokedAppointments())).update();
    }

    record Checkpoint(long sequence, String hash, Set<UUID> revoked, Instant observed) {}
    record Receipt(UUID id, String hash) {}

    java.util.Optional<Receipt> replay(UUID actor, UUID venue, UUID key) {
        return jdbc.sql("""
            SELECT activation_id,command_hash FROM cinenest_pricing.fixture_policy_activation
            WHERE actor_id=:actor AND venue_id=:venue AND command_key=:key
            """).param("actor", actor).param("venue", venue).param("key", key)
            .query((rs, row) -> new Receipt(rs.getObject(1, UUID.class), rs.getString(2))).optional();
    }

    Set<UUID> authors(UUID policy, long revision) {
        return Set.copyOf(jdbc.sql("""
            SELECT authored_by FROM cinenest_pricing.commercial_policy_revision WHERE policy_id=:policy AND revision<=:rev
            UNION SELECT created_by FROM cinenest_pricing.commercial_policy_draft WHERE policy_id=:policy
            """).param("policy", policy).param("rev", revision).query(UUID.class).list());
    }

    UUID insert(CommercialPolicyTrustVerifier.Scope scope, MatchedGrant grant, UUID key, String hash,
                Instant validThrough, long sequence, List<CommercialPolicyTrustVerifier.Approval> proofs) {
        UUID id = UUID.randomUUID();
        var evidence = proofs.stream().map(proof -> Map.of(
            "payload", java.util.Base64.getEncoder().encodeToString(proof.payload()),
            "approval", proof.signature(), "appointment", proof.appointmentSignature(),
            "identity", proof.identitySignature(), "custody", proof.custodySignature())).toList();
        jdbc.sql("""
            INSERT INTO cinenest_pricing.fixture_policy_activation
              (activation_id,actor_id,venue_id,room_id,version_id,policy_id,revision,content_hash,effective_from,
               effective_until,approval_until,revocation_sequence,appointments,approval_evidence,grant_id,grant_revision,
               command_key,command_hash)
            VALUES (:id,:actor,:venue,:room,:version,:policy,:rev,:content,:from,:until,:approval,:seq,
                    CAST(:appointments AS uuid[]),(CAST(:proofs AS jsonb)->'proofs'),:grant,:grantRev,:key,:hash)
            """).param("id", id).param("actor", grant.account()).param("venue", scope.venue())
            .param("room", scope.room()).param("version", scope.packageVersion()).param("policy", scope.policy())
            .param("rev", scope.revision()).param("content", scope.hash()).param("from", time(scope.effectiveFrom()))
            .param("until", time(scope.effectiveUntil())).param("approval", time(validThrough)).param("seq", sequence)
            .param("appointments", uuidArray(proofs.stream().map(p -> p.appointment().id()).toList()))
            .param("proofs", JSONObjectUtils.toJSONString(Map.of("proofs", evidence)))
            .param("grant", grant.grantId()).param("grantRev", grant.grantRevision())
            .param("key", key).param("hash", hash).update();
        jdbc.sql("""
            INSERT INTO cinenest_pricing.fixture_policy_activation_audit(activation_id,actor_id,venue_id)
            VALUES(:id,:actor,:venue)
            """).param("id", id).param("actor", grant.account()).param("venue", scope.venue()).update();
        return id;
    }

    static OffsetDateTime time(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String uuidArray(Iterable<UUID> ids) {
        var joiner = new java.util.StringJoiner(",", "{", "}");
        ids.forEach(id -> joiner.add(id.toString()));
        return joiner.toString();
    }
}
