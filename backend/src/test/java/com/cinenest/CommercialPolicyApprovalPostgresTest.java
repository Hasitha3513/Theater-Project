package com.cinenest.pricing.internal;

import com.cinenest.identity.ScopedAuthorization.MatchedGrant;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "CINENEST_V38_QUALIFICATION", matches = "AUTHORIZED_DISPOSABLE")
class CommercialPolicyApprovalPostgresTest {
    private static final String HISTORY = """
        SELECT jsonb_build_array(
            (SELECT jsonb_agg(to_jsonb(q) ORDER BY quote_id,revision) FROM cinenest_pricing.quotation_revision q),
            (SELECT jsonb_agg(to_jsonb(a) ORDER BY quote_id) FROM cinenest_pricing.quotation_acceptance a),
            (SELECT jsonb_agg(to_jsonb(b) ORDER BY booking_id,revision) FROM cinenest_booking.booking_revision b))::text
        """;
    private static final String HISTORY_CHECKSUMS = """
        SELECT jsonb_object_agg(version,checksum)::text
        FROM flyway_schema_history WHERE success AND version IS NOT NULL AND version <> '38'
        """;
    private JdbcTemplate jdbc;

    @Test
    void cleanFlyway() {
        var source = source("cinenest_test");
        var flyway = flyway(source, "38");
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(37);
        verifyLatest(flyway, new JdbcTemplate(source));
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void populatedV37UpgradeAndTrustedHistoryIntegrity() throws Exception {
        var source = source("cinenest_v38_upgrade");
        jdbc = new JdbcTemplate(source);
        assertThat(flyway(source, "37").migrate().migrationsExecuted).isEqualTo(36);
        seedHistory();
        var history = jdbc.queryForObject(HISTORY, String.class);
        var checksums = jdbc.queryForObject(HISTORY_CHECKSUMS, String.class);
        var flyway = flyway(source, "38");
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        verifyLatest(flyway, jdbc);
        assertThat(jdbc.queryForObject(HISTORY_CHECKSUMS, String.class)).isEqualTo(checksums);
        assertThat(jdbc.queryForObject(HISTORY, String.class)).isEqualTo(history);

        var writer = new JdbcCommercialPolicyDraftRepository(JdbcClient.create(source));
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        UUID policy = id("policy");
        tx.executeWithoutResult(status -> writer.write(grant("admin", "commercial.policy.edit"), policy,
            id("package"), UUID.randomUUID(), "CREATE", 0, "a".repeat(64), "Synthetic V38 fixture",
            CommercialPolicyDraftRules.document(document()), null));
        String hash = jdbc.queryForObject("SELECT content_hash FROM cinenest_pricing.commercial_policy_revision WHERE policy_id=?",
            String.class, policy);
        var repository = new JdbcCommercialPolicyApprovalRepository(JdbcClient.create(source));
        var finance = grant("submitter", "commercial.policy.review.finance");
        var wrongActor = grant("legal", "commercial.policy.review.legal");
        Instant now = Instant.now();
        var approval = event("APPROVE", "initial", 1, hash, now);
        assertThatThrownBy(() -> tx.execute(s -> repository.append(approval, wrongActor, id("finance"), "b".repeat(64), now)))
            .isInstanceOf(RuntimeException.class);
        var invalidRevision = event("APPROVE", "bad-hash", 1, "f".repeat(64), now);
        assertThatThrownBy(() -> tx.execute(s -> repository.append(invalidRevision, finance, id("finance"), "b".repeat(64), now)))
            .isInstanceOf(RuntimeException.class);
        jdbc.execute("""
            CREATE FUNCTION cinenest_pricing.test_v38_audit_failure() RETURNS trigger
            LANGUAGE plpgsql AS $$
            BEGIN RAISE EXCEPTION 'Synthetic audit failure' USING ERRCODE = 'P0001'; END
            $$
            """);
        jdbc.execute("""
            CREATE TRIGGER test_v38_audit_failure BEFORE INSERT ON cinenest_pricing.commercial_policy_approval_audit
            FOR EACH ROW EXECUTE FUNCTION cinenest_pricing.test_v38_audit_failure()
            """);
        assertThatThrownBy(() -> tx.execute(s -> repository.append(approval, finance, id("finance"), "b".repeat(64), now)))
            .hasRootCauseInstanceOf(java.sql.SQLException.class)
            .rootCause().extracting(error -> ((java.sql.SQLException) error).getSQLState()).isEqualTo("P0001");
        jdbc.execute("DROP TRIGGER test_v38_audit_failure ON cinenest_pricing.commercial_policy_approval_audit");
        jdbc.execute("DROP FUNCTION cinenest_pricing.test_v38_audit_failure()");
        assertThat(count("event")).isZero();
        assertThat(count("audit")).isZero();
        assertThat(count("receipt")).isZero();

        verifyForgedEnvelopeCannotWrite(repository, approval, finance, tx, now);
        var receipt = tx.execute(s -> repository.append(approval, finance, id("finance"), "b".repeat(64), now));
        assertThat(receipt).containsEntry("replayed", false);
        var replay = tx.execute(s -> repository.append(approval, finance, id("finance"), "b".repeat(64), now));
        assertThat(replay).containsEntry("eventId", receipt.get("eventId")).containsEntry("replayed", true);
        assertThatThrownBy(() -> tx.execute(s -> repository.append(approval, finance, id("finance"), "c".repeat(64), now)))
            .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM cinenest_pricing.commercial_policy_approval_event"))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM cinenest_pricing.commercial_policy_approval_receipt"))
            .hasRootCauseInstanceOf(java.sql.SQLException.class)
            .rootCause().extracting(error -> ((java.sql.SQLException) error).getSQLState()).isEqualTo("23514");
        var revocation = event("REVOKE", "revoke", 2, hash, now);
        var postRevocation = event("APPROVE", "later", 2, hash, now);
        var waitingBackend = new java.util.concurrent.CompletableFuture<Integer>();
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var concurrentIntake = tx.execute(status -> {
                repository.append(revocation, finance, id("operations"), "d".repeat(64), now);
                var attempt = executor.submit(() -> tx.execute(s -> {
                    waitingBackend.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    return repository.append(postRevocation, finance, id("finance"), "e".repeat(64), now);
                }));
                int holder = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                awaitAppointmentLock(holder, revocation.appointmentId(), waitingBackend, attempt);
                return attempt;
            });
            assertThatThrownBy(() -> concurrentIntake.get(5, java.util.concurrent.TimeUnit.SECONDS))
                .isInstanceOf(java.util.concurrent.ExecutionException.class)
                .hasRootCauseInstanceOf(java.sql.SQLException.class)
                .rootCause().extracting(error -> ((java.sql.SQLException) error).getSQLState()).isEqualTo("23514");
        } finally {
            executor.shutdownNow();
        }
        assertThatThrownBy(() -> tx.execute(s -> repository.append(approval, finance, id("finance"), "b".repeat(64), now)))
            .isInstanceOfSatisfying(CommercialPolicyDraft.Failure.class, error -> assertThat(error.status).isEqualTo(403));
        assertThat(count("event")).isEqualTo(2);
        assertThat(count("audit")).isEqualTo(2);
        assertThat(count("receipt")).isEqualTo(2);
        assertThat(jdbc.queryForObject(HISTORY, String.class)).isEqualTo(history);
    }

    private void verifyForgedEnvelopeCannotWrite(JdbcCommercialPolicyApprovalRepository repository,
            CommercialPolicyApproval approval, MatchedGrant submitter, TransactionTemplate tx, Instant now) throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("Ed25519");
        var trusted = generator.generateKeyPair();
        var forged = generator.generateKeyPair();
        var publicKey = java.util.Base64.getEncoder().encodeToString(trusted.getPublic().getEncoded());
        var trust = new CommercialPolicyApprovalTrustProperties(true, id("appointing"), id("identity-verifier"),
            id("custodian"), id("revocation-authority"), approval.revocationVersion(), now,
            List.of(new CommercialPolicyApprovalTrustProperties.Appointment(approval.appointmentId(), id("appointing"),
                id("identity-verifier"), approval.reviewer(), approval.venue(), approval.room(), approval.packageVersion(),
                java.util.Set.of("FINANCE"), now.minusSeconds(60), now.plusSeconds(900), false, false)),
            List.of(new CommercialPolicyApprovalTrustProperties.Key(approval.keyId(), approval.reviewer(),
                id("custodian"), publicKey, now.minusSeconds(60), now.plusSeconds(900), false)));
        var environment = new org.springframework.mock.env.MockEnvironment();
        environment.setActiveProfiles("local-synthetic");
        var service = new CommercialPolicyApprovalService(new CommercialPolicyApprovalVerifier(trust, environment),
            null, repository);
        byte[] payload = approval.canonicalPayload();
        var signer = java.security.Signature.getInstance("Ed25519");
        signer.initSign(forged.getPrivate());
        signer.update(payload);
        var encoder = java.util.Base64.getUrlEncoder().withoutPadding();
        String envelope = encoder.encodeToString(payload) + "." + encoder.encodeToString(signer.sign());
        assertThatThrownBy(() -> tx.execute(s -> service.intake(submitter.account(), approval.venue(), approval.policy(), envelope)))
            .isInstanceOfSatisfying(CommercialPolicyDraft.Failure.class, error -> assertThat(error.status).isEqualTo(403));
        assertThat(count("event")).isZero();
    }

    private void awaitAppointmentLock(int holder, UUID appointment, java.util.concurrent.Future<Integer> waitingBackend,
                                      java.util.concurrent.Future<?> attempt) {
        try {
            var seconds = java.util.concurrent.TimeUnit.SECONDS;
            int pid = waitingBackend.get(5, seconds);
            long deadline = System.nanoTime() + seconds.toNanos(5);
            do {
                int waiting = jdbc.queryForObject("""
                    WITH target AS (SELECT hashtextextended(?::text, 3802) AS key)
                    SELECT count(*) FROM pg_locks waiting
                    JOIN pg_locks held USING (locktype, database, classid, objid, objsubid)
                    CROSS JOIN target
                    WHERE waiting.locktype='advisory' AND waiting.objsubid=1
                      AND waiting.classid::bigint=(target.key >> 32 & 4294967295)
                      AND waiting.objid::bigint=(target.key & 4294967295)
                      AND waiting.pid=? AND NOT waiting.granted AND held.pid=? AND held.granted
                      AND held.pid=ANY(pg_blocking_pids(waiting.pid))
                    """, Integer.class, appointment.toString(), pid, holder);
                if (waiting > 0) {
                    assertThat(attempt.isDone()).isFalse();
                    return;
                }
                Thread.sleep(20);
            } while (!attempt.isDone() && System.nanoTime() < deadline);
            if (attempt.isDone()) {
                attempt.get();
                throw new AssertionError("Concurrent intake completed before revocation committed");
            }
            throw new AssertionError("Concurrent intake did not wait for the appointment transaction");
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) {
            throw new AssertionError("Concurrent intake could not be observed at the appointment lock", failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static CommercialPolicyApproval event(String outcome, String nonce, long revocationVersion, String hash, Instant now) {
        return new CommercialPolicyApproval(id("finance"), id("venue_A"), id("room_A1"), id("package_version"),
            id("policy"), id("appointment"), id(nonce), 1, revocationVersion, hash, "FINANCE", outcome, "synthetic-key",
            now.minusSeconds(2), now.plusSeconds(600));
    }

    private int count(String tableSuffix) {
        return jdbc.queryForObject("SELECT count(*) FROM cinenest_pricing.commercial_policy_approval_" + tableSuffix, Integer.class);
    }

    private static DriverManagerDataSource source(String database) {
        String url = System.getenv("CINENEST_DB_URL");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/cinenest_test")) {
            throw new IllegalStateException("An authorized disposable PostgreSQL URL is required");
        }
        return new DriverManagerDataSource(url.substring(0, url.lastIndexOf('/') + 1) + database,
            System.getenv("CINENEST_DB_USER"), System.getenv("CINENEST_DB_PASSWORD"));
    }

    private static Flyway flyway(DriverManagerDataSource source, String target) {
        return Flyway.configure().dataSource(source).target(target).load();
    }

    private static void verifyLatest(Flyway flyway, JdbcTemplate jdbc) {
        flyway.validate();
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("38");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL",
            Integer.class)).isEqualTo(37);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='31'",
            Integer.class)).isZero();
    }

    private static UUID id(String name) {
        return UUID.nameUUIDFromBytes(("v38-synthetic:" + name).getBytes(StandardCharsets.UTF_8));
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

    private void seedHistory() {
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
