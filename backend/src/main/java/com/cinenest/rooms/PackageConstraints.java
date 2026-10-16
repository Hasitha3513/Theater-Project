package com.cinenest.rooms;
import java.util.*;
/** Rooms-owned facts only. Call after authorization; locks participate in the caller's transaction. */
public interface PackageConstraints {
    record RoomFact(UUID roomId,int capacity,long revision,Set<String> facilities) {}
    record Facts(UUID venueId,long venueRevision,UUID policyId,long policyRevision,int minimumMinutes,List<RoomFact> rooms) {}
    Optional<Facts> inspect(UUID venue,Set<UUID> selected,boolean venueWide,boolean lock);
}
