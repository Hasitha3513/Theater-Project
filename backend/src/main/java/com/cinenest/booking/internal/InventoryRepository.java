package com.cinenest.booking.internal;

import java.time.Instant;
import java.util.*;

/** Booking-owned persistence port. All writes participate in the application transaction. */
interface InventoryRepository {
    record Hold(UUID id,UUID owner,UUID block,UUID venue,UUID room,Instant start,Instant end,int buffer,
                Instant until,Instant created,Instant expires,String state,long revision,int guests,
                String timezone,String configuration,String minimumIds) {}
    record Command(String hash,UUID hold,String action) {}
    Instant now();
    Optional<Hold> hold(UUID id);
    List<Hold> active(UUID venue);
    Set<UUID> activeVenues();
    Optional<UUID> activeOwnerVenue(UUID actor);
    Optional<Command> command(UUID actor,UUID key);
    void command(UUID actor,UUID key,String hash,UUID hold,String action);
    void audit(UUID actor,UUID hold,String action,String before,String after);
    long attempts(UUID actor,int windowSeconds);
    void policy(UUID id,int ttl,int rate,int seconds,String reference);
    void create(Hold hold,UUID policy);
    void transition(Hold hold,String state,UUID actor);
    boolean overlaps(UUID room,Instant start,Instant until);
    List<Map<String,Object>> inventory(UUID venue,int page,int size);
}
