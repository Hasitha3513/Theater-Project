package com.cinenest.payments.internal;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
@Repository
class JdbcInstructionRepository implements InstructionRepository {
 final JdbcClient jdbc;JdbcInstructionRepository(JdbcClient jdbc){this.jdbc=jdbc;}
 private static Revision row(ResultSet r,int ignored)throws SQLException{return new Revision(r.getObject("instruction_revision_id",UUID.class),r.getObject("venue_id",UUID.class),r.getString("instruction_version"),r.getObject("reference_id",UUID.class),r.getObject("reference_version",UUID.class),r.getString("content_sha256"),r.getTimestamp("effective_from").toInstant(),r.getTimestamp("effective_until").toInstant(),r.getTimestamp("approved_at").toInstant());}
 public Optional<Revision> revision(UUID v,UUID id){return jdbc.sql("SELECT * FROM cinenest_payments.bank_instruction_revision WHERE venue_id=:v AND instruction_revision_id=:i").param("v",v).param("i",id).query(JdbcInstructionRepository::row).optional();}
 public Optional<Revision> version(UUID v,String version){return jdbc.sql("SELECT * FROM cinenest_payments.bank_instruction_revision WHERE venue_id=:v AND instruction_version=:i").param("v",v).param("i",version).query(JdbcInstructionRepository::row).optional();}
 public Optional<Target> request(UUID v,UUID request){return jdbc.sql("SELECT owner_id FROM cinenest_payments.bank_payment_request WHERE request_id=:r AND venue_id=:v").param("r",request).param("v",v).query(UUID.class).optional().map(owner->new Target(owner,jdbc.sql("SELECT i.* FROM cinenest_payments.bank_instruction_request_binding b JOIN cinenest_payments.bank_instruction_revision i USING(instruction_revision_id,venue_id,instruction_version) WHERE b.request_id=:r AND b.venue_id=:v").param("r",request).param("v",v).query(JdbcInstructionRepository::row).optional()));}
 public Instant now(){return jdbc.sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single().toInstant();}
 public void bind(UUID request,Revision r){jdbc.sql("INSERT INTO cinenest_payments.bank_instruction_request_binding(request_id,venue_id,instruction_revision_id,instruction_version) VALUES(:q,:v,:r,:i)").param("q",request).param("v",r.venue()).param("r",r.id()).param("i",r.version()).update();}
 public void audit(UUID actor,UUID venue,UUID revision,UUID request,String action,String outcome,String reason,Instant at,UUID correlation){jdbc.sql("INSERT INTO cinenest_payments.bank_instruction_audit(event_id,actor_id,venue_id,instruction_revision_id,request_id,action,outcome,reason_code,observed_at,correlation_id) VALUES(:i,:a,:v,:r,:q,:action,:o,:reason,:at,:c)").param("i",UUID.randomUUID()).param("a",actor,Types.OTHER).param("v",venue,Types.OTHER).param("r",revision,Types.OTHER).param("q",request,Types.OTHER).param("action",action).param("o",outcome).param("reason",reason).param("at",at.atOffset(ZoneOffset.UTC)).param("c",correlation).update();}
}
