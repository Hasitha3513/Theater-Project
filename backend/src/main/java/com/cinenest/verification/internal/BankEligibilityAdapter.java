package com.cinenest.verification.internal;
import com.cinenest.verification.BankEligibility;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
@Component
class BankEligibilityAdapter implements BankEligibility {
 private final JdbcClient jdbc;
 BankEligibilityAdapter(JdbcClient jdbc){this.jdbc=jdbc;}
 public boolean eligible(UUID actor,UUID quote,UUID venue){
  var ids=jdbc.sql("SELECT request_id FROM cinenest_verification.verification_request WHERE owner_id=:actor AND quote_id=:quote AND venue_id=:venue AND NOT superseded AND state='VERIFIED' AND valid_until>clock_timestamp() FOR UPDATE").param("actor",actor).param("quote",quote).param("venue",venue).query(UUID.class).list();
  return ids.stream().anyMatch(id->jdbc.sql("SELECT count(*) FROM cinenest_verification.verification_request WHERE request_id=:id AND state='VERIFIED' AND NOT superseded AND valid_until>clock_timestamp()").param("id",id).query(Integer.class).single()==1 && jdbc.sql("SELECT CASE WHEN count(*)>0 AND bool_and(state='READY' AND retain_until>clock_timestamp()) THEN 0 ELSE 1 END FROM cinenest_verification.identity_evidence WHERE request_id=:id").param("id",id).query(Integer.class).single()==0);
 }
}
