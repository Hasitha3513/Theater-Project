package com.cinenest.payments.internal;
import com.cinenest.payments.AccessFunding;import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
@Component
class AccessFundingAdapter implements AccessFunding {
 private final JdbcClient jdbc;AccessFundingAdapter(JdbcClient jdbc){this.jdbc=jdbc;}
 public boolean eligible(UUID request,UUID owner,UUID venue,long due,String currency){
  if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Caller transaction required");
  var rs=jdbc.sql("SELECT request_id FROM cinenest_payments.bank_payment_request WHERE request_id=:r AND owner_id=:o AND venue_id=:v AND amount_minor=:due AND currency=:currency FOR UPDATE").param("r",request).param("o",owner).param("v",venue).param("due",due).param("currency",currency).query(UUID.class).list();if(rs.isEmpty())return false;
  jdbc.sql("SELECT c.credit_id FROM cinenest_payments.incoming_payment_credit c WHERE EXISTS(SELECT 1 FROM cinenest_payments.payment_allocation a WHERE a.credit_id=c.credit_id AND a.request_id=:r) ORDER BY c.credit_id FOR UPDATE").param("r",request).query(UUID.class).list();
  return jdbc.sql("""
   SELECT COALESCE(sum(a.amount_minor),0)=:due AND count(*)>0 AND bool_and(
    c.source_kind='SYNTHETIC_BANK' AND s.synthetic AND c.venue_id=:v AND c.currency=:currency
    AND v.status='SIMULATED_VERIFIED' AND v.verifier_id<>:o AND a.actor_id<>:o AND a.actor_id<>v.verifier_id
    AND NOT EXISTS(SELECT 1 FROM cinenest_payments.bank_credit_adjustment d WHERE d.credit_id=c.credit_id))
   FROM cinenest_payments.payment_allocation a JOIN cinenest_payments.incoming_payment_credit c USING(credit_id)
   JOIN cinenest_payments.bank_credit_verification v USING(credit_id) JOIN cinenest_payments.bank_credit_source_revision s USING(source_id)
   WHERE a.request_id=:r
   """).param("r",request).param("o",owner).param("v",venue).param("due",due).param("currency",currency).query(Boolean.class).single();
 }
}
