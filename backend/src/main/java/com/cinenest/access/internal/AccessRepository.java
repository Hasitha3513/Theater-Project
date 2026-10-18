package com.cinenest.access.internal;
import java.util.*;import java.time.Instant;
interface AccessRepository {
 List<Map<String,Object>> history(UUID id);List<UUID> cleanupCandidates();Instant now();UUID bookingFor(UUID credential);void lock(UUID booking);Optional<Map<String,Object>> credential(UUID id);Optional<Map<String,Object>> latest(UUID booking);Map<String,Object> work(UUID id);
 void create(Map<String,Object> row);void save(Map<String,Object> row);void workState(UUID id,String state,boolean installed,boolean increment);
 void audit(UUID actor,UUID credential,UUID booking,UUID venue,String action,String outcome);int rate(UUID actor,int seconds);int attempts(UUID id);int disclosures(UUID id);
 Optional<Map<String,Object>> command(UUID actor,UUID key);void command(UUID actor,UUID key,UUID booking,UUID credential,String kind,String hash);
 void attempt(UUID actor,UUID id,boolean success);List<UUID> ids(UUID venue,int page,int size);
}
