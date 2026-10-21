package com.cinenest.booking.internal;

import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.rooms.InventoryConstraints;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Booking-owned, bounded observation. Never invokes reconciliation or acquires domain locks. */
@Service
class InventoryCalendar {
 private final JdbcClient jdbc;private final InventoryConstraints rooms;private final ScopedAuthorization auth;private final TransactionTemplate snapshot;
 InventoryCalendar(JdbcClient jdbc,InventoryConstraints rooms,ScopedAuthorization auth,PlatformTransactionManager manager){
  this.jdbc=jdbc;this.rooms=rooms;this.auth=auth;snapshot=new TransactionTemplate(manager);snapshot.setReadOnly(true);snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);snapshot.setTimeout(10);
 }
 static InventoryFailure unavailable(){return new InventoryFailure(503,"Calendar unavailable");}
 private void eligible(UUID actor){if(!auth.eligible(actor,false))throw new InventoryFailure(403,"Access denied");}
 record Block(Instant start,Instant until,String kind,boolean pending){}
 byte[] read(UUID actor,UUID room,LocalDate date,int duration,int guests){
  if(duration<1||duration>1440||guests<1)throw InventoryRules.invalid();eligible(actor);
  byte[] result=snapshot.execute(status->observe(room,date,duration,guests));
  // Outside the repeatable-read snapshot: suspension during observation cannot reuse snapshot eligibility.
  eligible(actor);return result;
 }
 private byte[] observe(UUID room,LocalDate date,int duration,int guests){
  Instant now=jdbc.sql("SELECT statement_timestamp()").query(OffsetDateTime.class).single().toInstant();
  var f=rooms.observe(room).orElseThrow(()->new InventoryFailure(404,"Room not found"));
  if(!f.enabled()||f.bufferMinutes()==null||f.windows().isEmpty())throw new InventoryFailure(404,"Room not found");
  if(f.minimums().isEmpty()||f.bufferMinutes()<0)throw new InventoryFailure(409,"Room configuration unavailable");
  if(guests>f.capacity())throw InventoryRules.invalid();
  ZoneId zone;Instant from,to;try{zone=ZoneId.of(f.timezone());from=date.atStartOfDay(zone).toInstant();to=date.plusDays(1).atStartOfDay(zone).toInstant().plusSeconds(((long)duration+f.bufferMinutes())*60);}catch(DateTimeException|ArithmeticException e){throw InventoryRules.invalid();}
  var blocks=blocks(room,f,from,to,now);
  var items=new ArrayList<Map<String,Object>>(1440);
  for(int minute=0;minute<1440;minute++){
   String local=date.atStartOfDay().plusMinutes(minute).toString();InventoryRules.Candidate c=null;Boolean available=null;var reasons=new TreeSet<String>();
   Instant start=null;
   try{start=InventoryRules.local(local,f.timezone());}catch(InventoryFailure invalid){reasons.add("INVALID_LOCAL_TIME");}
   if(start!=null){
    try{c=InventoryRules.candidate(f,start,duration,guests);}catch(InventoryFailure invalid){
     if("CONFIGURATION_UNAVAILABLE".equals(invalid.reason))throw new InventoryFailure(409,"Room configuration unavailable");
     if(invalid.reason!=null){available=false;reasons.add(invalid.reason);}else reasons.add("INVALID_LOCAL_TIME");
    }
    if(c!=null){
     if(!start.isAfter(now)){available=false;reasons.add("PAST_START");}
     else{
      boolean known=false,pending=false;
      for(var b:blocks)if(start.isBefore(b.until())&&b.start().isBefore(c.blockedUntil())){
       if(b.pending()){pending=true;reasons.add("PENDING_RECONCILIATION");}
       else{known=true;reasons.add(switch(b.kind()){case "MAINTENANCE"->"MAINTENANCE_BLOCK";case "CONFIRMED"->"UNAVAILABLE";default->"TEMPORARILY_HELD";});}
      }
      available=known?Boolean.FALSE:pending?null:Boolean.TRUE;
     }
    }
   }
   var item=new LinkedHashMap<String,Object>();item.put("startLocal",local);item.put("sessionStart",c==null?null:c.start().toString());item.put("sessionEnd",c==null?null:c.end().toString());item.put("blockedUntil",c==null?null:c.blockedUntil().toString());item.put("available",available);item.put("reasons",List.copyOf(reasons));items.add(item);
  }
  var dto=new LinkedHashMap<String,Object>();dto.put("roomId",room.toString());dto.put("venueId",f.venueId().toString());dto.put("date",date.toString());dto.put("timezone",f.timezone());dto.put("durationMinutes",duration);dto.put("guests",guests);dto.put("configuration",f.configuration());dto.put("observedAt",now.toString());dto.put("evidenceLevel","INVENTORY_ONLY");dto.put("complete",true);dto.put("items",items);
  return encode(dto);
 }
 static byte[] encode(Map<String,Object> dto){byte[] bytes=JSONObjectUtils.toJSONString(dto).getBytes(StandardCharsets.UTF_8);if(bytes.length>512*1024)throw unavailable();return bytes;}
 private List<Block> blocks(UUID room,InventoryConstraints.Facts f,Instant from,Instant to,Instant now){
  var rows=jdbc.sql("""
   SELECT b.*,h.hold_id,h.state AS hold_state,h.expires_at,h.guests,h.timezone
   FROM cinenest_booking.inventory_block b LEFT JOIN cinenest_booking.reservation_hold h USING(block_id)
   WHERE b.room_id=:r AND b.blocking
     AND tstzrange(b.session_start,b.blocked_until,'[)') && tstzrange(CAST(:start AS timestamptz),CAST(:until AS timestamptz),'[)')
   ORDER BY b.session_start,b.block_id LIMIT 1001
   """).param("r",room).param("start",from.atOffset(ZoneOffset.UTC)).param("until",to.atOffset(ZoneOffset.UTC)).query().listOfRows();
  if(rows.size()>1000)throw unavailable();var result=new ArrayList<Block>();
  for(var row:rows){
   if(!f.venueId().equals(row.get("venue_id")))throw unavailable();
   Instant start=instant(row.get("session_start")),end=instant(row.get("session_end")),until=instant(row.get("blocked_until"));String kind=Objects.toString(row.get("kind"),"");boolean pending=false;
   if(!end.isAfter(start)||until.isBefore(end)||!Set.of("HOLD","CONFIRMED","MAINTENANCE").contains(kind))throw unavailable();
   if(kind.equals("HOLD")){
    if(row.get("hold_id")==null||!"ACTIVE".equals(row.get("hold_state"))||!(row.get("guests") instanceof Number n))throw unavailable();
    Instant expiry=instant(row.get("expires_at"));pending=!now.isBefore(expiry);
    if(!pending)try{
     var c=InventoryRules.candidate(f,start,Math.toIntExact(Duration.between(start,end).toMinutes()),n.intValue());pending=!f.timezone().equals(row.get("timezone"))||!c.blockedUntil().equals(until);
    }catch(InventoryFailure invalid){pending=true;}catch(ArithmeticException invalid){throw unavailable();}
   }
   result.add(new Block(start,until,kind,pending));
  }return result;
 }
 private static Instant instant(Object value){if(value instanceof java.sql.Timestamp t)return t.toInstant();if(value instanceof OffsetDateTime t)return t.toInstant();throw unavailable();}
}
