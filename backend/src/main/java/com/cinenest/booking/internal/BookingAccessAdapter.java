package com.cinenest.booking.internal;
import com.cinenest.booking.BookingAccess;import java.util.*;import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
@Component
class BookingAccessAdapter implements BookingAccess {
 private final JdbcClient jdbc;BookingAccessAdapter(JdbcClient jdbc){this.jdbc=jdbc;}
 public boolean amendmentPending(UUID booking){return jdbc.sql("SELECT EXISTS(SELECT 1 FROM cinenest_booking.booking_amendment WHERE booking_id=:b AND state='PROPOSED' AND expires_at>clock_timestamp())").param("b",booking).query(Boolean.class).single();}
 public Optional<Context> lock(UUID id){
  if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Caller transaction required");
  var rows=jdbc.sql("SELECT b.*,r.revision,r.quote_id,r.amount_minor,r.currency,r.snapshot FROM cinenest_booking.booking b JOIN cinenest_booking.booking_revision r USING(booking_id) WHERE booking_id=:id AND b.synthetic AND b.state='SIMULATED_CONFIRMED' ORDER BY r.revision DESC LIMIT 1 FOR UPDATE OF b").param("id",id).query().listOfRows();
  if(rows.isEmpty())return Optional.empty();var b=rows.getFirst();
  try{var s=com.nimbusds.jose.util.JSONObjectUtils.parse(b.get("snapshot").toString());return Optional.of(new Context(id,(UUID)b.get("request_id"),(UUID)b.get("owner_id"),(UUID)b.get("venue_id"),UUID.fromString(s.get("roomId").toString()),(UUID)b.get("quote_id"),((Number)b.get("revision")).longValue(),((Number)b.get("amount_minor")).longValue(),b.get("currency").toString(),Instant.parse(s.get("sessionStart").toString()),Instant.parse(s.get("sessionEnd").toString()),s.get("timezone").toString()));}catch(Exception e){throw new IllegalStateException("Purchased synthetic context invalid");}
 }
}
