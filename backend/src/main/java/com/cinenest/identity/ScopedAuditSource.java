package com.cinenest.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Owner-implemented safe audit projection. No raw metadata or foreign repository exposure. */
public interface ScopedAuditSource {
    record Query(UUID actor, UUID venue, Instant from, Instant to, Instant beforeTime,
                 UUID beforeId, UUID event, String action, String outcome, int limit) {}
    record Event(UUID eventId, String source, Instant occurredAt, UUID venueId, String action,
                 String outcome, String reasonCode, UUID correlationId, UUID actorId, UUID resourceId) {}
    String source();
    List<Event> read(Query query);
}
