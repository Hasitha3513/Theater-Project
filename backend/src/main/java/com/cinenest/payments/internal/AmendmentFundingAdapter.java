package com.cinenest.payments.internal;
import com.cinenest.booking.*;
import com.cinenest.payments.AccessFunding;
import com.cinenest.verification.BankEligibility;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
@Component
class AmendmentFundingAdapter implements AmendmentGuards.Funding {
 private final AccessFunding funding;private final BankEligibility verification;private final JdbcClient jdbc;
 AmendmentFundingAdapter(AccessFunding funding,BankEligibility verification,JdbcClient jdbc){this.funding=funding;this.verification=verification;this.jdbc=jdbc;}
 public boolean eligible(BookingAccess.Context b){
  if(!funding.eligible(b.request(),b.owner(),b.venue(),b.amount(),b.currency())||!verification.eligible(b.owner(),b.quote(),b.venue()))return false;
  return jdbc.sql("SELECT NOT EXISTS(SELECT 1 FROM cinenest_payments.bank_review_decision WHERE request_id=:r AND action='DISPUTE') AND NOT EXISTS(SELECT 1 FROM cinenest_payments.bank_financial_exception WHERE request_id=:r AND state<>'RESOLVED' AND kind<>'EXCESS')").param("r",b.request()).query(Boolean.class).single();
 }
}
