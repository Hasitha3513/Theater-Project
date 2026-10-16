package com.cinenest.pricing.internal;

import java.time.Instant;
import java.util.*;

/** Pricing-owned quotation persistence; all writes use caller transaction. */
interface QuoteRepository {
    record Quote(UUID id,UUID owner,UUID hold,UUID venue,UUID room,UUID pkg,long revision) {}
    record Revision(long revision,UUID version,UUID policy,long holdRevision,Instant expires,String method,Map<String,Object> snapshot,String hash) {}
    record Command(String action,String hash,UUID quote,long revision) {}
    Instant now();
    Optional<Quote> quote(UUID id,boolean lock);
    Optional<Quote> byHold(UUID hold);
    Revision revision(UUID quote,long revision);
    Optional<Long> accepted(UUID quote);
    Optional<Command> command(UUID actor,UUID key);
    long attempts(UUID actor,int seconds);
    void audit(UUID actor,UUID quote,Long revision,String action);
    void policy(UUID id,String profile,long tax,String reference);
    void create(Quote quote);
    void append(Quote quote,Revision revision);
    void accept(Quote quote,UUID actor,String hash);
    void command(UUID actor,UUID key,String action,String hash,UUID quote,long revision);
}
