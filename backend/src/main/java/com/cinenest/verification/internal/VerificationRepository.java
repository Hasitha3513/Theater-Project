package com.cinenest.verification.internal;
import java.time.Instant;
import java.util.*;
interface VerificationRepository {
    record Request(UUID id,UUID owner,UUID venue,UUID quote,UUID policy,UUID previous,int generation,long revision,String state,boolean superseded,Instant validUntil){}
    record Evidence(UUID id,UUID request,UUID object,UUID preview,String type,String state,Instant expires,Instant retain,long revision,String mime){}
    Instant now();
    Optional<Request> request(UUID id,boolean lock);
    Optional<Evidence> evidence(UUID id,boolean lock);
    List<Evidence> evidenceFor(UUID request);
    List<Request> queue(UUID venue,UUID actor);
    List<Map<String,Object>> history(UUID request);
    void policy(VerificationPolicy.Policy policy);
    void create(Request request);
    void supersede(UUID request);
    void transition(UUID request,String state,Instant validUntil);
    void allocate(Evidence evidence,int position);
    void evidenceState(UUID evidence,String state);
    void uploaded(UUID evidence,String digest,String mime,long bytes);
    void decision(UUID id,Request request,UUID actor,String verdict,String reason,Instant until);
    Optional<Map<String,Object>> command(UUID actor,UUID key,String hash);
    void command(UUID actor,UUID key,String hash,Map<String,Object> response);
    void audit(UUID actor,UUID request,String action,String outcome);
    void work(UUID evidence);
    List<UUID> pending(Instant now);
    int claim(UUID evidence,Instant until);
    boolean owns(UUID evidence,int attempt);
    void workState(UUID evidence,String state);
    List<Evidence> cleanup(Instant now);
    boolean referenced(UUID key);
}
