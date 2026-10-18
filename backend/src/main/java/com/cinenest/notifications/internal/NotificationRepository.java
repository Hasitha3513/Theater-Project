package com.cinenest.notifications.internal;
import com.cinenest.notifications.NotificationFacts.Fact;
import java.util.*;
interface NotificationRepository {
 void event(Fact fact); List<Map<String,Object>> unresolved(); List<Map<String,Object>> unassigned(UUID venue); Map<String,Object> event(UUID id);
 void intent(UUID event,UUID recipient,boolean staff,int expiry,int retention); void resolved(UUID event);
 List<Map<String,Object>> inbox(UUID actor); List<Map<String,Object>> queue(UUID venue); Map<String,Object> row(UUID id,boolean lock);
 void lockActor(UUID actor); long rate(UUID actor,int seconds); void audit(UUID actor,UUID event,UUID intent,UUID venue,String action,String outcome);
 Map<String,Object> command(UUID actor,UUID key);void command(UUID actor,UUID key,UUID intent,String kind,String hash);
 void read(UUID intent);void fixture(UUID intent,String fixture);void retry(UUID intent,int delay);void reconcile(UUID intent);
 List<UUID> pending();Map<String,Object> claim(UUID operation,int lease,int limit);boolean finish(UUID operation,long fence,String outcome,String evidence);void suppress(UUID id);
 List<Map<String,Object>> attempts(UUID operation);List<Map<String,Object>> history(UUID intent);boolean unexpired(UUID intent);
}
