package com.cinenest.booking.internal;
import com.cinenest.booking.BankRequestHolds;
import com.cinenest.booking.QuotationHolds;
import java.time.*;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
@Component
class BankRequestHoldAdapter implements BankRequestHolds {
 private final QuotationHolds holds;private final JdbcClient jdbc;
 BankRequestHoldAdapter(QuotationHolds holds,JdbcClient jdbc){this.holds=holds;this.jdbc=jdbc;}
 public State convert(UUID actor,UUID id,long revision,UUID request,Instant expires){
  if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Caller transaction required");
  var h=holds.lockAndInspect(actor,id).orElseThrow(()->new Rejected());
  Instant now=jdbc.sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single().toInstant();
  if(!h.state().equals("ACTIVE")||h.revision()!=revision||!h.expires().isAfter(now)||!expires.isAfter(now)||!expires.isBefore(h.start()))throw new Rejected();
  jdbc.sql("INSERT INTO cinenest_booking.booking_bank_request_hold(request_id,hold_id,owner_id,venue_id,original_expires_at,expires_at) VALUES(:request,:hold,:actor,:venue,:original,:expires)").param("request",request).param("hold",id).param("actor",actor).param("venue",h.venue()).param("original",h.expires().atOffset(ZoneOffset.UTC)).param("expires",expires.atOffset(ZoneOffset.UTC)).update();
  if(jdbc.sql("UPDATE cinenest_booking.reservation_hold SET expires_at=:expires,revision=revision+1 WHERE hold_id=:id AND revision=:revision AND state='ACTIVE'").param("expires",expires.atOffset(ZoneOffset.UTC)).param("id",id).param("revision",revision).update()!=1)throw new Rejected();
  return new State("ACTIVE",expires);
 }
 public State inspect(UUID actor,UUID id){var h=holds.lockAndInspect(actor,id).orElseThrow(()->new Rejected());return new State(h.state(),h.expires());}
}
