package com.cinenest.notifications;
import java.util.UUID;
/** Trusted source handoff only; called within the owning business transaction. No delivery here. */
public interface NotificationFacts {
 enum Kind { BOOKING_CANCELLED, AMENDMENT_CONFIRMED, BANK_CLAIM_RECEIVED, BANK_REVIEW_COMPLETED, BANK_REVIEW_NEEDS_INFORMATION, BANK_REVIEW_REJECTED, SYNTHETIC_ACCESS_READY, SYNTHETIC_ACCESS_EXPIRED, SYNTHETIC_ACCESS_REVOKED, SYNTHETIC_OPERATION_EXCEPTION }
 record Fact(UUID event, String source, UUID resource, long revision, UUID owner, UUID venue, Kind kind, boolean reviewers) {}
 void record(Fact fact);
}
