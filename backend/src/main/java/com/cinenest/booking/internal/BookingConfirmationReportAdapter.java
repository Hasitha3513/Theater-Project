package com.cinenest.booking.internal;
import com.cinenest.booking.BookingConfirmationReport;
import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.rooms.VenueReportingClock;
import java.time.*;import java.time.temporal.ChronoUnit;import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;import org.springframework.transaction.support.TransactionTemplate;
@Component
class BookingConfirmationReportAdapter implements BookingConfirmationReport {
 private final JdbcClient jdbc;private final ScopedAuthorization identity;private final VenueReportingClock clocks;
 private final TransactionTemplate snapshot,audit;
 BookingConfirmationReportAdapter(JdbcClient jdbc,ScopedAuthorization identity,VenueReportingClock clocks,PlatformTransactionManager manager){
  this.jdbc=jdbc;this.identity=identity;this.clocks=clocks;
  snapshot=new TransactionTemplate(manager);snapshot.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
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
    Instant observed=jdbc.sql("SELECT clock_timestamp()").query((rs,n)->rs.getTimestamp(1).toInstant()).single();
    var rows=jdbc.sql("""
     SELECT (b.confirmed_at AT TIME ZONE :zone)::date AS observed_day,r.snapshot->>'roomId' room,count(*) amount
     FROM cinenest_booking.booking b
     LEFT JOIN LATERAL (SELECT snapshot FROM cinenest_booking.booking_revision WHERE booking_id=b.booking_id ORDER BY revision DESC LIMIT 1) r ON TRUE
     WHERE b.venue_id=:venue AND b.synthetic=TRUE AND b.state='SIMULATED_CONFIRMED'
       AND b.confirmed_at>=:start AND b.confirmed_at<:end
       AND (:room IS NULL OR r.snapshot->>'roomId'=:room OR r.snapshot->>'roomId' IS NULL)
     GROUP BY 1,2 ORDER BY 1,2 LIMIT 31001
     """).param("zone",timezone).param("venue",venue).param("start",java.sql.Timestamp.from(start)).param("end",java.sql.Timestamp.from(end)).param("room",room==null?null:room.toString(),java.sql.Types.VARCHAR).query().listOfRows();
    if(rows.size()>31000)throw new Unavailable(503);
    Map<LocalDate,Long> days=new TreeMap<>();for(LocalDate d=from;d.isBefore(to);d=d.plusDays(1))days.put(d,0L);
    Map<UUID,Long> rooms=new TreeMap<>();long total=0;
    for(var row:rows){LocalDate day=((java.sql.Date)row.get("observed_day")).toLocalDate();UUID id=UUID.fromString(Objects.toString(row.get("room")));long count=((Number)row.get("amount")).longValue();if(!days.containsKey(day)||count<1)throw new Unavailable(503);days.put(day,Math.addExact(days.get(day),count));rooms.merge(id,count,Math::addExact);total=Math.addExact(total,count);}
    if(rooms.size()>1000)throw new Unavailable(503);
    var resultSnapshot=new Snapshot(venue,room,from,to,timezone,observed,VERSION,"confirmed_at","SIMULATED_CONFIRMED",true,true,"NOT_AVAILABLE",Long.toString(total),days.entrySet().stream().map(e->new Day(e.getKey(),e.getValue().toString())).toList(),rooms.entrySet().stream().map(e->new Room(e.getKey(),e.getValue().toString())).toList());
    insert(actor,venue,room,from,to,timezone,observed,correlation,"ALLOW",total==0?"EMPTY":"SCOPED_READ");return resultSnapshot;
   });
   // Fresh transaction/clock after the snapshot and its audit commit; never positive-cache authority.
   audit.executeWithoutResult(tx->permit(actor,venue));return result;
  }catch(Unavailable e){denied(actor,venue,correlation,e.status==403?"ACCESS_DENIED":e.status==400?"INVALID_REQUEST":e.status==404?"NOT_FOUND":"UNAVAILABLE");throw e;}
  catch(RuntimeException e){try{denied(actor,venue,correlation,"UNAVAILABLE");}catch(RuntimeException ignored){/* fail closed; no report/log fallback */}var failure=new Unavailable(503);failure.initCause(e);throw failure;}
 }
 public void denied(UUID actor,UUID venue,UUID correlation,String reason){try{audit.executeWithoutResult(tx->insert(actor,venue,null,null,null,null,Instant.now(),correlation,"DENY",reason));}catch(RuntimeException e){throw new Unavailable(503);}}
 private void insert(UUID actor,UUID venue,UUID room,LocalDate from,LocalDate to,String zone,Instant observed,UUID correlation,String outcome,String reason){
  jdbc.sql("INSERT INTO cinenest_booking.booking_confirmation_report_read_audit VALUES(:id,:actor,:venue,:room,:from,:to,:zone,:observed,:version,:outcome,:reason,:correlation)")
   .param("id",UUID.randomUUID()).param("actor",actor).param("venue",venue,java.sql.Types.OTHER).param("room",room,java.sql.Types.OTHER).param("from",from,java.sql.Types.DATE).param("to",to,java.sql.Types.DATE).param("zone",zone,java.sql.Types.VARCHAR).param("observed",java.sql.Timestamp.from(observed)).param("version",VERSION).param("outcome",outcome).param("reason",reason).param("correlation",correlation).update();
 }
}
