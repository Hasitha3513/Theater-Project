package com.cinenest.identity.internal;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Identity-owned V35 adapter. Caller owns one READ COMMITTED transaction. */
@Repository
class JdbcLocalCredentialRecoveryRepository implements LocalCredentialRecovery.AtomicCommitPort {
    record Authority(UUID grant,long revision) {}
    record Stored(LocalCredentialRecovery.Capability capability,UUID issuer,Authority authority) {}
    final JdbcClient jdbc;final IdentityStore store;
    JdbcLocalCredentialRecoveryRepository(JdbcClient jdbc,IdentityStore store){this.jdbc=jdbc;this.store=store;}
    void requireTransaction() {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                || !"read committed".equals(jdbc.sql("SHOW transaction_isolation").query(String.class).single()))throw new CredentialEpochSessions.Denied();
    }
    LocalCredentialRecovery.Snapshot locked(UUID target,UUID actor,UUID venue,Authority authority,Stored capability) {
        requireTransaction();var ids=new TreeSet<UUID>(List.of(target,actor));
        var locked=jdbc.sql("SELECT account_id FROM cinenest_identity.account WHERE account_id IN (:ids) ORDER BY account_id FOR NO KEY UPDATE")
            .param("ids",ids).query(UUID.class).list();
        if(locked.size()!=ids.size())throw denied();
        var members=jdbc.sql("SELECT account_id FROM cinenest_identity.account_scope WHERE account_id IN (:ids) AND venue_id=:v ORDER BY account_id FOR SHARE")
            .param("ids",ids).param("v",venue).query(UUID.class).list();
        if(members.size()!=ids.size())throw denied();
        var facts=store.credentialState(target,false);
        if(!facts.synthetic()||!facts.verified()||!"ELIGIBLE".equals(facts.eligibility())||!store.eligible(actor))throw denied();
        if(jdbc.sql("SELECT account_id FROM cinenest_identity.manual_customer WHERE account_id=:a AND verified_at IS NOT NULL FOR SHARE").param("a",target).query(UUID.class).list().size()!=1)throw denied();
        if(jdbc.sql("SELECT grant_id FROM cinenest_identity.access_grant WHERE grant_id=:g FOR SHARE").param("g",authority.grant()).query(UUID.class).list().size()!=1)throw denied();
        if(jdbc.sql("""
            SELECT count(*) FROM cinenest_identity.access_grant WHERE grant_id=:g AND revision=:r
             AND account_id=:a AND venue_id=:v AND scope_kind='VENUE' AND permission='identity.credential.recovery.issue'
             AND state='ACTIVE' AND valid_from<=clock_timestamp() AND valid_until>clock_timestamp()
            """).param("g",authority.grant()).param("r",authority.revision()).param("a",actor).param("v",venue).query(Integer.class).single()!=1)throw denied();
        return new LocalCredentialRecovery.Snapshot(
            new LocalCredentialRecovery.Account(target,facts.synthetic(),true,facts.verified(),facts.accountRevision(),facts.epoch(),Set.of(venue)),
            new LocalCredentialRecovery.Actor(actor,true,true,Set.of(venue)),capability==null?null:capability.capability());
    }
    Stored capability(UUID id) {
        return jdbc.sql("""
            SELECT c.*,EXISTS(SELECT 1 FROM cinenest_identity.manual_credential_version v WHERE v.capability_id=c.capability_id) consumed
            FROM cinenest_identity.manual_recovery_capability c WHERE c.capability_id=:id
            """).param("id",id).query((r,n)->new Stored(new LocalCredentialRecovery.Capability(
                r.getObject("capability_id",UUID.class),r.getObject("account_id",UUID.class),r.getObject("actor_id",UUID.class),r.getObject("venue_id",UUID.class),
                r.getLong("expected_account_revision"),r.getLong("expected_credential_revision"),r.getString("token_digest"),r.getTimestamp("issued_at").toInstant(),r.getTimestamp("expires_at").toInstant(),r.getBoolean("consumed")),
                r.getObject("issuer_id",UUID.class),new Authority(r.getObject("issuer_grant_id",UUID.class),r.getLong("issuer_grant_revision")))).optional().orElseThrow(JdbcLocalCredentialRecoveryRepository::denied);
    }
    LocalCredentialRecovery.Capability issue(UUID id,UUID command,UUID target,UUID actor,UUID venue,Authority authority,long accountRevision,long previous,String digest,int seconds,String reference) {
        requireTransaction();
        jdbc.sql("""
            INSERT INTO cinenest_identity.manual_recovery_capability(capability_id,issuance_command_id,account_id,actor_id,issuer_id,
              issuer_grant_id,issuer_grant_revision,venue_id,expected_account_revision,expected_credential_revision,token_digest,expires_at,issuer_reference,approval_reference)
            VALUES(:id,:cmd,:target,:actor,:actor,:grant,:gr,:venue,:ar,:previous,:digest,clock_timestamp()+make_interval(secs=>:seconds),'AUTHENTICATED_SYNTHETIC_ISSUER',:reference)
            """).param("id",id).param("cmd",command).param("target",target).param("actor",actor).param("grant",authority.grant()).param("gr",authority.revision())
            .param("venue",venue).param("ar",accountRevision).param("previous",previous).param("digest",digest).param("seconds",seconds).param("reference",reference).update();
        store.audit(actor,target,"AUTHENTICATION",true);return capability(id).capability();
    }
    public LocalCredentialRecovery.Receipt commit(LocalCredentialRecovery.Append a) {
        requireTransaction();
        var at=jdbc.sql("""
            INSERT INTO cinenest_identity.manual_credential_version(account_id,credential_revision,previous_revision,expected_account_revision,
              command_id,capability_id,actor_id,venue_id,password_hash,audit_id)
            VALUES(:account,:next,:previous,:ar,:command,:capability,:actor,:venue,:hash,:audit) RETURNING admitted_at
            """).param("account",a.account()).param("next",a.nextCredentialRevision()).param("previous",a.previousCredentialRevision()).param("ar",a.expectedAccountRevision())
            .param("command",a.command()).param("capability",a.capability()).param("actor",a.actor()).param("venue",a.venue()).param("hash",a.encodedPassword()).param("audit",a.audit().id()).query(java.sql.Timestamp.class).single();
        jdbc.sql("""
            INSERT INTO cinenest_identity.manual_credential_recovery_audit(audit_id,command_id,capability_id,account_id,actor_id,venue_id,
              previous_revision,credential_revision,expected_account_revision,admitted_at,action,session_invalidation_epoch,invalidate_all_previous_sessions)
            VALUES(:audit,:command,:capability,:account,:actor,:venue,:previous,:next,:ar,:at,'LOCAL_CREDENTIAL_ROTATED',:next,true)
            """).param("audit",a.audit().id()).param("command",a.command()).param("capability",a.capability()).param("account",a.account()).param("actor",a.actor()).param("venue",a.venue())
            .param("previous",a.previousCredentialRevision()).param("next",a.nextCredentialRevision()).param("ar",a.expectedAccountRevision()).param("at",at).update();
        // Force the named V35 checks before returning a receipt; outer commit is still mandatory.
        jdbc.sql("SET CONSTRAINTS cinenest_identity.manual_credential_version_audit_binding,cinenest_identity.manual_credential_append_integrity IMMEDIATE").update();
        return new LocalCredentialRecovery.Receipt(a.command(),a.capability(),a.account(),a.nextCredentialRevision(),a.audit().id(),a.sessionInvalidation(),true);
    }
    boolean attempt(UUID actor,int limit,int seconds) {
        return jdbc.sql("""
            INSERT INTO cinenest_identity.manual_throttle(action,subject_digest,bucket,attempts)
            VALUES('GLOBAL',:subject,floor(extract(epoch FROM clock_timestamp())/:seconds),1)
            ON CONFLICT(action,subject_digest,bucket) DO UPDATE SET attempts=manual_throttle.attempts+1 RETURNING attempts
            """).param("subject",LocalCustomerIdentity.digest("CREDENTIAL_RECOVERY:"+actor)).param("seconds",seconds).query(Integer.class).single()<=limit;
    }
    static LocalCredentialRecovery.Denied denied(){return new LocalCredentialRecovery.Denied(LocalCredentialRecovery.Reason.AUTHORITY);}
}
