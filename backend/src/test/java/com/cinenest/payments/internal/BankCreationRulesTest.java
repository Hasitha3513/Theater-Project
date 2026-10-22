package com.cinenest.payments.internal;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BankCreationRulesTest {
 final Instant now=Instant.parse("2026-10-19T04:30:00Z"); // 10:00 Colombo
 BankPolicy.Policy policy(long lead,long request,long hold,LocalTime opens,LocalTime closes){return new BankPolicy.Policy("SYNTHETIC-TEST","test",UUID.randomUUID(),Duration.ofSeconds(lead),Duration.ofSeconds(request),Duration.ofSeconds(10),Duration.ofSeconds(20),Duration.ofSeconds(30),Duration.ofSeconds(hold),Duration.ofSeconds(40),Duration.ofSeconds(10),Duration.ofSeconds(60),Duration.ofSeconds(1),opens,closes,Set.of());}
 BankPolicy.Policy policy(){return policy(60,300,240,LocalTime.of(9,0),LocalTime.of(17,0));}
 Map<String,Object> snapshot(long offset){return new HashMap<>(Map.of("synthetic",true,"commercialIssuance",false,"currency","LKR","sessionStart",now.plusSeconds(offset).toString(),"timezone","Asia/Colombo"));}
 @Test void qualifiedSyntheticTermsAndExactTimes(){assertThat(BankCreationRules.refusal(policy(),snapshot(3600),now,true)).isEqualTo("ELIGIBLE");assertThat(BankCreationRules.times(policy(),now)).containsEntry("hold",now.plusSeconds(240)).containsEntry("expires",now.plusSeconds(300));}
 @Test void missingVerificationRefusesBeforeTerms(){assertThat(BankCreationRules.refusal(policy(),Map.of(),now,false)).isEqualTo("VERIFICATION_REQUIRED");}
 @Test void realCommercialOrForeignCurrencyTermsFailClosed(){for(var mutation:List.of(Map.of("synthetic",(Object)false),Map.of("commercialIssuance",(Object)true),Map.of("currency",(Object)"USD"))){var s=snapshot(3600);s.putAll(mutation);assertThat(BankCreationRules.refusal(policy(),s,now,true)).isEqualTo("SYNTHETIC_TERMS_REQUIRED");}}
 @Test void leadBoundaryIsExclusive(){assertThat(BankCreationRules.refusal(policy(),snapshot(60),now,true)).isEqualTo("LEAD_TIME_REQUIRED");}
 @Test void requestAndHoldDeadlinesMustPrecedeSession(){assertThat(BankCreationRules.refusal(policy(),snapshot(300),now,true)).isEqualTo("DEADLINES_EXCEED_SESSION_START");assertThat(BankCreationRules.refusal(policy(60,200,400,LocalTime.MIN,LocalTime.MAX),snapshot(400),now,true)).isEqualTo("DEADLINES_EXCEED_SESSION_START");}
 @Test void reviewWindowUsesVenueTimezoneAndExclusiveClose(){assertThat(BankCreationRules.refusal(policy(60,300,240,LocalTime.of(10,0),LocalTime.of(11,0)),snapshot(3600),now,true)).isEqualTo("ELIGIBLE");for(var p:List.of(policy(60,300,240,LocalTime.of(11,0),LocalTime.of(12,0)),policy(60,300,240,LocalTime.of(9,0),LocalTime.of(10,0))))assertThat(BankCreationRules.refusal(p,snapshot(3600),now,true)).isEqualTo("REVIEW_WINDOW_CLOSED");}
 // The real PostgreSQL clock has microsecond precision. Positive integration fixtures
 // must not accidentally close during its last representable second of a local day.
 @Test void positiveIntegrationReviewWindowCoversAllPostgresDayBoundaries()throws Exception{
  var annotation=Class.forName("com.cinenest.BankIntegrationTest",false,getClass().getClassLoader()).getAnnotation(org.springframework.boot.test.context.SpringBootTest.class);
  String close=Arrays.stream(annotation.properties()).filter(p->p.startsWith("cinenest.bank.review-closes=")).findFirst().orElseThrow().split("=",2)[1];
  var positive=policy(60,300,240,LocalTime.MIN,LocalTime.parse(close));
  for(String date:List.of("2024-02-29","2026-10-21","2035-12-31"))for(String zone:List.of("Asia/Colombo","UTC","America/New_York"))for(String time:List.of("00:00:00","12:00:00","23:59:58.999999","23:59:59","23:59:59.500000","23:59:59.999999")){
   Instant clock=LocalDateTime.parse(date+"T"+time).atZone(ZoneId.of(zone)).toInstant();
   var terms=new HashMap<>(snapshot(3600));terms.put("sessionStart",clock.plus(Duration.ofDays(2)).toString());terms.put("timezone",zone);
   assertThat(BankCreationRules.refusal(positive,terms,clock,true)).as("%s %s %s",date,time,zone).isEqualTo("ELIGIBLE");
  }
 }
 @Test void finalNanosecondCloseRemainsExclusiveAndOldFixtureGapIsReproducible(){
  Instant clock=LocalDate.of(2026,10,21).atTime(23,59,59,500000000).atZone(ZoneId.of("Asia/Colombo")).toInstant();
  var terms=new HashMap<>(snapshot(3600));terms.put("sessionStart",clock.plus(Duration.ofDays(2)).toString());
  assertThat(BankCreationRules.refusal(policy(60,300,240,LocalTime.MIN,LocalTime.of(23,59,59)),terms,clock,true)).isEqualTo("REVIEW_WINDOW_CLOSED");
  assertThat(BankCreationRules.refusal(policy(60,300,240,LocalTime.MIN,LocalTime.MAX),terms,clock,true)).isEqualTo("ELIGIBLE");
  Instant exactClose=LocalDate.of(2026,10,21).atTime(LocalTime.MAX).atZone(ZoneId.of("Asia/Colombo")).toInstant();
  assertThat(BankCreationRules.refusal(policy(60,300,240,LocalTime.MIN,LocalTime.MAX),terms,exactClose,true)).isEqualTo("REVIEW_WINDOW_CLOSED");
 }
}
