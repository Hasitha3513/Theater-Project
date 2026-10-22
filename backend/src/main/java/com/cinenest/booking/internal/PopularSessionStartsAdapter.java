package com.cinenest.booking.internal;
import com.cinenest.booking.PopularSessionStarts;
import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.rooms.VenueReportingClock;
import java.time.*;import java.time.temporal.ChronoUnit;import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;import org.springframework.transaction.support.TransactionTemplate;
@Component
class PopularSessionStartsAdapter implements PopularSessionStarts {
 private final JdbcClient jdbc;private final ScopedAuthorization identity;private final VenueReportingClock clocks;
 private final com.cinenest.rooms.InventoryConstraints rooms;
 private final TransactionTemplate snapshot,audit;
 PopularSessionStartsAdapter(JdbcClient jdbc,ScopedAuthorization identity,VenueReportingClock clocks,PlatformTransactionManager manager,com.cinenest.rooms.InventoryConstraints rooms){
  this.jdbc=jdbc;this.identity=identity;this.clocks=clocks;this.rooms=rooms;
  snapshot=new TransactionTemplate(manager);snapshot.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);snapshot.setTimeout(10);
  audit=new TransactionTemplate(manager);audit.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
 }
 private void permit(UUID actor,UUID venue){if(!identity.eligible(actor)||!identity.member(actor,venue,false)||!identity.permits(actor,venue,"report.booking.synthetic.read",false))throw new Unavailable(403);}
 public Snapshot read(UUID actor,UUID venue,UUID room,LocalDate from,LocalDate to,UUID correlation){
  try{
   // Returning from execute means the mandatory audit has COMMITTED, not merely inserted.
   Snapshot result=snapshot.execute(tx->{
    permit(actor,venue);if(from==null||to==null||from.getYear()<1||to.getYear()>9999||ChronoUnit.DAYS.between(from,to)<1||ChronoUnit.DAYS.between(from,to)>31)throw new Unavailable(400);
    String timezone=clocks.timezone(venue).orElseThrow(()->new Unavailable(404));ZoneId zone=ZoneId.of(timezone);
    Instant start=from.atStartOfDay(zone).toInstant(),end=to.atStartOfDay(zone).toInstant();
    if(!start.isBefore(end))throw new Unavailable(400);
    if(room!=null && !rooms.venue(room).filter(venue::equals).isPresent())throw new Unavailable(404);
    Instant observed=jdbc.sql("SELECT clock_timestamp()").query((rs,n)->rs.getTimestamp(1).toInstant()).single();
    // Latest revision is selected before any cohort or room filter. Invalid persisted facts cannot vanish into an empty aggregate.
    var rows=jdbc.sql("""
     WITH facts AS MATERIALIZED (
       SELECT r.snapshot->>'roomId' room,r.snapshot->>'sessionStart' start_text,
        COALESCE(jsonb_typeof(r.snapshot->'sessionStart')='string' AND
          r.snapshot->>'sessionStart' ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}([.][0-9]{1,9})?Z$' AND
          jsonb_typeof(r.snapshot->'roomId')='string' AND
          r.snapshot->>'roomId' ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',false) valid
       FROM cinenest_booking.booking b
       LEFT JOIN LATERAL (SELECT snapshot FROM cinenest_booking.booking_revision WHERE booking_id=b.booking_id ORDER BY revision DESC LIMIT 1) r ON TRUE
       WHERE b.venue_id=:venue AND b.synthetic=TRUE AND b.state='SIMULATED_CONFIRMED'
     ), starts AS MATERIALIZED (SELECT room,valid,CASE WHEN valid THEN start_text::timestamptz END started FROM facts)
     SELECT valid,CASE WHEN valid THEN EXTRACT(HOUR FROM started AT TIME ZONE :zone)::int END slot_hour,count(*) amount
     FROM starts WHERE NOT valid OR (started>=:start AND started<:end AND (:room IS NULL OR room=:room))
     GROUP BY 1,2 ORDER BY 1,2 LIMIT 26
     """).param("zone",timezone).param("venue",venue).param("start",java.sql.Timestamp.from(start)).param("end",java.sql.Timestamp.from(end)).param("room",room==null?null:room.toString(),java.sql.Types.VARCHAR).query().listOfRows();
    long[] counts=new long[24];long total=0;
    if(rows.size()>24)throw new Unavailable(503);
    for(var row:rows){if(!Boolean.TRUE.equals(row.get("valid"))||!(row.get("slot_hour") instanceof Number))throw new Unavailable(503);int hour=((Number)row.get("slot_hour")).intValue();long count=((Number)row.get("amount")).longValue();if(hour<0||hour>23||count<1||counts[hour]!=0)throw new Unavailable(503);counts[hour]=count;total=Math.addExact(total,count);}
    List<Bucket> buckets=new ArrayList<>();for(int hour=0;hour<24;hour++)buckets.add(new Bucket(String.format(java.util.Locale.ROOT,"H%02d",hour),hour,hour+1,Long.toString(counts[hour])));
    var resultSnapshot=new Snapshot("CN-M-POPULAR-TIME-SLOTS",VERSION,BANDS,venue,room,from,to,timezone,observed,"LATEST_COMMITTED_PURCHASED_REVISION","sessionStart",true,true,Long.toString(total),List.copyOf(buckets));
    insert(actor,venue,room,from,to,timezone,observed,correlation,"ALLOW",total==0?"EMPTY":"SCOPED_READ");return resultSnapshot;
   });
   // Fresh transaction/clock after the snapshot and its audit commit; never positive-cache authority.
   audit.executeWithoutResult(tx->permit(actor,venue));return result;
  }catch(Unavailable e){denied(actor,venue,correlation,e.status==403?"ACCESS_DENIED":e.status==400?"INVALID_REQUEST":e.status==404?"NOT_FOUND":"UNAVAILABLE");throw e;}
  catch(RuntimeException e){try{denied(actor,venue,correlation,"UNAVAILABLE");}catch(RuntimeException ignored){/* fail closed; no report/log fallback */}var failure=new Unavailable(503);failure.initCause(e);throw failure;}
 }
 public void denied(UUID actor,UUID venue,UUID correlation,String reason){try{audit.executeWithoutResult(tx->insert(actor,venue,null,null,null,null,Instant.now(),correlation,"DENY",reason));}catch(RuntimeException e){throw new Unavailable(503);}}
 private void insert(UUID actor,UUID venue,UUID room,LocalDate from,LocalDate to,String zone,Instant observed,UUID correlation,String outcome,String reason){
  jdbc.sql("INSERT INTO cinenest_booking.popular_session_starts_read_audit VALUES(:id,:actor,:venue,:room,:from,:to,:zone,:observed,:version,:bands,:outcome,:reason,:correlation)")
   .param("id",UUID.randomUUID()).param("actor",actor).param("venue",venue,java.sql.Types.OTHER).param("room",room,java.sql.Types.OTHER).param("from",from,java.sql.Types.DATE).param("to",to,java.sql.Types.DATE).param("zone",zone,java.sql.Types.VARCHAR).param("observed",java.sql.Timestamp.from(observed)).param("version",VERSION).param("bands",BANDS).param("outcome",outcome).param("reason",reason).param("correlation",correlation).update();
 }
}
