package com.cinenest.booking.internal;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcStaffBookingReadStore implements StaffBookingReadStore {
 private final JdbcClient jdbc;
 JdbcStaffBookingReadStore(JdbcClient jdbc){this.jdbc=jdbc;}
 private static final String SELECT="""
 SELECT b.booking_id,b.venue_id,b.state,r.revision,
 (r.snapshot->>'roomId')::uuid AS room_id,
 (r.snapshot->>'sessionStart')::timestamptz AS scheduled_start,
 (r.snapshot->>'sessionEnd')::timestamptz AS scheduled_end,
 r.snapshot->>'timezone' AS timezone,r.snapshot->'package'->>'name' AS package_name,
 (r.snapshot->>'guests')::integer AS guests
 FROM cinenest_booking.booking b
 JOIN LATERAL (SELECT revision,snapshot FROM cinenest_booking.booking_revision
   WHERE booking_id=b.booking_id ORDER BY revision DESC LIMIT 1) r ON TRUE
 WHERE b.venue_id=:venue
 """;
 private Item item(java.sql.ResultSet row,int n)throws java.sql.SQLException{
  String state=row.getString("state"),zone=row.getString("timezone"),name=row.getString("package_name");
  int guests=row.getInt("guests");long revision=row.getLong("revision");
  if(!"SIMULATED_CONFIRMED".equals(state)||guests<1||revision<1||name==null)throw new IllegalStateException("Invalid purchased context");
  ZoneId.of(zone);
  return new Item(row.getObject("booking_id",UUID.class),row.getObject("venue_id",UUID.class),row.getObject("room_id",UUID.class),revision,state,row.getTimestamp("scheduled_start").toInstant(),row.getTimestamp("scheduled_end").toInstant(),zone,name,guests,true,false);
 }
 public List<Item> list(Query q){
  String sql=SELECT;
  if(q.reference()!=null)sql+=" AND b.booking_id=:reference";
  if(q.from()!=null)sql+=" AND (r.snapshot->>'sessionStart')::timestamptz>=:from AND (r.snapshot->>'sessionStart')::timestamptz<:to";
  sql+=" ORDER BY scheduled_start DESC,b.booking_id DESC LIMIT :limit OFFSET :offset";
  var request=jdbc.sql(sql).param("venue",q.venue()).param("limit",q.size()+1).param("offset",q.page()*q.size());
  if(q.reference()!=null)request=request.param("reference",q.reference());
  if(q.from()!=null)request=request.param("from",StaffBookingReadRules.databaseBoundary(q.from()).atOffset(ZoneOffset.UTC)).param("to",StaffBookingReadRules.databaseBoundary(q.to()).atOffset(ZoneOffset.UTC));
  return request.query(this::item).list();
 }
 public Optional<Item> detail(UUID venue,UUID booking){return jdbc.sql(SELECT+" AND b.booking_id=:booking").param("venue",venue).param("booking",booking).query(this::item).optional();}
 public Instant now(){return jdbc.sql("SELECT clock_timestamp()").query((rs,n)->rs.getTimestamp(1).toInstant()).single();}
 public void audit(UUID actor,UUID venue,UUID booking,String action,String outcome,String reason,UUID correlation){
  jdbc.sql("INSERT INTO cinenest_booking.staff_booking_read_audit(event_id,actor_id,venue_id,requested_booking_id,action,outcome,reason_code,correlation_id) VALUES(:id,:actor,:venue,:booking,:action,:outcome,:reason,:correlation)")
   .param("id",UUID.randomUUID()).param("actor",actor,java.sql.Types.OTHER).param("venue",venue,java.sql.Types.OTHER).param("booking",booking,java.sql.Types.OTHER).param("action",action).param("outcome",outcome).param("reason",reason).param("correlation",correlation).update();
 }
}
