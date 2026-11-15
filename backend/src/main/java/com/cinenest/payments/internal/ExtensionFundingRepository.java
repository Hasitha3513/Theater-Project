package com.cinenest.payments.internal;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ExtensionFundingRepository {
    record Terms(long purchasedRevision, long lifecycleVersion, long reservationRevision,
                 UUID packageVersionId, String configuration, String termsVersion,
                 long amountMinor, String currency) {}

    record Request(UUID id, UUID owner, UUID venue, UUID booking, UUID reservation, UUID key,
                   String commandHash, Terms terms, Instant createdAt, Instant expiresAt) {}

    private final JdbcClient jdbc;

    ExtensionFundingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private JdbcClient.MappedQuerySpec<Request> query(JdbcClient.StatementSpec statement) {
        return statement.query((rs, row) -> new Request(
                rs.getObject("request_id", UUID.class), rs.getObject("owner_id", UUID.class),
                rs.getObject("venue_id", UUID.class), rs.getObject("booking_id", UUID.class),
                rs.getObject("reservation_id", UUID.class), rs.getObject("command_key", UUID.class),
                rs.getString("command_sha256"),
                new Terms(rs.getLong("purchased_revision"), rs.getLong("lifecycle_version"),
                          rs.getLong("reservation_revision"), rs.getObject("package_version_id", UUID.class),
                          rs.getString("configuration"), rs.getString("terms_version"),
                          rs.getLong("amount_minor"), rs.getString("currency")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("expires_at", OffsetDateTime.class).toInstant()));
    }

    Optional<Request> byKey(UUID owner, UUID key) {
        return query(jdbc.sql("""
                SELECT * FROM cinenest_payments.extension_funding_request
                WHERE owner_id = :owner AND command_key = :key
                """).param("owner", owner).param("key", key)).optional();
    }

    Optional<Request> owned(UUID owner, UUID booking, UUID reservation) {
        return query(jdbc.sql("""
                SELECT * FROM cinenest_payments.extension_funding_request
                WHERE owner_id = :owner AND booking_id = :booking AND reservation_id = :reservation
                """).param("owner", owner).param("booking", booking)
                .param("reservation", reservation)).optional();
    }

    Optional<Request> insert(UUID owner, UUID venue, UUID booking, UUID reservation, UUID key,
                             String commandHash, Terms terms, Instant deadline) {
        return query(jdbc.sql("""
                INSERT INTO cinenest_payments.extension_funding_request
                    (request_id, owner_id, venue_id, booking_id, reservation_id, command_key, command_sha256,
                     purchased_revision, lifecycle_version, reservation_revision, package_version_id,
                     configuration, terms_version, amount_minor, currency, expires_at)
                SELECT :id, :owner, :venue, :booking, :reservation, :key, :hash, :purchase, :lifecycle,
                       :revision, :package, :configuration, :terms, :amount, :currency, :deadline
                WHERE clock_timestamp() < :deadline
                ON CONFLICT DO NOTHING
                RETURNING *
                """).param("id", UUID.randomUUID()).param("owner", owner).param("venue", venue)
                .param("booking", booking).param("reservation", reservation).param("key", key).param("hash", commandHash)
                .param("purchase", terms.purchasedRevision()).param("lifecycle", terms.lifecycleVersion())
                .param("revision", terms.reservationRevision()).param("package", terms.packageVersionId())
                .param("configuration", terms.configuration()).param("terms", terms.termsVersion())
                .param("amount", terms.amountMinor()).param("currency", terms.currency())
                .param("deadline", deadline.atOffset(ZoneOffset.UTC))).optional();
    }

    void audit(Request request) {
        jdbc.sql("""
                INSERT INTO cinenest_payments.extension_funding_audit (request_id, actor_id, venue_id)
                VALUES (:id, :owner, :venue)
                """).param("id", request.id()).param("owner", request.owner())
                .param("venue", request.venue()).update();
    }

    Instant now() {
        return jdbc.sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single().toInstant();
    }
}
