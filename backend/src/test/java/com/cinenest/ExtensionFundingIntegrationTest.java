package com.cinenest.payments.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cinenest.booking.ExtensionReservationContext;
import com.cinenest.identity.ScopedAuthorization;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.mock.env.MockEnvironment;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "CINENEST_EXTENSION_FUNDING_QUALIFICATION", matches = "AUTHORIZED_DISPOSABLE")
class ExtensionFundingIntegrationTest {
    private static final String CLEAN = "cinenest_extension_funding_clean";
    private static final String UPGRADE = "cinenest_extension_funding_upgrade";
    private static JdbcClient jdbc;
    private static ExtensionFundingRepository requests;
    private static DataSourceTransactionManager manager;
    private static TransactionTemplate tx;

    @BeforeAll static void cleanMigration() {
        var db = source(CLEAN);
        var flyway = Flyway.configure().dataSource(db).load();
        assertThat(flyway.info().applied()).isEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(42);
        flyway.validate();
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("43");
        jdbc = JdbcClient.create(db);
        requests = new ExtensionFundingRepository(jdbc);
        manager = new DataSourceTransactionManager(db);
        tx = new TransactionTemplate(manager);
        tx.setTimeout(10);
    }

    private static DriverManagerDataSource source(String database) {
        String root = System.getenv("CINENEST_DB_URL");
        if (root == null || !root.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/cinenest_test"))
            throw new IllegalStateException("Dedicated disposable loopback PostgreSQL required");
        return new DriverManagerDataSource(root.substring(0, root.lastIndexOf('/') + 1) + database,
                System.getenv("CINENEST_DB_USER"), System.getenv("CINENEST_DB_PASSWORD"));
    }

    private record Input(UUID owner, UUID venue, UUID booking, UUID reservation, UUID key,
                         ExtensionFundingRepository.Terms terms) {
        Input() {
            this(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    new ExtensionFundingRepository.Terms(1, 0, 0, UUID.randomUUID(), "frozen",
                            "SYNTHETIC_EXTENSION_RESERVATION_V1", 1200, "LKR"));
        }

        java.util.Optional<ExtensionFundingRepository.Request> insert(Instant deadline) {
            return requests.insert(owner, venue, booking, reservation, key, "a".repeat(64), terms, deadline);
        }
    }

    @Test void populatedV42UpgradeRetainsScopedDataAndHistoricalChecksums() {
        var db = source(UPGRADE);
        var previous = Flyway.configure().dataSource(db).target("42").load();
        assertThat(previous.info().applied()).isEmpty();
        assertThat(previous.migrate().migrationsExecuted).isEqualTo(41);
        var client = JdbcClient.create(db);
        var venue = UUID.randomUUID();
        client.sql("""
                INSERT INTO cinenest_rooms.venue
                    (venue_id,code,display_name,timezone,cleaning_buffer_minutes,enabled)
                VALUES (:venue,'FUNDINGUP','Synthetic upgrade venue','UTC',15,true)
                """).param("venue", venue).update();
        var historical = client.sql("""
                SELECT version, checksum FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank
                """).query().listOfRows();
        var latest = Flyway.configure().dataSource(db).load();
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
        latest.validate();
        assertThat(latest.info().current().getVersion().toString()).isEqualTo("43");
        assertThat(client.sql("""
                SELECT version, checksum FROM flyway_schema_history
                WHERE version IS NOT NULL AND version <> '43' ORDER BY installed_rank
                """).query().listOfRows()).isEqualTo(historical);
        assertThat(client.sql("SELECT display_name FROM cinenest_rooms.venue WHERE venue_id=:venue")
                .param("venue", venue).query(String.class).single()).isEqualTo("Synthetic upgrade venue");
    }

    @Test void exactDeadlineAndMissingAuditLeaveNoRequest() {
        var input = new Input();
        var deadline = requests.now();
        var expired = tx.execute(status -> input.insert(deadline));
        assertThat(expired).isEmpty();
        assertThatThrownBy(() -> tx.execute(status -> input.insert(deadline.plusSeconds(30))))
                .isInstanceOf(RuntimeException.class);
        assertThat(requests.byKey(input.owner(), input.key())).isEmpty();
    }

    @Test void concurrentDuplicatesCommitOneImmutableRequestAndAudit() throws Exception {
        var input = new Input();
        var deadline = requests.now().plusSeconds(30);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> record(input, deadline));
            var two = pool.submit(() -> record(input, deadline));
            assertThat(java.util.List.of(one.get(12, TimeUnit.SECONDS), two.get(12, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        var row = requests.owned(input.owner(), input.booking(), input.reservation()).orElseThrow();
        assertThat(requests.owned(UUID.randomUUID(), input.booking(), input.reservation())).isEmpty();
        assertThat(jdbc.sql("""
                SELECT actor_id FROM cinenest_payments.extension_funding_audit WHERE request_id=:id
                """).param("id", row.id()).query(UUID.class).single()).isEqualTo(input.owner());
        assertThatThrownBy(() -> jdbc.sql("""
                UPDATE cinenest_payments.extension_funding_request SET amount_minor=1 WHERE request_id=:id
                """).param("id", row.id()).update()).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.sql("""
                DELETE FROM cinenest_payments.extension_funding_audit WHERE request_id=:id
                """).param("id", row.id()).update()).isInstanceOf(DataAccessException.class);
    }

    @Test void realServicePersistsOwnedUnfundedRequestAndRecoversWithoutFinancialOrBookingWrites() {
        var input = new Input();
        var other = UUID.randomUUID();
        var inactive = new AtomicBoolean();
        var deadline = requests.now().plusSeconds(120);
        var env = new MockEnvironment();
        env.setActiveProfiles("synthetic-quotation");
        ScopedAuthorization identity = new ScopedAuthorization() {
            @Override public boolean eligible(UUID actor) { return input.owner().equals(actor) || other.equals(actor); }
            @Override public boolean eligible(UUID actor, boolean lock) { return eligible(actor); }
            @Override public boolean permits(UUID actor, UUID venue, String permission, boolean lock) { return false; }
            @Override public Set<UUID> venues(UUID actor, String permission) { return Set.of(); }
        };
        ExtensionReservationContext booking = (actor, bookingId, reservationId) -> {
            if (!input.owner().equals(actor) || !input.booking().equals(bookingId)
                    || !input.reservation().equals(reservationId)) {
                throw new ExtensionReservationContext.Rejected(404, "Synthetic test reservation unavailable");
            }
            return new ExtensionReservationContext.Observation(bookingId, reservationId, input.venue(), 1, 0,
                    inactive.get() ? 1 : 0, inactive.get() ? ExtensionReservationContext.State.WITHDRAWN
                        : ExtensionReservationContext.State.ACTIVE, "frozen", null, null, null, 1200, "LKR",
                    input.terms().packageVersionId(), "FROZEN_PURCHASED_TERMS", 30, input.terms().termsVersion(),
                    deadline.minusSeconds(600), deadline, requests.now(), !inactive.get(), false, List.of());
        };
        var service = new ExtensionFundingService(requests, booking, identity, env, manager, true);
        var command = fundingCommand(input, input.venue(), "1200");
        var protectedTables = List.of("cinenest_payments.payment_allocation", "cinenest_booking.booking_revision",
                "cinenest_booking.inventory_block", "cinenest_payments.incoming_payment_credit");
        var before = protectedTables.stream().map(this::count).toList();
        assertThatThrownBy(() -> service.create(input.owner(), input.booking(), input.reservation(),
                fundingCommand(input, UUID.randomUUID(), "1200")))
                .isInstanceOfSatisfying(BankFailure.class, failure -> assertThat(failure.status).isEqualTo(404));
        assertThatThrownBy(() -> service.create(other, input.booking(), input.reservation(), command))
                .isInstanceOfSatisfying(ExtensionReservationContext.Rejected.class,
                        failure -> assertThat(failure.status()).isEqualTo(404));
        assertThat(requests.byKey(input.owner(), input.key())).isEmpty();

        var created = service.create(input.owner(), input.booking(), input.reservation(), command);
        assertThat(created.state()).isEqualTo("PENDING");
        assertThat(created.funded()).isFalse();
        assertThat(created.amountMinor()).isEqualTo("1200");
        assertThat(created.packageVersionId()).isEqualTo(input.terms().packageVersionId());
        assertThat(created.reservationRevision()).isZero();
        assertThat(service.create(input.owner(), input.booking(), input.reservation(), command).requestId())
                .isEqualTo(created.requestId());
        assertThatThrownBy(() -> service.create(input.owner(), input.booking(), input.reservation(),
                fundingCommand(input, input.venue(), "1201")))
                .isInstanceOfSatisfying(BankFailure.class, failure -> assertThat(failure.status).isEqualTo(409));
        assertThatThrownBy(() -> service.read(other, input.booking(), input.reservation(), input.key()))
                .isInstanceOfSatisfying(BankFailure.class, failure -> assertThat(failure.status).isEqualTo(404));
        inactive.set(true);
        var recovered = service.read(input.owner(), input.booking(), input.reservation(), input.key());
        assertThat(recovered.requestId()).isEqualTo(created.requestId());
        assertThat(recovered.state()).isEqualTo("INACTIVE");
        assertThat(recovered.funded()).isFalse();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM cinenest_payments.extension_funding_audit WHERE request_id=:id AND actor_id=:owner
                """).param("id", created.requestId()).param("owner", input.owner()).query(Long.class).single())
                .isEqualTo(1L);
        assertThat(protectedTables.stream().map(this::count).toList()).isEqualTo(before);
    }

    private ExtensionFundingService.Command fundingCommand(Input input, UUID venue, String amount) {
        return new ExtensionFundingService.Command(input.key(), venue, 1, 0, input.terms().packageVersionId(),
                input.terms().termsVersion(), amount, "LKR", true);
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private boolean record(Input input, Instant deadline) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            var inserted = input.insert(deadline);
            inserted.ifPresent(requests::audit);
            return inserted.isPresent();
        }));
    }
}
