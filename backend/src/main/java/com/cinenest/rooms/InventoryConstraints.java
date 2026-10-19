package com.cinenest.rooms;

import java.time.*;
import java.util.*;

/** Rooms-owned configuration boundary. Lock venue before reading mutable configuration. */
public interface InventoryConstraints {
    record Window(int weekday, int openingMinute, int closingMinute) {}
    record Minimum(UUID id, int minutes, Instant effectiveFrom) {}
    record Facts(UUID venueId, UUID roomId, String timezone, boolean enabled, int capacity,
                 Integer bufferMinutes, boolean postCloseApproved, String configuration,
                 List<Window> windows, List<Minimum> minimums) {}
    Optional<UUID> venue(UUID room);
    void lock(UUID venue);
    Optional<Facts> inspect(UUID room);
    /** Non-mutating configuration observation in the caller's read-only repeatable-read snapshot. */
    Optional<Facts> observe(UUID room);
}
