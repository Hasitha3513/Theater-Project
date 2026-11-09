package com.cinenest.pricing.internal;

import com.nimbusds.jose.util.JSONObjectUtils;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

final class JdbcCustomerCommercialQuoteRepository {
    private final JdbcClient jdbc;

    JdbcCustomerCommercialQuoteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Receipt(UUID id, String commandHash, UUID policy, long policyRevision,
                   String policyHash, Instant expires) {}

    boolean syntheticActor(UUID actor) {
        return jdbc.sql("SELECT synthetic FROM cinenest_identity.account WHERE account_id=:actor FOR SHARE")
            .param("actor", actor).query(Boolean.class).optional().orElse(false);
    }

    Instant now() {
        return jdbc.sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single().toInstant();
    }

    Optional<Receipt> replay(UUID actor, UUID key) {
        return jdbc.sql("""
            SELECT quote_id,command_hash,policy_id,policy_revision,policy_hash,expires_at
            FROM cinenest_pricing.customer_commercial_quote WHERE actor_id=:actor AND command_key=:key
            """).param("actor", actor).param("key", key)
            .query((rs, row) -> new Receipt(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getObject(3, UUID.class), rs.getLong(4), rs.getString(5),
                rs.getObject(6, OffsetDateTime.class).toInstant())).optional();
    }

    UUID insert(UUID actor, CustomerCommercialQuoteService.Command command, String commandHash,
                CustomerCommercialQuoteService.Scope scope, Map<String, Object> snapshot, long total, Instant expiry) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO cinenest_pricing.customer_commercial_quote
                (quote_id, actor_id, hold_id, hold_revision, venue_id, room_id, package_id, version_id,
                 policy_id, policy_revision, policy_hash, command_key, command_hash, snapshot, content_hash,
                 total_minor, expires_at)
            VALUES (:id,:actor,:hold,:holdRevision,:venue,:room,:packageId,:version,:policy,:policyRevision,
                    :policyHash,:key,:commandHash,CAST(:snapshot AS jsonb),:contentHash,:total,:expires)
            """).param("id", id).param("actor", actor).param("hold", command.hold())
            .param("holdRevision", command.revision()).param("venue", command.venue()).param("room", command.room())
            .param("packageId", command.packageId()).param("version", command.version()).param("policy", scope.policy())
            .param("policyRevision", scope.revision()).param("policyHash", scope.hash()).param("key", command.key())
            .param("commandHash", commandHash).param("snapshot", JSONObjectUtils.toJSONString(snapshot))
            .param("contentHash", PublicationService.hash(snapshot)).param("total", total)
            .param("expires", OffsetDateTime.ofInstant(expiry, ZoneOffset.UTC)).update();
        jdbc.sql("""
            INSERT INTO cinenest_pricing.customer_commercial_quote_audit (quote_id, actor_id, venue_id)
            VALUES (:id,:actor,:venue)
            """).param("id", id).param("actor", actor).param("venue", command.venue()).update();
        return id;
    }
}
