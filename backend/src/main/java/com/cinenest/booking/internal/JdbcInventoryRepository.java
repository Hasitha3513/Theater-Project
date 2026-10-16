package com.cinenest.booking.internal;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcInventoryRepository implements InventoryRepository {
    private final JdbcClient jdbc;
    JdbcInventoryRepository(JdbcClient jdbc) {this.jdbc=jdbc;}
    private static final String SELECT="SELECT h.*,b.venue_id,b.room_id,b.session_start,b.session_end,b.buffer_minutes,b.blocked_until FROM cinenest_booking.reservation_hold h JOIN cinenest_booking.inventory_block b USING(block_id) ";
    private static final RowMapper<Hold> MAPPER=(rs,n)->new Hold(rs.getObject("hold_id",UUID.class),rs.getObject("owner_id",UUID.class),rs.getObject("block_id",UUID.class),rs.getObject("venue_id",UUID.class),rs.getObject("room_id",UUID.class),rs.getTimestamp("session_start").toInstant(),rs.getTimestamp("session_end").toInstant(),rs.getInt("buffer_minutes"),rs.getTimestamp("blocked_until").toInstant(),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("expires_at").toInstant(),rs.getString("state"),rs.getLong("revision"),rs.getInt("guests"),rs.getString("timezone"),rs.getString("configuration"),rs.getString("minimum_policy_ids"));
    public Instant now() {return jdbc.sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single().toInstant();}
    public Optional<Hold> hold(UUID id) {return jdbc.sql(SELECT+"WHERE h.hold_id=:id").param("id",id).query(MAPPER).optional();}
    public List<Hold> active(UUID venue) {return jdbc.sql(SELECT+"WHERE b.venue_id=:v AND h.state='ACTIVE' ORDER BY h.hold_id FOR UPDATE OF h,b").param("v",venue).query(MAPPER).list();}
    public Set<UUID> activeVenues() {return Set.copyOf(jdbc.sql("SELECT DISTINCT b.venue_id FROM cinenest_booking.reservation_hold h JOIN cinenest_booking.inventory_block b USING(block_id) WHERE h.state='ACTIVE'").query(UUID.class).list());}
    public Optional<UUID> activeOwnerVenue(UUID actor) {return jdbc.sql("SELECT b.venue_id FROM cinenest_booking.reservation_hold h JOIN cinenest_booking.inventory_block b USING(block_id) WHERE h.owner_id=:a AND h.state='ACTIVE'").param("a",actor).query(UUID.class).optional();}
    public Optional<Command> command(UUID actor,UUID key) {return jdbc.sql("SELECT request_hash,hold_id,action FROM cinenest_booking.inventory_command WHERE owner_id=:a AND command_key=:k").param("a",actor).param("k",key).query((rs,n)->new Command(rs.getString(1),rs.getObject(2,UUID.class),rs.getString(3))).optional();}
    public void command(UUID actor,UUID key,String hash,UUID hold,String action) {
        jdbc.sql("INSERT INTO cinenest_booking.inventory_command(owner_id,command_key,request_hash,hold_id,action) VALUES(:a,:k,:hash,:h,:action)").param("a",actor).param("k",key).param("hash",hash).param("h",hold).param("action",action).update();
    }
    public void audit(UUID actor,UUID hold,String action,String before,String after) {
        jdbc.sql("INSERT INTO cinenest_booking.inventory_audit(event_id,actor_id,hold_id,action,old_state,new_state,correlation_id) VALUES(:id,:a,:h,:action,:before,:after,:correlation)")
            .param("id",UUID.randomUUID()).param("a",actor,java.sql.Types.OTHER).param("h",hold,java.sql.Types.OTHER).param("action",action).param("before",before,java.sql.Types.VARCHAR).param("after",after,java.sql.Types.VARCHAR).param("correlation",UUID.randomUUID()).update();
    }
    public long attempts(UUID actor,int seconds) {return jdbc.sql("SELECT count(*) FROM cinenest_booking.inventory_audit WHERE actor_id=:a AND action='CREATE_ATTEMPT' AND occurred_at>clock_timestamp()-:seconds*INTERVAL '1 second'").param("a",actor).param("seconds",seconds).query(Long.class).single();}
    public void policy(UUID id,int ttl,int rate,int seconds,String reference) {
        jdbc.sql("INSERT INTO cinenest_booking.hold_policy(policy_id,ttl_seconds,rate_count,rate_seconds,approval_reference) VALUES(:id,:ttl,:rate,:seconds,:ref) ON CONFLICT(policy_id) DO NOTHING").param("id",id).param("ttl",ttl).param("rate",rate).param("seconds",seconds).param("ref",reference).update();
    }
    public void create(Hold h,UUID policy) {
        jdbc.sql("INSERT INTO cinenest_booking.inventory_block(block_id,venue_id,room_id,session_start,session_end,buffer_minutes,blocked_until,blocking,kind) VALUES(:b,:v,:r,:start,:end,:buffer,:until,TRUE,'HOLD')")
            .param("b",h.block()).param("v",h.venue()).param("r",h.room()).param("start",h.start().atOffset(ZoneOffset.UTC)).param("end",h.end().atOffset(ZoneOffset.UTC)).param("buffer",h.buffer()).param("until",h.until().atOffset(ZoneOffset.UTC)).update();
        jdbc.sql("INSERT INTO cinenest_booking.reservation_hold(hold_id,owner_id,block_id,policy_id,guests,configuration,timezone,minimum_policy_ids,created_at,expires_at,state) VALUES(:id,:owner,:b,:p,:guests,:cfg,:zone,:minimum,:created,:expires,'ACTIVE')")
            .param("id",h.id()).param("owner",h.owner()).param("b",h.block()).param("p",policy).param("guests",h.guests()).param("cfg",h.configuration()).param("zone",h.timezone()).param("minimum",h.minimumIds()).param("created",h.created().atOffset(ZoneOffset.UTC)).param("expires",h.expires().atOffset(ZoneOffset.UTC)).update();
    }
    public void transition(Hold h,String state,UUID actor) {
        if(jdbc.sql("UPDATE cinenest_booking.reservation_hold SET state=:state,revision=revision+1 WHERE hold_id=:id AND state='ACTIVE' AND revision=:rev").param("id",h.id()).param("state",state).param("rev",h.revision()).update()!=1)throw new InventoryFailure(409,"Inventory changed");
        jdbc.sql("UPDATE cinenest_booking.inventory_block SET blocking=FALSE,revision=revision+1 WHERE block_id=:b AND kind='HOLD'").param("b",h.block()).update();
        audit(actor,h.id(),state,h.state(),state);
    }
    public boolean overlaps(UUID room,Instant start,Instant until) {
        return jdbc.sql("SELECT count(*) FROM cinenest_booking.inventory_block WHERE room_id=:r AND blocking AND tstzrange(session_start,blocked_until,'[)') && tstzrange(CAST(:start AS timestamptz),CAST(:until AS timestamptz),'[)')")
            .param("r",room).param("start",start.atOffset(ZoneOffset.UTC)).param("until",until.atOffset(ZoneOffset.UTC)).query(Long.class).single()>0;
    }
    public List<Map<String,Object>> inventory(UUID venue,int page,int size) {
        return jdbc.sql("SELECT block_id,room_id,session_start,session_end,buffer_minutes,blocked_until,kind FROM cinenest_booking.inventory_block WHERE venue_id=:v AND blocking ORDER BY session_start,block_id LIMIT :size OFFSET :offset")
            .param("v",venue).param("size",size).param("offset",Math.multiplyExact((long)page,size)).query((rs,n)->Map.<String,Object>of("blockId",rs.getObject(1,UUID.class),"roomId",rs.getObject(2,UUID.class),"sessionStart",rs.getTimestamp(3).toInstant(),"sessionEnd",rs.getTimestamp(4).toInstant(),"bufferMinutes",rs.getInt(5),"blockedUntil",rs.getTimestamp(6).toInstant(),"kind",rs.getString(7))).list();
    }
}
