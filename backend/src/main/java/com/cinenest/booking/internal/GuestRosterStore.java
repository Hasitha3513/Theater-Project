package com.cinenest.booking.internal;
import java.util.*;
/** Booking-owned persistence port; caller holds the booking lock and transaction. */
interface GuestRosterStore {
 int purchased(UUID booking);
 Optional<Long> revision(UUID booking);
 List<Map<String,String>> entries(UUID booking);
 Optional<String> command(UUID actor,UUID key);
 void initialize(UUID booking);
 void append(UUID booking,String kind,String label,UUID booker);
 void advance(UUID booking);
 void remember(UUID actor,UUID key,UUID booking,String hash);
 void audit(UUID actor,UUID booking,String action);
}
