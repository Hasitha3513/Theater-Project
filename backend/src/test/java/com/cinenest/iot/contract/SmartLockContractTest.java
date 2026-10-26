package com.cinenest.iot.contract;

import com.cinenest.access.SmartLock;
import com.cinenest.access.SmartLock.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SmartLockContractTest {
    private final Instant now = Instant.parse("2030-01-01T10:00:00Z");
    private final DeviceBinding device = new DeviceBinding(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1);
    private final Window window = new Window(now.plusSeconds(60), now.plusSeconds(3600));
    private final Binding binding = new Binding(UUID.randomUUID(), 1, UUID.randomUUID(), 1, device, window);
    private final Capabilities caps = new Capabilities(device, Set.of(Capability.SCHEDULED_PIN, Capability.RECONCILE,
            Capability.REVOKE, Capability.STATUS_READBACK), Set.of(new PinFormat(6)), now, Evidence.CONTRACT_SIMULATION);
    private ContractLockAdapter adapter() { return new ContractLockAdapter(true, Clock.fixed(now, ZoneOffset.UTC), caps); }
    private Command command() { return new Command(UUID.randomUUID(), UUID.randomUUID(), binding, now, window.end()); }
    private Create request() { return new Create(command(), window, new PinFormat(6), new ProtectedPinReference(UUID.randomUUID())); }
    private Query query(Create r) { return new Query(r.command().binding(), new ProviderCredentialReference("TEST-" + r.command().commandId()), UUID.randomUUID(), now); }

    @Test void disabledByDefaultAndNoRuntimeRegistration() {
        var a = new ContractLockAdapter(Clock.fixed(now, ZoneOffset.UTC), caps);
        assertThatThrownBy(() -> a.inspectCapabilities(device)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> a.createTimedPin(request())).isInstanceOf(IllegalStateException.class);
        assertThat(ContractLockAdapter.class.getAnnotations()).isEmpty();
    }
    @Test void validatesTypedIdentityRevisionsAndWindow() {
        assertThatThrownBy(() -> new Window(now, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeviceBinding(device.venueId(), device.roomId(), device.deviceId(), device.assignmentId(), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Binding(binding.bookingId(), 0, binding.credentialId(), 1, device, window)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Binding(binding.bookingId(), 1, binding.credentialId(), 0, device, window)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Command(UUID.randomUUID(), UUID.randomUUID(), binding, now, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PinFormat(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProviderCredentialReference("https://secret.invalid/?pin=123456")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Create(command(), window, new PinFormat(6), null)).isInstanceOf(NullPointerException.class);
    }
    @Test void noEarlyEntryOrCleaningBufferExtension() {
        assertThatThrownBy(() -> new Create(command(), new Window(window.start().minusSeconds(1), window.end()), new PinFormat(6), new ProtectedPinReference(UUID.randomUUID()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Create(command(), new Window(window.start(), window.end().plusSeconds(900)), new PinFormat(6), new ProtectedPinReference(UUID.randomUUID()))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void capabilitiesAreExplicitImmutableAndUnsupportedNeverCreates() {
        var set = new HashSet<>(Set.of(Capability.RECONCILE));
        var c = new Capabilities(device, set, Set.of(), now, Evidence.CONTRACT_SIMULATION); set.clear();
        assertThat(c.supported()).containsExactly(Capability.RECONCILE);
        var a = new ContractLockAdapter(true, Clock.fixed(now, ZoneOffset.UTC), c);
        assertThat(a.createTimedPin(request()).outcome()).isEqualTo(Outcome.UNSUPPORTED); assertThat(a.creations()).isZero();
        assertThatThrownBy(() -> new Capabilities(device, Set.of(Capability.SCHEDULED_PIN), Set.of(), now, Evidence.NONE)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void pendingUnknownAndReconciliationPreserveOriginalIdentityWithoutDuplicate() {
        var a = adapter(); var r = request();
        assertThat(a.createTimedPin(r).outcome()).isEqualTo(Outcome.PENDING_DELIVERY);
        a.observe(r.command().commandId(), Outcome.UNKNOWN);
        assertThat(a.createTimedPin(r).outcome()).isEqualTo(Outcome.UNKNOWN);
        assertThat(a.createTimedPin(new Create(command(), window, r.format(), r.protectedPin())).outcome()).isEqualTo(Outcome.CONFLICT);
        a.observe(r.command().commandId(), Outcome.RECONCILIATION_REQUIRED);
        assertThat(a.reconcile(new Reconcile(r, null)).outcome()).isEqualTo(Outcome.RECONCILIATION_REQUIRED);
        a.observe(r.command().commandId(), Outcome.INSTALLATION_REPORTED);
        var o = a.reconcile(new Reconcile(r, null));
        assertThat(o.evidence()).isEqualTo(Evidence.CONTRACT_SIMULATION);
        assertThat(o.commandId()).isEqualTo(r.command().commandId()); assertThat(a.creations()).isEqualTo(1);
        a.observe(r.command().commandId(), Outcome.UNKNOWN);
        var replacement = new Binding(binding.bookingId(), 2, UUID.randomUUID(), 1, device, window);
        a.changeBinding(replacement);
        var next = new Create(new Command(UUID.randomUUID(), UUID.randomUUID(), replacement, now, window.end()), window, r.format(), new ProtectedPinReference(UUID.randomUUID()));
        assertThat(a.createTimedPin(next).outcome()).isEqualTo(Outcome.CONFLICT);
        assertThat(a.creations()).isEqualTo(1);
    }
    @Test void identicalConcurrentCommandsCreateOnceAndChangedPayloadFails() throws Exception {
        var a = adapter(); var r = request();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = pool.submit(() -> a.createTimedPin(r)); var second = pool.submit(() -> a.createTimedPin(r));
            assertThat(first.get()).isEqualTo(second.get());
        }
        var changed = new Create(r.command(), window, r.format(), new ProtectedPinReference(UUID.randomUUID()));
        assertThat(a.createTimedPin(changed).outcome()).isEqualTo(Outcome.CONFLICT); assertThat(a.creations()).isEqualTo(1);
    }
    @Test void wrongDeviceVenueAndFormatAreUnsupported() {
        var a = adapter(); var r = request();
        var wrong = new DeviceBinding(UUID.randomUUID(), device.roomId(), device.deviceId(), device.assignmentId(), 1);
        var b = new Binding(binding.bookingId(), 1, binding.credentialId(), 1, wrong, window);
        assertThat(a.createTimedPin(new Create(new Command(UUID.randomUUID(), UUID.randomUUID(), b, now, window.end()), window, r.format(), r.protectedPin())).outcome()).isEqualTo(Outcome.UNSUPPORTED);
        assertThat(a.createTimedPin(new Create(r.command(), window, new PinFormat(7), r.protectedPin())).outcome()).isEqualTo(Outcome.UNSUPPORTED);
        assertThat(a.creations()).isZero();
    }
    @Test void staleBookingCredentialAndAssignmentCannotReactivate() {
        for (int field = 0; field < 3; field++) {
            var a = adapter(); var r = request(); a.createTimedPin(r);
            var d = field == 2 ? new DeviceBinding(device.venueId(), device.roomId(), device.deviceId(), device.assignmentId(), 2) : device;
            var changed = new Binding(binding.bookingId(), field == 0 ? 2 : 1, binding.credentialId(), field == 1 ? 2 : 1, d, window);
            a.changeBinding(changed);
            assertThat(a.reconcile(new Reconcile(r, null)).outcome()).isEqualTo(Outcome.CONFLICT);
            assertThatThrownBy(() -> a.observe(r.command().commandId(), Outcome.INSTALLATION_REPORTED)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void expiredAndFutureCommandsDoNotCreate() {
        var r = request(); var expired = new ContractLockAdapter(true, Clock.fixed(window.end(), ZoneOffset.UTC), caps);
        assertThat(expired.createTimedPin(r).outcome()).isEqualTo(Outcome.CONFLICT);
        var future = new Command(UUID.randomUUID(), UUID.randomUUID(), binding, now.plusSeconds(1), window.end());
        assertThat(adapter().createTimedPin(new Create(future, window, r.format(), r.protectedPin())).outcome()).isEqualTo(Outcome.CONFLICT);
    }
    @Test void simulatedReadbackRevocationRemovalAndNoResurrection() {
        var a = adapter(); var r = request(); a.createTimedPin(r); a.observe(r.command().commandId(), Outcome.INSTALLATION_REPORTED);
        assertThat(a.readPinStatus(query(r)).outcome()).isEqualTo(Outcome.INSTALLATION_REPORTED);
        var revoke = new Revoke(command(), query(r).providerCredential());
        assertThat(a.revokePin(revoke).outcome()).isEqualTo(Outcome.REVOCATION_REQUESTED);
        assertThat(a.revokePin(revoke).outcome()).isEqualTo(Outcome.REVOCATION_REQUESTED);
        assertThatThrownBy(() -> a.observe(r.command().commandId(), Outcome.INSTALLATION_REPORTED)).isInstanceOf(IllegalStateException.class);
        a.observe(r.command().commandId(), Outcome.REMOVAL_REPORTED);
        assertThat(a.readPinStatus(query(r)).outcome()).isEqualTo(Outcome.REMOVAL_REPORTED);
        assertThat(a.revokePin(revoke).outcome()).isEqualTo(Outcome.REMOVAL_REPORTED);
        assertThat(a.revokePin(revoke).commandId()).isEqualTo(revoke.command().commandId());
        assertThat(a.reconcile(new Reconcile(r, null)).commandId()).isEqualTo(r.command().commandId());
        assertThatThrownBy(() -> a.observe(r.command().commandId(), Outcome.INSTALLATION_REPORTED)).isInstanceOf(IllegalStateException.class);
    }
    @Test void unsupportedReadRevokeAndReconcileDoNotAssumeCapabilities() {
        var c = new Capabilities(device, Set.of(Capability.SCHEDULED_PIN), Set.of(new PinFormat(6)), now, Evidence.CONTRACT_SIMULATION);
        var a = new ContractLockAdapter(true, Clock.fixed(now, ZoneOffset.UTC), c); var r = request();
        a.createTimedPin(r); a.observe(r.command().commandId(), Outcome.INSTALLATION_REPORTED);
        assertThat(a.readPinStatus(query(r)).outcome()).isEqualTo(Outcome.UNSUPPORTED);
        assertThat(a.revokePin(new Revoke(command(), query(r).providerCredential())).outcome()).isEqualTo(Outcome.UNSUPPORTED);
        assertThat(a.reconcile(new Reconcile(r, null)).outcome()).isEqualTo(Outcome.UNSUPPORTED);
    }
    @Test void secretReferencesRedactAndCloudAckCannotEstablishInstallation() {
        var r = request(); assertThat(r.toString()).doesNotContain(r.protectedPin().id().toString());
        assertThat(query(r).providerCredential().toString()).isEqualTo("ProviderCredentialReference[REDACTED]");
        for (var outcome : List.of(Outcome.INSTALLATION_REPORTED, Outcome.REMOVAL_REPORTED))
            assertThatThrownBy(() -> new Observation(binding, UUID.randomUUID(), UUID.randomUUID(), query(r).providerCredential(), outcome, Evidence.CLOUD_ACCEPTANCE, now)).isInstanceOf(IllegalArgumentException.class);
    }
}
