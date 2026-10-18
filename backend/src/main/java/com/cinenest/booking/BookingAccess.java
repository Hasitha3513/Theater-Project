package com.cinenest.booking;
import java.time.Instant;import java.util.*;
/** Authoritative purchased context. Caller transaction required; never an ACTIVE hold decision. */
public interface BookingAccess {
 record Context(UUID booking,UUID request,UUID owner,UUID venue,UUID room,UUID quote,long revision,long amount,String currency,Instant start,Instant end,String timezone){}
 Optional<Context> lock(UUID booking);
 boolean amendmentPending(UUID booking);
}
