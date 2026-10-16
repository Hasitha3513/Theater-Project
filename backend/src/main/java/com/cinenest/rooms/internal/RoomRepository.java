package com.cinenest.rooms.internal;

import java.util.*;

/** Application-owned persistence port; adapters do not own authorization or policy. */
interface RoomRepository {
    void lockVenue(UUID venue);
    Optional<Map<String,Object>> venue(UUID id);
    Optional<Map<String,Object>> publicRoom(UUID id);
    Optional<Map<String,Object>> room(UUID venue, UUID id);
    List<Map<String,Object>> venues(Set<UUID> scope, boolean publicOnly);
    Map<String,Object> rooms(UUID venue, String q, int minimum, String facility, Boolean enabled, int page, int size, boolean publicOnly);
    void saveVenue(UUID id, Map<String,Object> data, boolean create);
    void saveRoom(UUID venue, UUID id, Map<String,Object> data, boolean create);
    void audit(UUID actor, UUID venue, UUID resource, String type, String action, boolean allowed, Long oldVersion, Long newVersion);
}
