package com.cinenest.pricing.internal;
import java.util.*;
interface PublicationRepository {
 void lock(UUID venue,UUID pkg);
 UUID author(UUID pkg,long revision);
 List<Map<String,Object>> versions(UUID venue,UUID pkg);
 List<Map<String,Object>> catalog(UUID venue);
 Optional<Map<String,Object>> replay(UUID pkg,UUID key);
 void insert(Map<String,Object> record);
 void state(UUID id,long expected,String state,String reason);
 Optional<Map<String,Object>> commandReceipt(UUID pkg,UUID key);
 void command(UUID id,UUID key,String hash,UUID actor);
 void work(UUID id,String state,String error);
 Optional<Map<String,Object>> job(UUID id);
 List<UUID> due();
}
