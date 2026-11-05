package com.cinenest.pricing.internal;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Offline planning facts, never an authorization or an operational price configuration. */
record CommercialConfiguration(
        String identifier, long version, String decisionReference, Status status,
        UUID venueId, Map<UUID, Integer> roomCapacities, String currency,
        Prices prices, int includedMinutes, int extensionStepMinutes,
        int includedGuests, int maximumGuests, int cleaningBufferMinutes,
        Integer maximumMinutes, Instant effectiveAt, boolean synthetic,
        Map<Requirement, Review> reviews) {

    enum Status { OWNER_PREFERENCES_APPROVED_SCOPE_PENDING }
    enum Approval { PENDING, APPROVED, REJECTED }
    enum Requirement { TAX, FINANCE, LEGAL, CANCELLATION_REFUND, OPERATIONS, PS4_EQUIPMENT, PS4_RIGHTS }

    /** References describe claimed provenance; no caller-created Review verifies authority. */
    record Review(Approval state, String reference, UUID accountableActor,
                  UUID venueId, long configurationVersion, Instant reviewedAt) {}

    record Prices(long baseMinor, long additionalHourMinor, long extraGuestMinor,
                  long ps4SessionMinor, long onlineFeeMinor) {}

    CommercialConfiguration {
        roomCapacities = Map.copyOf(roomCapacities);
        reviews = Map.copyOf(reviews);
    }

    static CommercialConfiguration ownerPreferences() {
        return new CommercialConfiguration("DAY05_INITIAL_VENUE_COMMERCIAL_V1", 1, null,
                Status.OWNER_PREFERENCES_APPROVED_SCOPE_PENDING, null, Map.of(), "LKR",
                new Prices(250000, 100000, 80000, 100000, 10000),
                180, 60, 4, 5, 15, null, null, false, Map.of());
    }
}
