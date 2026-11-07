package com.cinenest.pricing.internal;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Constructed only by the isolated qualification harness. */
final class JdbcCommercialPolicyBindingRepository implements CommercialPolicyBindingRepository {
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;

    JdbcCommercialPolicyBindingRepository(DataSource source) {
        jdbc = JdbcClient.create(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public Receipt bind(CommercialPolicyBinding command) {
        return Objects.requireNonNull(transaction.execute(status -> write(command)));
    }

    private Receipt write(CommercialPolicyBinding c) {
        jdbc.sql("SELECT cinenest_pricing.require_binding_authority(:actor,:venue,'commercial.policy.edit',NULL,NULL)")
                .param("actor", c.actor()).param("venue", c.venue()).query((row, n) -> true).single();
        var key = c.actor() + "/" + c.venue() + "/" + c.commandKey();
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 3701))")
                .param("key", key).query((row, n) -> true).single();
        var parameters = Map.ofEntries(
                Map.entry("actor", c.actor()), Map.entry("command", c.commandKey()),
                Map.entry("venue", c.venue()), Map.entry("room", c.room()),
                Map.entry("version", c.packageVersion()), Map.entry("policy", c.policy()),
                Map.entry("revision", c.revision()), Map.entry("hash", c.contentHash()),
                Map.entry("start", OffsetDateTime.ofInstant(c.from(), ZoneOffset.UTC)),
                Map.entry("end", OffsetDateTime.ofInstant(c.until(), ZoneOffset.UTC)),
                Map.entry("finance", c.financeReview()), Map.entry("legal", c.legalReview()),
                Map.entry("operations", c.operationsReview()));
        var existing = jdbc.sql("""
                SELECT binding_id,
                    (room_id, version_id, policy_id, revision, content_hash, effective_from,
                     effective_until, finance_review, legal_review, operations_review) =
                    (:room, :version, :policy, :revision, :hash, :start, :end, :finance, :legal, :operations) AS matches
                FROM cinenest_pricing.commercial_policy_binding
                WHERE actor_id=:actor AND venue_id=:venue AND command_key=:command
                """).params(parameters).query((row, n) -> {
                    if (!row.getBoolean("matches")) {
                        throw new IllegalArgumentException("Binding command key already used for different facts");
                    }
                    return new Receipt(row.getObject("binding_id", UUID.class), true);
                }).optional();
        if (existing.isPresent()) {
            return existing.get();
        }
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO cinenest_pricing.commercial_policy_binding
                    (binding_id, actor_id, command_key, venue_id, room_id, version_id, policy_id,
                     revision, content_hash, effective_from, effective_until, finance_review,
                     legal_review, operations_review, provenance)
                VALUES (:id,:actor,:command,:venue,:room,:version,:policy,:revision,:hash,
                    :start,:end,:finance,:legal,:operations,'SYNTHETIC_V36_REVIEW_ONLY')
                """).params(parameters).param("id", id).update();
        jdbc.sql("""
                INSERT INTO cinenest_pricing.commercial_policy_binding_audit
                    (binding_id, actor_id, venue_id, recorded_at)
                SELECT binding_id, actor_id, venue_id, recorded_at
                FROM cinenest_pricing.commercial_policy_binding WHERE binding_id=:id
                """).param("id", id).update();
        return new Receipt(id, false);
    }
}
