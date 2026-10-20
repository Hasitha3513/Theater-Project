package com.cinenest.booking.internal;

import java.time.Instant;
import java.util.*;

/** Booking-owned persistence port. No financial/evidence/identity repository access. */
interface StaffBookingReadStore {
 record Item(UUID bookingId,UUID venueId,UUID roomId,long revision,String state,
             Instant scheduledStart,Instant scheduledEnd,String timezone,String packageName,
             int purchasedGuestCount,boolean synthetic,boolean realBooking) {}
 record Query(UUID venue,UUID reference,Instant from,Instant to,int page,int size) {}
 List<Item> list(Query query);
 Optional<Item> detail(UUID venue,UUID booking);
 Instant now();
 void audit(UUID actor,UUID venue,UUID booking,String action,String outcome,String reason,UUID correlation);
}
