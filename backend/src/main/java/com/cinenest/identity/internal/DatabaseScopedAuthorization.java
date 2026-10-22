package com.cinenest.identity.internal;

import com.cinenest.identity.ScopedAuthorization;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class DatabaseScopedAuthorization implements ScopedAuthorization {
    private final JdbcClient jdbc;
    DatabaseScopedAuthorization(JdbcClient jdbc) { this.jdbc = jdbc; }
    public boolean member(UUID actor,UUID venue,boolean lock) {
        if(actor==null||venue==null)return false;
        return !jdbc.sql("SELECT account_id FROM cinenest_identity.account_scope WHERE account_id=:a AND venue_id=:v"+(lock?" FOR UPDATE":"")).param("a",actor).param("v",venue).query(UUID.class).list().isEmpty();
    }
    public boolean eligible(UUID actor) {
        return eligible(actor,false);
    }
    public boolean eligible(UUID actor,boolean lock) {
        if(actor==null)return false;
        var current=jdbc.sql("SELECT eligibility FROM cinenest_identity.account WHERE account_id=:actor AND synthetic=TRUE"+(lock?" FOR UPDATE":"")).param("actor",actor).query(String.class).list();
        return !current.isEmpty() && current.getFirst().equals("ELIGIBLE");
    }
    public boolean permitsSelf(UUID actor,String action,boolean lock){
        if(!Set.of("notification.read","notification.mark-read","access.synthetic.read","access.synthetic.issue","access.synthetic.reveal","access.synthetic.attempt").contains(action)||!eligible(actor,lock))return false;
        if(lock)jdbc.sql("SELECT grant_id FROM cinenest_identity.access_grant WHERE account_id=:a AND permission=:p AND scope_kind='SELF' ORDER BY grant_id FOR UPDATE").param("a",actor).param("p",action).query(UUID.class).list();
        return jdbc.sql("SELECT count(*) FROM cinenest_identity.access_grant WHERE account_id=:a AND permission=:p AND scope_kind='SELF' AND venue_id IS NULL AND state='ACTIVE' AND valid_from<=clock_timestamp() AND valid_until>clock_timestamp()").param("a",actor).param("p",action).query(Integer.class).single()>0;
    }
    private String writeEquivalent(String action) {
        return switch(action) { case "report.booking.synthetic.read", "booking.synthetic.read", "guest.synthetic.read", "report.bank.synthetic.read" -> action; case "operations.synthetic.read", "operations.synthetic.record", "operations.synthetic.assign", "operations.synthetic.review" -> action; case "iot.synthetic.read", "iot.synthetic.command", "iot.synthetic.simulate", "iot.synthetic.reconcile" -> action; case "notification.admin.read", "notification.admin.retries", "notification.admin.reconciliations", "notification.admin.simulations" -> action; case "access.synthetic.review", "access.synthetic.revoke", "access.synthetic.simulate" -> action; case "room.read" -> "room.write"; case "venue.read" -> "venue.write";
            case "package.read" -> "package.edit"; case "account.read", "audit.account.read", "audit.verification.read", "identity.grant.read" -> action; case "refund.synthetic.read", "refund.synthetic.review", "refund.synthetic.destination.verify", "refund.synthetic.approve", "refund.synthetic.execute", "refund.synthetic.reconcile", "bank.synthetic.credit.verify", "bank.synthetic.credit.read", "bank.synthetic.allocate", "bank.synthetic.resolve", "bank.synthetic.correct.request", "bank.synthetic.correct.approve", "bank.claim.review", "bank.evidence.read", "bank.claim.escalate", "verification.review", "verification.evidence.read", "inventory.read", "package.publish", "package.deactivate", "package.edit", "room.write", "venue.write" -> action; default -> throw new IllegalArgumentException("Unknown action"); };
    }
    @Transactional
    public boolean permits(UUID actor, UUID venue, String action, boolean lock) {
        if (actor == null || venue == null) return false;
        String equivalent = writeEquivalent(action);
        var accounts = jdbc.sql("SELECT eligibility FROM cinenest_identity.account WHERE account_id=:actor AND synthetic=TRUE" + (lock ? " FOR UPDATE" : ""))
            .param("actor",actor).query(String.class).list();
        if (accounts.isEmpty() || !accounts.getFirst().equals("ELIGIBLE")) return false;
        // Lock first, then evaluate with a fresh statement snapshot/clock after any revocation wait.
        if (lock) jdbc.sql("SELECT grant_id FROM cinenest_identity.access_grant WHERE account_id=:actor AND venue_id=:venue ORDER BY grant_id FOR UPDATE")
            .param("actor",actor).param("venue",venue).query(UUID.class).list();
        return jdbc.sql("""
            SELECT count(*) FROM cinenest_identity.access_grant g
            WHERE g.account_id=:actor AND g.venue_id=:venue AND g.scope_kind='VENUE'
              AND g.permission IN (:permissions) AND g.state='ACTIVE'
              AND g.valid_from<=clock_timestamp() AND g.valid_until>clock_timestamp()
            """).param("actor",actor).param("venue",venue).param("permissions",action.equals("package.read")?java.util.List.of("package.read","package.edit","package.publish","package.deactivate"):java.util.List.of(action,equivalent))
            .query(Integer.class).single()>0;
    }
    @Transactional
    public Set<UUID> venues(UUID actor, String action) {
        if (actor == null) return Set.of();
        String equivalent = writeEquivalent(action);
        return new HashSet<>(jdbc.sql("""
            SELECT DISTINCT g.venue_id FROM cinenest_identity.access_grant g
            JOIN cinenest_identity.account a ON a.account_id=g.account_id
            WHERE a.account_id=:actor AND a.synthetic=TRUE AND a.eligibility='ELIGIBLE'
             AND g.scope_kind='VENUE' AND g.permission IN (:permissions) AND g.state='ACTIVE'
             AND g.valid_from<=clock_timestamp() AND g.valid_until>clock_timestamp()
            """).param("actor",actor).param("permissions",action.equals("package.read")?java.util.List.of("package.read","package.edit","package.publish","package.deactivate"):java.util.List.of(action,equivalent)).query(UUID.class).list());
    }
}
