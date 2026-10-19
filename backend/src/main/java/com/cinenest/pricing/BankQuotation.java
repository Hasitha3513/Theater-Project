package com.cinenest.pricing;
import java.time.Instant;
import java.util.*;
/** Immutable accepted synthetic BANK terms. Caller transaction and Identity lock required. */
public interface BankQuotation {
 static boolean unexpired(Instant expires,Instant now){return expires.isAfter(now);}
 record Facts(UUID quote,UUID owner,UUID venue,UUID hold,long revision,long holdRevision,String hash,Instant expires,Map<String,Object> snapshot){}
 record Observation(Facts facts,String reason){}
 /** Non-locking, non-mutating owner observation inside a read-only repeatable-read transaction. */
 Optional<Observation> observe(UUID actor,UUID quote);
 Optional<Facts> requestBound(UUID actor,UUID quote,long revision,String hash);
 Optional<Facts> accepted(UUID actor,UUID quote,long revision,String hash);
}
