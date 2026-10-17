package com.cinenest.pricing;
import java.time.Instant;
import java.util.*;
/** Immutable accepted synthetic BANK terms. Caller transaction and Identity lock required. */
public interface BankQuotation {
 record Facts(UUID quote,UUID owner,UUID venue,UUID hold,long revision,long holdRevision,String hash,Instant expires,Map<String,Object> snapshot){}
 Optional<Facts> requestBound(UUID actor,UUID quote,long revision,String hash);
 Optional<Facts> accepted(UUID actor,UUID quote,long revision,String hash);
}
