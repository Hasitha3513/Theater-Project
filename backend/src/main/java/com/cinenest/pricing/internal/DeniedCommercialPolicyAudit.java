package com.cinenest.pricing.internal;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
class DeniedCommercialPolicyAudit {
    private final JdbcClient jdbc;
    DeniedCommercialPolicyAudit(JdbcClient jdbc){this.jdbc=jdbc;}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void record(UUID actor){jdbc.sql("INSERT INTO cinenest_pricing.commercial_policy_audit(event_id,actor_id,operation,outcome,reason,correlation_id,occurred_at) VALUES(:id,:a,'DENY','DENY','Commercial policy access denied',:c,clock_timestamp())").param("id",UUID.randomUUID()).param("a",actor,java.sql.Types.OTHER).param("c",UUID.randomUUID()).update();}
}
