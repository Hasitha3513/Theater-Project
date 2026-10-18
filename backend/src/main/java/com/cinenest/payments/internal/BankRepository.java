package com.cinenest.payments.internal;
import java.time.Instant;
import java.util.*;
interface BankRepository {
 record Request(UUID id,UUID owner,UUID venue,UUID quote,UUID hold,UUID policy,long revision,String state,String review,Instant initiate,Instant expires,Instant submit,Instant reviewBy,Instant escalate,Instant holdUntil,String timezone,String amount,String currency,Map<String,Object> snapshot){}
 record Claim(UUID id,UUID request,int generation,Instant submitted){}
 record Evidence(UUID id,UUID request,UUID claim,UUID object,UUID preview,String state,Instant expires,Instant retain,String mime){}
 Map<String,Object> policy(UUID id);Instant now();Optional<Request> request(UUID id,boolean lock);Optional<Claim> claim(UUID id);Optional<Claim> latest(UUID request);
 List<Request> owned(UUID owner,int offset,int limit);
 List<Request> queue(UUID venue,UUID actor);List<Evidence> evidence(UUID claim);Optional<Evidence> upload(UUID id,boolean lock);
 void create(UUID id,com.cinenest.pricing.BankQuotation.Facts q,BankPolicy.Policy p,Map<String,Instant> times);
 void claim(UUID id,UUID request,int generation,long amount,Instant paid,String reference);
 void allocate(Evidence e,int position);void uploaded(UUID id,String digest,String mime,int bytes);void evidenceState(UUID id,String state);
 boolean timely(UUID claim,Instant deadline);void completeEvidence(UUID id);boolean duplicate(UUID id);
 void transition(UUID request,String state,String review);void decision(Request r,UUID claim,UUID actor,String action,String reason,UUID handover);
 List<Map<String,Object>> workSummary(UUID request);List<Map<String,Object>> history(UUID request);List<Map<String,Object>> claims(UUID request);Optional<Map<String,Object>> command(UUID actor,UUID key,String hash);void command(UUID actor,UUID key,String hash,Map<String,Object> result);
 void audit(UUID actor,UUID request,String action,String result);void work(UUID request,UUID evidence,String kind,Instant due);
 List<Map<String,Object>> pending();int claimWork(UUID id,Instant lease);boolean owns(UUID id,int attempt);void workState(UUID id,String state,Instant due);
 List<Evidence> cleanup();boolean referenced(UUID key);List<Request> expiring();
}
