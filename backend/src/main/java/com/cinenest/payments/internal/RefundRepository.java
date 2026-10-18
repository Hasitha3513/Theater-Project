package com.cinenest.payments.internal;

import java.util.*;

/** Payments-owned persistence port; application code never supplies SQL. */
interface RefundRepository {
 Map<String,Object> refund(UUID id,boolean lock);
 List<UUID> owned(UUID request,UUID owner,UUID venue,int offset,int limit);
 List<UUID> queue(UUID venue);
 Optional<Map<String,Object>> replay(UUID actor,UUID key,String hash);
 void command(UUID actor,UUID key,String hash,Map<String,Object> result);
 void create(UUID id,UUID credit,UUID request,UUID owner,UUID venue,long amount,String policy,Map<String,Object> snapshot);
 void transition(UUID id,String state,UUID approver);
 Optional<Map<String,Object>> destination(UUID id);
 void destination(UUID id,String reference,UUID proposer,UUID verifier,String hash);
 void decision(UUID id,UUID actor,String action,String reason);
 List<Map<String,Object>> decisions(UUID id);
 List<Map<String,Object>> attempts(UUID id);
 void attempt(UUID id,UUID refund,int sequence,UUID actor,UUID key);
 List<Map<String,Object>> evidence(UUID id);
 void evidence(UUID attempt,String fixture,UUID recorder,UUID verifier,String hash);
 void work(UUID id,String state);
 Optional<String> work(UUID id);
 List<Map<String,Object>> audit(UUID id);
}
