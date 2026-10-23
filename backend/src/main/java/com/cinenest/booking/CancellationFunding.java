package com.cinenest.booking;
import java.util.UUID;
/** Payments implements its own allocation locks and obligation writes; caller transaction required. */
public interface CancellationFunding {
 boolean eligible(BookingAccess.Context booking);
 UUID reserve(BookingAccess.Context booking,UUID cancellation);
}
