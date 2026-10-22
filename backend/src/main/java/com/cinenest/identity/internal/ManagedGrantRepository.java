package com.cinenest.identity.internal;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ManagedGrantRepository {
    record Target(String eligibility,boolean synthetic) {}
    record Grant(UUID grantId,UUID venueId,String permission,String storedState,Instant validFrom,Instant validUntil,long revision,
                 boolean effectiveAtObservation,boolean overlapsAnotherActiveGrant,boolean anotherEffectiveGrant) {}
    private final JdbcClient jdbc;
    ManagedGrantRepository(JdbcClient jdbc){this.jdbc=jdbc;}
    void lock(UUID actor,UUID target,UUID venue){
        var ids=List.of(actor,target).stream().distinct().sorted().toList();
        jdbc.sql("SELECT account_id FROM cinenest_identity.account WHERE account_id IN (:ids) ORDER BY account_id FOR UPDATE").param("ids",ids).query(UUID.class).list();
        jdbc.sql("SELECT account_id FROM cinenest_identity.account_scope WHERE account_id IN (:ids) AND venue_id=:v ORDER BY account_id FOR UPDATE").param("ids",ids).param("v",venue).query(UUID.class).list();
        // Lock every existing grant for these principals, including rows which could change into the managed subset.
        // Parent account locks also block FK-backed new grant/membership insertions until this read/audit commits.
        jdbc.sql("SELECT grant_id FROM cinenest_identity.access_grant WHERE account_id IN (:ids) ORDER BY grant_id FOR UPDATE").param("ids",ids).query(UUID.class).list();
    }
    boolean member(UUID actor,UUID venue){return jdbc.sql("SELECT count(*) FROM cinenest_identity.account_scope WHERE account_id=:a AND venue_id=:v").param("a",actor).param("v",venue).query(Integer.class).single()==1;}
    Optional<Target> target(UUID target){return jdbc.sql("SELECT eligibility,synthetic FROM cinenest_identity.account WHERE account_id=:a").param("a",target).query((rs,n)->new Target(rs.getString(1),rs.getBoolean(2))).optional();}
    Instant now(){return jdbc.sql("SELECT clock_timestamp()").query(Instant.class).single();}
    private static final String MANAGED=" account_id=:a AND venue_id=:v AND scope_kind='VENUE' AND permission IN ('booking.synthetic.read','guest.synthetic.read') ";
    String fingerprint(UUID target,UUID venue,Instant at,String eligibility){
        // Change detector only; cursor authenticity is independently HMAC-SHA256 protected.
        return eligibility+":"+jdbc.sql("SELECT md5(COALESCE(string_agg(grant_id::text||':'||permission||':'||state||':'||extract(epoch FROM valid_from)::text||':'||extract(epoch FROM valid_until)::text||':'||revision::text||':'||(state='ACTIVE' AND valid_from<=:at AND valid_until>:at)::text,'|' ORDER BY grant_id),'')) FROM cinenest_identity.access_grant WHERE "+MANAGED)
            .param("a",target).param("v",venue).param("at",java.sql.Timestamp.from(at)).query(String.class).single();
    }
    List<Grant> rows(UUID target,UUID venue,Instant at,boolean eligible,UUID after,int limit){
        return jdbc.sql("""
            WITH managed AS (SELECT * FROM cinenest_identity.access_grant WHERE
            """+MANAGED+"""
            ) SELECT g.*,
              (:eligible AND g.state='ACTIVE' AND g.valid_from<=:at AND g.valid_until>:at) AS effective,
              EXISTS(SELECT 1 FROM managed x WHERE x.grant_id<>g.grant_id AND x.permission=g.permission
                AND x.state='ACTIVE' AND g.state='ACTIVE' AND x.valid_from<g.valid_until AND g.valid_from<x.valid_until) AS overlapping,
              EXISTS(SELECT 1 FROM managed x WHERE x.grant_id<>g.grant_id AND x.permission=g.permission
                AND :eligible AND x.state='ACTIVE' AND x.valid_from<=:at AND x.valid_until>:at) AS other_effective
            FROM managed g WHERE (CAST(:after AS uuid) IS NULL OR g.grant_id>:after) ORDER BY g.grant_id LIMIT :limit
            """).param("a",target).param("v",venue).param("at",java.sql.Timestamp.from(at)).param("eligible",eligible).param("after",after,java.sql.Types.OTHER).param("limit",limit)
            .query((rs,n)->new Grant(rs.getObject("grant_id",UUID.class),rs.getObject("venue_id",UUID.class),rs.getString("permission"),rs.getString("state"),rs.getTimestamp("valid_from").toInstant(),rs.getTimestamp("valid_until").toInstant(),rs.getLong("revision"),rs.getBoolean("effective"),rs.getBoolean("overlapping"),rs.getBoolean("other_effective"))).list();
    }
    void audit(UUID actor,UUID venue,UUID target,Instant at,String outcome,String reason){
        jdbc.sql("INSERT INTO cinenest_identity.grant_read_audit VALUES(:id,:v,:a,:t,'MANAGED_GRANTS_READ',:outcome,:reason,:at,:correlation)")
            .param("id",UUID.randomUUID()).param("v",venue).param("a",actor).param("t",target,java.sql.Types.OTHER).param("outcome",outcome).param("reason",reason).param("at",java.sql.Timestamp.from(at)).param("correlation",UUID.randomUUID()).update();
    }
}
