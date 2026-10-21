package com.cinenest.booking.internal;

import com.cinenest.rooms.OperationsBookingContext;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads only Booking-owned immutable purchases. No lock, command or foreign SQL. */
@Component
class OperationsBookingContextAdapter implements OperationsBookingContext {
 private final JdbcClient jdbc;
 OperationsBookingContextAdapter(JdbcClient jdbc){this.jdbc=jdbc;}
 public Optional<Context> inspect(UUID booking,UUID venue,Long revision){
  var sql=jdbc.sql("SELECT b.booking_id,b.venue_id,r.revision,r.snapshot FROM cinenest_booking.booking b JOIN cinenest_booking.booking_revision r USING(booking_id) WHERE b.booking_id=:b AND b.venue_id=:v AND b.synthetic AND b.state='SIMULATED_CONFIRMED'"+(revision==null?"":" AND r.revision=:revision")+" ORDER BY r.revision DESC LIMIT 1").param("b",booking).param("v",venue);
  if(revision!=null)sql.param("revision",revision);
  return sql.query((rs,n)->{
   try{
    var snapshot=JSONObjectUtils.parse(rs.getString("snapshot"));
    var start=Instant.parse(snapshot.get("sessionStart").toString());var end=Instant.parse(snapshot.get("sessionEnd").toString());
    String timezone=snapshot.get("timezone").toString();ZoneId.of(timezone);
    long version=rs.getLong("revision");if(version<1||!end.isAfter(start))throw new InvalidContext();
    return new Context(rs.getObject("booking_id",UUID.class),rs.getObject("venue_id",UUID.class),UUID.fromString(snapshot.get("roomId").toString()),version,start,end,timezone);
   }catch(Exception e){throw new InvalidContext();}
  }).optional();
 }
}
