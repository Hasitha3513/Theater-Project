package com.cinenest.iot;

import com.cinenest.access.SmartLock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Inert future adapter ports and declarations. Neither declarations nor connectivity qualify hardware. */
public final class LockIntegration {
    private LockIntegration() {}
    public enum Support { SUPPORTED, UNSUPPORTED, UNKNOWN, REQUIRES_PHYSICAL_VERIFICATION }
    public enum Capability { SCHEDULED_PIN, STATUS_READBACK, REVOKE, RECONCILE, REENTRY,
        OFFLINE_VALIDATION, OFFLINE_REVOCATION }
    public enum Connectivity { UNKNOWN, CONNECTED, DISCONNECTED, DEGRADED }
    public enum Reason { CONTRACT_COMPATIBLE, MISSING_METADATA, ADAPTER_MISMATCH, FIRMWARE_UNSUPPORTED,
        SCOPE_MISMATCH, REVISION_MISMATCH, STALE_DECLARATION, UNKNOWN_CAPABILITY,
        UNSUPPORTED_CAPABILITY, PHYSICAL_VERIFICATION_REQUIRED, PIN_FORMAT_MISSING, OPERATION_DISABLED }
    public enum Operation { READ_STATUS, CHECK_CONNECTIVITY, PROVISION, REVOKE, REGISTER, DISCOVER }
    public enum Failure { TIMEOUT, UNAVAILABLE, REJECTED, UNKNOWN }
    public enum Retry { STOP, RETRY_READ }
    public enum Replay { NEW, SAME_COMMAND, CONFLICT }
    public record AdapterId(String provider, String adapter, String protocol, String version) {
        public AdapterId { token(provider); token(adapter); token(protocol); token(version); }
    }
    public record Descriptor(AdapterId id, Map<String, Set<String>> firmwareByFamily) {
        public Descriptor {
            Objects.requireNonNull(id); Objects.requireNonNull(firmwareByFamily);
            var copy = new TreeMap<String, Set<String>>();
            firmwareByFamily.forEach((family, versions) -> {
                token(family); Objects.requireNonNull(versions);
                versions.forEach(LockIntegration::token);
                require(!versions.isEmpty(), "Missing firmware declarations");
                copy.put(family, Set.copyOf(versions));
            });
            require(!copy.isEmpty(), "Missing device families"); firmwareByFamily = Map.copyOf(copy);
        }
    }
    /** Missing optional declarations remain missing; policy rejects them instead of guessing support. */
    public record Device(SmartLock.DeviceBinding binding, long metadataRevision,
                         String provider, String protocol, String family, String firmware) {
        public Device { Objects.requireNonNull(binding); require(metadataRevision > 0, "Invalid metadata revision"); }
        @Override public String toString() { return "Device[REDACTED]"; }
    }
    public record CapabilityReport(Device device, Map<Capability, Support> declarations,
                                   Set<SmartLock.PinFormat> pinFormats, Instant observedAt, Instant expiresAt) {
        public CapabilityReport {
            Objects.requireNonNull(device); declarations = Map.copyOf(declarations);
            pinFormats = Set.copyOf(pinFormats); window(observedAt, expiresAt);
        }
        public Support support(Capability key) { return declarations.getOrDefault(key, Support.UNKNOWN); }
    }
    public record DiscoveryScope(UUID venueId, Instant deadline) {
        public DiscoveryScope { Objects.requireNonNull(venueId); Objects.requireNonNull(deadline); }
    }
    public record DeviceStatus(Device device, Connectivity connectivity, Instant observedAt, Instant expiresAt) {
        public DeviceStatus { Objects.requireNonNull(device); Objects.requireNonNull(connectivity); window(observedAt, expiresAt); }
    }
    /** A result cannot enable access, including CONTRACT_COMPATIBLE. */
    public record Decision(Reason reason) {
        public Decision { Objects.requireNonNull(reason); }
        public boolean contractCompatible() { return reason == Reason.CONTRACT_COMPATIBLE; }
        public String assignmentState() { return "UNQUALIFIED"; }
        public boolean customerAccessEnabled() { return false; }
    }
    /** Immutable audit intent only: not a persisted journal or authorization attestation. */
    public record AuditIntent(UUID actor, UUID command, UUID correlation, SmartLock.DeviceBinding binding,
                              String payloadSha256, Reason reason, Instant at) {
        public AuditIntent {
            Objects.requireNonNull(actor); Objects.requireNonNull(command); Objects.requireNonNull(correlation);
            Objects.requireNonNull(binding); Objects.requireNonNull(reason); Objects.requireNonNull(at);
            require(payloadSha256 != null && payloadSha256.matches("[0-9a-f]{64}"), "Invalid payload fingerprint");
        }
        @Override public String toString() { return "AuditIntent[REDACTED]"; }
    }
    /** Caller-supplied finite policy; P1 executes no timer, scheduler, retry or I/O. */
    public record RetryPolicy(int maximumAttempts, Duration timeout) {
        public RetryPolicy { require(maximumAttempts > 0, "Invalid attempt limit"); Objects.requireNonNull(timeout);
            require(!timeout.isNegative() && !timeout.isZero(), "Invalid timeout"); }
    }
    /** Future provider port only. P1 supplies no implementation, activation, discovery or onboarding worker. */
    public interface Adapter {
        Descriptor describeAdapter();
        List<Device> discoverDevices(DiscoveryScope scope);
        CapabilityReport inspectCapabilities(Device device);
        Decision validateCompatibility(Device device);
        Decision requestRegistration(Device device, UUID command);
        DeviceStatus readDeviceStatus(Device device);
        CapabilityReport readPinCapabilities(Device device);
        DeviceStatus checkConnectivity(Device device);
        SmartLock.Observation requestProvisioning(SmartLock.Create request);
        SmartLock.Observation requestRevocation(SmartLock.Revoke request);
        SmartLock.Observation readAcknowledgement(SmartLock.Query query);
    }
    private static void token(String value) {
        require(value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}"), "Invalid logical identifier");
    }
    private static void window(Instant start, Instant end) {
        Objects.requireNonNull(start); Objects.requireNonNull(end); require(start.isBefore(end), "Invalid observation window");
    }
    private static void require(boolean valid, String message) { if (!valid) throw new IllegalArgumentException(message); }
}
