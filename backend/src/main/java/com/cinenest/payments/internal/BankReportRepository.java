package com.cinenest.payments.internal;
import com.cinenest.payments.BankBalanceReport;import java.util.*;import java.time.Instant;
interface BankReportRepository {
 Instant snapshotTime();
 Map<String,Object> summary(BankBalanceReport.Query query);
 List<Map<String,Object>> rows(BankBalanceReport.Query query);
 List<BankBalanceReport.ExceptionFact> exceptions(UUID credit);
 void denied(UUID actor,UUID venue);
 void audit(UUID actor,UUID venue,String currency,Instant snapshot);
}
