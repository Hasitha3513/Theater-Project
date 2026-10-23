package com.cinenest.payments.internal;
import com.cinenest.booking.*;import com.cinenest.payments.AccessFunding;import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.stereotype.Component;
@Component
class CancellationFundingAdapter implements CancellationFunding {
 private final JdbcClient jdbc;private final AccessFunding funding;
 CancellationFundingAdapter(JdbcClient jdbc,AccessFunding funding){this.jdbc=jdbc;this.funding=funding;}
 public boolean eligible(BookingAccess.Context b){
  if(!funding.eligible(b.request(),b.owner(),b.venue(),b.amount(),b.currency()))return false;
  return jdbc.sql("SELECT NOT EXISTS(SELECT 1 FROM cinenest_payments.bank_review_decision WHERE request_id=:r AND action='DISPUTE') AND NOT EXISTS(SELECT 1 FROM cinenest_payments.bank_financial_exception WHERE request_id=:r AND state<>'RESOLVED' AND kind<>'EXCESS') AND NOT EXISTS(SELECT 1 FROM cinenest_payments.cancellation_refund_obligation WHERE booking_id=:b)").param("r",b.request()).param("b",b.booking()).query(Boolean.class).single();
 }
 public UUID reserve(BookingAccess.Context b,UUID cancellation){
  if(!eligible(b))throw new IllegalStateException("Financial review required");UUID id=UUID.randomUUID();
  jdbc.sql("INSERT INTO cinenest_payments.cancellation_refund_obligation VALUES(:id,:cancel,:b,:request,:owner,:venue,:amount,:currency,'RESERVED_REVIEW_REQUIRED',clock_timestamp())").param("id",id).param("cancel",cancellation).param("b",b.booking()).param("request",b.request()).param("owner",b.owner()).param("venue",b.venue()).param("amount",b.amount()).param("currency",b.currency()).update();
  int count=jdbc.sql("INSERT INTO cinenest_payments.cancellation_refund_source(obligation_id,allocation_id,amount_minor) SELECT :id,allocation_id,amount_minor FROM cinenest_payments.payment_allocation WHERE request_id=:r").param("id",id).param("r",b.request()).update();if(count<1)throw new IllegalStateException("Financial review required");return id;
 }
}
