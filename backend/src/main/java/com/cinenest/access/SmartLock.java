package com.cinenest.access;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Access-owned required port. Device-owning adapters may implement this contract without
 * an Access-to-IoT dependency. No runtime implementation or hardware authority is supplied.
 * All results describe their evidence level: cloud acceptance is never installation proof.
 */
public interface SmartLock {
    Capabilities inspectCapabilities(DeviceBinding device);
    Observation createTimedPin(Create request);
    Observation readPinStatus(Query query);
    Observation revokePin(Revoke request);
    Observation reconcile(Reconcile request);

    enum Capability { SCHEDULED_PIN, STATUS_READBACK, REVOKE, RECONCILE, REENTRY }
    enum Outcome { UNSUPPORTED, PENDING_DELIVERY, UNKNOWN, INSTALLATION_REPORTED,
        RECONCILIATION_REQUIRED, REVOCATION_REQUESTED, REMOVAL_REPORTED, CONFLICT }
    enum Evidence { NONE, CLOUD_ACCEPTANCE, CONTRACT_SIMULATION, QUALIFIED_DEVICE_REPORT }

    record Window(Instant start, Instant end) {
        public Window {
            Objects.requireNonNull(start); Objects.requireNonNull(end);
            require(start.isBefore(end), "Invalid access window");
        }
    }
    record DeviceBinding(UUID venueId, UUID roomId, UUID deviceId, UUID assignmentId,
                         long assignmentRevision) {
        public DeviceBinding {
            Objects.requireNonNull(venueId); Objects.requireNonNull(roomId);
            Objects.requireNonNull(deviceId); Objects.requireNonNull(assignmentId);
            require(assignmentRevision > 0, "Invalid assignment revision");
        }
    }
    record Binding(UUID bookingId, long bookingRevision, UUID credentialId,
                   long credentialRevision, DeviceBinding device, Window purchasedWindow) {
        public Binding {
            Objects.requireNonNull(bookingId); Objects.requireNonNull(credentialId);
            Objects.requireNonNull(device); Objects.requireNonNull(purchasedWindow);
            require(bookingRevision > 0 && credentialRevision > 0, "Invalid revision");
        }
    }
    /** A reference into Access-owned protected material, never a PIN or reveal token. */
    record ProtectedPinReference(UUID id) {
        public ProtectedPinReference { Objects.requireNonNull(id); }
        @Override public String toString() { return "ProtectedPinReference[REDACTED]"; }
    }
    record ProviderCredentialReference(String value) {
        public ProviderCredentialReference {
            require(value != null && value.matches("[A-Za-z0-9_:-]{1,128}"), "Invalid provider reference");
        }
        @Override public String toString() { return "ProviderCredentialReference[REDACTED]"; }
    }
    /** Advertised format only. This contract generates no digits and chooses no length. */
    record PinFormat(int digits) {
        public PinFormat { require(digits > 0, "Invalid advertised PIN format"); }
    }
    record Capabilities(DeviceBinding device, Set<Capability> supported,
                        Set<PinFormat> formats, Instant observedAt, Evidence evidence) {
        public Capabilities {
            Objects.requireNonNull(device); supported = Set.copyOf(supported);
            formats = Set.copyOf(formats); Objects.requireNonNull(observedAt);
            Objects.requireNonNull(evidence);
            require(!supported.contains(Capability.SCHEDULED_PIN) || !formats.isEmpty(),
                    "Scheduled PIN requires an explicit format");
        }
    }
    record Command(UUID commandId, UUID correlationId, Binding binding,
                   Instant issuedAt, Instant expiresAt) {
        public Command {
            Objects.requireNonNull(commandId); Objects.requireNonNull(correlationId);
            Objects.requireNonNull(binding); Objects.requireNonNull(issuedAt);
            Objects.requireNonNull(expiresAt);
            require(issuedAt.isBefore(expiresAt), "Invalid command deadline");
        }
    }
    record Create(Command command, Window requestedWindow, PinFormat format,
                  ProtectedPinReference protectedPin) {
        public Create {
            Objects.requireNonNull(command); Objects.requireNonNull(requestedWindow);
            Objects.requireNonNull(format); Objects.requireNonNull(protectedPin);
            require(requestedWindow.equals(command.binding().purchasedWindow()),
                    "Access must equal the purchased window");
            require(!command.expiresAt().isAfter(requestedWindow.end()),
                    "Create deadline exceeds access end");
        }
    }
    record Query(Binding binding, ProviderCredentialReference providerCredential,
                 UUID correlationId, Instant queriedAt) {
        public Query {
            Objects.requireNonNull(binding); Objects.requireNonNull(providerCredential);
            Objects.requireNonNull(correlationId); Objects.requireNonNull(queriedAt);
        }
    }
    record Revoke(Command command, ProviderCredentialReference providerCredential) {
        public Revoke { Objects.requireNonNull(command); Objects.requireNonNull(providerCredential); }
    }
    /** Reconciliation retains the original command identity and exact creation payload. */
    record Reconcile(Create original, ProviderCredentialReference providerCredential) {
        public Reconcile { Objects.requireNonNull(original); }
    }
    record Observation(Binding binding, UUID commandId, UUID correlationId,
                       ProviderCredentialReference providerCredential, Outcome outcome,
                       Evidence evidence, Instant observedAt) {
        public Observation {
            Objects.requireNonNull(binding); Objects.requireNonNull(commandId);
            Objects.requireNonNull(correlationId); Objects.requireNonNull(outcome);
            Objects.requireNonNull(evidence); Objects.requireNonNull(observedAt);
            if (outcome == Outcome.INSTALLATION_REPORTED || outcome == Outcome.REMOVAL_REPORTED) {
                require(providerCredential != null, "Reported result requires a provider reference");
                require(evidence == Evidence.CONTRACT_SIMULATION || evidence == Evidence.QUALIFIED_DEVICE_REPORT,
                        "Cloud acknowledgement is not installation/removal evidence");
            }
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
