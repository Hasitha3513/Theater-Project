package com.cinenest.identity.internal;
import com.cinenest.identity.ScopedAuditSource;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
@Repository
class JdbcAdminRepository implements AdminRepository {
    private final JdbcClient jdbc;
    JdbcAdminRepository(JdbcClient jdbc){this.jdbc=jdbc;}
    public List<Account> accounts(UUID venue,UUID target,String eligibility,UUID after,int limit){
        return jdbc.sql("SELECT a.account_id,a.eligibility,a.revision FROM cinenest_identity.account a JOIN cinenest_identity.account_scope s USING(account_id) WHERE a.synthetic=TRUE AND s.venue_id=:v"
            +(target==null?"":" AND a.account_id=:target")+(eligibility==null?"":" AND a.eligibility=:eligibility")+(after==null?"":" AND a.account_id>:after")+" ORDER BY a.account_id LIMIT :limit")
            .param("v",venue).param("target",target,java.sql.Types.OTHER).param("eligibility",eligibility,java.sql.Types.VARCHAR).param("after",after,java.sql.Types.OTHER).param("limit",limit)
            .query((rs,n)->new Account(rs.getObject("account_id",UUID.class),true,rs.getString("eligibility"),rs.getLong("revision"),venue)).list();
    }
    public List<ScopedAuditSource.Event> audit(ScopedAuditSource.Query q){
        return jdbc.sql("SELECT * FROM cinenest_identity.admin_read_audit WHERE venue_id=:v AND occurred_at>=:from AND occurred_at<:to"
            +(q.beforeTime()==null?"":" AND (occurred_at,event_id)<(:time,:id)")+(q.event()==null?"":" AND event_id=:event")+(q.action()==null?"":" AND action=:action")+(q.outcome()==null?"":" AND outcome=:outcome")+" ORDER BY occurred_at DESC,event_id DESC LIMIT :limit")
            .param("v",q.venue()).param("from",java.sql.Timestamp.from(q.from())).param("to",java.sql.Timestamp.from(q.to()))
            .param("time",q.beforeTime()==null?null:java.sql.Timestamp.from(q.beforeTime()),java.sql.Types.TIMESTAMP).param("id",q.beforeId(),java.sql.Types.OTHER).param("event",q.event(),java.sql.Types.OTHER)
            .param("action",q.action(),java.sql.Types.VARCHAR).param("outcome",q.outcome(),java.sql.Types.VARCHAR).param("limit",q.limit())
            .query((rs,n)->new ScopedAuditSource.Event(rs.getObject("event_id",UUID.class),"IDENTITY",rs.getTimestamp("occurred_at").toInstant(),q.venue(),rs.getString("action"),rs.getString("outcome"),rs.getString("reason_code"),rs.getObject("correlation_id",UUID.class),rs.getObject("actor_id",UUID.class),rs.getObject("target_id",UUID.class))).list();
    }
    public void denied(UUID actor){
        jdbc.sql("INSERT INTO cinenest_identity.security_audit(event_id,actor_id,event_kind,outcome) VALUES(:id,:a,'HTTP_DENIAL','DENY')").param("id",UUID.randomUUID()).param("a",actor).update();
    }
    public void record(UUID actor,UUID venue,UUID target,String action,String outcome,String reason){
        jdbc.sql("INSERT INTO cinenest_identity.admin_read_audit(event_id,venue_id,actor_id,target_id,action,outcome,reason_code,correlation_id) VALUES(:id,:v,:a,:t,:action,:outcome,:reason,:c)")
            .param("id",UUID.randomUUID()).param("v",venue).param("a",actor).param("t",target,java.sql.Types.OTHER).param("action",action).param("outcome",outcome).param("reason",reason).param("c",UUID.randomUUID()).update();
    }
}
