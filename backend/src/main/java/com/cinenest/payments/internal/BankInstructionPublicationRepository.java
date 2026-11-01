package com.cinenest.payments.internal;
import java.time.Instant;
import java.util.*;
interface BankInstructionPublicationRepository {
    record Proposal(UUID id,UUID venue,UUID proposer,BankInstructionPublicationPolicy.Draft draft) {}
    record Event(UUID id,UUID actor,UUID key,UUID venue,UUID proposal,String action,String payloadHash,long expectedRevision,
                 long revision,String state,UUID reviewer,UUID published,Instant at,UUID correlation,UUID audit) {}
    record Snapshot(Proposal proposal,Event event) {}
    Instant now();
    Optional<Snapshot> find(UUID venue,UUID proposal,boolean lock);
    Optional<Event> receipt(UUID actor,UUID key);
    void insertProposal(Proposal proposal);
    void insertEvent(Event event);
    void audit(Event event);
    void readAudit(UUID actor,Snapshot snapshot,UUID correlation);
    void denied(UUID actor,UUID venue,String action,String reason,UUID correlation);
    void publish(Proposal proposal,Event event);
}
