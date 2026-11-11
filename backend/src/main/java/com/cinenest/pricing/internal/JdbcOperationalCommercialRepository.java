package com.cinenest.pricing.internal;

import com.cinenest.booking.QuotationHolds.Facts;
import com.cinenest.identity.ScopedAuthorization.MatchedGrant;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
class JdbcOperationalCommercialRepository {
    private final JdbcClient jdbc;

    JdbcOperationalCommercialRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Serialize the namespace across key rotation, including its first observation. */
    void lockTrustNamespace(String namespace) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
            || !"read committed".equals(jdbc.sql("SHOW transaction_isolation").query(String.class).single())) {
            throw new IllegalStateException("Commercial trust requires a read committed transaction");
        }
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:lockName, 42))")
            .param("lockName", "cinenest:commercial-trust:" + namespace)
            .query((rs, row) -> true).single();
    }

    Instant now() {
        return jdbc.sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single().toInstant();
    }

    void acceptRevocations(OperationalCommercialAuthority.Evidence evidence) {
        lockTrustNamespace(evidence.namespace());
        if (!now().isBefore(evidence.validUntil())) {
            throw unavailable();
        }
        var latest = jdbc.sql("""
            SELECT sequence, digest
            FROM cinenest_pricing.operational_revocation_observation
            WHERE namespace = :namespace
            ORDER BY sequence DESC LIMIT 1
            """).param("namespace", evidence.namespace())
            .query((rs, row) -> new Watermark(rs.getLong("sequence"), rs.getString("digest"))).optional();
        if (latest.isPresent()) {
            var previous = latest.get();
            if (evidence.sequence() < previous.sequence()
                || (evidence.sequence() == previous.sequence() && !evidence.revocationDigest().equals(previous.digest()))) {
                throw unavailable();
            }
            if (evidence.sequence() == previous.sequence()) {
                return;
            }
        }
        jdbc.sql("""
            INSERT INTO cinenest_pricing.operational_revocation_observation (namespace, sequence, digest)
            VALUES (:namespace, :sequence, :digest)
            """).param("namespace", evidence.namespace()).param("sequence", evidence.sequence())
            .param("digest", evidence.revocationDigest()).update();
    }

    record Activation(UUID id, OperationalCommercialAuthority.Scope scope, String namespace,
                      Instant effectiveUntil, Map<String, Object> terms) {}
    record Receipt(UUID id, String hash, UUID activation, Instant expires) {}

    Optional<Map<String, Object>> activationContext(UUID venue, UUID policy) {
        return jdbc.sql("""
            SELECT r.revision,r.content_hash,r.effective_from_proposal,r.effective_until_proposal
            FROM cinenest_pricing.commercial_policy_draft d
            JOIN cinenest_pricing.commercial_policy_revision r
              ON r.policy_id=d.policy_id AND r.revision=d.current_revision
            WHERE d.policy_id=:policy AND d.venue_id=:venue AND r.submitted_for_review
              AND cinenest_pricing.commercial_policy_is_complete(r)
            """).param("policy", policy).param("venue", venue).<Map<String, Object>>query((rs, row) -> {
                var fields = new java.util.LinkedHashMap<String, Object>();
                fields.put("permitted", true);
                fields.put("policyId", policy);
                fields.put("venueId", venue);
                fields.put("policyRevision", rs.getLong(1));
                fields.put("policyHash", rs.getString(2));
                var from = rs.getObject(3, OffsetDateTime.class);
                var until = rs.getObject(4, OffsetDateTime.class);
                fields.put("effectiveFrom", from == null ? null : from.toInstant().toString());
                fields.put("effectiveUntil", until == null ? null : until.toInstant().toString());
                return fields;
            }).optional();
    }

    UUID packageFor(OperationalCommercialAuthority.Scope scope) {
        return jdbc.sql("""
            SELECT v.package_id FROM cinenest_pricing.package_version v
            JOIN cinenest_pricing.package p ON p.package_id=v.package_id
            WHERE v.version_id=:version AND p.venue_id=:venue
            """).param("version", scope.version()).param("venue", scope.venue()).query(UUID.class).optional()
            .orElseThrow(() -> new DraftFailure(409, "Published package version unavailable"));
    }

    void requireActivatable(OperationalCommercialAuthority.Scope scope, UUID packageId, Instant from, Instant until) {
        boolean available = jdbc.sql("""
            SELECT true FROM cinenest_pricing.commercial_policy_draft d
            JOIN cinenest_pricing.commercial_policy_revision r
              ON r.policy_id=d.policy_id AND r.revision=d.current_revision
            WHERE d.policy_id=:policy AND d.venue_id=:venue
              AND (d.package_id IS NULL OR d.package_id=:package)
              AND r.revision=:revision AND r.content_hash=:hash
              AND r.submitted_for_review AND cinenest_pricing.commercial_policy_is_complete(r)
              AND r.effective_from_proposal<=:from
              AND (r.effective_until_proposal IS NULL OR :until<=r.effective_until_proposal)
            FOR SHARE OF d
            """).param("policy", scope.policy()).param("venue", scope.venue()).param("package", packageId)
            .param("revision", scope.revision()).param("hash", scope.policyHash())
            .param("from", time(from)).param("until", time(until)).query(Boolean.class).optional().orElse(false);
        if (!available) {
            throw new DraftFailure(409, "Approved policy revision or effective window unavailable");
        }
    }

    Optional<Receipt> activationReplay(UUID actor, UUID venue, UUID key) {
        return jdbc.sql("""
            SELECT activation_id, command_hash FROM cinenest_pricing.operational_policy_activation
            WHERE actor_id=:actor AND venue_id=:venue AND command_key=:key
            """).param("actor", actor).param("venue", venue).param("key", key)
            .query((rs, row) -> new Receipt(rs.getObject(1, UUID.class), rs.getString(2), null, null)).optional();
    }

    UUID insertActivation(MatchedGrant grant, OperationalCommercialAuthority.Scope scope,
                          Instant from, Instant until, OperationalCommercialAuthority.Evidence evidence,
                          UUID key, String hash, boolean qualificationOnly) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO cinenest_pricing.operational_policy_activation
                (activation_id,actor_id,venue_id,room_id,version_id,policy_id,policy_revision,policy_hash,
                 effective_from,effective_until,authority_until,trust_namespace,revocation_sequence,
                 grant_id,grant_revision,command_key,command_hash,qualification_only)
            VALUES (:id,:actor,:venue,:room,:version,:policy,:revision,:hash,:from,:until,:authorityUntil,
                    :namespace,:sequence,:grant,:grantRevision,:key,:commandHash,:qualificationOnly)
            """).param("id", id).param("actor", grant.account()).param("venue", scope.venue())
            .param("room", scope.room()).param("version", scope.version()).param("policy", scope.policy())
            .param("revision", scope.revision()).param("hash", scope.policyHash()).param("from", time(from))
            .param("until", time(until)).param("authorityUntil", time(evidence.validUntil()))
            .param("namespace", evidence.namespace()).param("sequence", evidence.sequence())
            .param("grant", grant.grantId()).param("grantRevision", grant.grantRevision())
            .param("key", key).param("commandHash", hash).param("qualificationOnly", qualificationOnly).update();
        jdbc.sql("""
            INSERT INTO cinenest_pricing.operational_policy_activation_audit (activation_id,actor_id,venue_id)
            VALUES (:id,:actor,:venue)
            """).param("id", id).param("actor", grant.account()).param("venue", scope.venue()).update();
        return id;
    }

    Activation active(Facts hold, UUID packageId, UUID version, Instant now, boolean qualificationOnly) {
        return jdbc.sql("""
            SELECT a.activation_id,a.policy_id,a.policy_revision,a.policy_hash,a.effective_until,
                   a.trust_namespace,to_jsonb(r)::text
            FROM cinenest_pricing.operational_policy_activation a
            JOIN cinenest_pricing.commercial_policy_revision r
              ON r.policy_id=a.policy_id AND r.revision=a.policy_revision AND r.content_hash=a.policy_hash
            JOIN cinenest_pricing.commercial_policy_draft d
              ON d.policy_id=a.policy_id AND d.current_revision=a.policy_revision AND d.venue_id=a.venue_id
            WHERE a.venue_id=:venue AND a.room_id=:room AND a.version_id=:version
              AND (d.package_id IS NULL OR d.package_id=:package)
              AND a.effective_from<=:now AND :now<a.effective_until
              AND a.qualification_only=:qualificationOnly
              AND a.effective_from<=:start AND :end<=a.effective_until
            FOR SHARE OF d
            """).param("venue", hold.venue()).param("room", hold.room()).param("version", version)
            .param("package", packageId).param("now", time(now)).param("qualificationOnly", qualificationOnly)
            .param("start", time(hold.start()))
            .param("end", time(hold.end())).query((rs, row) -> new Activation(rs.getObject(1, UUID.class),
                new OperationalCommercialAuthority.Scope(hold.venue(), hold.room(), version,
                    rs.getObject(2, UUID.class), rs.getLong(3), rs.getString(4)),
                rs.getString(6), rs.getObject(5, OffsetDateTime.class).toInstant(),
                JdbcPublicationRepository.json(rs.getString(7))))
            .optional().orElseThrow(() -> new DraftFailure(409, "Eligible commercial policy unavailable"));
    }

    Optional<Receipt> quoteReplay(UUID actor, UUID key) {
        return jdbc.sql("""
            SELECT quote_id,command_hash,activation_id,expires_at FROM cinenest_pricing.operational_quote
            WHERE actor_id=:actor AND command_key=:key
            """).param("actor", actor).param("key", key).query((rs, row) -> new Receipt(
                rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class),
                rs.getObject(4, OffsetDateTime.class).toInstant())).optional();
    }

    UUID insertQuote(MatchedGrant grant, Facts hold, UUID packageId, Activation activation,
                     OperationalCommercialAuthority.Evidence evidence, UUID key, String hash,
                     Map<String, Object> snapshot, long total, Instant expires) {
        UUID id = UUID.randomUUID();
        var scope = activation.scope();
        jdbc.sql("""
            INSERT INTO cinenest_pricing.operational_quote
                (quote_id,actor_id,hold_id,hold_revision,activation_id,venue_id,room_id,package_id,version_id,
                 policy_id,policy_revision,policy_hash,trust_namespace,revocation_sequence,command_key,
                 command_hash,content_hash,snapshot,total_minor,expires_at)
            VALUES (:id,:actor,:hold,:holdRevision,:activation,:venue,:room,:package,:version,:policy,
                    :policyRevision,:policyHash,:namespace,:sequence,:key,:hash,:snapshotHash,
                    CAST(:snapshot AS jsonb),:total,:expires)
            """).param("id", id).param("actor", grant.account()).param("hold", hold.id())
            .param("holdRevision", hold.revision()).param("activation", activation.id())
            .param("venue", scope.venue()).param("room", scope.room()).param("package", packageId)
            .param("version", scope.version()).param("policy", scope.policy()).param("policyRevision", scope.revision())
            .param("policyHash", scope.policyHash()).param("namespace", evidence.namespace()).param("sequence", evidence.sequence())
            .param("key", key).param("hash", hash).param("snapshotHash", PublicationService.hash(snapshot))
            .param("snapshot", JSONObjectUtils.toJSONString(snapshot)).param("total", total).param("expires", time(expires)).update();
        jdbc.sql("""
            INSERT INTO cinenest_pricing.operational_quote_audit (quote_id,actor_id,venue_id)
            VALUES (:id,:actor,:venue)
            """).param("id", id).param("actor", grant.account()).param("venue", scope.venue()).update();
        return id;
    }

    private static OffsetDateTime time(Instant instant) {
        return JdbcCommercialPolicyActivationRepository.time(instant);
    }

    private record Watermark(long sequence, String digest) {}

    private static DraftFailure unavailable() {
        return new DraftFailure(503, "Commercial authority unavailable");
    }
}
