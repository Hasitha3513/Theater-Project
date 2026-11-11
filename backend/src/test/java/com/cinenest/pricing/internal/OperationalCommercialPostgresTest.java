package com.cinenest.pricing.internal;

import static com.cinenest.pricing.internal.CommercialPolicyActivationPostgresTest.SNAPSHOTS;
import static com.cinenest.pricing.internal.CommercialPolicyActivationPostgresTest.flyway;
import static com.cinenest.pricing.internal.CommercialPolicyActivationPostgresTest.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cinenest.booking.QuotationHolds.Facts;
import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.identity.ScopedAuthorization.MatchedGrant;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;

@EnabledIfEnvironmentVariable(named = "CINENEST_V42_QUALIFICATION", matches = "AUTHORIZED_DISPOSABLE")
class OperationalCommercialPostgresTest {
    @Test
    void cleanMigrationPreservesDefaultDenialAndRejectsRevocationRollback() {
        var db = new CommercialPolicyActivationPostgresTest();
        db.init("cinenest_test");
        var latest = flyway("cinenest_test", "42");
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(41);
        latest.validate();
        assertThat(latest.info().current().getVersion().toString()).isEqualTo("42");
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='31'",
            Integer.class)).isZero();
        assertThat(db.count("operational_policy_activation")).isZero();
        assertThat(db.count("operational_quote")).isZero();
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM cinenest_identity.operational_credential",
            Integer.class)).isZero();

        var repository = new JdbcOperationalCommercialRepository(db.client);
        var until = repository.now().plusSeconds(60);
        var current = new OperationalCommercialAuthority.Evidence("qualification", 2, "a".repeat(64), until);
        db.tx.executeWithoutResult(tx -> repository.acceptRevocations(current));
        db.tx.executeWithoutResult(tx -> repository.acceptRevocations(current));
        var rollback = new OperationalCommercialAuthority.Evidence("qualification", 1, "a".repeat(64), until);
        var conflicting = new OperationalCommercialAuthority.Evidence("qualification", 2, "b".repeat(64), until);
        assertThatThrownBy(() -> db.tx.executeWithoutResult(tx -> repository.acceptRevocations(rollback)))
            .isInstanceOf(DraftFailure.class);
        assertThatThrownBy(() -> db.tx.executeWithoutResult(tx -> repository.acceptRevocations(conflicting)))
            .isInstanceOf(DraftFailure.class);
        assertThatThrownBy(() -> db.jdbc.update("DELETE FROM cinenest_pricing.operational_revocation_observation"))
            .isInstanceOf(DataAccessException.class);
        assertThat(db.count("operational_revocation_observation")).isEqualTo(1);
    }

    @Test
    void populatedV41UpgradePreservesHistoryAndFlywayChecksums() throws Exception {
        String database = "cinenest_v40_upgrade";
        var db = new CommercialPolicyActivationPostgresTest();
        db.init(database);
        assertThat(flyway(database, "41").migrate().migrationsExecuted).isEqualTo(40);
        db.seedHistory();
        var original = db.jdbc.queryForObject(SNAPSHOTS, String.class);
        assertThat(original).contains("SIMULATED");
        String checksumSql = """
            SELECT jsonb_object_agg(version, checksum)::text FROM flyway_schema_history
            WHERE success AND version IS NOT NULL AND version <> '42'
            """;
        var checksums = db.jdbc.queryForObject(checksumSql, String.class);
        var latest = flyway(database, "42");
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
        latest.validate();
        assertThat(db.jdbc.queryForObject(checksumSql, String.class)).isEqualTo(checksums);
        assertThat(db.jdbc.queryForObject(SNAPSHOTS, String.class)).isEqualTo(original);
        assertThat(db.count("operational_quote")).isZero();
        verifyOperationalIdentityAndHoldProvenance(db);
        verifyOperationalLedgers(db);
        assertThat(db.jdbc.queryForObject(SNAPSHOTS,String.class)).isEqualTo(original);
    }
    private static void verifyOperationalIdentityAndHoldProvenance(CommercialPolicyActivationPostgresTest db)
        throws ReflectiveOperationException {
        UUID actor = id("v42-customer");
        UUID venue = id("venue_A");
        UUID grant = id("v42-hold-grant");
        String issuer = "https://fixture.invalid";
        String subject = "v42-fixture";
        db.jdbc.update("INSERT INTO cinenest_identity.account VALUES (?,FALSE,'ELIGIBLE',0)", actor);
        db.jdbc.update("INSERT INTO cinenest_identity.account_scope VALUES (?,?)", actor, venue);
        db.jdbc.update("""
            INSERT INTO cinenest_identity.access_grant VALUES
                (?,?,'commercial.hold.create','VENUE',?,'ACTIVE',clock_timestamp()-interval '1 minute',
                 clock_timestamp()+interval '10 minutes',0)
            """, grant, actor, venue);
        db.jdbc.update("""
            INSERT INTO cinenest_identity.operational_credential
                (account_id,issuer,subject,account_class,epoch,provenance_hash,verified_at,expires_at)
            VALUES (?,?,?,'CUSTOMER',1,?,clock_timestamp()-interval '1 minute',clock_timestamp()+interval '10 minutes')
            """, actor, issuer, subject, "c".repeat(64));

        // Reach package-private Identity internals from the approved Pricing integration test path.
        var storeType = Class.forName("com.cinenest.identity.internal.IdentityStore");
        var storeConstructor = storeType.getDeclaredConstructor(JdbcClient.class);
        storeConstructor.setAccessible(true);
        var store = storeConstructor.newInstance(db.client);
        java.util.Optional<?> disabled = ReflectionTestUtils.invokeMethod(store, "operationalAssociation", issuer, subject);
        assertThat(disabled).isEmpty();
        ReflectionTestUtils.setField(store, "operationalEnabled", true);
        java.util.Optional<?> association = ReflectionTestUtils.invokeMethod(store, "operationalAssociation", issuer, subject);
        var sessions = Class.forName("com.cinenest.identity.internal.CredentialEpochSessions");
        Authentication auth = ReflectionTestUtils.invokeMethod(sessions, "operationalAuthentication",
            association.orElseThrow());
        var scopeType = Class.forName("com.cinenest.identity.internal.DatabaseScopedAuthorization");
        var scopeConstructor = scopeType.getDeclaredConstructor(JdbcClient.class, storeType);
        scopeConstructor.setAccessible(true);
        var identity = (ScopedAuthorization) scopeConstructor.newInstance(db.client, store);
        var security = SecurityContextHolder.createEmptyContext();
        security.setAuthentication(auth);
        SecurityContextHolder.setContext(security);
        try {
            assertThat(identity.operationalPrincipal(actor, false)).isTrue();
            assertThat(identity.matchedOperationalGrant(actor, id("venue_B"), "commercial.hold.create", false)).isEmpty();
            assertThat(identity.matchedOperationalGrant(actor, venue, "commercial.policy.activate", false)).isEmpty();
            var proof = db.tx.execute(tx ->
                identity.matchedOperationalGrant(actor, venue, "commercial.hold.create", true).orElseThrow());
            assertThat(proof.grantId()).isEqualTo(grant);
            assertThat(proof.credentialEpoch()).isEqualTo(1);

            UUID block = id("v42-operational-block");
            UUID hold = id("v42-operational-hold");
            db.tx.executeWithoutResult(tx -> {
                db.jdbc.update("""
                    INSERT INTO cinenest_booking.inventory_block
                        (block_id,venue_id,room_id,session_start,session_end,buffer_minutes,blocked_until,blocking,kind)
                    VALUES (?,?,?,statement_timestamp()+interval '2 days',statement_timestamp()+interval '2 days 1 hour',
                            0,statement_timestamp()+interval '2 days 1 hour',false,'HOLD')
                    """, block, venue, id("room_A1"));
                db.jdbc.update("""
                    INSERT INTO cinenest_booking.reservation_hold
                        (hold_id,owner_id,block_id,policy_id,guests,configuration,timezone,minimum_policy_ids,
                         created_at,expires_at,state)
                    VALUES (?,?,?,?,1,'fixture','Asia/Colombo','',clock_timestamp(),
                            clock_timestamp()+interval '5 minutes','ACTIVE')
                    """, hold, actor, block, id("hold_policy"));
                db.jdbc.update("""
                    INSERT INTO cinenest_booking.operational_hold_provenance
                        (hold_id,package_id,version_id,grant_id,grant_revision,credential_epoch)
                    VALUES (?,?,?,?,?,?)
                    """, hold, id("package"), id("package_version"), grant, proof.grantRevision(), proof.credentialEpoch());
            });
            assertThat(db.jdbc.queryForObject("""
                SELECT credential_epoch FROM cinenest_booking.operational_hold_provenance WHERE hold_id=?
                """, Long.class, hold)).isEqualTo(1L);
            assertThatThrownBy(() -> db.jdbc.update(
                "DELETE FROM cinenest_booking.operational_hold_provenance WHERE hold_id=?", hold))
                .isInstanceOf(DataAccessException.class);
            db.jdbc.update("UPDATE cinenest_identity.operational_credential SET epoch=2 WHERE account_id=?", actor);
            assertThat(identity.operationalPrincipal(actor, false)).isFalse();
            db.jdbc.update("""
                UPDATE cinenest_identity.operational_credential SET epoch=3,revoked_at=clock_timestamp() WHERE account_id=?
                """, actor);
            java.util.Optional<?> revoked = ReflectionTestUtils.invokeMethod(store, "operationalAssociation", issuer, subject);
            assertThat(revoked).isEmpty();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static void verifyOperationalLedgers(CommercialPolicyActivationPostgresTest db) throws Exception {
        db.prepareActivation();
        var repository = new JdbcOperationalCommercialRepository(db.client);
        var now = repository.now().truncatedTo(ChronoUnit.MICROS);
        var scope = new OperationalCommercialAuthority.Scope(id("venue_A"), id("room_A1"),
            id("package_version"), id("policy"), 1, db.jdbc.queryForObject(
                "SELECT content_hash FROM cinenest_pricing.commercial_policy_revision WHERE policy_id=?",
                String.class, id("policy")));
        var evidence = new OperationalCommercialAuthority.Evidence("qualification", 1, "a".repeat(64),
            now.plusSeconds(300));
        db.tx.executeWithoutResult(tx -> repository.acceptRevocations(evidence));
        var activationGrant = new MatchedGrant(id("admin"), 0, 0, scope.venue(), id("admin_grant"), 0,
            "commercial.policy.activate");
        UUID activationKey = UUID.randomUUID();
        String commandHash = "b".repeat(64);
        var activationId = db.tx.execute(tx -> repository.insertActivation(activationGrant, scope, now.minusSeconds(30),
            now.plusSeconds(180), evidence, activationKey, commandHash, true));
        assertThat(repository.activationReplay(id("admin"), scope.venue(), activationKey).orElseThrow().id())
            .isEqualTo(activationId);
        assertThatThrownBy(() -> db.tx.execute(tx -> repository.insertActivation(activationGrant, scope,
            now.minusSeconds(15), now.plusSeconds(120), evidence, UUID.randomUUID(), commandHash, true)))
            .isInstanceOf(DataAccessException.class);
        assertThat(db.count("operational_policy_activation")).isEqualTo(1);
        assertThat(db.count("operational_policy_activation_audit")).isEqualTo(1);

        var issueGrant = new MatchedGrant(id("admin"), 0, 0, scope.venue(), id("admin_grant"), 0,
            "commercial.quote.issue");
        var hold = new Facts(UUID.randomUUID(), issueGrant.account(), scope.venue(), scope.room(),
            now.plusSeconds(60), now.plusSeconds(90), now.plusSeconds(120), "ACTIVE", 0, 1,
            "Asia/Colombo", 0, now.plusSeconds(90), "fixture");
        var activation = repository.active(hold, id("package"), scope.version(), now, true);
        assertThatThrownBy(() -> repository.active(hold, id("package"), scope.version(), now, false))
            .isInstanceOf(DraftFailure.class);
        var snapshot = Map.<String, Object>of("currency", "LKR", "classification", "SYNTHETIC_QUALIFICATION_NOT_OFFER");
        UUID quoteKey = UUID.randomUUID();

        db.installAuditFailure("operational_quote_audit");
        try {
            assertThatThrownBy(() -> db.tx.execute(tx -> repository.insertQuote(issueGrant, hold, id("package"),
                activation, evidence, quoteKey, commandHash, snapshot, 250000, now.plusSeconds(120))))
                .isInstanceOf(DataAccessException.class);
            assertThat(db.count("operational_quote")).isZero();
        } finally {
            db.removeAuditFailure("operational_quote_audit");
        }

        java.util.concurrent.Callable<Boolean> issue = () -> {
            try {
                db.tx.execute(tx -> repository.insertQuote(issueGrant, hold, id("package"),
                    activation, evidence, quoteKey, commandHash, snapshot, 250000, now.plusSeconds(120)));
                return true;
            } catch (org.springframework.dao.DuplicateKeyException expected) {
                return false;
            }
        };
        try (var threads = Executors.newFixedThreadPool(2)) {
            var first = threads.submit(issue);
            var second = threads.submit(issue);
            assertThat(first.get(10, TimeUnit.SECONDS)).isNotEqualTo(second.get(10, TimeUnit.SECONDS));
        }
        assertThat(repository.quoteReplay(issueGrant.account(), quoteKey).orElseThrow().hash()).isEqualTo(commandHash);
        assertThat(db.count("operational_quote")).isEqualTo(1);
        assertThat(db.count("operational_quote_audit")).isEqualTo(1);
        assertThatThrownBy(() -> db.jdbc.update("UPDATE cinenest_pricing.operational_quote SET total_minor=1"))
            .isInstanceOf(DataAccessException.class);
    }
}
