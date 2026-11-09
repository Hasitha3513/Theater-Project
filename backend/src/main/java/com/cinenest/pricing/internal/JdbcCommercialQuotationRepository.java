package com.cinenest.pricing.internal;

import com.cinenest.booking.QuotationHolds;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

final class JdbcCommercialQuotationRepository {
    private final JdbcClient jdbc;

    JdbcCommercialQuotationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Activation(UUID policy, long revision, String hash, Instant until, Instant approvalUntil,
                      Map<String, Object> terms) {}

    record Receipt(UUID id, String hash) {}

    boolean syntheticActor(UUID actor) {
        return jdbc.sql("SELECT synthetic FROM cinenest_identity.account WHERE account_id=:actor FOR SHARE")
            .param("actor", actor).query(Boolean.class).optional().orElse(false);
    }

    Activation active(UUID activation, QuotationHolds.Facts hold, UUID version, Instant now) {
        return jdbc.sql("""
            SELECT a.policy_id,a.revision,a.content_hash,a.effective_until,
                least(a.approval_until, checkpoint.observed_at + interval '15 minutes'),to_jsonb(r)::text
            FROM cinenest_pricing.fixture_policy_activation a
            JOIN cinenest_pricing.commercial_policy_revision r
              ON r.policy_id=a.policy_id AND r.revision=a.revision AND r.content_hash=a.content_hash
            JOIN cinenest_pricing.commercial_policy_draft d
              ON d.policy_id=a.policy_id AND d.current_revision=a.revision
            CROSS JOIN LATERAL (
                SELECT revoked, observed_at FROM cinenest_pricing.fixture_policy_revocations
                ORDER BY sequence DESC LIMIT 1
            ) checkpoint
            WHERE a.activation_id=:activation AND a.venue_id=:venue AND a.room_id=:room AND a.version_id=:version
              AND a.effective_from<=:now AND :now<a.effective_until AND :now<a.approval_until
              AND checkpoint.observed_at<=:now AND :now<checkpoint.observed_at + interval '15 minutes'
              AND NOT a.appointments && checkpoint.revoked
              AND a.effective_from<=:start AND :end<=a.effective_until
            FOR SHARE OF d
            """).param("activation", activation).param("venue", hold.venue()).param("room", hold.room())
            .param("version", version).param("now", JdbcCommercialPolicyActivationRepository.time(now))
            .param("start", JdbcCommercialPolicyActivationRepository.time(hold.start()))
            .param("end", JdbcCommercialPolicyActivationRepository.time(hold.end()))
            .query((rs, row) -> {
                try {
                    return new Activation(rs.getObject(1, UUID.class), rs.getLong(2), rs.getString(3),
                        rs.getObject(4, OffsetDateTime.class).toInstant(), rs.getObject(5, OffsetDateTime.class).toInstant(),
                        JSONObjectUtils.parse(rs.getString(6)));
                } catch (java.text.ParseException bad) {
                    throw new IllegalStateException("Stored policy is unreadable", bad);
                }
            }).optional().orElseThrow(() -> new DraftFailure(409, "Fixture activation unavailable"));
    }

    java.util.Optional<Receipt> replay(UUID actor, UUID key) {
        return jdbc.sql("""
            SELECT quote_id,command_hash FROM cinenest_pricing.fixture_commercial_quotation
            WHERE actor_id=:actor AND command_key=:key
            """).param("actor", actor).param("key", key)
            .query((rs, row) -> new Receipt(rs.getObject(1, UUID.class), rs.getString(2))).optional();
    }

    UUID insert(UUID actor, UUID key, String commandHash, QuotationHolds.Facts hold, UUID version, UUID activation,
                Activation policy, Map<String, Object> snapshot, long total, Instant expiry) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO cinenest_pricing.fixture_commercial_quotation
                (quote_id,actor_id,hold_id,hold_revision,venue_id,room_id,version_id,activation_id,policy_id,
                 policy_revision,policy_hash,snapshot,content_hash,total_minor,expires_at,command_key,command_hash)
            VALUES (:id,:actor,:hold,:revision,:venue,:room,:version,:activation,:policy,:policyRevision,:policyHash,
                    CAST(:snapshot AS jsonb),:hash,:total,:expires,:key,:commandHash)
            """).param("id", id).param("actor", actor).param("hold", hold.id()).param("revision", hold.revision())
            .param("venue", hold.venue()).param("room", hold.room()).param("version", version)
            .param("activation", activation).param("policy", policy.policy()).param("policyRevision", policy.revision())
            .param("policyHash", policy.hash()).param("snapshot", JSONObjectUtils.toJSONString(snapshot))
            .param("hash", PublicationService.hash(snapshot)).param("total", total)
            .param("expires", JdbcCommercialPolicyActivationRepository.time(expiry)).param("key", key)
            .param("commandHash", commandHash).update();
        jdbc.sql("""
            INSERT INTO cinenest_pricing.fixture_commercial_quote_audit(quote_id,actor_id,venue_id)
            VALUES (:id,:actor,:venue)
            """).param("id", id).param("actor", actor).param("venue", hold.venue()).update();
        return id;
    }
}
