package com.cinenest.booking.internal;

import java.time.Instant;
import java.util.*;

/** Booking-owned durable maintenance command boundary. No Rooms/Identity SQL. */
interface RoomBlockRepository {
    record Block(UUID id,UUID actor,UUID key,String hash,UUID venue,UUID room,Instant start,Instant end,String configuration,Instant created) {}
    Instant now();
    Optional<Block> command(UUID actor,UUID key);
    Optional<Block> find(UUID venue,UUID room,UUID block);
    List<Block> list(UUID venue,UUID room,int page,int size);
    void create(Block block,UUID event);
    void audit(UUID event,UUID actor,UUID venue,UUID room,UUID block,UUID command,String action);
}
