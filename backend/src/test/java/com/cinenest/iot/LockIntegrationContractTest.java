package com.cinenest.iot;

import com.cinenest.access.SmartLock;
import com.cinenest.iot.LockIntegration.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class LockIntegrationContractTest {
    private final Instant now = Instant.parse("2030-01-01T00:00:00Z");
    private SmartLock.DeviceBinding binding() { return new SmartLock.DeviceBinding(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1); }
    @Test void declarationsAndFirmwareCollectionsAreDefensivelyImmutable() {
        var versions = new HashSet<>(Set.of("1.0")); var families = new HashMap<String, Set<String>>(); families.put("F", versions);
        var d = new Descriptor(new AdapterId("P", "A", "LOCAL", "1"), families); versions.add("2.0"); families.clear();
        assertThat(d.firmwareByFamily()).containsOnlyKeys("F"); assertThat(d.firmwareByFamily().get("F")).containsExactly("1.0");
        assertThatThrownBy(() -> d.firmwareByFamily().put("X", Set.of("1"))).isInstanceOf(UnsupportedOperationException.class);
        var caps = new EnumMap<Capability, Support>(Capability.class); caps.put(Capability.SCHEDULED_PIN, Support.SUPPORTED);
        var formats = new HashSet<>(Set.of(new SmartLock.PinFormat(7)));
        var r = new CapabilityReport(new Device(binding(), 1, "P", "LOCAL", "F", "1.0"), caps, formats, now, now.plusSeconds(30));
        caps.clear(); formats.clear(); assertThat(r.support(Capability.SCHEDULED_PIN)).isEqualTo(Support.SUPPORTED);
        assertThat(r.support(Capability.OFFLINE_REVOCATION)).isEqualTo(Support.UNKNOWN); assertThat(r.pinFormats()).hasSize(1);
    }
    @Test void logicalIdentityAndFirmwareDeclarationsRejectAmbiguity() {
        for (String bad : List.of("", " ", "*", "../outside", "https://host/path", "a\nb"))
            assertThatThrownBy(() -> new AdapterId(bad, "A", "P", "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Descriptor(new AdapterId("P", "A", "LOCAL", "1"), Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Descriptor(new AdapterId("P", "A", "LOCAL", "1"), Map.of("F", Set.of("*")))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void observationWindowsAndRetryBoundsMustBeExplicitAndValid() {
        var device = new Device(binding(), 1, "P", "LOCAL", "F", "1");
        assertThatThrownBy(() -> new DeviceStatus(device, Connectivity.UNKNOWN, now, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(0, Duration.ofSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(1, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Device(binding(), 0, "P", "LOCAL", "F", "1")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void everyDecisionRetainsUnqualifiedAccessDisabledIncludingCompatible() {
        for (Reason reason : Reason.values()) {
            var decision = new Decision(reason); assertThat(decision.assignmentState()).isEqualTo("UNQUALIFIED");
            assertThat(decision.customerAccessEnabled()).isFalse();
            assertThat(decision.contractCompatible()).isEqualTo(reason == Reason.CONTRACT_COMPATIBLE);
        }
    }
    @Test void acknowledgementReusesS1AndCannotPromoteCloudAcceptanceToInstallation() {
        var b = new SmartLock.Binding(UUID.randomUUID(), 1, UUID.randomUUID(), 1, binding(), new SmartLock.Window(now, now.plusSeconds(30)));
        for (var outcome : List.of(SmartLock.Outcome.INSTALLATION_REPORTED, SmartLock.Outcome.REMOVAL_REPORTED))
            assertThatThrownBy(() -> new SmartLock.Observation(b, UUID.randomUUID(), UUID.randomUUID(),
                new SmartLock.ProviderCredentialReference("TEST"), outcome, SmartLock.Evidence.CLOUD_ACCEPTANCE, now)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new SmartLock.Observation(b, UUID.randomUUID(), UUID.randomUUID(), null, SmartLock.Outcome.UNKNOWN,
            SmartLock.Evidence.NONE, now).outcome()).isEqualTo(SmartLock.Outcome.UNKNOWN);
    }
    @Test void auditIntentContainsOnlyReferencesFingerprintAndFixedReason() {
        var a = new AuditIntent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), binding(), "a".repeat(64), Reason.OPERATION_DISABLED, now);
        assertThat(a.toString()).isEqualTo("AuditIntent[REDACTED]");
        assertThatThrownBy(() -> new AuditIntent(a.actor(), a.command(), a.correlation(), a.binding(), "raw-payload", a.reason(), now)).isInstanceOf(IllegalArgumentException.class);
    }
}
