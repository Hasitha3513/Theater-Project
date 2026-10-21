package com.cinenest.rooms;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Consumer-owned observation port; Booking implements it without a reverse module dependency. */
public interface OperationsBookingContext {
 record Context(UUID bookingId,UUID venueId,UUID roomId,long purchasedRevision,Instant sessionStart,Instant sessionEnd,String timezone){}
 /** Null revision means latest purchase; otherwise inspect that immutable purchased revision. */
 Optional<Context> inspect(UUID booking,UUID venue,Long revision);
 final class InvalidContext extends RuntimeException {}
}
