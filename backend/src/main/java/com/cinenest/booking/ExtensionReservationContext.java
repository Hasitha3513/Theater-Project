package com.cinenest.booking;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Booking-owned, owner-scoped inventory observation. Caller transaction required.
 * This is not staff delegation, financial eligibility, a funding request or a promise
 * that the observation remains current after the caller transaction ends.
 */
public interface ExtensionReservationContext {
    enum State { ACTIVE, WITHDRAWN, EXPIRED, INVALIDATED }
    record Window(Instant start, Instant end, Instant blockedUntil) {}
    record Coverage(Instant start, Instant end) {}
    record Observation(UUID bookingId, UUID reservationId, UUID venueId,
                       long purchasedRevision, long lifecycleVersion, long proposalRevision,
                       State state, String configuration, Window originalSession,
                       Window proposedSession, Coverage additionalCoverage, long additionalMinor,
                       String currency, UUID packageVersionId, String pricingBasis,
                       int additionalMinutes, String termsVersion, Instant createdAt,
                       Instant expiresAt, Instant observedAt, boolean effective,
                       boolean pendingReconciliation, List<String> reasons) {
        public Observation { reasons = List.copyOf(reasons); }
    }
    /** Rechecks current eligible ownership and records the existing mandatory read audit. */
    Observation observeOwned(UUID owner, UUID booking, UUID reservation);

    /** Sanitized existing HTTP-compatible denial; never contains SQL, evidence or secrets. */
    final class Rejected extends RuntimeException {
        private final int status;
        public Rejected(int status, String message) { super(message); this.status = status; }
        public int status() { return status; }
    }
}
