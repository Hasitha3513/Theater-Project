package com.cinenest.iot.internal;

import com.cinenest.access.SmartLock;
import com.cinenest.iot.LockIntegration.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Pure declaration checks only. No booking eligibility, grant or physical-evidence authority is conferred. */
final class LockCompatibilityPolicy {
    Decision evaluate(Descriptor descriptor, Device device, Device expected,
                      CapabilityReport report, Set<Capability> required, Instant now) {
        required = Set.copyOf(required); Objects.requireNonNull(now);
        if (descriptor == null || device == null || expected == null || report == null ||
            missing(device.provider()) || missing(device.protocol()) || missing(device.family()) || missing(device.firmware()) ||
            missing(expected.provider()) || missing(expected.protocol()) || missing(expected.family()) || missing(expected.firmware()))
            return result(Reason.MISSING_METADATA);
        var a = device.binding(); var b = expected.binding();
        if (!a.venueId().equals(b.venueId()) || !a.roomId().equals(b.roomId()) ||
            !a.deviceId().equals(b.deviceId()) || !a.assignmentId().equals(b.assignmentId())) return result(Reason.SCOPE_MISMATCH);
        if (a.assignmentRevision() != b.assignmentRevision() || device.metadataRevision() != expected.metadataRevision())
            return result(Reason.REVISION_MISMATCH);
        if (!device.equals(expected) || !descriptor.id().provider().equals(device.provider()) ||
            !descriptor.id().protocol().equals(device.protocol())) return result(Reason.ADAPTER_MISMATCH);
        if (!descriptor.firmwareByFamily().getOrDefault(device.family(), Set.of()).contains(device.firmware()))
            return result(Reason.FIRMWARE_UNSUPPORTED);
        if (!report.device().equals(device)) return result(Reason.REVISION_MISMATCH);
        if (now.isBefore(report.observedAt()) || !now.isBefore(report.expiresAt())) return result(Reason.STALE_DECLARATION);
        // Stable enum ordering makes multi-failure decisions independent of caller collection ordering.
        for (var capability : Capability.values()) {
            if (!required.contains(capability)) continue;
            var support = report.support(capability);
            if (support != Support.SUPPORTED) return result(switch (support) {
                case UNKNOWN -> Reason.UNKNOWN_CAPABILITY;
                case UNSUPPORTED -> Reason.UNSUPPORTED_CAPABILITY;
                case REQUIRES_PHYSICAL_VERIFICATION -> Reason.PHYSICAL_VERIFICATION_REQUIRED;
                case SUPPORTED -> throw new IllegalStateException("Unreachable support state");
            });
        }
        if (required.contains(Capability.SCHEDULED_PIN) && report.pinFormats().isEmpty()) return result(Reason.PIN_FORMAT_MISSING);
        return result(Reason.CONTRACT_COMPATIBLE);
    }
    // Deliberately do not inspect or invoke the adapter, even for valid or malformed requests.
    Decision requestProvisioning(Adapter adapter, SmartLock.Create request) { return result(Reason.OPERATION_DISABLED); }
    Decision requestRevocation(Adapter adapter, SmartLock.Revoke request) { return result(Reason.OPERATION_DISABLED); }
    Replay replay(AuditIntent previous, AuditIntent current) {
        Objects.requireNonNull(current);
        if (previous == null) return Replay.NEW;
        return previous.actor().equals(current.actor()) && previous.command().equals(current.command()) &&
            previous.correlation().equals(current.correlation()) && previous.binding().equals(current.binding()) &&
            previous.payloadSha256().equals(current.payloadSha256()) && previous.reason() == current.reason()
            ? Replay.SAME_COMMAND : Replay.CONFLICT;
    }
    Retry retry(Operation operation, Failure failure, int completedAttempts, RetryPolicy policy, Instant now, Instant deadline) {
        Objects.requireNonNull(operation); Objects.requireNonNull(failure); Objects.requireNonNull(policy);
        Objects.requireNonNull(now); Objects.requireNonNull(deadline);
        if (completedAttempts < 1 || completedAttempts >= policy.maximumAttempts() || !now.isBefore(deadline) ||
            (operation != Operation.READ_STATUS && operation != Operation.CHECK_CONNECTIVITY) ||
            (failure != Failure.TIMEOUT && failure != Failure.UNAVAILABLE) ||
            policy.timeout().compareTo(Duration.between(now, deadline)) > 0) return Retry.STOP;
        return Retry.RETRY_READ;
    }
    private static boolean missing(String value) { return value == null || value.isBlank() || "UNKNOWN".equalsIgnoreCase(value); }
    private static Decision result(Reason reason) { return new Decision(reason); }
}
