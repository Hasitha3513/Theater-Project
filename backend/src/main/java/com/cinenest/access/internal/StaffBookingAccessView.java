package com.cinenest.access.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Explicit staff projection. Never serialize persistence maps or customer disclosure data. */
record StaffBookingAccessView(UUID bookingId, UUID venueId, UUID roomId, long bookingRevision,
        Instant sessionStart, Instant sessionEnd, String timezone, Instant observedAt,
        boolean credentialExists, String state, Credential credential, List<String> permittedActions,
        boolean synthetic, boolean doorControlled) {
    record Credential(UUID id, long version, long revision, long bookingRevision,
            String provisioning, UUID operationId) {}
}
