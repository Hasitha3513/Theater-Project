package com.cinenest.pricing.internal;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure validation only: no Spring bean, storage, endpoints, policy activation or payable totals. */
final class CommercialConfigurationRules {
    private CommercialConfigurationRules() {}

    record Assessment(List<String> blockers) {
        Assessment { blockers = List.copyOf(blockers); }
        boolean commerciallyEligible() { return false; }
    }

    static Assessment assess(CommercialConfiguration c, boolean ps4Selected) {
        Set<String> errors = new LinkedHashSet<>();
        // This offline contract cannot certify external approvals, even with complete-looking inputs.
        errors.add("OFFLINE_PLANNING_ONLY");
        if (c.synthetic()) errors.add("SYNTHETIC_CONFIGURATION");
        if (blank(c.identifier()) || c.version() < 1 || c.status() == null) errors.add("INVALID_VERSION_OR_IDENTITY");
        if (blank(c.decisionReference())) errors.add("MISSING_DECISION_REFERENCE");
        if (c.venueId() == null || c.roomCapacities().isEmpty()) errors.add("MISSING_VENUE_OR_ROOM_SCOPE");
        if (!"LKR".equals(c.currency())) errors.add("INVALID_CURRENCY");
        var p = c.prices();
        if (p == null || p.baseMinor() <= 0 || p.additionalHourMinor() < 0 || p.extraGuestMinor() < 0
                || p.ps4SessionMinor() < 0 || p.onlineFeeMinor() < 0) errors.add("INVALID_AMOUNTS");
        if (c.includedMinutes() <= 0 || c.extensionStepMinutes() != 60 || c.cleaningBufferMinutes() < 0)
            errors.add("INVALID_DURATION");
        if (c.maximumMinutes() == null) errors.add("MISSING_MAXIMUM_DURATION");
        else if (c.maximumMinutes() < c.includedMinutes() || c.extensionStepMinutes() <= 0
                || ((long)c.maximumMinutes() - c.includedMinutes()) % c.extensionStepMinutes() != 0)
            errors.add("INVALID_MAXIMUM_DURATION");
        if (c.includedGuests() < 1 || c.maximumGuests() < c.includedGuests()) errors.add("INVALID_GUEST_COUNTS");
        if (c.roomCapacities().values().stream().anyMatch(capacity -> capacity < 1 || capacity < c.maximumGuests()))
            errors.add("INVALID_ROOM_CAPACITY");
        if (c.effectiveAt() == null) errors.add("MISSING_EFFECTIVE_DATE");
        for (var requirement : CommercialConfiguration.Requirement.values()) {
            if (!ps4Selected && (requirement == CommercialConfiguration.Requirement.PS4_EQUIPMENT
                    || requirement == CommercialConfiguration.Requirement.PS4_RIGHTS)) continue;
            var review = c.reviews().get(requirement);
            if (review == null || review.state() != CommercialConfiguration.Approval.APPROVED) {
                errors.add("PENDING_" + requirement);
            } else if (blank(review.reference()) || review.accountableActor() == null || review.venueId() == null
                    || !review.venueId().equals(c.venueId()) || review.configurationVersion() != c.version()
                    || review.reviewedAt() == null) {
                errors.add("INVALID_PROVENANCE_" + requirement);
            } else {
                errors.add("UNVERIFIED_APPROVAL_" + requirement);
            }
        }
        return new Assessment(List.copyOf(errors));
    }

    /** Known components only. Excludes pending tax/fees; deliberately not a quotation or payable total. */
    static long knownPackageComponents(CommercialConfiguration c, int minutes, int guests, boolean ps4Selected) {
        var errors = assess(c, ps4Selected).blockers();
        if (errors.contains("INVALID_AMOUNTS") || errors.contains("INVALID_CURRENCY")
                || errors.contains("INVALID_DURATION") || errors.contains("INVALID_GUEST_COUNTS")
                || errors.contains("INVALID_ROOM_CAPACITY") || errors.contains("INVALID_MAXIMUM_DURATION")
                || c.maximumMinutes() == null || minutes < c.includedMinutes() || minutes > c.maximumMinutes()
                || ((long)minutes - c.includedMinutes()) % c.extensionStepMinutes() != 0
                || guests < 1 || guests > c.maximumGuests()) throw DraftRules.invalid("commercial planning selection");
        long units = ((long)minutes - c.includedMinutes()) / c.extensionStepMinutes();
        long result = DraftRules.add(c.prices().baseMinor(), DraftRules.multiply(units, c.prices().additionalHourMinor()));
        result = DraftRules.add(result, DraftRules.multiply(Math.max(0, guests - c.includedGuests()), c.prices().extraGuestMinor()));
        return ps4Selected ? DraftRules.add(result, c.prices().ps4SessionMinor()) : result;
    }

    static void requireSuccessor(CommercialConfiguration previous, CommercialConfiguration next) {
        if (blank(previous.identifier()) || !previous.identifier().equals(next.identifier())
                || previous.version() < 1 || next.version() <= previous.version())
            throw DraftRules.invalid("commercial configuration successor");
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
}
