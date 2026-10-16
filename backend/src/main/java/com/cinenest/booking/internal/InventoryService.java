package com.cinenest.booking.internal;

import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.rooms.InventoryConstraints;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class InventoryService implements com.cinenest.booking.QuotationHolds {
    private final InventoryRepository repository;
    private final InventoryConstraints rooms;
    private final ScopedAuthorization identity;
    private final TransactionTemplate tx;
    private final boolean enabled;
    private final int ttl,rate,seconds;
    private final String reference;
    InventoryService(InventoryRepository repository,InventoryConstraints rooms,ScopedAuthorization identity,PlatformTransactionManager transactions,
        @Value("${cinenest.booking.holds.enabled:false}") boolean enabled,
        @Value("${cinenest.booking.holds.ttl-seconds:600}") int ttl,
        @Value("${cinenest.booking.holds.rate-count:0}") int rate,
        @Value("${cinenest.booking.holds.rate-seconds:0}") int seconds,
        @Value("${cinenest.booking.holds.approval-reference:}") String reference) {
        this.repository=repository;this.rooms=rooms;this.identity=identity;this.tx=new TransactionTemplate(transactions);
        this.enabled=enabled;this.ttl=ttl;this.rate=rate;this.seconds=seconds;this.reference=reference;
    }
    private void eligible(UUID actor) {if(!identity.eligible(actor,true))throw new InventoryFailure(403,"Access denied");}
    private void configured() {if(!enabled || ttl<=0 || rate<=0 || seconds<=0 || reference.isBlank())throw new InventoryFailure(503,"Inventory hold creation is not configured");}
    private InventoryConstraints.Facts lockRoom(UUID room) {
        UUID venue=rooms.venue(room).orElseThrow(()->new InventoryFailure(404,"Room not found"));rooms.lock(venue);
        return rooms.inspect(room).orElseThrow(()->new InventoryFailure(404,"Room not found"));
    }
    private void lockVenues(Set<UUID> venues) {venues.stream().sorted().forEach(rooms::lock);}
    private static String hash(String action,Map<String,Object> body) {
        try {
            var text=new StringBuilder(action);
            new TreeMap<>(body).forEach((k,v)->{String s=String.valueOf(v);text.append(k.length()).append(':').append(k).append(s.length()).append(':').append(s);});
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        } catch(java.security.NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
    Map<String,Object> availability(UUID actor,UUID room,String local,int duration,int guests) {
        return tx.execute(status->{
            eligible(actor);var f=lockRoom(room);reconcileLocked(f.venueId());Instant now=repository.now();
            var c=InventoryRules.candidate(f,InventoryRules.local(local,f.timezone()),duration,guests);
            if(!c.start().isAfter(now))throw InventoryRules.invalid();
            var result=candidateView(f,c,now);result.put("available",!repository.overlaps(room,c.start(),c.blockedUntil()));
            return result;
        });
    }
    Map<String,Object> create(UUID actor,Map<String,Object> body) {
        configured();
        // Admission has its own committed transaction: invalid/conflicting requests consume budget too.
        Boolean admitted=tx.execute(status->{eligible(actor);
            if(body.get("idempotencyKey") instanceof String raw) {
                try {var prior=repository.command(actor,UUID.fromString(raw));if(prior.isPresent() && prior.get().action().equals("CREATE") && prior.get().hash().equals(hash("CREATE",body)))return true;}catch(IllegalArgumentException invalidKey) { /* Invalid requests still consume admission budget. */ }
            }
            if(repository.attempts(actor,seconds)>=rate)return false;repository.audit(actor,null,"CREATE_ATTEMPT",null,null);return true;});
        if(!Boolean.TRUE.equals(admitted))throw new InventoryFailure(429,"Too many hold attempts; wait before retrying");
        InventoryRules.keys(body,"roomId","sessionStartLocal","durationMinutes","guests","expectedConfiguration","idempotencyKey");
        UUID room=InventoryRules.id(InventoryRules.text(body,"roomId")),key=InventoryRules.id(InventoryRules.text(body,"idempotencyKey"));String hash=hash("CREATE",body);
        return tx.execute(status->{
            eligible(actor);var receipt=repository.command(actor,key);
            if(receipt.isPresent())return replay(actor,receipt.get(),hash,"CREATE");
            UUID venue=rooms.venue(room).orElseThrow(()->new InventoryFailure(404,"Room not found"));
            var venues=new HashSet<UUID>();venues.add(venue);repository.activeOwnerVenue(actor).ifPresent(venues::add);lockVenues(venues);
            venues.forEach(this::reconcileLocked);
            var f=rooms.inspect(room).orElseThrow(()->new InventoryFailure(404,"Room not found"));
            if(!f.configuration().equals(InventoryRules.text(body,"expectedConfiguration")))throw new InventoryFailure(409,"Room configuration changed; inquire again");
            Instant now=repository.now();var c=InventoryRules.candidate(f,InventoryRules.local(InventoryRules.text(body,"sessionStartLocal"),f.timezone()),InventoryRules.positive(body,"durationMinutes"),InventoryRules.positive(body,"guests"));
            if(!c.start().isAfter(now))throw InventoryRules.invalid();
            if(repository.activeOwnerVenue(actor).isPresent())throw new InventoryFailure(409,"Release the existing active hold first");
            if(repository.overlaps(room,c.start(),c.blockedUntil()))throw new InventoryFailure(409,"Inventory conflict; select another interval");
            Instant expiry=InventoryRules.expires(now,c.start(),ttl);
            UUID policy=UUID.nameUUIDFromBytes((ttl+":"+rate+":"+seconds+":"+reference).getBytes(StandardCharsets.UTF_8));repository.policy(policy,ttl,rate,seconds,reference);
            var hold=new InventoryRepository.Hold(UUID.randomUUID(),actor,UUID.randomUUID(),venue,room,c.start(),c.end(),c.buffer(),c.blockedUntil(),now,expiry,"ACTIVE",0,InventoryRules.positive(body,"guests"),f.timezone(),f.configuration(),c.policyIds());
            repository.create(hold,policy);repository.audit(actor,hold.id(),"CREATED",null,"ACTIVE");repository.command(actor,key,hash,hold.id(),"CREATE");return holdView(hold,repository.now());
        });
    }
    private Map<String,Object> replay(UUID actor,InventoryRepository.Command receipt,String hash,String action) {
        if(!receipt.hash().equals(hash) || !receipt.action().equals(action))throw new InventoryFailure(409,"Idempotency key already used with another request");
        return readLocked(actor,receipt.hold());
    }
    private Map<String,Object> readLocked(UUID actor,UUID id) {
        var hold=owned(actor,id);rooms.lock(hold.venue());reconcileLocked(hold.venue());return holdView(owned(actor,id),repository.now());
    }
    private InventoryRepository.Hold owned(UUID actor,UUID id) {
        var h=repository.hold(id).orElseThrow(()->new InventoryFailure(404,"Hold not found"));
        if(!h.owner().equals(actor))throw new InventoryFailure(403,"Access denied");return h;
    }
    Map<String,Object> read(UUID actor,UUID id) {return tx.execute(status->{eligible(actor);return readLocked(actor,id);});}
    Map<String,Object> release(UUID actor,UUID id,Map<String,Object> body) {
        InventoryRules.keys(body,"expectedRevision","idempotencyKey");UUID key=InventoryRules.id(InventoryRules.text(body,"idempotencyKey"));String hash=hash("RELEASE:"+id,body);long revision=InventoryRules.revision(body);
        return tx.execute(status->{eligible(actor);var receipt=repository.command(actor,key);if(receipt.isPresent())return replay(actor,receipt.get(),hash,"RELEASE");
            var h=owned(actor,id);rooms.lock(h.venue());reconcileLocked(h.venue());h=owned(actor,id);
            if(h.revision()!=revision)throw new InventoryFailure(409,"Hold changed; refresh its state");
            if(h.state().equals("ACTIVE"))repository.transition(h,"RELEASED",actor);
            repository.command(actor,key,hash,id,"RELEASE");return holdView(owned(actor,id),repository.now());});
    }
    Map<String,Object> inventory(UUID actor,UUID venue,int page,int size) {
        if(page<0 || size<1 || size>100)throw InventoryRules.invalid();
        return tx.execute(status->{if(!identity.permits(actor,venue,"inventory.read",true))throw new InventoryFailure(403,"Access denied");rooms.lock(venue);reconcileLocked(venue);repository.audit(actor,null,"INVENTORY_READ",null,null);
            return Map.of("items",repository.inventory(venue,page,size),"page",page,"size",size,"serverNow",repository.now(),"evidenceLevel","INVENTORY_ONLY");});
    }
    Set<UUID> pendingVenues() {return repository.activeVenues();}
    void reconcile(UUID venue) {tx.executeWithoutResult(status->{rooms.lock(venue);reconcileLocked(venue);});}
    private static com.cinenest.booking.QuotationHolds.Facts quotationFacts(InventoryRepository.Hold h) {
        return new com.cinenest.booking.QuotationHolds.Facts(h.id(),h.owner(),h.venue(),h.room(),h.start(),h.end(),h.expires(),h.state(),h.revision(),h.guests(),h.timezone(),h.buffer(),h.until(),h.configuration());
    }
    public Optional<com.cinenest.booking.QuotationHolds.Facts> locate(UUID hold) {
        return repository.hold(hold).map(InventoryService::quotationFacts);
    }
    public Optional<com.cinenest.booking.QuotationHolds.Facts> lockAndInspect(UUID actor,UUID hold) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Quotation hold inspection requires caller transaction");
        var h=repository.hold(hold);
        if(h.isEmpty() || !h.get().owner().equals(actor))return Optional.empty();
        rooms.lock(h.get().venue());reconcileLocked(h.get().venue());
        return repository.hold(hold).map(InventoryService::quotationFacts);
    }
    private void reconcileLocked(UUID venue) {
        for(var h:repository.active(venue)) {
            if(!repository.now().isBefore(h.expires())) {repository.transition(h,"EXPIRED",null);continue;}
            boolean valid=false;
            try {
                var f=rooms.inspect(h.room()).orElseThrow(InventoryRules::invalid);
                var c=InventoryRules.candidate(f,h.start(),Math.toIntExact(Duration.between(h.start(),h.end()).toMinutes()),h.guests());
                valid=f.timezone().equals(h.timezone()) && c.blockedUntil().equals(h.until());
            } catch(InventoryFailure invalid) { /* Persist invalidation, never silently rewrite the interval. */ }
            if(!valid)repository.transition(h,"INVALIDATED",null);
        }
    }
    private Map<String,Object> candidateView(InventoryConstraints.Facts f,InventoryRules.Candidate c,Instant now) {
        var result=new LinkedHashMap<String,Object>();result.put("roomId",f.roomId());result.put("timezone",f.timezone());result.put("sessionStart",c.start());result.put("sessionEnd",c.end());result.put("bufferMinutes",c.buffer());result.put("blockedUntil",c.blockedUntil());result.put("configuration",f.configuration());result.put("serverNow",now);result.put("evidenceLevel","INVENTORY_ONLY");return result;
    }
    private Map<String,Object> holdView(InventoryRepository.Hold h,Instant now) {
        var result=new LinkedHashMap<String,Object>();result.put("holdId",h.id());result.put("roomId",h.room());result.put("sessionStart",h.start());result.put("sessionEnd",h.end());result.put("bufferMinutes",h.buffer());result.put("blockedUntil",h.until());result.put("timezone",h.timezone());result.put("createdAt",h.created());result.put("expiresAt",h.expires());result.put("state",h.state());result.put("revision",h.revision());result.put("serverNow",now);result.put("evidenceLevel","INVENTORY_ONLY");return result;
    }
}
