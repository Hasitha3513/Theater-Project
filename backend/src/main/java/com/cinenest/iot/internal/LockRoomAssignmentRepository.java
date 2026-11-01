package com.cinenest.iot.internal;

import java.time.Instant;
import java.util.*;

/** IoT-owned persistence; every method participates in the caller's transaction. */
interface LockRoomAssignmentRepository {
    record Assignment(UUID id, UUID venue, UUID room, UUID device, long revision,
                      String configuration, long deviceRevision, UUID actor, Instant createdAt) {}
    record Receipt(Assignment assignment, byte[] hash) {
        public Receipt { hash = hash.clone(); }
        @Override public byte[] hash() { return hash.clone(); }
    }
    Instant now();
    Optional<Receipt> receipt(UUID actor, UUID key);
    /** Locks the stable device identity before observing its latest immutable metadata revision. */
    Optional<Long> lockDevice(UUID venue, UUID device);
    Assignment create(UUID actor, UUID venue, UUID room, LockRoomAssignmentPolicy.Create command, UUID correlation);
    Optional<Assignment> detail(UUID venue, UUID room, UUID assignment);
    List<Assignment> page(UUID venue, UUID room, int page);
    void audit(UUID actor, UUID venue, UUID room, UUID assignment, String action,
               String denial, UUID correlation, Instant at);
}
