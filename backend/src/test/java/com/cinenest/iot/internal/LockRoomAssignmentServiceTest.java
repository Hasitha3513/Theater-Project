package com.cinenest.iot.internal;

import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.rooms.InventoryConstraints;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LockRoomAssignmentServiceTest {
    final UUID actor=UUID.randomUUID(),venue=LockRoomAssignmentPolicyTest.VENUE,room=LockRoomAssignmentPolicyTest.ROOM,device=LockRoomAssignmentPolicyTest.DEVICE;
    final LockRoomAssignmentRepository repo=mock(LockRoomAssignmentRepository.class);
    final ScopedAuthorization auth=mock(ScopedAuthorization.class);
    final InventoryConstraints rooms=mock(InventoryConstraints.class);
    final Transactions manager=new Transactions();
    final LockRoomAssignmentPolicy policy=new LockRoomAssignmentPolicy(LockRoomAssignmentPolicyTest.enabled("local-synthetic"));
    final LockRoomAssignmentRepository.Assignment assignment=new LockRoomAssignmentRepository.Assignment(UUID.randomUUID(),venue,room,device,1,"0:0:",1,actor,Instant.parse("2030-01-01T00:00:00Z"));
    LockRoomAssignmentService service;
    static class Transactions extends AbstractPlatformTransactionManager {
        int commits,rollbacks;
        protected Object doGetTransaction(){return new Object();}
        protected void doBegin(Object tx,TransactionDefinition definition){assertThat(definition.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);}
        protected void doCommit(DefaultTransactionStatus status){commits++;}
        protected void doRollback(DefaultTransactionStatus status){rollbacks++;}
    }
    @BeforeEach void setup() {
        when(auth.eligible(actor,true)).thenReturn(true);when(auth.member(actor,venue,true)).thenReturn(true);
        when(auth.permits(eq(actor),eq(venue),anyString(),eq(true))).thenReturn(true);
        when(rooms.inspect(room)).thenReturn(Optional.of(new InventoryConstraints.Facts(venue,room,"Asia/Colombo",false,1,null,false,"0:0:",List.of(),List.of())));
        when(repo.receipt(any(),any())).thenReturn(Optional.empty());when(repo.lockDevice(venue,device)).thenReturn(Optional.of(1L));
        when(repo.now()).thenReturn(Instant.parse("2030-01-01T00:00:00Z"));when(repo.create(eq(actor),eq(venue),eq(room),any(),any())).thenReturn(assignment);
        when(repo.detail(venue,room,assignment.id())).thenReturn(Optional.of(assignment));when(repo.page(venue,room,0)).thenReturn(List.of(assignment));
        service=new LockRoomAssignmentService(policy,repo,auth,rooms,manager);
    }
    LockRoomAssignmentService.Result run(String op,String payload) { return service.run(actor,venue.toString(),room.toString(),assignment.id().toString(),null,false,payload==null?null:payload.getBytes(java.nio.charset.StandardCharsets.UTF_8),op); }
    @Test void createsQualificationOnlyAndRechecksAuthorityAfterLocks() {
        var r=run("CREATE",LockRoomAssignmentPolicyTest.payload());assertThat(r.status()).isEqualTo(201);
        assertThat(((Map<?,?>)r.body()).get("state")).isEqualTo("UNQUALIFIED");
        assertThat(((Map<?,?>)r.body()).get("customerAccessEnabled")).isEqualTo(false);
        var order=inOrder(auth,rooms,repo);order.verify(auth).eligible(actor,true);order.verify(auth).member(actor,venue,true);order.verify(auth).permits(actor,venue,LockRoomAssignmentPolicy.CREATE,true);
        order.verify(rooms).lock(venue);order.verify(rooms).inspect(room);order.verify(repo).receipt(any(),any());order.verify(repo).lockDevice(venue,device);
        order.verify(auth).eligible(actor,true);order.verify(auth).member(actor,venue,true);order.verify(auth).permits(actor,venue,LockRoomAssignmentPolicy.CREATE,true);order.verify(repo).create(eq(actor),eq(venue),eq(room),any(),any());
        assertThat(manager.commits).isEqualTo(1);
    }
    @Test void anonymousOperatorDeniedBeforeRoomInspection() { assertThat(service.run(null,venue.toString(),room.toString(),null,null,false,new byte[0],"CREATE").status()).isEqualTo(401);verifyNoInteractions(rooms); }
    @Test void ineligibleAndWrongVenueAreDeniedBeforeDiscovery() {
        when(auth.eligible(actor,true)).thenReturn(false);assertThat(run("CREATE",LockRoomAssignmentPolicyTest.payload()).status()).isEqualTo(403);verifyNoInteractions(rooms);
        when(auth.eligible(actor,true)).thenReturn(true);when(auth.member(actor,venue,true)).thenReturn(false);assertThat(run("LIST",null).status()).isEqualTo(403);verifyNoInteractions(rooms);
    }
    @Test void readDoesNotImplyCreateOrCreateImplyRead() {
        when(auth.permits(actor,venue,LockRoomAssignmentPolicy.CREATE,true)).thenReturn(false);
        assertThat(run("LIST",null).status()).isEqualTo(200);assertThat(run("CREATE",LockRoomAssignmentPolicyTest.payload()).status()).isEqualTo(403);
        when(auth.permits(actor,venue,LockRoomAssignmentPolicy.CREATE,true)).thenReturn(true);when(auth.permits(actor,venue,LockRoomAssignmentPolicy.READ,true)).thenReturn(false);assertThat(run("DETAIL",null).status()).isEqualTo(403);
    }
    @Test void wrongRoomContextAndMissingRoomDoNotLeakAssignments() {
        when(rooms.inspect(room)).thenReturn(Optional.of(new InventoryConstraints.Facts(UUID.randomUUID(),room,"Asia/Colombo",true,1,0,false,"0:0:",List.of(),List.of())));
        assertThat(run("LIST",null).status()).isEqualTo(404);verify(repo,never()).page(any(),any(),anyInt());
        when(rooms.inspect(room)).thenReturn(Optional.empty());assertThat(run("DETAIL",null).status()).isEqualTo(404);
    }
    @Test void staleRoomConfigurationIsRejected() { assertThat(run("CREATE",LockRoomAssignmentPolicyTest.payload().replace("0:0:","stale")).status()).isEqualTo(409);verify(repo,never()).create(any(),any(),any(),any(),any()); }
    @Test void staleOrForeignDeviceIsRejected() {
        when(repo.lockDevice(venue,device)).thenReturn(Optional.of(2L));assertThat(run("CREATE",LockRoomAssignmentPolicyTest.payload()).status()).isEqualTo(409);
        when(repo.lockDevice(venue,device)).thenReturn(Optional.empty());assertThat(run("CREATE",LockRoomAssignmentPolicyTest.payload()).status()).isEqualTo(404);verify(repo,never()).create(any(),any(),any(),any(),any());
    }
    @Test void authorityExpiryAfterDeviceLockPreventsCreation() { when(auth.permits(actor,venue,LockRoomAssignmentPolicy.CREATE,true)).thenReturn(true,false);assertThat(run("CREATE",LockRoomAssignmentPolicyTest.payload()).status()).isEqualTo(403);verify(repo,never()).create(any(),any(),any(),any(),any()); }
    @Test void duplicateRoomAndDeviceConflictAreSanitized() { when(repo.create(any(),any(),any(),any(),any())).thenThrow(new DataIntegrityViolationException("private uniqueness detail"));var r=run("CREATE",LockRoomAssignmentPolicyTest.payload());assertThat(r.status()).isEqualTo(409);assertThat(r.body().toString()).doesNotContain("private","uniqueness");assertThat(manager.rollbacks).isEqualTo(1); }
    @Test void replayIsReauthorizedAndReturnsOriginalIdentityEvenAfterConfigurationChanges() {
        var command=policy.parse(venue,room,LockRoomAssignmentPolicyTest.payload().getBytes(java.nio.charset.StandardCharsets.UTF_8));when(repo.receipt(actor,command.key())).thenReturn(Optional.of(new LockRoomAssignmentRepository.Receipt(assignment,command.hash())));
        when(rooms.inspect(room)).thenReturn(Optional.of(new InventoryConstraints.Facts(venue,room,"Asia/Colombo",false,1,null,false,"1:1:",List.of(),List.of())));
        assertThat(run("CREATE",LockRoomAssignmentPolicyTest.payload()).status()).isEqualTo(200);verify(repo,never()).create(any(),any(),any(),any(),any());verify(repo).audit(eq(actor),eq(venue),eq(room),eq(assignment.id()),eq("REPLAY"),isNull(),any(),any());
    }
    @Test void changedPayloadReplayCannotCreateAnotherAssignment() {
        when(repo.receipt(any(),any())).thenReturn(Optional.of(new LockRoomAssignmentRepository.Receipt(assignment,new byte[32])));assertThat(run("CREATE",LockRoomAssignmentPolicyTest.payload()).status()).isEqualTo(409);verify(repo,never()).create(any(),any(),any(),any(),any());
    }
    @Test void auditPersistenceFailureReturnsUnavailableAndRollsBack() {
        when(repo.create(any(),any(),any(),any(),any())).thenThrow(new IllegalStateException("audit failed"));assertThat(run("CREATE",LockRoomAssignmentPolicyTest.payload()).status()).isEqualTo(503);assertThat(manager.rollbacks).isEqualTo(1);
    }
    @Test void listAndDetailRequireAtomicReadAuditAndExactRepositoryScope() {
        assertThat(run("LIST",null).status()).isEqualTo(200);assertThat(run("DETAIL",null).status()).isEqualTo(200);verify(repo).detail(venue,room,assignment.id());
        doThrow(new IllegalStateException()).when(repo).audit(eq(actor),eq(venue),eq(room),any(),eq("DETAIL_READ"),isNull(),any(),any());assertThat(run("DETAIL",null).status()).isEqualTo(503);
    }
    @Test void invalidInputNeverReachesCreateAndDenialAuditFailureFailsClosed() {
        assertThat(run("CREATE","{}").status()).isEqualTo(400);verify(repo,never()).create(any(),any(),any(),any(),any());
        doThrow(new IllegalStateException()).when(repo).audit(any(),isNull(),isNull(),isNull(),eq("DENIED"),any(),any(),any());assertThat(run("CREATE","{}").status()).isEqualTo(503);
    }
    @Test void disabledServiceNeverTouchesPersistence() {
        var off=new LockRoomAssignmentService(new LockRoomAssignmentPolicy(new org.springframework.mock.env.MockEnvironment()),repo,auth,rooms,manager);
        assertThat(off.run(actor,venue.toString(),room.toString(),null,null,false,null,"LIST").status()).isEqualTo(503);verifyNoInteractions(repo);
    }
}
