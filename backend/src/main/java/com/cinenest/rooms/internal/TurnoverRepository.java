package com.cinenest.rooms.internal;
import java.util.*;
interface TurnoverRepository {
 void lock(UUID actor);
 Optional<Map<String,Object>> receipt(UUID actor,UUID key);
 Map<String,Object> find(UUID venue,UUID id,boolean lock);
 List<Map<String,Object>> list(UUID venue,int page);
 List<Map<String,Object>> entries(UUID id);
 void create(UUID id,UUID actor,UUID venue,UUID room,UUID booking,Long bookingRevision,String version,List<String> checklist);
 void update(UUID id,String state,UUID assignee,UUID acknowledged);
 void evidence(UUID actor,UUID id,long revision,String action,Map<String,Object> data);
 void receipt(UUID actor,UUID key,String hash,UUID id);
 void audit(UUID actor,UUID venue,UUID id,long revision,String action);
}
