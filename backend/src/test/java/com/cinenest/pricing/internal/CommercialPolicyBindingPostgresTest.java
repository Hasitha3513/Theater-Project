package com.cinenest.pricing.internal;

import com.cinenest.identity.ScopedAuthorization.MatchedGrant;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CINENEST_V37_QUALIFICATION", matches = "AUTHORIZED_DISPOSABLE")
class CommercialPolicyBindingPostgresTest {
    private static final Instant FROM = Instant.parse("2030-02-01T10:00:00Z");
    private static final Instant UNTIL = FROM.plusSeconds(3600);
    private static final String SNAPSHOTS = """
            SELECT jsonb_build_array(
                (SELECT jsonb_agg(to_jsonb(q) ORDER BY quote_id,revision) FROM cinenest_pricing.quotation_revision q),
                (SELECT jsonb_agg(to_jsonb(a) ORDER BY quote_id) FROM cinenest_pricing.quotation_acceptance a),
                (SELECT jsonb_agg(to_jsonb(b) ORDER BY booking_id,revision) FROM cinenest_booking.booking_revision b))::text
            """;

    private final String url = System.getenv("CINENEST_DB_URL");
    private JdbcTemplate jdbc;
    private JdbcCommercialPolicyBindingRepository repository;

    @Test
    void cleanFlyway() throws SQLException {
        var source = database("cinenest_test");
        var flyway = flyway(source, "37");
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(36);
        var clean = new JdbcTemplate(source);
        verifyLatest(flyway, clean);
        clean.execute("TRUNCATE cinenest_pricing.package CASCADE");
        clean.execute("TRUNCATE cinenest_identity.account CASCADE");
        for (int isolation : List.of(Connection.TRANSACTION_REPEATABLE_READ, Connection.TRANSACTION_SERIALIZABLE)) {
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                connection.setTransactionIsolation(isolation);
                connection.setAutoCommit(false);
                assertThatThrownBy(() -> statement.execute("TRUNCATE cinenest_pricing.commercial_policy_binding CASCADE"))
                        .isInstanceOfSatisfying(SQLException.class, ex -> assertThat(ex.getSQLState()).isEqualTo("23514"));
                connection.rollback();
            }
        }
        for (String table : List.of("commercial_policy_binding", "commercial_policy_binding_audit")) {
            assertThat(clean.queryForObject("SELECT count(*) FROM cinenest_pricing." + table, Integer.class)).isZero();
        }
    }

    @Test
    void populatedUpgradeAndBindingIntegrity() throws Exception {
        var source = database("cinenest_v37_upgrade");
        jdbc = new JdbcTemplate(source);
        assertThat(flyway(source, "36").migrate().migrationsExecuted).isEqualTo(35);
        seedHistory();
        String snapshots = jdbc.queryForObject(SNAPSHOTS, String.class);
        assertThat(snapshots).contains("SIMULATED", "commercialIssuance");
        var flyway = flyway(source, "37");
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        verifyLatest(flyway, jdbc);
        assertThat(jdbc.queryForObject(SNAPSHOTS, String.class)).isEqualTo(snapshots);

        repository = new JdbcCommercialPolicyBindingRepository(source);
        var writer = new JdbcCommercialPolicyDraftRepository(JdbcClient.create(source));
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        UUID policy = id("policy");
        String hash = createPolicy(writer, tx, policy, true);
        var reviews = new LinkedHashMap<String, UUID>();
        var key = id("command");
        reject("42501", () -> repository.bind(binding(key, policy, hash, id("venue_A"),
                id("room_A1"), id("package_version"), List.of(id("absent"), id("absent"), id("absent")))));
        for (String domain : List.of("finance", "legal", "operations")) {
            reviews.put(domain, review(writer, tx, policy, hash, domain));
        }
        var approvals = List.copyOf(reviews.values());
        reject("42501", () -> repository.bind(binding(key, policy, hash, id("venue_B"),
                id("room_A1"), id("package_version"), approvals)));
        reject("23514", () -> repository.bind(binding(key, policy, hash, id("venue_A"),
                id("foreign_room"), id("package_version"), approvals)));
        reject("23503", () -> repository.bind(binding(key, policy, hash, id("venue_A"),
                id("room_A2"), id("package_version"), approvals)));
        reject("23514", () -> repository.bind(binding(key, policy, "f".repeat(64), id("venue_A"),
                id("room_A1"), id("package_version"), approvals)));
        reject("42501", () -> repository.bind(binding(key, policy, hash, id("venue_A"),
                id("room_A1"), id("package_version"), List.of(approvals.get(1), approvals.get(0), approvals.get(2)))));
        String incomplete = createPolicy(writer, tx, id("incomplete"), false);
        reject("23514", () -> repository.bind(binding(key, id("incomplete"), incomplete, id("venue_A"),
                id("room_A1"), id("package_version"), approvals)));
        var command = binding(key, policy, hash, id("venue_A"), id("room_A1"), id("package_version"), approvals);
        jdbc.update("UPDATE cinenest_identity.access_grant SET valid_until=clock_timestamp() WHERE grant_id=?", id("finance_grant"));
        reject("42501", () -> repository.bind(command));
        jdbc.update("UPDATE cinenest_identity.access_grant SET valid_until=clock_timestamp()+interval '15 minutes' WHERE grant_id=?", id("finance_grant"));
        jdbc.update("UPDATE cinenest_identity.access_grant SET state='REVOKED' WHERE grant_id=?", id("finance_grant"));
        reject("42501", () -> repository.bind(command));
        jdbc.update("UPDATE cinenest_identity.access_grant SET state='ACTIVE' WHERE grant_id=?", id("finance_grant"));

        jdbc.execute("""
                CREATE FUNCTION cinenest_pricing.reject_fixture_audit() RETURNS trigger LANGUAGE plpgsql AS
                $$ BEGIN RAISE EXCEPTION 'injected fixture audit failure' USING ERRCODE='23514'; END; $$
                """);
        jdbc.execute("""
                CREATE TRIGGER reject_fixture_audit BEFORE INSERT ON cinenest_pricing.commercial_policy_binding_audit
                FOR EACH ROW EXECUTE FUNCTION cinenest_pricing.reject_fixture_audit()
                """);
        try {
            reject("23514", () -> repository.bind(command));
            assertCounts(0);
        } finally {
            jdbc.execute("DROP TRIGGER reject_fixture_audit ON cinenest_pricing.commercial_policy_binding_audit");
            jdbc.execute("DROP FUNCTION cinenest_pricing.reject_fixture_audit()");
        }
        verifyWaitingTruncates(source, tx, command);
        var firstOverlap = adjacent(command, id("first_overlap"));
        var other = adjacent(command, id("other_command"));
        var ready = new CountDownLatch(2);
        try (var threads = Executors.newFixedThreadPool(2)) {
            var first = threads.submit(() -> attempt(firstOverlap, ready));
            var second = threads.submit(() -> attempt(other, ready));
            var a = first.get(20, TimeUnit.SECONDS);
            var b = second.get(20, TimeUnit.SECONDS);
            assertThat(List.of(a, b)).containsExactlyInAnyOrder("COMMITTED", "23P01");
            var winner = a.equals("COMMITTED") ? firstOverlap : other;
            var replay = repository.bind(winner);
            assertThat(replay.replay()).isTrue();
            assertThat(replay.bindingId()).isEqualTo(jdbc.queryForObject(
                    "SELECT binding_id FROM cinenest_pricing.commercial_policy_binding WHERE command_key=?", UUID.class, winner.commandKey()));
            assertThatThrownBy(() -> repository.bind(new CommercialPolicyBinding(
                    winner.actor(), winner.commandKey(), winner.venue(), winner.room(), winner.packageVersion(),
                    winner.policy(), winner.revision(), winner.contentHash(), winner.from(), winner.until().plusSeconds(1),
                    winner.financeReview(), winner.legalReview(), winner.operationsReview())))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertCounts(2);
        reject("23514", () -> jdbc.execute("UPDATE cinenest_pricing.commercial_policy_binding SET revision=revision"));
        reject("23514", () -> jdbc.execute("DELETE FROM cinenest_pricing.commercial_policy_binding"));
        for (String table : List.of("cinenest_pricing.commercial_policy_binding_audit",
                "cinenest_pricing.package", "cinenest_identity.account")) {
            reject("23514", () -> jdbc.execute("TRUNCATE " + table + " CASCADE"));
        }
        assertCounts(2);
        assertThat(jdbc.queryForObject(SNAPSHOTS, String.class)).isEqualTo(snapshots);
        assertThat(jdbc.queryForObject("SELECT DISTINCT provenance FROM cinenest_pricing.commercial_policy_binding",
                String.class)).isEqualTo("SYNTHETIC_V36_REVIEW_ONLY");
    }

    private static CommercialPolicyBinding adjacent(CommercialPolicyBinding c, UUID key) {
        return new CommercialPolicyBinding(c.actor(), key, c.venue(), c.room(), c.packageVersion(), c.policy(),
                c.revision(), c.contentHash(), c.until(), c.until().plusSeconds(3600), c.financeReview(),
                c.legalReview(), c.operationsReview());
    }

    private void verifyWaitingTruncates(DriverManagerDataSource source, TransactionTemplate tx,
            CommercialPolicyBinding command) throws Exception {
        String truncate = "TRUNCATE cinenest_pricing.commercial_policy_binding CASCADE";
        var insertPid = new CompletableFuture<Integer>();
        var truncatePid = new CompletableFuture<Integer>();
        var inserted = new CompletableFuture<UUID>();
        var allowCommit = new CompletableFuture<Void>();
        var workers = Executors.newFixedThreadPool(2);
        try (var first = source.getConnection(); var statement = first.createStatement()) {
            first.setAutoCommit(false);
            statement.execute("SET LOCAL statement_timeout='10s'");
            statement.execute(truncate);
            var firstPid = first.unwrap(org.postgresql.PGConnection.class).getBackendPID();
            var insert = workers.submit(() -> tx.execute(status -> {
                jdbc.execute("SET LOCAL statement_timeout='15s'");
                insertPid.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                var receipt = repository.bind(command);
                inserted.complete(receipt.bindingId());
                allowCommit.orTimeout(10, TimeUnit.SECONDS).join();
                return receipt.bindingId();
            }));
            int inserting = insertPid.get(5, TimeUnit.SECONDS);
            awaitBlock(inserting, firstPid);
            first.commit();

            UUID bindingId = inserted.get(5, TimeUnit.SECONDS);
            var second = workers.submit(() -> {
                try (var connection = source.getConnection(); var query = connection.createStatement()) {
                    connection.setAutoCommit(false);
                    query.execute("SET LOCAL statement_timeout='10s'");
                    truncatePid.complete(connection.unwrap(org.postgresql.PGConnection.class).getBackendPID());
                    assertThatThrownBy(() -> query.execute(truncate))
                            .isInstanceOfSatisfying(SQLException.class, ex -> assertThat(ex.getSQLState()).isEqualTo("23514"));
                    connection.rollback();
                    try (var rows = query.executeQuery("SELECT count(*) FROM cinenest_pricing.commercial_policy_binding")) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getInt(1)).isEqualTo(1);
                    }
                }
                return null;
            });
            awaitBlock(truncatePid.get(5, TimeUnit.SECONDS), inserting);
            allowCommit.complete(null);
            assertThat(insert.get(5, TimeUnit.SECONDS)).isEqualTo(bindingId);
            second.get(5, TimeUnit.SECONDS);
            assertCounts(1);
            assertThat(jdbc.queryForObject(
                    "SELECT binding_id FROM cinenest_pricing.commercial_policy_binding_audit", UUID.class))
                    .isEqualTo(bindingId);
        } finally {
            allowCommit.complete(null);
            workers.shutdownNow();
            assertThat(workers.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void awaitBlock(int waiter, int blocker) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        do {
            if (Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, blocker, waiter))) {
                return;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        fail("Expected PostgreSQL lock wait for PID " + waiter + " behind " + blocker);
    }

    private String attempt(CommercialPolicyBinding command, CountDownLatch ready) throws InterruptedException {
        ready.countDown();
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            repository.bind(command);
            return "COMMITTED";
        } catch (RuntimeException ex) {
            return sqlState(ex);
        }
    }

    private void assertCounts(int expected) {
        for (String table : List.of("commercial_policy_binding", "commercial_policy_binding_audit")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM cinenest_pricing." + table, Integer.class))
                    .isEqualTo(expected);
        }
    }

    private static void reject(String state, Runnable write) {
        assertThatThrownBy(write::run).satisfies(ex -> assertThat(sqlState(ex)).isEqualTo(state));
    }

    private static String sqlState(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                return sql.getSQLState();
            }
        }
        throw new AssertionError("No SQLSTATE", failure);
    }

    private DriverManagerDataSource database(String name) {
        assertThat(url).matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/cinenest_test");
        return new DriverManagerDataSource(url.substring(0, url.lastIndexOf('/') + 1) + name,
                System.getenv("CINENEST_DB_USER"), System.getenv("CINENEST_DB_PASSWORD"));
    }

    private static Flyway flyway(DriverManagerDataSource source, String target) {
        return Flyway.configure().dataSource(source).target(target).load();
    }

    private static void verifyLatest(Flyway flyway, JdbcTemplate jdbc) {
        flyway.validate();
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("37");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL",
                Integer.class)).isEqualTo(36);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='31'",
                Integer.class)).isZero();
    }

    private static CommercialPolicyBinding binding(UUID key, UUID policy, String hash, UUID venue, UUID room,
            UUID version, List<UUID> reviews) {
        return new CommercialPolicyBinding(id("admin"), key, venue, room, version, policy, 1, hash, FROM, UNTIL,
                reviews.get(0), reviews.get(1), reviews.get(2));
    }

    private static UUID id(String name) {
        return UUID.nameUUIDFromBytes(("v37-synthetic:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static MatchedGrant grant(String role, String permission) {
        return new MatchedGrant(id(role), 0, 0, id("venue_A"), id(role + "_grant"), 0, permission);
    }

    private String createPolicy(JdbcCommercialPolicyDraftRepository writer, TransactionTemplate tx,
            UUID policy, boolean complete) {
        var document = document();
        if (!complete) {
            document.put("fees", List.of());
            document.put("submittedForReview", false);
        }
        tx.executeWithoutResult(status -> writer.write(grant("admin", "commercial.policy.edit"), policy,
                id("package"), UUID.randomUUID(), "CREATE", 0, "a".repeat(64), "Synthetic V37 fixture",
                CommercialPolicyDraftRules.document(document), null));
        return jdbc.queryForObject("SELECT content_hash FROM cinenest_pricing.commercial_policy_revision WHERE policy_id=?",
                String.class, policy);
    }

    private UUID review(JdbcCommercialPolicyDraftRepository writer, TransactionTemplate tx,
            UUID policy, String hash, String role) {
        String domain = role.toUpperCase(java.util.Locale.ROOT);
        tx.executeWithoutResult(status -> writer.write(grant(role, "commercial.policy.review." + role), policy,
                null, UUID.randomUUID(), "REVIEW", 1, "a".repeat(64), "Synthetic V37 fixture", null,
                Map.of("domain", domain, "outcome", "APPROVE", "expectedContentHash", hash,
                        "evidenceReference", "SYNTHETIC-NOT-AUTHORITY")));
        return jdbc.queryForObject("""
                SELECT review_id FROM cinenest_pricing.commercial_policy_review WHERE policy_id=? AND domain=?
                """, UUID.class, policy, domain);
    }

    private static Map<String,Object> document(){
        var d=new LinkedHashMap<String,Object>();
        d.put("title","Synthetic qualified policy");
        d.put("effectiveFromProposal","2030-01-01T00:00:00Z");
        d.put("effectiveUntilProposal",null);
        d.put("taxApplicability","NOT_APPLICABLE");
        d.put("taxType","NONE");
        d.put("taxRateBasisPoints",null);
        d.put("taxAmountMinor",null);
        d.put("taxTreatment","NONE");
        d.put("taxBasis","NONE");
        d.put("taxRounding","NONE");
        d.put("paymentMethods",List.of("BANK"));
        d.put("feeChannels",List.of("ONLINE"));
        d.put("fees",List.of(Map.of("method","BANK","channel","ONLINE","type","NONE","amountMinor","0","currency","LKR","refundability","REFUNDABLE")));
        d.put("cancellationEligibility","NOT_ALLOWED");
        d.put("cancellationCutoffMinutes",null);
        d.put("cancellationTimeBasis","VENUE_TIMEZONE");
        d.put("noShowTreatment","NO_REFUND");
        d.put("exceptionHandling","NONE");
        d.put("exceptionNotes","");
        d.put("refundEligibility","NONE");
        d.put("refundCalculation","NONE");
        d.put("refundBasis","NONE");
        d.put("refundRateBasisPoints",null);
        d.put("refundAmountMinor",null);
        d.put("nonRefundableComponents",List.of());
        d.put("refundDestination","NONE");
        d.put("suppliedReferences",List.of("SYNTHETIC-NOT-AUTHORITY"));
        d.put("submittedForReview",true);
        return d;
    }

    private void seedHistory() {
        for (String role : List.of("admin", "finance", "legal", "operations")) {
            jdbc.update("INSERT INTO cinenest_identity.account VALUES (?,TRUE,'ELIGIBLE',0)", id(role));
        }
        String sql = """
                INSERT INTO cinenest_rooms.venue(venue_id,code,display_name,timezone,cleaning_buffer_minutes,enabled) VALUES ('@venue_A@','UATA','Synthetic Venue A','Asia/Colombo',15,true),('@venue_B@','UATB','Synthetic Venue B','Asia/Colombo',15,true);
                INSERT INTO cinenest_rooms.room(room_id,venue_id,code,name,capacity,enabled) VALUES ('@room_A1@','@venue_A@','A1','Room A1',5,true),('@room_A2@','@venue_A@','A2','Room A2',5,true);
                INSERT INTO cinenest_pricing.package(package_id,venue_id,code) VALUES ('@package@','@venue_A@','UAT');
                INSERT INTO cinenest_pricing.package_version(version_id,package_id,version_no,source_revision,content_hash,snapshot,policy_refs,author_id,publisher_id,reason) VALUES ('@package_version@','@package@',1,0,'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb','{"synthetic":true}','{}','@admin@','@finance@','Synthetic UAT fixture');
                INSERT INTO cinenest_pricing.package_version_room VALUES ('@package_version@','@room_A1@');
                INSERT INTO cinenest_pricing.quotation_policy_revision(policy_id,profile,currency,synthetic,tax_minor,bank_fee_minor,gateway_fee_minor,approval_reference) VALUES ('@quote_policy@','SYNTHETIC_FIXED_MINOR_V1','LKR',true,0,0,0,'SYNTHETIC UAT');
                INSERT INTO cinenest_booking.hold_policy(policy_id,ttl_seconds,rate_count,rate_seconds,approval_reference) VALUES ('@hold_policy@',600,10,60,'SYNTHETIC UAT');
                INSERT INTO cinenest_booking.inventory_block(block_id,venue_id,room_id,session_start,session_end,buffer_minutes,blocked_until,blocking,kind) VALUES ('@confirmed_inventory@','@venue_A@','@room_A1@','2030-02-03T04:30Z','2030-02-03T07:30Z',15,'2030-02-03T07:45Z',true,'CONFIRMED');
                INSERT INTO cinenest_booking.reservation_hold(hold_id,owner_id,block_id,policy_id,guests,configuration,timezone,minimum_policy_ids,created_at,expires_at,state) VALUES ('@hold@','@admin@','@confirmed_inventory@','@hold_policy@',1,'SYNTHETIC','Asia/Colombo','',clock_timestamp(),'2030-02-03T04:30Z','CONSUMED');
                INSERT INTO cinenest_pricing.quotation(quote_id,owner_id,hold_id,venue_id,room_id,package_id,current_revision) VALUES ('@quote@','@admin@','@hold@','@venue_A@','@room_A1@','@package@',1);
                INSERT INTO cinenest_pricing.quotation_revision(quote_id,revision,version_id,policy_id,hold_revision,expires_at,payment_method,snapshot,content_hash,total_minor) VALUES ('@quote@',1,'@package_version@','@quote_policy@',0,'2030-02-03T04:30Z','BANK','{"synthetic":true,"commercialIssuance":false,"fixture":"SIMULATED"}','aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',250000);
                INSERT INTO cinenest_pricing.quotation_acceptance(quote_id,revision,actor_id,content_hash,synthetic) VALUES ('@quote@',1,'@admin@','aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',true);
                INSERT INTO cinenest_booking.booking(booking_id,request_id,hold_id,owner_id,venue_id,state,synthetic) VALUES ('@booking@','@booking_request@','@hold@','@admin@','@venue_A@','SIMULATED_CONFIRMED',true);
                INSERT INTO cinenest_booking.booking_revision(booking_id,revision,quote_id,quote_revision,quote_hash,amount_minor,currency,snapshot) VALUES ('@booking@',1,'@quote@',1,'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',250000,'LKR','{"synthetic":true,"commercialIssuance":false,"fixture":"SIMULATED"}');
                INSERT INTO cinenest_rooms.room(room_id,venue_id,code,name,capacity) VALUES ('@foreign_room@','@venue_B@','B1','Foreign fixture',5);
                """;
        var matcher = java.util.regex.Pattern.compile("@([a-zA-Z0-9_]+)@").matcher(sql);
        jdbc.execute(matcher.replaceAll(match -> id(match.group(1)).toString()));
        for (String role : List.of("admin", "finance", "legal", "operations")) {
            jdbc.update("INSERT INTO cinenest_identity.account_scope VALUES (?,?)", id(role), id("venue_A"));
            jdbc.update("""
                    INSERT INTO cinenest_identity.access_grant VALUES
                    (?,?,?,'VENUE',?,'ACTIVE',clock_timestamp()-interval '1 minute',
                    clock_timestamp()+interval '15 minutes',0)
                    """, id(role + "_grant"), id(role),
                    role.equals("admin") ? "commercial.policy.edit" : "commercial.policy.review." + role,
                    id("venue_A"));
        }
    }
}
