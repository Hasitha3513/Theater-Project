package com.cinenest.booking;
import java.util.*;
/** Consumer-owned ports; implementations retain each operational dataset's ownership.
 * All calls require the caller transaction. Access serialization precedes Identity and Booking locks. */
public interface AmendmentGuards {
 interface Access { void lock(UUID booking); boolean neverIssued(UUID booking); }
 interface Funding { boolean eligible(BookingAccess.Context original); }
 record Quote(UUID id,long revision,String hash,Map<String,Object> snapshot,UUID packageId) {}
 interface Pricing {
  Quote validate(UUID actor,UUID originalQuote,UUID targetHold,UUID quote,long revision,String hash,boolean accept);
  UUID packageId(UUID originalQuote,UUID actor);
 }
 final class Rejected extends RuntimeException { public Rejected(){super("Amendment eligibility changed; refresh the protected booking");} }
}
