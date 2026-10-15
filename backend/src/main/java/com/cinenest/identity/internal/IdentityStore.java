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

    void audit(UUID actor, UUID target, String event, boolean allowed) {
        jdbc.sql("INSERT INTO cinenest_identity.security_audit(event_id,actor_id,target_id,event_kind,outcome) VALUES (:id,:actor,:target,:event,:outcome)")
            .param("id", UUID.randomUUID()).param("actor", actor, java.sql.Types.OTHER)
            .param("target", target, java.sql.Types.OTHER).param("event", event)
            .param("outcome", allowed ? "ALLOW" : "DENY").update();
    }
}
