package com.cinenest.booking;
import java.time.Instant;
import java.util.UUID;
/** Booking-owned conversion. Must join the caller transaction, never confirms a booking. */
public interface BankRequestHolds {
 final class Rejected extends RuntimeException {public Rejected(){super("Bank inventory conversion unavailable or changed");}}
 record State(String state,Instant expires){}
 /** Observational mode never reconciles or writes; command mode uses the existing venue guard. */
 String conversionRefusal(UUID actor,UUID hold,long revision,Instant expires,boolean lock);
 State convert(UUID actor,UUID hold,long revision,UUID request,Instant expires);
 State inspect(UUID actor,UUID hold);
}
