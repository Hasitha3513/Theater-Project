package com.cinenest.pricing.internal;

import static com.cinenest.pricing.internal.CommercialPolicyTrustVerifier.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.identity.ScopedAuthorization.MatchedGrant;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named="CINENEST_V40_QUALIFICATION", matches="AUTHORIZED_DISPOSABLE")
class CommercialPolicyActivationPostgresTest {
    static final String MIGRATION_HISTORY = """
        SELECT jsonb_object_agg(version, checksum)::text FROM flyway_schema_history
        WHERE success AND version IS NOT NULL AND version NOT IN ('39','40')
        """;
    static final String SNAPSHOTS = """
        SELECT jsonb_build_array(
          (SELECT jsonb_agg(to_jsonb(q) ORDER BY quote_id,revision) FROM cinenest_pricing.quotation_revision q),
          (SELECT jsonb_agg(to_jsonb(a) ORDER BY quote_id) FROM cinenest_pricing.quotation_acceptance a),
          (SELECT jsonb_agg(to_jsonb(b) ORDER BY booking_id,revision) FROM cinenest_booking.booking_revision b))::text
        """;
    JdbcTemplate jdbc;
    JdbcClient client;
    TransactionTemplate tx;
    ScopedAuthorization identity;
    JdbcCommercialPolicyActivationRepository repository;
    CommercialPolicyActivationService service;
    Scope scope;
    Instant now;
    private KeyPair appointing, identityVerifier, custody, revocation;
    List<Approval> approvals;
    Revocations checkpoint;

    @Test
    void cleanFlywayAndActivationIntegrity() throws Exception {
        init("cinenest_test");
        var flyway = flyway("cinenest_test", "40");
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(39);
        validate(flyway);
        seedHistory();
        prepareActivation();

        UUID key = UUID.randomUUID();
        assertThatThrownBy(() -> service.activateFixture(id("finance"), key, scope, approvals, checkpoint))
            .isInstanceOf(DraftFailure.class);
        assertThatThrownBy(() -> service.activateFixture(id("admin"), key, scope, approvals.subList(0, 2), checkpoint))
            .isInstanceOf(DraftFailure.class);
        var wrongHash = new Scope(scope.venue(),scope.room(),scope.packageVersion(),scope.policy(),scope.revision(),
            "f".repeat(64),scope.effectiveFrom(),scope.effectiveUntil());
        assertThatThrownBy(() -> service.activateFixture(id("admin"), key, wrongHash, approvals, checkpoint))
            .isInstanceOf(DraftFailure.class);
        installAuditFailure("fixture_policy_activation_audit");
        assertThatThrownBy(() -> service.activateFixture(id("admin"), key, scope, approvals, checkpoint))
            .isInstanceOf(DataAccessException.class);
        assertThat(count("fixture_policy_activation")).isZero();
        assertThat(count("fixture_policy_revocations")).isZero();
        removeAuditFailure("fixture_policy_activation_audit");

        UUID saved;
        var competing = Executors.newSingleThreadExecutor();
        try {
            var started = new CompletableFuture<Integer>();
            var command = new java.util.concurrent.atomic.AtomicReference<UUID>();
            var overlap = tx.execute(status -> {
                command.set(service.activateFixture(id("admin"), key, scope, approvals, checkpoint));
                var future = competing.submit(() -> tx.execute(s -> {
                    started.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    return service.activateFixture(id("admin"), UUID.randomUUID(), scope, approvals, checkpoint);
                }));
                awaitLock(started);
                return future;
            });
            assertThatThrownBy(() -> overlap.get(5, TimeUnit.SECONDS))
                .hasRootCauseInstanceOf(java.sql.SQLException.class)
                .rootCause().extracting(error -> ((java.sql.SQLException) error).getSQLState()).isEqualTo("23P01");
            saved = command.get();
        } finally {
            competing.shutdownNow();
        }
        assertThat(service.activateFixture(id("admin"), key, scope, approvals, checkpoint)).isEqualTo(saved);
        var alternate = List.of(approval(Domain.FINANCE), approvals.get(1), approvals.get(2));
        assertThatThrownBy(() -> service.activateFixture(id("admin"), key, scope, alternate, checkpoint))
            .isInstanceOf(DraftFailure.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM cinenest_pricing.fixture_policy_activation"))
            .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> service.activateFixture(id("admin"), UUID.randomUUID(), scope, approvals, checkpoint))
            .hasRootCauseInstanceOf(java.sql.SQLException.class)
            .rootCause().extracting(error -> ((java.sql.SQLException) error).getSQLState()).isEqualTo("23P01");

        // Competing activation and signed revocation use the same transaction lock.
        var revokedCheckpoint = revocations(2, Set.of(approvals.getFirst().appointment().id()));
        var worker = Executors.newSingleThreadExecutor();
        try {
            var started = new CompletableFuture<Integer>();
            var rejected = tx.execute(status -> {
                service.reconcileFixtureRevocations(revokedCheckpoint);
                var future = worker.submit(() -> tx.execute(s -> {
                    started.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    return service.activateFixture(id("admin"), UUID.randomUUID(), scope, approvals, checkpoint);
                }));
                awaitLock(started);
                return future;
            });
            assertThatThrownBy(() -> rejected.get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(DraftFailure.class);
        } finally {
            worker.shutdownNow();
        }
        assertThat(count("fixture_policy_activation")).isEqualTo(1);
        assertThat(count("fixture_policy_activation_audit")).isEqualTo(1);
        assertThat(count("fixture_policy_revocations")).isEqualTo(2);
    }

    private void awaitLock(Future<Integer> backend) {
        try {
            int pid = backend.get(5, TimeUnit.SECONDS);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                if (Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT pg_backend_pid()=ANY(pg_blocking_pids(?))", Boolean.class, pid))) {
                    return;
                }
                Thread.sleep(20);
            }
            throw new AssertionError("Fixture transaction did not wait on the revocation lock");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        } catch (ExecutionException | TimeoutException failure) {
            throw new AssertionError(failure);
        }
    }

