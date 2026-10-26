package com.cinenest.iot.internal;

import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import java.sql.Types;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLockRegistryRepository implements LockRegistryRepository {
    private final JdbcClient jdbc;
    JdbcLockRegistryRepository(JdbcClient jdbc){this.jdbc=jdbc;}
    public Instant now(){return jdbc.sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single().toInstant();}
    static Instant time(Object value){return value instanceof java.sql.Timestamp t?t.toInstant():((OffsetDateTime)value).toInstant();}
    public Optional<Map<String,Object>> receipt(UUID actor,UUID key){return jdbc.sql("SELECT c.*,d.registered_at FROM cinenest_iot.lock_registry_command c JOIN cinenest_iot.lock_registry_device d USING(device_id,venue_id) WHERE c.actor_id=:a AND c.command_key=:k").param("a",actor).param("k",key).query().listOfRows().stream().findFirst();}
    private void event(UUID id,UUID actor,UUID venue,UUID device,Long revision,String kind,String denial,UUID correlation,Instant at){
        jdbc.sql("INSERT INTO cinenest_iot.lock_registry_audit(event_id,actor_id,venue_id,device_id,metadata_revision,event_kind,outcome,denial_code,correlation_id,observed_at) VALUES(:id,:a,:v,:d,:r,:k,:o,:den,:c,:t)")
            .param("id",id).param("a",actor,Types.OTHER).param("v",venue,Types.OTHER).param("d",device,Types.OTHER).param("r",revision,Types.BIGINT).param("k",kind).param("o",denial==null?"ALLOW":"DENY").param("den",denial,Types.VARCHAR).param("c",correlation).param("t",OffsetDateTime.ofInstant(at,ZoneOffset.UTC)).update();
    }
    public void audit(UUID actor,UUID venue,UUID device,Long revision,String kind,String denial,UUID correlation,Instant at){event(UUID.randomUUID(),actor,venue,device,revision,kind,denial,correlation,at);}
    public Map<String,Object> register(UUID actor,UUID venue,LockRegistryPolicy.Registration r,LockRegistryReferenceResolver.Identity identity,UUID correlation){
        UUID device=UUID.randomUUID(),event=UUID.randomUUID();
        jdbc.sql("INSERT INTO cinenest_iot.lock_registry_device(device_id,venue_id,provider,provider_scope_id,provider_identity_id,registered_by,registration_command_key) VALUES(:d,:v,'TUYA',:s,:i,:a,:k)")
            .param("d",device).param("v",venue).param("s",identity.scope()).param("i",identity.identity()).param("a",actor).param("k",r.key()).update();
        event(event,actor,venue,device,1L,"REGISTER",null,correlation,now());
        jdbc.sql("INSERT INTO cinenest_iot.lock_registry_revision(device_id,venue_id,revision,manufacturer,model,sku,hardware_revision,firmware_revision,provider_reference_id,provider_reference_version,gateway_reference_id,gateway_reference_version,declaration_source,declaration_reference,declared_capabilities,recorded_by,audit_event_id,audit_kind) VALUES(:d,:v,1,:m,:model,:sku,:hw,:fw,:ref,:ver,:g,:gv,:src,:sr,CAST(:caps AS jsonb),:a,:event,'REGISTER')")
            .param("d",device).param("v",venue).param("m",r.manufacturer()).param("model",r.model()).param("sku",r.sku(),Types.VARCHAR).param("hw",r.hardware(),Types.VARCHAR).param("fw",r.firmware(),Types.VARCHAR)
            .param("ref",r.reference().id()).param("ver",r.reference().version()).param("g",r.gateway()==null?null:r.gateway().id(),Types.OTHER).param("gv",r.gateway()==null?null:r.gateway().version(),Types.OTHER)
            .param("src",r.source()).param("sr",r.sourceReference(),Types.OTHER).param("caps",JsonMapper.builder().build().writeValueAsString(r.capabilities()))
            .param("a",actor).param("event",event).update();
        jdbc.sql("INSERT INTO cinenest_iot.lock_registry_command(actor_id,command_key,venue_id,device_id,request_sha256,audit_event_id) VALUES(:a,:k,:v,:d,:hash,:event)")
            .param("a",actor).param("k",r.key()).param("v",venue).param("d",device).param("hash",r.hash()).param("event",event).update();
        return receipt(actor,r.key()).orElseThrow();
    }
    private static final String SELECT="SELECT d.provider,d.registration_status,d.registered_at,r.* FROM cinenest_iot.lock_registry_device d JOIN cinenest_iot.lock_registry_revision r USING(device_id,venue_id) ";
    public Optional<Map<String,Object>> detail(UUID venue,UUID device,Long revision){
        var q=jdbc.sql(SELECT+"WHERE d.venue_id=:v AND d.device_id=:d AND r.revision="+(revision==null?"(SELECT max(x.revision) FROM cinenest_iot.lock_registry_revision x WHERE x.device_id=d.device_id)":":r"));
        q.param("v",venue).param("d",device);if(revision!=null)q.param("r",revision);return q.query().listOfRows().stream().findFirst();
    }
    public List<Map<String,Object>> page(UUID venue,UUID device,int page){
        if(device==null)return jdbc.sql(SELECT+"WHERE d.venue_id=:v AND r.revision=(SELECT max(x.revision) FROM cinenest_iot.lock_registry_revision x WHERE x.device_id=d.device_id) ORDER BY d.registered_at DESC,d.device_id DESC LIMIT 26 OFFSET :offset")
            .param("v",venue).param("offset",page*25).query().listOfRows();
        return jdbc.sql(SELECT+"WHERE d.venue_id=:v AND d.device_id=:d ORDER BY r.revision DESC LIMIT 26 OFFSET :offset").param("v",venue).param("d",device).param("offset",page*25).query().listOfRows();
    }
}
