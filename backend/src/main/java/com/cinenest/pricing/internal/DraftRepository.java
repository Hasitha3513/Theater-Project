package com.cinenest.pricing.internal;
import java.util.*;
interface DraftRepository {
 Optional<Map<String,Object>> find(UUID venue,UUID id,boolean writeLock);
 Map<String,Object> list(UUID venue,String q,int page,int size);
 List<Map<String,Object>> options(UUID venue);
 void save(UUID venue,UUID id,Map<String,Object> data,boolean create);
 void history(UUID actor,UUID id,Map<String,Object> snapshot,String reason);
 Map<String,Object> history(UUID venue,UUID id,int page,int size);
 void audit(UUID actor,UUID venue,UUID id,String action,boolean allowed,Long oldRevision,Long newRevision,String reason);
}
