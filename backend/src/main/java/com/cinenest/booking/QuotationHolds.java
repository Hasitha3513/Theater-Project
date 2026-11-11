package com.cinenest.booking;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Trusted Booking facts, read under the caller transaction after Identity and Pricing locks.
 * lockAndInspect takes the Rooms venue guard, reconciles inventory, and never renews a hold. */
public interface QuotationHolds {
    record Facts(UUID id, UUID owner, UUID venue, UUID room, Instant start, Instant end,
        Instant expires, String state, long revision, int guests, String timezone, int bufferMinutes, Instant blockedUntil, String configuration) {}
    record OperationalFacts(Facts hold, UUID packageId, UUID versionId) {}

    /** Pricing-owned package validation. Implementations lock the publication before Rooms locks. */
    @FunctionalInterface
    interface OperationalPackageValidator {
        boolean eligible(UUID venue, UUID room, UUID packageId, UUID versionId,
            Instant startsAt, Instant endsAt, int guests);
    }

    default Optional<OperationalFacts> locateOperational(UUID actor, UUID hold) {
        return Optional.empty();
    }

    default Optional<OperationalFacts> lockAndInspectOperational(UUID actor, UUID hold) {
        return Optional.empty();
    }

    Optional<Facts> locate(UUID hold);
    /** Derived current state only: no reconciliation, audit, row locks or writes. */
    Optional<Facts> observe(UUID actor,UUID hold);
    Optional<Facts> lockAndInspect(UUID actor, UUID hold);
}
