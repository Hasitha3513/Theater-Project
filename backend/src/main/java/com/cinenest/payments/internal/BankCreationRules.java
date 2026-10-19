package com.cinenest.payments.internal;
import java.time.*;
import java.util.*;
/** Existing synthetic timing/terms rules shared by observation and creation. No side effects. */
final class BankCreationRules {
 private BankCreationRules(){}
 static String refusal(BankPolicy.Policy policy,Map<String,Object> snapshot,Instant now,boolean verified){
  if(!verified)return "VERIFICATION_REQUIRED";
  if(!Boolean.TRUE.equals(snapshot.get("synthetic"))||!Boolean.FALSE.equals(snapshot.get("commercialIssuance"))||!"LKR".equals(snapshot.get("currency")))return "SYNTHETIC_TERMS_REQUIRED";
  Instant start=Instant.parse(snapshot.get("sessionStart").toString());
  if(!start.isAfter(now.plus(policy.lead())))return "LEAD_TIME_REQUIRED";
  if(!start.isAfter(now.plus(policy.hold()))||!start.isAfter(now.plus(policy.request())))return "DEADLINES_EXCEED_SESSION_START";
  LocalTime local=now.atZone(ZoneId.of(snapshot.get("timezone").toString())).toLocalTime();
  if(local.isBefore(policy.opens())||!local.isBefore(policy.closes()))return "REVIEW_WINDOW_CLOSED";
  return "ELIGIBLE";
 }
 static Map<String,Instant> times(BankPolicy.Policy p,Instant now){return Map.of("expires",now.plus(p.request()),"initiate",now.plus(p.initiation()),"submit",now.plus(p.submission()),"review",now.plus(p.review()),"escalate",now.plus(p.escalation()),"hold",now.plus(p.hold()));}
}
