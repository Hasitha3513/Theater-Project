package com.cinenest.rooms;
import java.util.*;
/** Rooms-owned facts only. Call after authorization; locks participate in the caller's transaction. */
public interface PackageConstraints {
    record RoomFact(UUID roomId,int capacity,long revision,Set<String> facilities) {}
    record Facts(UUID venueId,long venueRevision,UUID policyId,long policyRevision,int minimumMinutes,List<RoomFact> rooms) {}
    record Policy(UUID id,long revision,int minimumMinutes,java.time.Instant effectiveFrom) {}
    record PublicationFacts(String timezone,Facts current,List<Policy> policies) {}
    Optional<PublicationFacts> publication(UUID venue,Set<UUID> rooms,boolean wide,boolean lock);
    Optional<Facts> inspect(UUID venue,Set<UUID> selected,boolean venueWide,boolean lock);
}
