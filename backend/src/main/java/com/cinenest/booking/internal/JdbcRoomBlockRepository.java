package com.cinenest.booking.internal;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRoomBlockRepository implements RoomBlockRepository {
    private final JdbcClient jdbc;
    JdbcRoomBlockRepository(JdbcClient jdbc){this.jdbc=jdbc;}
    private static final RowMapper<Block> ROW=(r,n)->new Block(r.getObject("block_id",UUID.class),r.getObject("actor_id",UUID.class),r.getObject("command_key",UUID.class),r.getString("request_hash"),r.getObject("venue_id",UUID.class),r.getObject("room_id",UUID.class),r.getTimestamp("start_at").toInstant(),r.getTimestamp("end_at").toInstant(),r.getString("expected_configuration"),r.getTimestamp("recorded_at").toInstant());
    public Instant now(){return jdbc.sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single().toInstant();}
    public Optional<Block> command(UUID actor,UUID key){return jdbc.sql("SELECT * FROM cinenest_booking.room_block_command WHERE actor_id=:a AND command_key=:k").param("a",actor).param("k",key).query(ROW).optional();}
    public Optional<Block> find(UUID venue,UUID room,UUID block){return jdbc.sql("SELECT * FROM cinenest_booking.room_block_command WHERE venue_id=:v AND room_id=:r AND block_id=:b").param("v",venue).param("r",room).param("b",block).query(ROW).optional();}
    public List<Block> list(UUID venue,UUID room,int page,int size){return jdbc.sql("SELECT * FROM cinenest_booking.room_block_command WHERE venue_id=:v AND room_id=:r ORDER BY start_at,block_id LIMIT :size OFFSET :offset").param("v",venue).param("r",room).param("size",size).param("offset",Math.multiplyExact((long)page,size)).query(ROW).list();}
    public void create(Block b,UUID event){
        jdbc.sql("INSERT INTO cinenest_booking.inventory_block(block_id,venue_id,room_id,session_start,session_end,buffer_minutes,blocked_until,blocking,kind,revision) VALUES(:b,:v,:r,:s,:e,0,:e,true,'MAINTENANCE',0)")
            .param("b",b.id()).param("v",b.venue()).param("r",b.room()).param("s",b.start().atOffset(ZoneOffset.UTC)).param("e",b.end().atOffset(ZoneOffset.UTC)).update();
        jdbc.sql("INSERT INTO cinenest_booking.room_block_command(actor_id,command_key,request_hash,block_id,venue_id,room_id,start_at,end_at,expected_configuration,blocked_until,audit_event_id,recorded_at) VALUES(:a,:k,:h,:b,:v,:r,:s,:e,:c,:e,:event,:now)")
            .param("a",b.actor()).param("k",b.key()).param("h",b.hash()).param("b",b.id()).param("v",b.venue()).param("r",b.room()).param("s",b.start().atOffset(ZoneOffset.UTC)).param("e",b.end().atOffset(ZoneOffset.UTC)).param("c",b.configuration()).param("event",event).param("now",b.created().atOffset(ZoneOffset.UTC)).update();
        audit(event,b.actor(),b.venue(),b.room(),b.id(),b.key(),"CREATE");
    }
    public void audit(UUID event,UUID actor,UUID venue,UUID room,UUID block,UUID command,String action){
        jdbc.sql("INSERT INTO cinenest_booking.room_block_audit(event_id,actor_id,venue_id,room_id,block_id,command_key,action,correlation_id) VALUES(:e,:a,:v,:r,:b,:k,:action,:correlation)")
            .param("e",event).param("a",actor).param("v",venue).param("r",room).param("b",block,java.sql.Types.OTHER).param("k",command,java.sql.Types.OTHER).param("action",action).param("correlation",UUID.randomUUID()).update();
    }
}
