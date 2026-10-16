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
    public boolean eligible(UUID actor) {
        return actor!=null && jdbc.sql("SELECT count(*) FROM cinenest_identity.account WHERE account_id=:actor AND synthetic=TRUE AND eligibility='ELIGIBLE'").param("actor",actor).query(Integer.class).single()==1;
    }
    private String writeEquivalent(String action) {
        return switch(action) { case "room.read" -> "room.write"; case "venue.read" -> "venue.write";
            case "package.read" -> "package.edit"; case "package.edit", "room.write", "venue.write" -> action; default -> throw new IllegalArgumentException("Unknown action"); };
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
              AND g.permission IN (:action,:equivalent) AND g.state='ACTIVE'
              AND g.valid_from<=clock_timestamp() AND g.valid_until>clock_timestamp()
            """).param("actor",actor).param("venue",venue).param("action",action).param("equivalent",equivalent)
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
             AND g.scope_kind='VENUE' AND g.permission IN (:action,:equivalent) AND g.state='ACTIVE'
             AND g.valid_from<=clock_timestamp() AND g.valid_until>clock_timestamp()
            """).param("actor",actor).param("action",action).param("equivalent",equivalent).query(UUID.class).list());
    }
}
