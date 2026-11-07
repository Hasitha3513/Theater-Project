package com.cinenest.pricing.internal;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Internal synthetic qualification command. No operational caller or approval intake is wired. */
record CommercialPolicyBinding(
        UUID actor, UUID commandKey, UUID venue, UUID room, UUID packageVersion,
        UUID policy, long revision, String contentHash, Instant from, Instant until,
        UUID financeReview, UUID legalReview, UUID operationsReview) {
    CommercialPolicyBinding {
        for (var value : new Object[]{actor, commandKey, venue, room, packageVersion, policy,
                contentHash, from, until, financeReview, legalReview, operationsReview}) {
            Objects.requireNonNull(value);
        }
        if (revision < 1 || !contentHash.matches("[0-9a-f]{64}") || !from.isBefore(until)
                || from.getNano() % 1000 != 0 || until.getNano() % 1000 != 0) {
            throw new IllegalArgumentException("Invalid commercial binding command");
        }
    }
}
