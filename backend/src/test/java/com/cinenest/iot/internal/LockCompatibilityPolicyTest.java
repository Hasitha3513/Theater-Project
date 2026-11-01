package com.cinenest.iot.internal;
import com.cinenest.access.SmartLock;
import com.cinenest.iot.LockIntegration.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
class LockCompatibilityPolicyTest {
    private final Instant now = Instant.parse("2030-01-01T00:00:00Z");
    private final LockCompatibilityPolicy policy = new LockCompatibilityPolicy();
    private final SmartLock.DeviceBinding binding = new SmartLock.DeviceBinding(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1);
    private Device device() { return new Device(binding, 1, "P", "TEST", "F", "1"); }
    private Descriptor descriptor() { return new Descriptor(new AdapterId("P", "A", "TEST", "1"), Map.of("F", Set.of("1"))); }
    private CapabilityReport report(Device d, Map<Capability,Support> caps) { return new CapabilityReport(d, caps, Set.of(new SmartLock.PinFormat(7)), now, now.plusSeconds(30)); }
    private Decision check(Device d, Device expected, CapabilityReport report) { return policy.evaluate(descriptor(), d, expected, report, Set.of(Capability.SCHEDULED_PIN), now); }
    @ParameterizedTest @EnumSource(Support.class) void allCapabilityStatesHaveExplicitFailClosedSemantics(Support state) {
        var d = device(); var result = check(d, d, report(d, Map.of(Capability.SCHEDULED_PIN, state)));
        assertThat(result.reason()).isEqualTo(switch(state) {case SUPPORTED -> Reason.CONTRACT_COMPATIBLE;case UNSUPPORTED -> Reason.UNSUPPORTED_CAPABILITY;case UNKNOWN -> Reason.UNKNOWN_CAPABILITY;case REQUIRES_PHYSICAL_VERIFICATION -> Reason.PHYSICAL_VERIFICATION_REQUIRED;});
        assertThat(result.customerAccessEnabled()).isFalse(); assertThat(result.assignmentState()).isEqualTo("UNQUALIFIED");
    }
    @Test void nullRequiredCapabilityAndUnknownFirmwareCannotBecomeCompatible() {
        var d=device(); var invalid=new HashSet<Capability>(); invalid.add(null);
        assertThatThrownBy(() -> policy.evaluate(descriptor(),d,d,report(d,Map.of()),invalid,now)).isInstanceOf(NullPointerException.class);
        var unknown=new Device(binding,1,"P","TEST","F","UNKNOWN");
        var declared=new Descriptor(new AdapterId("P","A","TEST","1"),Map.of("F",Set.of("UNKNOWN")));
        assertThat(policy.evaluate(declared,unknown,unknown,report(unknown,Map.of(Capability.SCHEDULED_PIN,Support.SUPPORTED)),Set.of(Capability.SCHEDULED_PIN),now).reason()).isEqualTo(Reason.MISSING_METADATA);
    }
    @Test void missingDeclarationsNeverInheritManufacturerOrProviderSupport() { var d=device(); assertThat(check(d,d,report(d,Map.of())).reason()).isEqualTo(Reason.UNKNOWN_CAPABILITY); }
    @Test void missingMetadataAndUnsupportedFirmwareRejected() {
        var d=device(); assertThat(check(null,d,null).reason()).isEqualTo(Reason.MISSING_METADATA);
        for (String missing : new String[]{null,""," "}) { var x=new Device(binding,1,"P","TEST","F",missing); assertThat(check(x,x,report(x,Map.of())).reason()).isEqualTo(Reason.MISSING_METADATA); }
        var x=new Device(binding,1,"P","TEST","F","2"); assertThat(check(x,x,report(x,Map.of())).reason()).isEqualTo(Reason.FIRMWARE_UNSUPPORTED);
    }
    @Test void wrongProviderProtocolAndFamilyDoNotFallback() {
        for (Device d : List.of(new Device(binding,1,"OTHER","TEST","F","1"),new Device(binding,1,"P","OTHER","F","1")))
            assertThat(check(d,d,report(d,Map.of())).reason()).isEqualTo(Reason.ADAPTER_MISMATCH);
        var d=new Device(binding,1,"P","TEST","OTHER","1"); assertThat(check(d,d,report(d,Map.of())).reason()).isEqualTo(Reason.FIRMWARE_UNSUPPORTED);
    }
    @Test void wrongVenueRoomDeviceOrAssignmentDenied() {
        var d=device(); var ids=List.of(binding.venueId(),binding.roomId(),binding.deviceId(),binding.assignmentId());
        for(int i=0;i<4;i++){var x=new ArrayList<>(ids);x.set(i,UUID.randomUUID());var b=new SmartLock.DeviceBinding(x.get(0),x.get(1),x.get(2),x.get(3),1);var bad=new Device(b,1,"P","TEST","F","1");assertThat(check(bad,d,report(bad,Map.of())).reason()).isEqualTo(Reason.SCOPE_MISMATCH);}
    }
    @Test void assignmentAndMetadataRevisionAndReportBindingAreExact() {
        var d=device();var b=new SmartLock.DeviceBinding(binding.venueId(),binding.roomId(),binding.deviceId(),binding.assignmentId(),2);
        for(var x:List.of(new Device(binding,2,"P","TEST","F","1"),new Device(b,1,"P","TEST","F","1"))) assertThat(check(x,d,report(x,Map.of())).reason()).isEqualTo(Reason.REVISION_MISMATCH);
        assertThat(check(d,d,report(new Device(binding,2,"P","TEST","F","1"),Map.of())).reason()).isEqualTo(Reason.REVISION_MISMATCH);
    }
    @Test void staleFutureAndBoundaryReportsDenied() {
        var d=device(); for(var t:List.of(now.minusNanos(1),now.plusSeconds(30))) assertThat(policy.evaluate(descriptor(),d,d,report(d,Map.of()),Set.of(),t).reason()).isEqualTo(Reason.STALE_DECLARATION);
    }
    @Test void requiredPinNeedsDeclaredFormatAndAllRequiredCapabilities() {
        var d=device();var caps=Map.of(Capability.SCHEDULED_PIN,Support.SUPPORTED);
        assertThat(check(d,d,new CapabilityReport(d,caps,Set.of(),now,now.plusSeconds(30))).reason()).isEqualTo(Reason.PIN_FORMAT_MISSING);
        assertThat(policy.evaluate(descriptor(),d,d,report(d,caps),Set.of(Capability.SCHEDULED_PIN,Capability.OFFLINE_REVOCATION),now).reason()).isEqualTo(Reason.UNKNOWN_CAPABILITY);
    }
    @Test void provisioningAndRevocationDeniedBeforeAnyAdapterInvocationEvenWithValidIntents() {
        var count=new AtomicInteger();var adapter=(Adapter)Proxy.newProxyInstance(Adapter.class.getClassLoader(),new Class<?>[]{Adapter.class},(p,m,a)->{count.incrementAndGet();throw new AssertionError("Adapter invoked");});
        var b=new SmartLock.Binding(UUID.randomUUID(),1,UUID.randomUUID(),1,binding,new SmartLock.Window(now,now.plusSeconds(30)));
        var c=new SmartLock.Command(UUID.randomUUID(),UUID.randomUUID(),b,now,now.plusSeconds(20));
        var create=new SmartLock.Create(c,b.purchasedWindow(),new SmartLock.PinFormat(7),new SmartLock.ProtectedPinReference(UUID.randomUUID()));
        assertThat(policy.requestProvisioning(adapter,create).reason()).isEqualTo(Reason.OPERATION_DISABLED);
        assertThat(policy.requestRevocation(adapter,new SmartLock.Revoke(c,new SmartLock.ProviderCredentialReference("TEST"))).reason()).isEqualTo(Reason.OPERATION_DISABLED);
        assertThat(policy.requestProvisioning(null,null).customerAccessEnabled()).isFalse();assertThat(policy.requestRevocation(null,null).assignmentState()).isEqualTo("UNQUALIFIED");assertThat(count.get()).isZero();
    }
    @Test void replayRetainsCommandCorrelationScopeAndPayloadWithoutWritingAudit() {
        var a=new AuditIntent(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),binding,"a".repeat(64),Reason.OPERATION_DISABLED,now);
        assertThat(policy.replay(null,a)).isEqualTo(Replay.NEW); assertThat(policy.replay(a,a)).isEqualTo(Replay.SAME_COMMAND);
        assertThat(policy.replay(a,new AuditIntent(a.actor(),a.command(),a.correlation(),binding,a.payloadSha256(),a.reason(),now.plusSeconds(1)))).isEqualTo(Replay.SAME_COMMAND);
        for(var x:List.of(new AuditIntent(a.actor(),a.command(),a.correlation(),binding,"b".repeat(64),a.reason(),now),new AuditIntent(a.actor(),a.command(),UUID.randomUUID(),binding,a.payloadSha256(),a.reason(),now),new AuditIntent(UUID.randomUUID(),a.command(),a.correlation(),binding,a.payloadSha256(),a.reason(),now))) assertThat(policy.replay(a,x)).isEqualTo(Replay.CONFLICT);
        assertThat(a.payloadSha256()).isEqualTo("a".repeat(64));
    }
    @ParameterizedTest @EnumSource(Operation.class) void retriesArePureBoundedReadDecisionsAndNeverCredentialCommands(Operation operation) {
        var r=policy.retry(operation,Failure.TIMEOUT,1,new RetryPolicy(2,Duration.ofSeconds(2)),now,now.plusSeconds(10));
        assertThat(r).isEqualTo(operation==Operation.READ_STATUS||operation==Operation.CHECK_CONNECTIVITY?Retry.RETRY_READ:Retry.STOP);
    }
    @Test void failureOutageDeadlineAttemptAndUnknownResultsCannotForceRetry() {
        var p=new RetryPolicy(2,Duration.ofSeconds(2));
        assertThat(policy.retry(Operation.READ_STATUS,Failure.UNAVAILABLE,1,p,now,now.plusSeconds(10))).isEqualTo(Retry.RETRY_READ);
        for(var f:List.of(Failure.UNKNOWN,Failure.REJECTED))assertThat(policy.retry(Operation.READ_STATUS,f,1,p,now,now.plusSeconds(10))).isEqualTo(Retry.STOP);
        for(int n:List.of(-1,0,2,3))assertThat(policy.retry(Operation.READ_STATUS,Failure.TIMEOUT,n,p,now,now.plusSeconds(10))).isEqualTo(Retry.STOP);
        for(var end:List.of(now,now.minusSeconds(1),now.plusSeconds(1)))assertThat(policy.retry(Operation.READ_STATUS,Failure.TIMEOUT,1,p,now,end)).isEqualTo(Retry.STOP);
    }
}
