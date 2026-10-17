package com.cinenest.payments.internal;

import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Closed synthetic source-to-payer mapping, never a receiving bank account or client destination. */
@Component
class RefundPolicy {
 // Explicit fixture beneficiary association, independent of the booker and receiving account.
 private static final UUID PAYER=UUID.nameUUIDFromBytes("customer-a".getBytes(java.nio.charset.StandardCharsets.UTF_8));
 UUID originalPayer(String source){destination(source);return PAYER;}
 private final Environment env;private final SyntheticCreditSource credits;
 RefundPolicy(Environment env,SyntheticCreditSource credits){this.env=env;this.credits=credits;}
 long ceiling(){credits.configured();try{if(!env.getProperty("cinenest.bank.refunds.enabled",Boolean.class,false))throw new IllegalArgumentException();long limit=Long.parseLong(env.getRequiredProperty("cinenest.bank.refunds.maximum-minor"));if(limit<=0)throw new IllegalArgumentException();return limit;}catch(RuntimeException e){throw new BankFailure(503,"Synthetic refund policy unavailable");}}
 String destination(String source){return switch(source){case "SYNTHETIC-BANK-100"->"SYNTHETIC-PAYER-100";case "SYNTHETIC-BANK-200"->"SYNTHETIC-PAYER-200";case "SYNTHETIC-BANK-10000"->"SYNTHETIC-PAYER-10000";case "SYNTHETIC-BANK-50000"->"SYNTHETIC-PAYER-50000";default->throw new BankFailure(400,"Controlled original-payer fixture required");};}
 Map<String,Object> snapshot(){return Map.of("version","SYNTHETIC-REFUND-V1","synthetic",true,"realTransfers",false,"maximumMinor",Long.toString(ceiling()),"deductionMinor","0","destinationMapping","CLOSED_BANK_TO_ORIGINAL_PAYER_V1","originalPayerAccountId",PAYER.toString());}
 String hash(){return BankPolicy.hash(PAYER+":SYNTHETIC-REFUND-V1:NO_DEDUCTIONS:"+ceiling()+":BANK100=PAYER100;BANK200=PAYER200;BANK10000=PAYER10000;BANK50000=PAYER50000");}
 static long amount(Object value){if(!(value instanceof String s)||!s.matches("[1-9][0-9]{0,18}"))throw new BankFailure(400,"Positive exact minor-unit string required");try{return Long.parseLong(s);}catch(NumberFormatException e){throw new BankFailure(400,"Amount overflow");}}
 static String outcome(String fixture){return switch(fixture){case "SYNTHETIC-CONFIRMED"->"SIMULATED_CONFIRMED";case "SYNTHETIC-FAILED"->"FAILED_CONFIRMED";case "SYNTHETIC-UNKNOWN"->"UNKNOWN_RECONCILIATION_REQUIRED";default->throw new BankFailure(400,"Controlled outgoing fixture required");};}
}
