package com.cinenest.booking.internal;

import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.rooms.InventoryConstraints;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
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
    private static final String CREATE_OPERATIONAL_HOLD = "commercial.hold.create";
    private static final String ISSUE_OPERATIONAL_QUOTE = "commercial.quote.issue";
    private final boolean enabled;
    private final boolean operationalEnabled;
    private final boolean operationalIssuanceEnabled;
    private final ObjectProvider<OperationalPackageValidator> operationalPackages;
    private final int ttl,rate,seconds;
    private final String reference;
    InventoryService(InventoryRepository repository,InventoryConstraints rooms,ScopedAuthorization identity,PlatformTransactionManager transactions,
        @Value("${cinenest.booking.holds.enabled:false}") boolean enabled,
        @Value("${cinenest.booking.holds.ttl-seconds:600}") int ttl,
        @Value("${cinenest.booking.holds.rate-count:0}") int rate,
        @Value("${cinenest.booking.holds.rate-seconds:0}") int seconds,
        @Value("${cinenest.booking.holds.approval-reference:}") String reference,
        @Value("${cinenest.commercial.operational.enabled:false}") boolean operationalEnabled,
        @Value("${cinenest.commercial.operational.issuance-enabled:false}") boolean operationalIssuanceEnabled,
        ObjectProvider<OperationalPackageValidator> operationalPackages) {
        this.repository=repository;this.rooms=rooms;this.identity=identity;this.tx=new TransactionTemplate(transactions);
        this.enabled=enabled;this.ttl=ttl;this.rate=rate;this.seconds=seconds;this.reference=reference;
        this.operationalEnabled=operationalEnabled;
        this.operationalIssuanceEnabled=operationalIssuanceEnabled;
        this.operationalPackages=operationalPackages;
    }
    private boolean operational(UUID actor) {
        if (operationalEnabled && identity.operationalPrincipal(actor, true)) {
            return true;
        }
        if (!identity.eligible(actor, true)) {
            throw denied();
        }
        return false;
    }

    private Optional<ScopedAuthorization.MatchedGrant> matchedGrant(
        UUID actor, UUID venue, String permission, boolean lock) {
        return identity.matchedOperationalGrant(actor, venue, permission, lock)
            .filter(grant -> actor.equals(grant.account()) && venue.equals(grant.venue())
                && permission.equals(grant.permission()));
    }

    private ScopedAuthorization.MatchedGrant operationalGrant(UUID actor, UUID venue, boolean lock) {
        return matchedGrant(actor, venue, CREATE_OPERATIONAL_HOLD, lock).orElseThrow(InventoryService::denied);
    }

    private static InventoryFailure denied() {
        return new InventoryFailure(403, "Access denied");
    }

    private UUID venueFor(UUID room) {
        return rooms.venue(room).orElseThrow(() -> new InventoryFailure(404, "Room not found"));
    }
    private void configured() {if(!enabled || ttl<=0 || rate<=0 || seconds<=0 || reference.isBlank())throw new InventoryFailure(503,"Inventory hold creation is not configured");}
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
            boolean operational = operational(actor);
            UUID venue = venueFor(room);
            if (operational) {
                operationalGrant(actor, venue, true);
            }
            rooms.lock(venue);
            var f = rooms.inspect(room).orElseThrow(() -> new InventoryFailure(404, "Room not found"));
            if (!venue.equals(f.venueId())) {
                throw denied();
            }
            reconcileLocked(f.venueId());Instant now=repository.now();
            var c=InventoryRules.candidate(f,InventoryRules.local(local,f.timezone()),duration,guests);
            if(!c.start().isAfter(now))throw InventoryRules.invalid();
            var result=candidateView(f,c,now);result.put("available",!repository.overlaps(room,c.start(),c.blockedUntil()));
            if (operational) {
                result.put("mode", "OPERATIONAL");
            }
            return result;
        });
    }
    Map<String,Object> create(UUID actor,Map<String,Object> body) {
        configured();
        // Admission has its own committed transaction: invalid/conflicting requests consume budget too.
        Boolean admitted=tx.execute(status->{
            if (operational(actor)) {
                repository.lockOperationalOwner(actor);
            }
            if(body.get("idempotencyKey") instanceof String raw) {
                try {var prior=repository.command(actor,UUID.fromString(raw));if(prior.isPresent() && prior.get().action().equals("CREATE") && prior.get().hash().equals(hash("CREATE",body)))return true;}catch(IllegalArgumentException invalidKey) { /* Invalid requests still consume admission budget. */ }
            }
            if(repository.attempts(actor,seconds)>=rate)return false;repository.audit(actor,null,"CREATE_ATTEMPT",null,null);return true;});
        if(!Boolean.TRUE.equals(admitted))throw new InventoryFailure(429,"Too many hold attempts; wait before retrying");
        UUID room=InventoryRules.id(InventoryRules.text(body,"roomId")),key=InventoryRules.id(InventoryRules.text(body,"idempotencyKey"));String hash=hash("CREATE",body);
        return tx.execute(status->{
            boolean operational = operational(actor);
            if (operational) {
                repository.lockOperationalOwner(actor);
                InventoryRules.keys(body, "roomId", "sessionStartLocal", "durationMinutes", "guests",
                    "expectedConfiguration", "idempotencyKey", "packageId", "packageVersionId");
            } else {
                InventoryRules.keys(body, "roomId", "sessionStartLocal", "durationMinutes", "guests",
                    "expectedConfiguration", "idempotencyKey");
            }
            var receipt=repository.command(actor,key);
            if(receipt.isPresent())return replay(actor,receipt.get(),hash,"CREATE",operational);
            UUID venue = venueFor(room);
            var proof = operational ? validateOperationalPackage(actor, venue, room, body) : null;
            var venues=new HashSet<UUID>();venues.add(venue);repository.activeOwnerVenue(actor).ifPresent(venues::add);lockVenues(venues);
            venues.forEach(this::reconcileLocked);
            var f=rooms.inspect(room).orElseThrow(()->new InventoryFailure(404,"Room not found"));
            if (!venue.equals(f.venueId())) {
                throw denied();
            }
            requireConfiguration(f, body);
            Instant now=repository.now();
            var c = candidate(f, body);
            if(!c.start().isAfter(now))throw InventoryRules.invalid();
            if(repository.activeOwnerVenue(actor).isPresent())throw new InventoryFailure(409,"Release the existing active hold first");
            if(repository.overlaps(room,c.start(),c.blockedUntil()))throw new InventoryFailure(409,"Inventory conflict; select another interval");
            Instant expiry=InventoryRules.expires(now,c.start(),ttl);
            UUID policy=UUID.nameUUIDFromBytes((ttl+":"+rate+":"+seconds+":"+reference).getBytes(StandardCharsets.UTF_8));repository.policy(policy,ttl,rate,seconds,reference);
            var hold=new InventoryRepository.Hold(UUID.randomUUID(),actor,UUID.randomUUID(),venue,room,c.start(),c.end(),c.buffer(),c.blockedUntil(),now,expiry,"ACTIVE",0,InventoryRules.positive(body,"guests"),f.timezone(),f.configuration(),c.policyIds());
            if (proof != null) {
                var currentGrant = operationalGrant(actor, venue, false);
                if (!proof.grantId().equals(currentGrant.grantId()) || proof.grantRevision() != currentGrant.grantRevision()
                    || proof.credentialEpoch() != currentGrant.credentialEpoch()) {
                    throw denied();
                }
            }
            repository.create(hold,policy);
            if (proof != null) {
                repository.provenance(hold.id(), proof);
            }
            repository.audit(actor,hold.id(),"CREATED",null,"ACTIVE");
            repository.command(actor,key,hash,hold.id(),"CREATE");
            return holdView(hold,repository.now(),Optional.ofNullable(proof));
        });
    }
    private InventoryRepository.OperationalProvenance validateOperationalPackage(
        UUID actor, UUID venue, UUID room, Map<String,Object> body) {
        var grant = operationalGrant(actor, venue, true);
        UUID packageId = InventoryRules.id(InventoryRules.text(body, "packageId"));
        UUID versionId = InventoryRules.id(InventoryRules.text(body, "packageVersionId"));
        var packages = operationalPackages.getIfUnique();
        if (packages == null) {
            throw new InventoryFailure(503, "Operational packages are unavailable");
        }
        // Pricing locks precede Rooms locks. Recheck the same configuration after taking the venue lock.
        var facts = rooms.inspect(room).orElseThrow(() -> new InventoryFailure(404, "Room not found"));
        if (!venue.equals(facts.venueId())) {
            throw denied();
        }
        requireConfiguration(facts, body);
        var interval = candidate(facts, body);
        if (!packages.eligible(venue, room, packageId, versionId, interval.start(), interval.end(),
            InventoryRules.positive(body, "guests"))) {
            throw new InventoryFailure(409, "Published package is unavailable for this hold");
        }
        return new InventoryRepository.OperationalProvenance(packageId, versionId, grant.grantId(),
            grant.grantRevision(), grant.credentialEpoch());
    }

    private static void requireConfiguration(InventoryConstraints.Facts facts, Map<String,Object> body) {
        if (!facts.configuration().equals(InventoryRules.text(body, "expectedConfiguration"))) {
            throw new InventoryFailure(409, "Room configuration changed; inquire again");
        }
    }

    private static InventoryRules.Candidate candidate(InventoryConstraints.Facts facts, Map<String,Object> body) {
        return InventoryRules.candidate(facts,
            InventoryRules.local(InventoryRules.text(body, "sessionStartLocal"), facts.timezone()),
            InventoryRules.positive(body, "durationMinutes"), InventoryRules.positive(body, "guests"));
    }

    private Map<String,Object> replay(UUID actor, InventoryRepository.Command receipt, String hash, String action,
        boolean operational) {
        if (!receipt.hash().equals(hash) || !receipt.action().equals(action)) {
            throw new InventoryFailure(409, "Idempotency key already used with another request");
        }
        return readLocked(actor, receipt.hold(), operational);
    }

    private Optional<InventoryRepository.OperationalProvenance> authorizeExisting(
        UUID actor, InventoryRepository.Hold hold, boolean operational) {
        var proof = repository.provenance(hold.id());
        if (proof.isPresent() != operational) {
            throw denied();
        }
        if (operational) {
            operationalGrant(actor, hold.venue(), true);
        }
        return proof;
    }

    private Map<String,Object> readLocked(UUID actor, UUID id, boolean operational) {
        var hold = owned(actor,id);
        var proof = authorizeExisting(actor, hold, operational);
        rooms.lock(hold.venue());
        reconcileLocked(hold.venue());
        return holdView(owned(actor,id),repository.now(),proof);
    }
    private InventoryRepository.Hold owned(UUID actor,UUID id) {
        var h=repository.hold(id).orElseThrow(()->new InventoryFailure(404,"Hold not found"));
        if(!h.owner().equals(actor))throw new InventoryFailure(403,"Access denied");return h;
    }
    Map<String,Object> read(UUID actor,UUID id) {
        return tx.execute(status -> readLocked(actor, id, operational(actor)));
    }
    Map<String,Object> release(UUID actor,UUID id,Map<String,Object> body) {
        InventoryRules.keys(body,"expectedRevision","idempotencyKey");UUID key=InventoryRules.id(InventoryRules.text(body,"idempotencyKey"));String hash=hash("RELEASE:"+id,body);long revision=InventoryRules.revision(body);
        return tx.execute(status->{
            boolean operational = operational(actor);
            if (operational) {
                repository.lockOperationalOwner(actor);
            }
            var receipt=repository.command(actor,key);
            if(receipt.isPresent())return replay(actor,receipt.get(),hash,"RELEASE",operational);
            var h=owned(actor,id);
            var proof = authorizeExisting(actor, h, operational);
            rooms.lock(h.venue());reconcileLocked(h.venue());h=owned(actor,id);
            if(h.revision()!=revision)throw new InventoryFailure(409,"Hold changed; refresh its state");
            if(h.state().equals("ACTIVE"))repository.transition(h,"RELEASED",actor);
            repository.command(actor,key,hash,id,"RELEASE");return holdView(owned(actor,id),repository.now(),proof);});
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
        return repository.hold(hold).filter(h -> repository.provenance(h.id()).isEmpty()).map(InventoryService::quotationFacts);
    }
    public Optional<com.cinenest.booking.QuotationHolds.Facts> lockAndInspect(UUID actor,UUID hold) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Quotation hold inspection requires caller transaction");
        var h=repository.hold(hold);
        if(h.isEmpty() || !h.get().owner().equals(actor) || repository.provenance(hold).isPresent())return Optional.empty();
        rooms.lock(h.get().venue());reconcileLocked(h.get().venue());
        return repository.hold(hold).map(InventoryService::quotationFacts);
    }
    @Override
    public Optional<OperationalFacts> locateOperational(UUID actor, UUID hold) {
        if (!operationalEnabled || !identity.operationalPrincipal(actor, false)) {
            return Optional.empty();
        }
        return repository.hold(hold).filter(h -> h.owner().equals(actor)).flatMap(h ->
            repository.provenance(h.id()).map(proof ->
                new OperationalFacts(quotationFacts(h), proof.packageId(), proof.versionId())));
    }

    @Override
    public Optional<OperationalFacts> lockAndInspectOperational(UUID actor, UUID hold) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Operational hold inspection requires caller transaction");
        }
        var found = locateOperational(actor, hold);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        UUID venue = found.get().hold().venue();
        rooms.lock(venue);
        reconcileLocked(venue);
        return locateOperational(actor, hold);
    }

    private boolean currentValid(InventoryRepository.Hold h,boolean observation) {
        try {
            var f=(observation?rooms.observe(h.room()):rooms.inspect(h.room())).orElseThrow(InventoryRules::invalid);
            var c=InventoryRules.candidate(f,h.start(),Math.toIntExact(Duration.between(h.start(),h.end()).toMinutes()),h.guests());
            return f.timezone().equals(h.timezone())&&c.blockedUntil().equals(h.until());
        }catch(InventoryFailure invalid){return false;}
    }
    public Optional<com.cinenest.booking.QuotationHolds.Facts> observe(UUID actor,UUID hold) {
        return repository.hold(hold)
            .filter(h -> h.owner().equals(actor) && repository.provenance(h.id()).isEmpty()).map(h->{
            String state=h.state();
            if(state.equals("ACTIVE"))state=!repository.now().isBefore(h.expires())?"EXPIRED":!currentValid(h,true)?"INVALIDATED":"ACTIVE";
            var f=quotationFacts(h);
            return new com.cinenest.booking.QuotationHolds.Facts(f.id(),f.owner(),f.venue(),f.room(),f.start(),f.end(),f.expires(),state,f.revision(),f.guests(),f.timezone(),f.bufferMinutes(),f.blockedUntil(),f.configuration());
        });
    }
    private void reconcileLocked(UUID venue) {
        for(var h:repository.active(venue)) {
            if(!repository.now().isBefore(h.expires())) {repository.transition(h,"EXPIRED",null);continue;}
            boolean valid=currentValid(h,false);
            if(!valid)repository.transition(h,"INVALIDATED",null);
        }
    }
    private Map<String,Object> candidateView(InventoryConstraints.Facts f,InventoryRules.Candidate c,Instant now) {
        var result=new LinkedHashMap<String,Object>();result.put("roomId",f.roomId());result.put("timezone",f.timezone());result.put("sessionStart",c.start());result.put("sessionEnd",c.end());result.put("bufferMinutes",c.buffer());result.put("blockedUntil",c.blockedUntil());result.put("configuration",f.configuration());result.put("serverNow",now);result.put("evidenceLevel","INVENTORY_ONLY");return result;
    }
    private Map<String,Object> holdView(InventoryRepository.Hold h, Instant now, Optional<InventoryRepository.OperationalProvenance> proof) {
        var result=new LinkedHashMap<String,Object>();result.put("holdId",h.id());result.put("roomId",h.room());result.put("sessionStart",h.start());result.put("sessionEnd",h.end());result.put("bufferMinutes",h.buffer());result.put("blockedUntil",h.until());result.put("timezone",h.timezone());result.put("createdAt",h.created());result.put("expiresAt",h.expires());result.put("state",h.state());result.put("revision",h.revision());result.put("serverNow",now);result.put("evidenceLevel","INVENTORY_ONLY");
        proof.ifPresent(source -> {
            result.put("mode", "OPERATIONAL");
            result.put("venueId", h.venue());
            if (operationalIssuanceEnabled && matchedGrant(h.owner(), h.venue(), ISSUE_OPERATIONAL_QUOTE, false).isPresent()) {
                result.put("commercialQuotationPermitted", true);
            }
            result.put("packageId", source.packageId());
            result.put("packageVersionId", source.versionId());
        });
        return result;
    }
}
