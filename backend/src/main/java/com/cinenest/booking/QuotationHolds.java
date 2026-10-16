package com.cinenest.booking;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Trusted Booking facts, read under the caller transaction after Identity and Pricing locks.
 * lockAndInspect takes the Rooms venue guard, reconciles inventory, and never renews a hold. */
public interface QuotationHolds {
    record Facts(UUID id, UUID owner, UUID venue, UUID room, Instant start, Instant end,
        Instant expires, String state, long revision, int guests, String timezone, int bufferMinutes, Instant blockedUntil, String configuration) {}
    Optional<Facts> locate(UUID hold);
    Optional<Facts> lockAndInspect(UUID actor, UUID hold);
}
