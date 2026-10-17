package com.cinenest.booking;
import java.util.*;
/** Trusted in-process confirmation; joins the financial coordinator transaction. No HTTP command. */
public interface SyntheticBooking {
 record Funding(UUID request,UUID actor,UUID owner,UUID venue,UUID hold,UUID quote,long quoteRevision,String hash,long amount,String currency,Map<String,Object> snapshot,UUID command){}
 boolean entitled(UUID owner,UUID hold);
 UUID confirm(Funding funding);
 Optional<Map<String,Object>> read(UUID actor,UUID booking);
 Optional<UUID> forRequest(UUID request);
}
