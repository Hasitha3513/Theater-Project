package com.cinenest.booking;
import java.util.*;
/** Trusted in-process confirmation; joins the financial coordinator transaction. No HTTP command. */
public interface SyntheticBooking {
 record Funding(UUID request,UUID actor,UUID owner,UUID venue,UUID hold,UUID quote,long quoteRevision,String hash,long amount,String currency,Map<String,Object> snapshot,UUID command){}
 record Line(String lineId,String kind,String label,String quantity,String amountMinor){}
 record Purchase(String name,String description,String packageVersionId,String sessionStart,String sessionEnd,String timezone,String roomId,String amountMinor,String currency,List<Line> lines){}
 record CustomerItem(UUID id,UUID requestId,UUID venueId,String state,long revision,String purchasedAt,Purchase purchase,boolean synthetic,boolean realBooking){}
 record CustomerPage(List<CustomerItem> items,int page,int size,boolean hasNext,String observedAt,boolean synthetic){}
 /** Non-locking internal observation. Consumers enforce their own independent financial permission. */
 record FinancialContext(UUID bookingId,UUID ownerId,UUID venueId,UUID originatingRequestId,long bookingRevision){}
 Optional<FinancialContext> financialContext(UUID booking);
 CustomerPage ownedHistory(UUID actor,int page,int size);
 boolean entitled(UUID owner,UUID hold);
 UUID confirm(Funding funding);
 Optional<Map<String,Object>> read(UUID actor,UUID booking);
 Optional<String> state(UUID booking);
 Optional<UUID> forRequest(UUID request);
}
