package com.cinenest.iot.internal;

import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.rooms.InventoryConstraints;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name="cinenest.iot.lock-room-assignment.enabled", havingValue="true")
final class LockRoomAssignmentService {
    record Result(int status, Object body) {}
    private final LockRoomAssignmentPolicy policy;
    private final LockRoomAssignmentRepository repository;
    private final ScopedAuthorization authorization;
    private final InventoryConstraints rooms;
    private final TransactionTemplate transaction;
    LockRoomAssignmentService(LockRoomAssignmentPolicy policy, LockRoomAssignmentRepository repository,
            ScopedAuthorization authorization, InventoryConstraints rooms, PlatformTransactionManager manager) {
        this.policy=policy; this.repository=repository; this.authorization=authorization; this.rooms=rooms;
        transaction=new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    private void permit(UUID actor, UUID venue, String permission) {
        if (actor==null) throw new LockRoomAssignmentPolicy.Failure(401);
        if (!authorization.eligible(actor,true) || !authorization.member(actor,venue,true)
            || !authorization.permits(actor,venue,permission,true)) throw new LockRoomAssignmentPolicy.Failure(403);
    }
    Result run(UUID actor, String rawVenue, String rawRoom, String rawAssignment,
               String rawPage, boolean invalid, byte[] body, String operation) {
        try { policy.configured(); } catch (LockRoomAssignmentPolicy.Failure failure) { return error(failure.status); }
        UUID correlation=UUID.randomUUID();
        try {
            UUID venue=LockRoomAssignmentPolicy.uuid(rawVenue);
            return transaction.execute(status -> {
                String permission=operation.equals("CREATE") ? LockRoomAssignmentPolicy.CREATE : LockRoomAssignmentPolicy.READ;
                permit(actor,venue,permission);
                if (invalid || !Set.of("CREATE","LIST","DETAIL").contains(operation)) throw new LockRoomAssignmentPolicy.Failure(400);
                UUID room=LockRoomAssignmentPolicy.uuid(rawRoom);
                // Public Rooms port owns both the configuration lock and authoritative venue/room facts.
                rooms.lock(venue);
                var context=rooms.inspect(room).filter(f -> venue.equals(f.venueId()) && room.equals(f.roomId()))
                    .orElseThrow(() -> new LockRoomAssignmentPolicy.Failure(404));
                if (operation.equals("CREATE")) {
                    var command=policy.parse(venue,room,body);
                    var prior=repository.receipt(actor,command.key());
                    if (prior.isPresent()) {
                        var receipt=prior.get(); var assignment=receipt.assignment();
                        if (!venue.equals(assignment.venue()) || !room.equals(assignment.room())
                            || !MessageDigest.isEqual(command.hash(),receipt.hash())) throw new LockRoomAssignmentPolicy.Failure(409);
                        permit(actor,venue,permission); // Recheck validity after lock waits, including on replay.
                        repository.audit(actor,venue,room,assignment.id(),"REPLAY",null,correlation,repository.now());
                        return new Result(200,view(assignment,true));
                    }
                    if (!command.roomConfiguration().equals(context.configuration())) throw new LockRoomAssignmentPolicy.Failure(409);
                    long deviceRevision=repository.lockDevice(venue,command.device())
                        .orElseThrow(() -> new LockRoomAssignmentPolicy.Failure(404));
                    if (deviceRevision!=command.deviceRevision()) throw new LockRoomAssignmentPolicy.Failure(409);
                    permit(actor,venue,permission);
                    return new Result(201,view(repository.create(actor,venue,room,command,correlation),false));
                }
                permit(actor,venue,permission);
                if (operation.equals("LIST")) {
                    int page=LockRoomAssignmentPolicy.page(rawPage);
                    var rows=repository.page(venue,room,page); var at=repository.now();
                    repository.audit(actor,venue,room,null,"LIST_READ",null,correlation,at);
                    return new Result(200,Map.of("items",rows.stream().limit(25).map(a -> view(a,false)).toList(),
                        "page",page,"size",25,"hasNext",rows.size()>25,"observedAt",at.toString(),
                        "roomConfiguration",context.configuration(),"customerAccessEnabled",false,"mode","QUALIFICATION_ONLY"));
                }
                UUID id=LockRoomAssignmentPolicy.uuid(rawAssignment);
                var row=repository.detail(venue,room,id).orElseThrow(() -> new LockRoomAssignmentPolicy.Failure(404));
                repository.audit(actor,venue,room,id,"DETAIL_READ",null,correlation,repository.now());
                return new Result(200,view(row,false));
            });
        } catch (LockRoomAssignmentPolicy.Failure failure) { return denied(actor,correlation,failure.status); }
        catch (DataIntegrityViolationException conflict) { return denied(actor,correlation,409); }
        catch (RuntimeException failure) { return denied(actor,correlation,503); }
    }
    private Result denied(UUID actor, UUID correlation, int status) {
        try { transaction.executeWithoutResult(tx -> repository.audit(actor,null,null,null,"DENIED",
            switch(status) { case 400 -> "INVALID_REQUEST"; case 401,403 -> "ACCESS_DENIED";
                case 404 -> "NOT_FOUND"; case 409 -> "CONFLICT"; default -> "UNAVAILABLE"; },correlation,repository.now())); }
        catch (RuntimeException failure) { return error(503); }
        return error(status);
    }
    private Result error(int status) { return new Result(status,Map.of("message","Room lock assignment unavailable, changed or denied.",
        "mode","QUALIFICATION_ONLY","customerAccessEnabled",false)); }
    private Map<String,Object> view(LockRoomAssignmentRepository.Assignment a, boolean replay) {
        var result=new LinkedHashMap<String,Object>();
        result.put("assignmentId",a.id()); result.put("venueId",a.venue()); result.put("roomId",a.room());
        result.put("deviceId",a.device()); result.put("revision",a.revision());
        result.put("expectedRoomConfiguration",a.configuration()); result.put("expectedDeviceMetadataRevision",a.deviceRevision());
        result.put("createdBy",a.actor()); result.put("createdAt",a.createdAt().toString());
        result.put("state","UNQUALIFIED"); result.put("customerAccessEnabled",false);
        result.put("physicalAcceptance","NOT_VERIFIED"); result.put("mode","QUALIFICATION_ONLY"); result.put("replayed",replay);
        return result;
    }
}