    void init(String db) {
        var source = source(db);
        jdbc = new JdbcTemplate(source);
        client = JdbcClient.create(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        identity = mock(ScopedAuthorization.class);
        repository = new JdbcCommercialPolicyActivationRepository(client);
    }

    void prepareActivation() throws Exception {
        var grant = grant("admin", "commercial.policy.edit");
        when(identity.matchedGrant(id("admin"), id("venue_A"), "commercial.policy.edit", true))
            .thenReturn(Optional.of(grant));
        var writer = new JdbcCommercialPolicyDraftRepository(client);
        var terms = document();
        now = repository.now();
        terms.put("effectiveFromProposal", now.minusSeconds(300).toString());
        tx.executeWithoutResult(s -> writer.write(grant, id("policy"), id("package"), UUID.randomUUID(),
            "CREATE", 0, "a".repeat(64), "Synthetic V40", CommercialPolicyDraftRules.document(terms), null));
        String hash = jdbc.queryForObject("SELECT content_hash FROM cinenest_pricing.commercial_policy_revision WHERE policy_id=?",
            String.class, id("policy"));
        scope = new Scope(id("venue_A"), id("room_A1"), id("package_version"), id("policy"), 1, hash,
            now.minusSeconds(60), now.plusSeconds(86400));
        appointing = key();
        identityVerifier = key();
        custody = key();
        revocation = key();
        var verifier = new CommercialPolicyTrustVerifier(new Authorities(appointing.getPublic(),
            identityVerifier.getPublic(), custody.getPublic(), revocation.getPublic()));
        service = new CommercialPolicyActivationService(identity, verifier, repository, tx, id("admin"));
        approvals = List.of(approval(Domain.FINANCE), approval(Domain.LEGAL), approval(Domain.OPERATIONS));
        checkpoint = revocations(1, Set.of());
    }

    Approval approval(Domain domain) throws Exception {
        var signing = key();
        var appointment = new Appointment(UUID.randomUUID(), UUID.randomUUID(), domain, scope,
            signing.getPublic(), now.minusSeconds(120), now.plusSeconds(600));
        var approval = new Approval(appointment, sign(appointing, canonical("APPOINTMENT", appointment.canonical())),
            sign(identityVerifier, canonical("IDENTITY", appointment.canonical())),
            sign(custody, canonical("CUSTODY", appointment.canonical())), now, now.plusSeconds(500), "");
        return new Approval(appointment, approval.appointmentSignature(), approval.identitySignature(),
            approval.custodySignature(), approval.issuedAt(), approval.expiresAt(), sign(signing, approval.payload()));
    }

    Revocations revocations(long seq, Set<UUID> revoked) throws Exception {
        var record = new Revocations(seq, now, revoked, "");
        return new Revocations(seq, now, revoked, sign(revocation, record.payload()));
    }

    private static KeyPair key() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static String sign(KeyPair key, byte[] bytes) throws Exception {
        var signature = Signature.getInstance("Ed25519");
        signature.initSign(key.getPrivate());
        signature.update(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM cinenest_pricing." + table, Integer.class);
    }

    void installAuditFailure(String table) {
        jdbc.execute("""
            CREATE FUNCTION cinenest_pricing.test_fixture_audit_failure() RETURNS trigger
            LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Synthetic audit failure'; END $$;
            """);
        jdbc.execute("CREATE TRIGGER test_failure BEFORE INSERT ON cinenest_pricing." + table
            + " FOR EACH ROW EXECUTE FUNCTION cinenest_pricing.test_fixture_audit_failure()");
    }

    void removeAuditFailure(String table) {
        jdbc.execute("DROP TRIGGER test_failure ON cinenest_pricing." + table);
        jdbc.execute("DROP FUNCTION cinenest_pricing.test_fixture_audit_failure()");
    }

    static DriverManagerDataSource source(String database) {
        String url = System.getenv("CINENEST_DB_URL");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/cinenest_test")
                || !Set.of("cinenest_test","cinenest_v40_upgrade").contains(database)) {
            throw new IllegalStateException("Authorized disposable PostgreSQL required");
        }
        return new DriverManagerDataSource(url.substring(0,url.lastIndexOf('/')+1) + database,
            System.getenv("CINENEST_DB_USER"), System.getenv("CINENEST_DB_PASSWORD"));
    }

    static Flyway flyway(String database, String target) {
        return Flyway.configure().dataSource(source(database)).target(target).load();
    }

    void validate(Flyway flyway) {
        flyway.validate();
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("40");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL",
            Integer.class)).isEqualTo(39);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='31'",
            Integer.class)).isZero();
    }

    static UUID id(String name) {
        return UUID.nameUUIDFromBytes(("v40-synthetic:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static MatchedGrant grant(String role, String permission) {
        return new MatchedGrant(id(role), 0, 0, id("venue_A"), id(role + "_grant"), 0, permission);
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

    void seedHistory() {
        for (String role : List.of("admin", "finance", "legal", "operations", "submitter")) {
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
        for (String role : List.of("admin", "finance", "legal", "operations", "submitter")) {
            jdbc.update("INSERT INTO cinenest_identity.account_scope VALUES (?,?)", id(role), id("venue_A"));
            jdbc.update("""
                    INSERT INTO cinenest_identity.access_grant VALUES
                    (?,?,?,'VENUE',?,'ACTIVE',clock_timestamp()-interval '1 minute',
                    clock_timestamp()+interval '15 minutes',0)
                    """, id(role + "_grant"), id(role),
                    role.equals("admin") ? "commercial.policy.edit" : "commercial.policy.review." + (role.equals("submitter") ? "finance" : role),
                    id("venue_A"));
        }
    }
}
