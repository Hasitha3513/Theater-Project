package com.cinenest.iot.internal;

import java.sql.Types;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Prepared adapter for the separately proposed V33 schema. No schema is created by this adapter. */
@Repository
@ConditionalOnProperty(name="cinenest.iot.lock-room-assignment.enabled", havingValue="true")
class JdbcLockRoomAssignmentRepository implements LockRoomAssignmentRepository {
    private final JdbcClient jdbc;
    JdbcLockRoomAssignmentRepository(JdbcClient jdbc) { this.jdbc=jdbc; }
    public Instant now() { return jdbc.sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single().toInstant(); }
    private static Assignment row(Map<String,Object> row) {
        Object at=row.get("created_at");
        Instant time=at instanceof java.sql.Timestamp t ? t.toInstant() : ((OffsetDateTime)at).toInstant();
        return new Assignment((UUID)row.get("assignment_id"),(UUID)row.get("venue_id"),(UUID)row.get("room_id"),
            (UUID)row.get("device_id"),((Number)row.get("assignment_revision")).longValue(),
            (String)row.get("room_configuration"),((Number)row.get("device_metadata_revision")).longValue(),
            (UUID)row.get("created_by"),time);
    }
    public Optional<Receipt> receipt(UUID actor, UUID key) {
        return jdbc.sql("SELECT a.*,c.request_sha256 FROM cinenest_iot.lock_room_assignment_command c JOIN cinenest_iot.lock_room_assignment a USING(assignment_id,venue_id,room_id,device_id) WHERE c.actor_id=:actor AND c.command_key=:key")
            .param("actor",actor).param("key",key).query().listOfRows().stream()
            .findFirst().map(r -> new Receipt(row(r),(byte[])r.get("request_sha256")));
    }
    public Optional<Long> lockDevice(UUID venue, UUID device) {
        var owned=jdbc.sql("SELECT device_id FROM cinenest_iot.lock_registry_device WHERE venue_id=:venue AND device_id=:device FOR UPDATE")
            .param("venue",venue).param("device",device).query(UUID.class).optional();
        if (owned.isEmpty()) return Optional.empty();
        return jdbc.sql("SELECT max(revision) FROM cinenest_iot.lock_registry_revision WHERE venue_id=:venue AND device_id=:device")
            .param("venue",venue).param("device",device).query(Long.class).optional();
    }
    public Assignment create(UUID actor, UUID venue, UUID room, LockRoomAssignmentPolicy.Create command, UUID correlation) {
        UUID assignment=UUID.randomUUID(),event=UUID.randomUUID(); Instant at=now();
        jdbc.sql("INSERT INTO cinenest_iot.lock_room_assignment(assignment_id,venue_id,room_id,device_id,room_configuration,device_metadata_revision,created_by,command_key,audit_event_id,created_at) VALUES(:id,:venue,:room,:device,:configuration,:revision,:actor,:key,:event,:at)")
            .param("id",assignment).param("venue",venue).param("room",room).param("device",command.device())
            .param("configuration",command.roomConfiguration()).param("revision",command.deviceRevision())
            .param("actor",actor).param("key",command.key()).param("event",event).param("at",OffsetDateTime.ofInstant(at,ZoneOffset.UTC)).update();
        event(event,actor,venue,room,assignment,command.key(),"CREATE",null,correlation,at);
        jdbc.sql("INSERT INTO cinenest_iot.lock_room_assignment_command(actor_id,command_key,assignment_id,venue_id,room_id,device_id,audit_event_id,request_sha256,recorded_at) VALUES(:actor,:key,:id,:venue,:room,:device,:event,:hash,:at)")
            .param("actor",actor).param("key",command.key()).param("id",assignment).param("venue",venue)
            .param("room",room).param("device",command.device()).param("event",event).param("hash",command.hash())
            .param("at",OffsetDateTime.ofInstant(at,ZoneOffset.UTC)).update();
        return new Assignment(assignment,venue,room,command.device(),1,command.roomConfiguration(),command.deviceRevision(),actor,at);
    }
    public Optional<Assignment> detail(UUID venue, UUID room, UUID assignment) {
        return jdbc.sql("SELECT * FROM cinenest_iot.lock_room_assignment WHERE venue_id=:venue AND room_id=:room AND assignment_id=:id")
            .param("venue",venue).param("room",room).param("id",assignment).query().listOfRows().stream().findFirst().map(JdbcLockRoomAssignmentRepository::row);
    }
    public List<Assignment> page(UUID venue, UUID room, int page) {
        return jdbc.sql("SELECT * FROM cinenest_iot.lock_room_assignment WHERE venue_id=:venue AND room_id=:room ORDER BY created_at DESC,assignment_id DESC LIMIT 26 OFFSET :offset")
            .param("venue",venue).param("room",room).param("offset",page*25).query().listOfRows().stream().map(JdbcLockRoomAssignmentRepository::row).toList();
    }
    private void event(UUID event, UUID actor, UUID venue, UUID room, UUID assignment, UUID key,
                       String action, String denial, UUID correlation, Instant at) {
        jdbc.sql("INSERT INTO cinenest_iot.lock_room_assignment_audit(event_id,actor_id,venue_id,room_id,assignment_id,command_key,action,outcome,denial_code,correlation_id,occurred_at) VALUES(:event,:actor,:venue,:room,:assignment,:key,:action,:outcome,:denial,:correlation,:at)")
            .param("event",event).param("actor",actor,Types.OTHER).param("venue",venue,Types.OTHER)
            .param("room",room,Types.OTHER).param("assignment",assignment,Types.OTHER).param("key",key,Types.OTHER)
            .param("action",action).param("outcome",denial==null?"ALLOW":"DENY").param("denial",denial,Types.VARCHAR)
            .param("correlation",correlation).param("at",OffsetDateTime.ofInstant(at,ZoneOffset.UTC)).update();
    }
    public void audit(UUID actor, UUID venue, UUID room, UUID assignment, String action, String denial, UUID correlation, Instant at) {
        event(UUID.randomUUID(),actor,venue,room,assignment,null,action,denial,correlation,at);
    }
}
