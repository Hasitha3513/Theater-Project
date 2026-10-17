package com.cinenest.verification.internal;
import com.cinenest.identity.ScopedAuditSource;
import com.cinenest.identity.ScopedAuthorization;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
@Component
class VerificationAuditProjection implements ScopedAuditSource {
    private final JdbcClient jdbc;private final ScopedAuthorization auth;
    VerificationAuditProjection(JdbcClient jdbc,ScopedAuthorization auth){this.jdbc=jdbc;this.auth=auth;}
    public String source(){return "VERIFICATION";}
    public List<Event> read(Query q){
        if(!auth.permits(q.actor(),q.venue(),"audit.verification.read",true))throw new org.springframework.security.access.AccessDeniedException("Denied");
        return jdbc.sql("SELECT a.*, CASE WHEN a.outcome='PERMIT' THEN 'ALLOW' ELSE 'DENY' END AS projected_outcome FROM cinenest_verification.verification_audit a JOIN cinenest_verification.verification_request r USING(request_id) JOIN cinenest_verification.verification_policy_revision p USING(policy_id) WHERE r.venue_id=:v AND p.synthetic=TRUE AND a.recorded_at>=:from AND a.recorded_at<:to"
            +" AND a.outcome IN ('PERMIT','DENY')"+(q.beforeTime()==null?"":" AND (a.recorded_at,a.event_id)<(:time,:id)")+(q.event()==null?"":" AND a.event_id=:event")+(q.action()==null?"":" AND :action='VERIFICATION_EVENT'")+(q.outcome()==null?"":" AND a.outcome=:outcome")+" ORDER BY a.recorded_at DESC,a.event_id DESC LIMIT :limit")
            .param("v",q.venue()).param("from",java.sql.Timestamp.from(q.from())).param("to",java.sql.Timestamp.from(q.to())).param("time",q.beforeTime()==null?null:java.sql.Timestamp.from(q.beforeTime()),java.sql.Types.TIMESTAMP)
            .param("id",q.beforeId(),java.sql.Types.OTHER).param("event",q.event(),java.sql.Types.OTHER).param("action",q.action(),java.sql.Types.VARCHAR).param("outcome",q.outcome()==null?null:q.outcome().equals("ALLOW")?"PERMIT":"DENY",java.sql.Types.VARCHAR).param("limit",q.limit())
            .query((rs,n)->new Event(rs.getObject("event_id",UUID.class),source(),rs.getTimestamp("recorded_at").toInstant(),q.venue(),"VERIFICATION_EVENT",rs.getString("projected_outcome"),"SOURCE_EVENT",rs.getObject("correlation_id",UUID.class),rs.getObject("actor_id",UUID.class),rs.getObject("request_id",UUID.class))).list();
    }
}
