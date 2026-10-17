package com.cinenest.payments.internal;
import java.time.*;import java.util.*;
import org.springframework.core.env.Environment;import org.springframework.stereotype.Component;
/** Closed source fixture catalog. Nothing is derived from a receipt or caller-supplied money. */
@Component
class SyntheticCreditSource {
 record Fact(String key,String account,String movement,String kind,long amount,Instant credited,UUID venue,String hash){}
 private final Environment env;SyntheticCreditSource(Environment env){this.env=env;}
 void configured(){load(true);}void maintenance(){load(false);}
 private void load(boolean commands){try{if((commands&&!env.getProperty("cinenest.bank.ledger.enabled",Boolean.class,false))||!Set.of(env.getActiveProfiles()).equals(Set.of("synthetic-quotation")))throw new IllegalArgumentException();UUID.fromString(env.getRequiredProperty("cinenest.bank.ledger.venue-id"));Instant.parse(env.getRequiredProperty("cinenest.bank.ledger.fixture-credited-at"));if(Long.parseLong(env.getRequiredProperty("cinenest.bank.ledger.resolution-seconds"))<=0||Long.parseLong(env.getRequiredProperty("cinenest.bank.ledger.resolution-seconds"))>Integer.MAX_VALUE)throw new IllegalArgumentException();}catch(RuntimeException e){throw new BankFailure(503,"Synthetic financial policy unavailable; real payments disabled");}}
 UUID venue(){maintenance();return UUID.fromString(env.getRequiredProperty("cinenest.bank.ledger.venue-id"));}
 long resolutionSeconds(){maintenance();return Long.parseLong(env.getRequiredProperty("cinenest.bank.ledger.resolution-seconds"));}
 Fact fixture(String key){configured();String canonical=key.equals("SYNTHETIC-OVERLAP-100")?"SYNTHETIC-BANK-100":key;
  long amount=switch(canonical){case "SYNTHETIC-BANK-100"->100;case "SYNTHETIC-BANK-200"->200;case "SYNTHETIC-BANK-10000","SYNTHETIC-GATEWAY-10000"->10000;case "SYNTHETIC-BANK-50000","SYNTHETIC-GATEWAY-50000"->50000;default->throw new BankFailure(400,"Approved synthetic source fixture required");};
  String kind=canonical.contains("GATEWAY")?"SYNTHETIC_GATEWAY":"SYNTHETIC_BANK";UUID venue=UUID.fromString(env.getRequiredProperty("cinenest.bank.ledger.venue-id"));Instant at=Instant.parse(env.getRequiredProperty("cinenest.bank.ledger.fixture-credited-at"));
  return new Fact(canonical,"SYNTHETIC-TEST-ACCOUNT",canonical,kind,amount,at,venue,BankPolicy.hash(canonical+":"+amount+":"+at+":"+venue));
 }
}
