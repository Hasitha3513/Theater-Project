package com.cinenest.identity.internal;

import com.cinenest.identity.AccountView;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class IdentityStore {
    private final JdbcClient jdbc;

    IdentityStore(JdbcClient jdbc) { this.jdbc = jdbc; }

    Optional<UUID> associatedEligibleAccount(String issuer, String subject) {
        return jdbc.sql("""
            SELECT a.account_id FROM cinenest_identity.account a
            JOIN cinenest_identity.external_identity e USING (account_id)
            WHERE e.issuer = :issuer AND e.subject = :subject
              AND a.synthetic = TRUE AND a.eligibility = 'ELIGIBLE'
            """).param("issuer", issuer).param("subject", subject).query(UUID.class).optional();
    }

    boolean eligible(UUID actor) {
        return jdbc.sql("SELECT count(*) FROM cinenest_identity.account WHERE account_id = :id AND synthetic = TRUE AND eligibility = 'ELIGIBLE'")
            .param("id", actor).query(Integer.class).single() == 1;
    }

    @Transactional
    Optional<AccountView> read(UUID actor, UUID target) {
        // One authorization/read statement: no permission/scope Cartesian product or cached decision.
        var result = jdbc.sql("""
            SELECT target.account_id, target.eligibility FROM cinenest_identity.account target
            WHERE target.account_id = :target AND target.synthetic = TRUE
              AND EXISTS (SELECT 1 FROM cinenest_identity.account caller
                          WHERE caller.account_id = :actor AND caller.synthetic = TRUE AND caller.eligibility = 'ELIGIBLE')
              AND (target.account_id = :actor OR EXISTS (
                    SELECT 1 FROM cinenest_identity.access_grant g
                    JOIN cinenest_identity.account_scope target_scope ON target_scope.venue_id = g.venue_id
                    WHERE g.account_id = :actor AND g.permission = 'account.read'
                      AND g.scope_kind = 'VENUE' AND g.state = 'ACTIVE'
                      AND g.valid_from <= CURRENT_TIMESTAMP AND g.valid_until > CURRENT_TIMESTAMP
                      AND target_scope.account_id = target.account_id))
            """).param("actor", actor).param("target", target)
            .query((rs, row) -> new AccountView(rs.getObject("account_id", UUID.class), rs.getString("eligibility"))).optional();
        audit(actor, target, "ACCOUNT_READ", result.isPresent());
        return result;
    }

    record CredentialState(long accountRevision,long epoch,String hash,boolean verified,boolean synthetic,String eligibility) {
        @Override public String toString(){return "CredentialState[REDACTED]";}
    }
    CredentialState credentialState(UUID account,boolean lock) {
        var accounts=jdbc.sql("SELECT revision,synthetic,eligibility FROM cinenest_identity.account WHERE account_id=:id"+(lock?" FOR SHARE":""))
            .param("id",account).query((r,n)->new Object[]{r.getLong(1),r.getBoolean(2),r.getString(3)}).list();
        if(accounts.size()!=1)throw new CredentialEpochSessions.Denied();
        var a=accounts.getFirst();
        var manual=jdbc.sql("SELECT password_hash,verified_at IS NOT NULL FROM cinenest_identity.manual_customer WHERE account_id=:id")
            .param("id",account).query((r,n)->new Object[]{r.getString(1),r.getBoolean(2)}).list();
        var versions=jdbc.sql("""
            SELECT v.password_hash,v.credential_revision,v.previous_revision,a.session_invalidation_epoch,
                   a.invalidate_all_previous_sessions FROM cinenest_identity.manual_credential_version v
            LEFT JOIN cinenest_identity.manual_credential_recovery_audit a
              ON a.audit_id=v.audit_id AND a.command_id=v.command_id AND a.capability_id=v.capability_id
             AND a.account_id=v.account_id AND a.actor_id=v.actor_id AND a.venue_id=v.venue_id
             AND a.previous_revision=v.previous_revision AND a.credential_revision=v.credential_revision
             AND a.expected_account_revision=v.expected_account_revision AND a.admitted_at=v.admitted_at
            WHERE v.account_id=:id ORDER BY v.credential_revision DESC LIMIT 1
            """).param("id",account).query((r,n)->new Object[]{r.getString(1),r.getLong(2),r.getLong(3),r.getObject(4),r.getBoolean(5)}).list();
        long epoch=0;String hash=manual.isEmpty()?null:(String)manual.getFirst()[0];
        if(!versions.isEmpty()) {
            var v=versions.getFirst();epoch=(Long)v[1];
            if(epoch<=0 || (Long)v[2]!=epoch-1 || !(v[3] instanceof Number e) || e.longValue()!=epoch || !Boolean.TRUE.equals(v[4]))throw new CredentialEpochSessions.Denied();
            hash=(String)v[0];
        }
        if(hash!=null && !hash.matches("\\{pbkdf2-v1\\}[0-9a-f]{96}"))throw new CredentialEpochSessions.Denied();
        if(epoch>0 && (hash==null || manual.size()!=1 || !Boolean.TRUE.equals(manual.getFirst()[1])))throw new CredentialEpochSessions.Denied();
        return new CredentialState((Long)a[0],epoch,hash,!manual.isEmpty()&&Boolean.TRUE.equals(manual.getFirst()[1]),(Boolean)a[1],(String)a[2]);
    }
    long credentialEpoch(UUID account,boolean lock) {
        var state=credentialState(account,lock);
        if(!state.synthetic() || !"ELIGIBLE".equals(state.eligibility()))throw new CredentialEpochSessions.Denied();
        return state.epoch();
    }

    void audit(UUID actor, UUID target, String event, boolean allowed) {
        jdbc.sql("INSERT INTO cinenest_identity.security_audit(event_id,actor_id,target_id,event_kind,outcome) VALUES (:id,:actor,:target,:event,:outcome)")
            .param("id", UUID.randomUUID()).param("actor", actor, java.sql.Types.OTHER)
            .param("target", target, java.sql.Types.OTHER).param("event", event)
            .param("outcome", allowed ? "ALLOW" : "DENY").update();
    }
}
