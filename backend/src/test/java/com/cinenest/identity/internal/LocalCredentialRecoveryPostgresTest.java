package com.cinenest.identity.internal;

import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

/** Source-only opt-in suite. It NEVER provisions a server/database or uses a preserved UAT DB.
 * A future authorized runner must supply TWO fresh run-owned DBs, four synthetic accounts,
 * one dedicated test issuer grant and the exact environment gate below. No Spring startup.
 * SQL errors are reduced to SQLSTATE so credential material cannot enter test diagnostics.
 */
@EnabledIfEnvironmentVariable(named="CINENEST_CREDENTIAL_RECOVERY_PG_TESTS",matches="AUTHORIZED_DISPOSABLE")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LocalCredentialRecoveryPostgresTest {
    static final UUID TARGET=UUID.randomUUID(),OTHER=UUID.randomUUID(),ACTOR=UUID.randomUUID(),ISSUER=UUID.randomUUID();
    static final UUID VENUE=UUID.randomUUID(),WRONG_VENUE=UUID.randomUUID(),GRANT=UUID.randomUUID();
    static final String TABLES="cinenest_identity.manual_recovery_capability,cinenest_identity.manual_credential_version,cinenest_identity.manual_credential_recovery_audit";
    static final String HASH="{pbkdf2-v1}"+"a".repeat(96); // Format-only synthetic fixture, never a live credential.
    static String v28Before;
    static Capability consumed;
    record Capability(UUID id,UUID account,long accountRevision,long previousRevision) {}
    record Append(UUID command,UUID audit,Capability capability,Timestamp admittedAt) {}
    String url(boolean clean) {
        String run=System.getenv("CINENEST_CREDENTIAL_RECOVERY_RUN_ID");
        String value=System.getenv(clean?"CINENEST_CREDENTIAL_RECOVERY_PG_CLEAN_URL":"CINENEST_CREDENTIAL_RECOVERY_PG_URL");
        if(run==null||!run.matches("[a-f0-9]{32}")||value==null||!value.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/credential_recovery_"+run+(clean?"_clean":"_upgrade")))
            throw new IllegalArgumentException("Exact run-owned disposable database required");
        return value;
    }
    Connection connect(boolean clean)throws SQLException {
        try {var properties=new Properties();
            properties.setProperty("user",Objects.requireNonNull(System.getenv("CINENEST_DB_USER"),"Explicit test user required"));
            properties.setProperty("password",Objects.requireNonNull(System.getenv("CINENEST_DB_PASSWORD"),"Explicit test password required"));
            properties.setProperty("connectTimeout","5");properties.setProperty("socketTimeout","15");
            properties.setProperty("options","-c statement_timeout=10000 -c lock_timeout=5000");
            var c=DriverManager.getConnection(url(clean),properties);
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);c.setAutoCommit(false);return c;
        }catch(SQLException e){throw safe(e);}
    }
    String stateOf(SQLException e) {return e==null?"NO_FAILURE":e.getSQLState();}
    SQLException safe(SQLException e) {return new SQLException("V35 sanitized database condition",e.getSQLState());}
    int execute(Connection c,String text,Object...args)throws SQLException {
        try(var q=c.prepareStatement(text)){for(int i=0;i<args.length;i++)q.setObject(i+1,args[i]);return q.executeUpdate();}
        catch(SQLException e){throw safe(e);}
    }
    long number(Connection c,String text,Object...args)throws SQLException {
        try(var q=c.prepareStatement(text)){for(int i=0;i<args.length;i++)q.setObject(i+1,args[i]);try(var r=q.executeQuery()){r.next();return r.getLong(1);}}
        catch(SQLException e){throw safe(e);}
    }
    void denied(Connection c,String state,String text,Object...args)throws SQLException {
        var save=c.setSavepoint();try {SQLException e=catchThrowableOfType(()->execute(c,text,args),SQLException.class);assertThat(e==null?null:e.getSQLState()).isEqualTo(state);}
        finally {c.rollback(save);c.releaseSavepoint(save);}
    }
    Flyway flyway(boolean clean,String target) {
        return Flyway.configure().dataSource(url(clean),System.getenv("CINENEST_DB_USER"),System.getenv("CINENEST_DB_PASSWORD"))
            .locations("classpath:db/migration").target(target).load();
    }
    Map<String,Integer> history(Connection c)throws SQLException {
        var result=new TreeMap<String,Integer>();try(var q=c.createStatement();var r=q.executeQuery("SELECT version,checksum FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank")){
            while(r.next())result.put(r.getString(1),r.getInt(2));return result;
        }catch(SQLException e){throw safe(e);}
    }
    String v28Fingerprint(Connection c)throws SQLException {
        try(var q=c.createStatement();var r=q.executeQuery("SELECT md5(coalesce(string_agg(row_to_json(m)::text,'' ORDER BY account_id),'')) FROM cinenest_identity.manual_customer m")){r.next();return r.getString(1);}
        catch(SQLException e){throw safe(e);}
    }
    void fixture(Connection c)throws SQLException {
        for(UUID id:List.of(TARGET,OTHER,ACTOR,ISSUER)) {
            execute(c,"INSERT INTO cinenest_identity.account VALUES (?,TRUE,'ELIGIBLE',1)",id);
            execute(c,"INSERT INTO cinenest_identity.account_scope VALUES (?,?)",id,VENUE);
            execute(c,"INSERT INTO cinenest_identity.manual_customer(account_id,email,password_hash,verification_digest,verification_expires_at,verified_at) VALUES (?,?,?,?,clock_timestamp()+interval '1 day',clock_timestamp())",
                id,"fixture-"+id+"@example.test",HASH,LocalCustomerIdentity.digest(id.toString()));
        }
        execute(c,"INSERT INTO cinenest_identity.access_grant(grant_id,account_id,permission,scope_kind,venue_id,state,valid_from,valid_until,revision) VALUES (?,?,'identity.credential.recovery.issue','VENUE',?,'ACTIVE',clock_timestamp()-interval '1 minute',clock_timestamp()+interval '1 hour',0)",GRANT,ISSUER,VENUE);
    }
    Capability issue(Connection c,UUID target,UUID venue,long accountRevision,long previous,UUID grant,UUID command,Duration life)throws SQLException {
        UUID id=UUID.randomUUID();execute(c,"INSERT INTO cinenest_identity.manual_recovery_capability(capability_id,issuance_command_id,account_id,actor_id,issuer_id,issuer_grant_id,issuer_grant_revision,venue_id,expected_account_revision,expected_credential_revision,token_digest,expires_at,issuer_reference,approval_reference) VALUES (?,?,?,?,?,?,0,?,?,?,?,clock_timestamp()+make_interval(secs=>?),'SYNTHETIC TEST ISSUER','SYNTHETIC TEST APPROVAL')",
            id,command,target,ACTOR,ISSUER,grant,venue,accountRevision,previous,LocalCustomerIdentity.digest(id.toString()),life.toSeconds());
        return new Capability(id,target,accountRevision,previous);
    }
    Capability issue(UUID target,Duration life)throws SQLException {
        try(var c=connect(false)){long head=number(c,"SELECT coalesce(max(credential_revision),0) FROM cinenest_identity.manual_credential_version WHERE account_id=?",target);
            var result=issue(c,target,VENUE,1,head,GRANT,UUID.randomUUID(),life);c.commit();return result;}
    }
    Append append(Connection c,Capability cap,UUID target,UUID venue)throws SQLException {
        UUID command=UUID.randomUUID(),audit=UUID.randomUUID();
        try(var q=c.prepareStatement("INSERT INTO cinenest_identity.manual_credential_version(account_id,credential_revision,previous_revision,expected_account_revision,command_id,capability_id,actor_id,venue_id,password_hash,audit_id,admitted_at) VALUES (?,?,?,?,?,?,?,?,?,?,?) RETURNING admitted_at")){
            Object[] values={target,cap.previousRevision()+1,cap.previousRevision(),cap.accountRevision(),command,cap.id(),ACTOR,venue,HASH,audit,Timestamp.valueOf("2000-01-01 00:00:00")};for(int i=0;i<values.length;i++)q.setObject(i+1,values[i]);
            try(var r=q.executeQuery()){r.next();return new Append(command,audit,cap,r.getTimestamp(1));}
        }catch(SQLException e){throw safe(e);}
    }
    void audit(Connection c,Append a,boolean allSessions)throws SQLException {
        var cap=a.capability();execute(c,"INSERT INTO cinenest_identity.manual_credential_recovery_audit(audit_id,command_id,capability_id,account_id,actor_id,venue_id,previous_revision,credential_revision,expected_account_revision,admitted_at,action,session_invalidation_epoch,invalidate_all_previous_sessions) VALUES (?,?,?,?,?,?,?,?,?,?,'LOCAL_CREDENTIAL_ROTATED',?,?)",
            a.audit(),a.command(),cap.id(),cap.account(),ACTOR,VENUE,cap.previousRevision(),cap.previousRevision()+1,cap.accountRevision(),a.admittedAt(),cap.previousRevision()+1,allSessions);
    }
    String consume(Capability cap) {
        try(var c=connect(false)){try {var a=append(c,cap,cap.account(),VENUE);audit(c,a,true);c.commit();return "COMMITTED";}
            catch(SQLException e){c.rollback();return e.getSQLState();}}
        catch(SQLException e){return e.getSQLState();}
    }
    long versions()throws SQLException {try(var c=connect(false)){return number(c,"SELECT count(*) FROM cinenest_identity.manual_credential_version");}}

    @Test @Order(1) void cleanMigrationHasExactTablesFunctionsTriggersAndHead()throws Exception {
        try {var f=flyway(true,"35");assertThat(f.info().applied()).isEmpty();assertThat(f.migrate().migrationsExecuted).isEqualTo(34);f.validate();assertThat(f.info().current().getVersion().toString()).isEqualTo("35");assertThat(f.migrate().migrationsExecuted).isZero();}
        catch(RuntimeException e){throw new AssertionError("V35 clean Flyway failed; inspect sanitized runner evidence");}
        try(var c=connect(true)){
            assertThat(number(c,"SELECT count(*) FROM information_schema.tables WHERE table_schema='cinenest_identity' AND table_name IN ('manual_recovery_capability','manual_credential_version','manual_credential_recovery_audit')")).isEqualTo(3);
            assertThat(number(c,"SELECT count(*) FROM pg_proc WHERE pronamespace='cinenest_identity'::regnamespace AND proname IN ('reject_local_recovery_history_mutation','check_local_recovery_capability','check_local_credential_append','local_recovery_empty_truncate_guard')")).isEqualTo(4);
            assertThat(number(c,"SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal AND tgrelid IN ('cinenest_identity.manual_recovery_capability'::regclass,'cinenest_identity.manual_credential_version'::regclass,'cinenest_identity.manual_credential_recovery_audit'::regclass)")).isEqualTo(9);
        }
    }
    @Test @Order(2) void populatedV34UpgradePreservesAllHistoricalChecksumsAndV28Bindings()throws Exception {
        Map<String,Integer> before;try {var f=flyway(false,"34");assertThat(f.info().applied()).isEmpty();assertThat(f.migrate().migrationsExecuted).isEqualTo(33);}
        catch(RuntimeException e){throw new AssertionError("V34 preparation failed; inspect sanitized runner evidence");}
        try(var c=connect(false)){fixture(c);c.commit();before=history(c);v28Before=v28Fingerprint(c);}
        try {var f=flyway(false,"35");assertThat(f.migrate().migrationsExecuted).isEqualTo(1);f.validate();assertThat(f.migrate().migrationsExecuted).isZero();}
        catch(RuntimeException e){throw new AssertionError("V35 upgrade failed; inspect sanitized runner evidence");}
        try(var c=connect(false)){assertThat(history(c)).containsAllEntriesOf(before).hasSize(34);assertThat(v28Fingerprint(c)).isEqualTo(v28Before);
            assertThat(number(c,"SELECT count(*) FROM pg_trigger WHERE tgrelid='cinenest_identity.manual_customer'::regclass AND tgname='manual_customer_binding' AND tgenabled='O'")).isEqualTo(1);
            denied(c,"P0001","UPDATE cinenest_identity.manual_customer SET password_hash=password_hash WHERE account_id=?",TARGET);}
    }
    @Test @Order(3) void emptyFixtureResetNeedsAllThreeLocksAndReadCommitted()throws Exception {
        try(var c=connect(true)){denied(c,"23514","TRUNCATE cinenest_identity.manual_credential_recovery_audit CASCADE");
            execute(c,"LOCK TABLE "+TABLES+" IN ACCESS EXCLUSIVE MODE");execute(c,"TRUNCATE "+TABLES);c.rollback();}
        try(var c=connect(true)){c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);execute(c,"LOCK TABLE "+TABLES+" IN ACCESS EXCLUSIVE MODE");denied(c,"23514","TRUNCATE "+TABLES);c.rollback();}
    }
    @Test @Order(4) void eligibleSyntheticIssuanceUsesServerTimeAndImmutableIssuerEvidence()throws Exception {
        var cap=issue(TARGET,Duration.ofMinutes(5));try(var c=connect(false)){
            assertThat(number(c,"SELECT count(*) FROM cinenest_identity.manual_recovery_capability WHERE capability_id=? AND issuer_id=? AND issuer_grant_id=? AND mode='LOCAL_SYNTHETIC_ONLY' AND issued_at<=clock_timestamp() AND expires_at>issued_at",cap.id(),ISSUER,GRANT)).isEqualTo(1);}
    }
    @Test @Order(5) void ineligibleTargetDenied()throws Exception {try(var c=connect(false)){execute(c,"UPDATE cinenest_identity.account SET eligibility='SUSPENDED' WHERE account_id=?",TARGET);assertThat(stateOf(catchThrowableOfType(()->issue(c,TARGET,VENUE,1,0,GRANT,UUID.randomUUID(),Duration.ofMinutes(5)),SQLException.class))).isEqualTo("23514");c.rollback();}}
    @Test @Order(6) void ineligibleActorDenied()throws Exception {try(var c=connect(false)){execute(c,"UPDATE cinenest_identity.account SET eligibility='SUSPENDED' WHERE account_id=?",ACTOR);assertThat(stateOf(catchThrowableOfType(()->issue(c,TARGET,VENUE,1,0,GRANT,UUID.randomUUID(),Duration.ofMinutes(5)),SQLException.class))).isEqualTo("23514");c.rollback();}}
    @Test @Order(7) void wrongVenueIssuanceDenied()throws Exception {try(var c=connect(false)){assertThat(stateOf(catchThrowableOfType(()->issue(c,TARGET,WRONG_VENUE,1,0,GRANT,UUID.randomUUID(),Duration.ofMinutes(5)),SQLException.class))).isEqualTo("23514");c.rollback();}}
    @Test @Order(8) void staleAccountAndCredentialRevisionsDenied()throws Exception {for(boolean accountRevision:List.of(true,false))try(var c=connect(false)){assertThat(stateOf(catchThrowableOfType(()->issue(c,TARGET,VENUE,accountRevision?2:1,accountRevision?0:1,GRANT,UUID.randomUUID(),Duration.ofMinutes(5)),SQLException.class))).isEqualTo("23514");c.rollback();}}
    @Test @Order(9) void approvalTextAndInventoryGrantDoNotEstablishIssuerAuthority()throws Exception {try(var c=connect(false)){execute(c,"UPDATE cinenest_identity.access_grant SET permission='inventory.block.create' WHERE grant_id=?",GRANT);assertThat(stateOf(catchThrowableOfType(()->issue(c,TARGET,VENUE,1,0,GRANT,UUID.randomUUID(),Duration.ofMinutes(5)),SQLException.class))).isEqualTo("23514");c.rollback();}}
    @Test @Order(10) void issuanceCommandCannotBeReused()throws Exception {UUID command=UUID.randomUUID();try(var c=connect(false)){issue(c,TARGET,VENUE,1,0,GRANT,command,Duration.ofMinutes(5));c.commit();}try(var c=connect(false)){assertThat(stateOf(catchThrowableOfType(()->issue(c,TARGET,VENUE,1,0,GRANT,command,Duration.ofMinutes(5)),SQLException.class))).isEqualTo("23505");c.rollback();}}
    @Test @Order(11) void validAppendHasMandatoryAttributableAuditAndPreservesV28()throws Exception {consumed=issue(TARGET,Duration.ofMinutes(5));assertThat(consume(consumed)).isEqualTo("COMMITTED");try(var c=connect(false)){assertThat(v28Fingerprint(c)).isEqualTo(v28Before);assertThat(number(c,"SELECT count(*) FROM cinenest_identity.manual_credential_version v JOIN cinenest_identity.manual_credential_recovery_audit a USING(audit_id,command_id,capability_id,account_id,actor_id,venue_id,previous_revision,credential_revision,expected_account_revision,admitted_at) WHERE v.capability_id=? AND a.session_invalidation_epoch=v.credential_revision",consumed.id())).isEqualTo(1);}}
    @Test @Order(12) void expiredAdmissionDenied()throws Exception {var cap=issue(TARGET,Duration.ofSeconds(1));Thread.sleep(1100);long before=versions();assertThat(consume(cap)).isEqualTo("23514");assertThat(versions()).isEqualTo(before);}
    @Test @Order(13) void admissionBeforeExpiryCanCommitAfterExpiryWithoutRevalidationOfWallClock()throws Exception {var cap=issue(TARGET,Duration.ofSeconds(2));try(var c=connect(false)){var a=append(c,cap,TARGET,VENUE);Thread.sleep(2100);audit(c,a,true);execute(c,"SET CONSTRAINTS ALL IMMEDIATE");c.commit();}}
    @Test @Order(14) void consumedCapabilityReplayDenied()throws Exception {assertThat(consumed).isNotNull();long before=versions();assertThat(consume(consumed)).isEqualTo("23514");assertThat(versions()).isEqualTo(before);}
    @Test @Order(15) void missingAuditRejectsDeferredCompletionAndRollsBackAppend()throws Exception {var cap=issue(TARGET,Duration.ofMinutes(5));long before=versions();try(var c=connect(false)){append(c,cap,TARGET,VENUE);denied(c,"23514","SET CONSTRAINTS cinenest_identity.manual_credential_append_integrity IMMEDIATE");c.rollback();}assertThat(versions()).isEqualTo(before);}
    @Test @Order(16) void missingSessionEpochAcknowledgementDenied()throws Exception {var cap=issue(TARGET,Duration.ofMinutes(5));long before=versions();try(var c=connect(false)){var a=append(c,cap,TARGET,VENUE);assertThat(stateOf(catchThrowableOfType(()->audit(c,a,false),SQLException.class))).isEqualTo("23514");c.rollback();}assertThat(versions()).isEqualTo(before);}
    @Test @Order(17) void committedHistoryRejectsUpdateDeleteAndNonemptyTruncate()throws Exception {long before=versions();try(var c=connect(false)){for(String table:List.of("manual_recovery_capability","manual_credential_version","manual_credential_recovery_audit")){denied(c,"23514","UPDATE cinenest_identity."+table+" SET account_id=account_id");denied(c,"23514","DELETE FROM cinenest_identity."+table);}execute(c,"LOCK TABLE "+TABLES+" IN ACCESS EXCLUSIVE MODE");denied(c,"23514","TRUNCATE "+TABLES);c.rollback();}assertThat(versions()).isEqualTo(before);}
    @Test @Order(18) void explicitRollbackDoesNotConsumeCapabilityOrPersistAudit()throws Exception {var cap=issue(TARGET,Duration.ofMinutes(5));long before=versions();try(var c=connect(false)){audit(c,append(c,cap,TARGET,VENUE),true);c.rollback();}assertThat(versions()).isEqualTo(before);assertThat(consume(cap)).isEqualTo("COMMITTED");}
    @Test @Order(19) void crossAccountAndCrossVenueConsumptionDenied()throws Exception {var cap=issue(TARGET,Duration.ofMinutes(5));for(boolean wrongAccount:List.of(true,false))try(var c=connect(false)){assertThat(stateOf(catchThrowableOfType(()->append(c,cap,wrongAccount?OTHER:TARGET,wrongAccount?VENUE:WRONG_VENUE),SQLException.class))).isEqualTo("23514");c.rollback();}}
    @Test @Order(20) void concurrentSameCapabilityHasOneWinner()throws Exception {var cap=issue(TARGET,Duration.ofMinutes(5));race(List.of(cap,cap),List.of("COMMITTED","23514"));}
    @Test @Order(21) void concurrentSameRevisionCapabilitiesHaveOneWinner()throws Exception {var first=issue(TARGET,Duration.ofMinutes(5));var second=issue(TARGET,Duration.ofMinutes(5));race(List.of(first,second),List.of("COMMITTED","23514"));}
    @Test @Order(22) void sharedIssuerAcrossDifferentTargetsDoesNotRequireConflictingKeyShareUpgrades()throws Exception {race(List.of(issue(TARGET,Duration.ofMinutes(5)),issue(OTHER,Duration.ofMinutes(5))),List.of("COMMITTED","COMMITTED"));}
    @Test @Order(23) void revokedMembershipAfterIssuanceDeniesAdmission()throws Exception {var cap=issue(TARGET,Duration.ofMinutes(5));try(var c=connect(false)){execute(c,"DELETE FROM cinenest_identity.account_scope WHERE account_id=? AND venue_id=?",TARGET,VENUE);assertThat(stateOf(catchThrowableOfType(()->append(c,cap,TARGET,VENUE),SQLException.class))).isEqualTo("23514");c.rollback();}}
    @Test @Order(24) void revokedIssuerAuthorityAfterIssuanceDeniesConsumption()throws Exception {
        var cap=issue(TARGET,Duration.ofMinutes(5));try(var c=connect(false)){
            execute(c,"UPDATE cinenest_identity.access_grant SET state='REVOKED',revision=revision+1 WHERE grant_id=?",GRANT);
            assertThat(stateOf(catchThrowableOfType(()->append(c,cap,TARGET,VENUE),SQLException.class))).isEqualTo("23514");c.rollback();
        }
    }
    @Test @Order(25) void admittedTransactionHoldsMembershipAgainstConcurrentRevocation()throws Exception {
        var cap=issue(TARGET,Duration.ofMinutes(5));try(var admission=connect(false);var revoke=connect(false)){
            append(admission,cap,TARGET,VENUE);execute(revoke,"SET LOCAL lock_timeout='200ms'");
            assertThat(stateOf(catchThrowableOfType(()->execute(revoke,"DELETE FROM cinenest_identity.account_scope WHERE account_id=? AND venue_id=?",TARGET,VENUE),SQLException.class))).isEqualTo("55P03");
            revoke.rollback();admission.rollback();
        }
    }
    @Test @Order(26) void admissionTimestampIsGeneratedByServerInsteadOfAcceptedFromCaller()throws Exception {
        var cap=issue(TARGET,Duration.ofMinutes(5));try(var c=connect(false)){
            var a=append(c,cap,TARGET,VENUE);assertThat(a.admittedAt()).isNotEqualTo(Timestamp.valueOf("2000-01-01 00:00:00"));
            assertThat(number(c,"SELECT count(*) FROM cinenest_identity.manual_recovery_capability WHERE capability_id=? AND issued_at<=? AND expires_at>?",cap.id(),a.admittedAt(),a.admittedAt())).isEqualTo(1);
            audit(c,a,true);c.rollback();
        }
    }
    void race(List<Capability> caps,List<String> expected)throws Exception {
        var gate=new CountDownLatch(1);try(var workers=Executors.newFixedThreadPool(2)){
            var jobs=new ArrayList<Future<String>>();for(var cap:caps)jobs.add(workers.submit(()->{gate.await(5,TimeUnit.SECONDS);return consume(cap);}));gate.countDown();
            assertThat(List.of(jobs.get(0).get(15,TimeUnit.SECONDS),jobs.get(1).get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrderElementsOf(expected);
        }
    }
}
