package com.cinenest.booking;

import java.util.Map;
import java.util.UUID;

/** Decision-only port for isolated synthetic qualification. No evidence or entry authority. */
public interface SyntheticGuestAdmission {
    record Scope(UUID booking, UUID venue, UUID room, long bookingRevision,
                 long rosterRevision, String policyVersion) {}

    enum Outcome { ELIGIBLE, DENIED, UNRESOLVED }

    /** Return current, non-revoked decisions for the exact supplied scope only. */
    record Decision(Scope scope, Outcome outcome, boolean revoked) {}

    Map<UUID, Decision> currentDecisions(Scope scope);
}
