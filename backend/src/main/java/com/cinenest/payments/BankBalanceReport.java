package com.cinenest.payments;
import java.time.Instant;import java.util.*;
/** Payments-owned synthetic facts. Exact minor units are serialized as decimal strings. */
public interface BankBalanceReport {
 record Query(UUID venue,String currency,Instant from,Instant to,int page,int size) {}
 record Amounts(String c,String a,String t,String r,String u,String f,String available,String unavailable) {}
 record ExceptionFact(String kind,String state) {}
 record Row(UUID creditId,Instant creditedAt,Instant verifiedAt,Amounts amounts,String eligibility,List<ExceptionFact> exceptions,boolean creditDrilldown) {}
 record Snapshot(Instant snapshotAt,String metricVersion,long total,Amounts summary,List<Row> rows) {}
 Snapshot snapshot(UUID actor,Query query);
 void denied(UUID actor,UUID venue);
}
