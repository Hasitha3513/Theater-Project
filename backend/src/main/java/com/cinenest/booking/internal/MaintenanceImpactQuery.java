package com.cinenest.booking.internal;

import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.rooms.InventoryConstraints;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.MultiValueMap;

/** Observational inventory preview. No reconciliation, domain locks or inventory writes. */
@Service
class MaintenanceImpactQuery {
 record Counts(int activeHolds,int expiredPendingHolds,int invalidatablePendingHolds,int confirmedBlocks,int maintenanceBlocks){}
 record View(UUID venueId,UUID roomId,String timezone,String configuration,Instant requestedStart,Instant requestedEnd,Instant observedAt,
             boolean complete,int recordLimit,Counts counts,String incompleteReason,boolean synthetic,String evidenceLevel,String occupancy,String readiness){}
 record Result(int status,Object body){}
 private final JdbcClient jdbc;private final InventoryConstraints rooms;private final ScopedAuthorization auth;private final TransactionTemplate snapshot,audit;
 MaintenanceImpactQuery(JdbcClient jdbc,InventoryConstraints rooms,ScopedAuthorization auth,PlatformTransactionManager manager){
  this.jdbc=jdbc;this.rooms=rooms;this.auth=auth;snapshot=new TransactionTemplate(manager);snapshot.setReadOnly(true);snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);snapshot.setTimeout(10);audit=new TransactionTemplate(manager);audit.setTimeout(10);
 }
 private boolean permitted(UUID actor,UUID venue,boolean lock){return auth.permits(actor,venue,"inventory.read",lock)&&auth.member(actor,venue,lock);}
 Result read(UUID actor,String rawVenue,String rawRoom,MultiValueMap<String,String> params,UUID correlation){
  UUID venue=null,room=null;View observed=null;boolean authorized=false;
  try{
   venue=InventoryRules.id(rawVenue);room=InventoryRules.id(rawRoom);
   if(!permitted(actor,venue,false))throw new InventoryFailure(403,"Access denied");authorized=true;
   if(!params.keySet().equals(Set.of("startLocal","endLocal"))||params.values().stream().anyMatch(v->v.size()!=1)||params.values().stream().flatMap(List::stream).anyMatch(v->v==null||!v.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}")))throw InventoryRules.invalid();
   final UUID v=venue,r=room;observed=snapshot.execute(status->observe(v,r,params.getFirst("startLocal"),params.getFirst("endLocal")));
   final View result=observed;
   // Fresh authorization outside the RR snapshot. Disclosure follows successful audit commit.
   return audit.execute(status->{
    if(!permitted(actor,v,true)){insert(actor,null,null,null,"DENY","ACCESS_DENIED",correlation);return new Result(403,null);}
    insert(actor,v,r,result,"ALLOW",result.complete()?"SCOPED_READ":"INCOMPLETE",correlation);return new Result(200,result);
   });
  }catch(RuntimeException e){
   int code=e instanceof InventoryFailure f?f.status:e instanceof DateTimeException||e instanceof IllegalArgumentException?400:503;
   String reason=switch(code){case 400->"INVALID_REQUEST";case 403->"ACCESS_DENIED";case 404->"NOT_FOUND";default->"UNAVAILABLE";};
   final UUID v=authorized?venue:null,r=authorized?room:null;final View fact=observed;
   try{audit.executeWithoutResult(status->insert(actor,v,r,fact,"DENY",reason,correlation));}catch(RuntimeException failure){return new Result(503,null);}
   return new Result(code,null);
  }
 }
 private View observe(UUID venue,UUID room,String startLocal,String endLocal){
  Instant at=jdbc.sql("SELECT statement_timestamp()").query(OffsetDateTime.class).single().toInstant();
  var f=rooms.observe(room).orElseThrow(()->new InventoryFailure(404,"Room unavailable"));if(!f.venueId().equals(venue))throw new InventoryFailure(404,"Room unavailable");
  try{ZoneId.of(f.timezone());}catch(DateTimeException invalid){throw new InventoryFailure(409,"Room timezone unavailable");}
  Instant start=InventoryRules.local(startLocal,f.timezone()),end=InventoryRules.local(endLocal,f.timezone());
  // Same single venue-local-day read envelope as the calendar; not a closure-duration policy.
  LocalDateTime local=LocalDateTime.parse(startLocal),finish=LocalDateTime.parse(endLocal);
  if(!end.isAfter(start)||finish.isAfter(local.toLocalDate().plusDays(1).atStartOfDay()))throw InventoryRules.invalid();
  var rows=jdbc.sql("""
   SELECT b.*,h.hold_id,h.state AS hold_state,h.expires_at,h.guests,h.timezone AS hold_timezone,to_jsonb(e) AS extension_record
   FROM cinenest_booking.inventory_block b LEFT JOIN cinenest_booking.reservation_hold h USING(block_id) LEFT JOIN cinenest_booking.booking_extension_reservation e ON e.tail_block=b.block_id
   WHERE b.room_id=:r AND b.blocking
    AND tstzrange(b.session_start,b.blocked_until,'[)') && tstzrange(CAST(:start AS timestamptz),CAST(:end AS timestamptz),'[)')
   ORDER BY b.session_start,b.block_id LIMIT 1001
   """).param("r",room).param("start",start.atOffset(ZoneOffset.UTC)).param("end",end.atOffset(ZoneOffset.UTC)).query().listOfRows();
  boolean complete=rows.size()<=1000;int active=0,expired=0,invalid=0,confirmed=0,maintenance=0;
  if(complete)for(var row:rows){
   if(!venue.equals(row.get("venue_id")))throw new IllegalStateException("Inconsistent inventory scope");
   Instant from=instant(row.get("session_start")),to=instant(row.get("session_end")),until=instant(row.get("blocked_until"));
   if(!to.isAfter(from)||until.isBefore(to))throw new IllegalStateException("Invalid inventory interval");
   switch(Objects.toString(row.get("kind"),"")){
    case "EXTENSION_HOLD"->{var e=ExtensionInventoryObservation.record(row);if(ExtensionInventoryObservation.expired(e,at))expired++;else if(!ExtensionReservationService.valid(e,f))invalid++;else active++;}
    case "CONFIRMED"->confirmed++;
    case "MAINTENANCE"->maintenance++;
    case "HOLD"->{
     if(row.get("hold_id")==null||!"ACTIVE".equals(row.get("hold_state"))||!(row.get("guests") instanceof Number guests))throw new IllegalStateException("Invalid blocking hold");
     if(!at.isBefore(instant(row.get("expires_at"))))expired++;
     else{
      boolean valid;
      try{var candidate=InventoryRules.candidate(f,from,Math.toIntExact(Duration.between(from,to).toMinutes()),guests.intValue());valid=f.timezone().equals(row.get("hold_timezone"))&&candidate.blockedUntil().equals(until);}catch(InventoryFailure failure){valid=false;}
      if(valid)active++;else invalid++;
     }
    }
    default->throw new IllegalStateException("Unknown inventory kind");
   }
  }
  return new View(venue,room,f.timezone(),f.configuration(),start,end,at,complete,1000,complete?new Counts(active,expired,invalid,confirmed,maintenance):null,complete?null:"RECORD_LIMIT",true,"INVENTORY_ONLY","UNKNOWN","UNVERIFIED");
 }
 private static Instant instant(Object value){if(value instanceof java.sql.Timestamp t)return t.toInstant();if(value instanceof OffsetDateTime t)return t.toInstant();throw new IllegalStateException("Invalid inventory timestamp");}
 private void insert(UUID actor,UUID venue,UUID room,View observed,String outcome,String reason,UUID correlation){
  jdbc.sql("INSERT INTO cinenest_booking.maintenance_impact_read_audit VALUES(:id,:a,:v,:r,:start,:end,:at,:outcome,:reason,:correlation)")
   .param("id",UUID.randomUUID()).param("a",actor,java.sql.Types.OTHER).param("v",venue,java.sql.Types.OTHER).param("r",room,java.sql.Types.OTHER)
   .param("start",observed==null?null:observed.requestedStart().atOffset(ZoneOffset.UTC),java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
   .param("end",observed==null?null:observed.requestedEnd().atOffset(ZoneOffset.UTC),java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
   .param("at",observed==null?OffsetDateTime.now(ZoneOffset.UTC):observed.observedAt().atOffset(ZoneOffset.UTC))
   .param("outcome",outcome).param("reason",reason).param("correlation",correlation).update();
 }
}
