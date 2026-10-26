package com.cinenest.iot.contract;

import com.cinenest.access.SmartLock;
import java.time.Clock;
import java.util.*;

/** Test-source only, network-free, disabled by default. No Spring registration or persistence. */
final class ContractLockAdapter implements SmartLock {
    private final boolean enabled;
    private final Clock clock;
    private final Capabilities capabilities;
    private final Map<UUID, Create> receipts = new HashMap<>();
    private final Map<UUID, Observation> observations = new HashMap<>();
    private final Map<UUID, Binding> current = new HashMap<>();
    private final Map<UUID, Revoke> revocations = new HashMap<>();
    ContractLockAdapter(Clock clock, Capabilities capabilities) { this(false, clock, capabilities); }
    ContractLockAdapter(boolean enabled, Clock clock, Capabilities capabilities) {
        this.enabled = enabled; this.clock = clock; this.capabilities = capabilities;
    }
    private void enabled() { if (!enabled) throw new IllegalStateException("Contract adapter disabled"); }
    public Capabilities inspectCapabilities(DeviceBinding device) {
        enabled(); if (!capabilities.device().equals(device)) throw new IllegalArgumentException("Device mismatch");
        return capabilities;
    }
    private Observation result(Command c, ProviderCredentialReference ref, Outcome outcome, Evidence evidence) {
        return new Observation(c.binding(), c.commandId(), c.correlationId(), ref, outcome, evidence, clock.instant());
    }
    private boolean current(Binding b) { return b.equals(current.get(b.bookingId())); }
    public synchronized Observation createTimedPin(Create request) {
        enabled(); var c = request.command();
        var prior = receipts.get(c.commandId());
        if (prior != null) {
            if (!prior.equals(request) || !current(c.binding())) return result(c, null, Outcome.CONFLICT, Evidence.NONE);
            return observations.get(c.commandId());
        }
        if (revocations.containsKey(c.commandId())) return result(c, null, Outcome.CONFLICT, Evidence.NONE);
        if (!capabilities.device().equals(c.binding().device()) ||
                !capabilities.supported().contains(Capability.SCHEDULED_PIN) ||
                !capabilities.formats().contains(request.format())) return result(c, null, Outcome.UNSUPPORTED, Evidence.NONE);
        var old = current.get(c.binding().bookingId());
        if (!clock.instant().isBefore(c.expiresAt()) || c.issuedAt().isAfter(clock.instant()) ||
                (old != null && !old.equals(c.binding()))) return result(c, null, Outcome.CONFLICT, Evidence.NONE);
        // An unresolved logical credential never admits a second provisioning command.
        if (observations.values().stream().anyMatch(o -> o.binding().credentialId().equals(c.binding().credentialId()) ||
                (o.binding().bookingId().equals(c.binding().bookingId()) && o.outcome() != Outcome.REMOVAL_REPORTED)))
            return result(c, null, Outcome.CONFLICT, Evidence.NONE);
        receipts.put(c.commandId(), request); current.put(c.binding().bookingId(), c.binding());
        var result = result(c, null, Outcome.PENDING_DELIVERY, Evidence.CONTRACT_SIMULATION);
        observations.put(c.commandId(), result); return result;
    }
    public synchronized Observation readPinStatus(Query query) {
        enabled();
        return observations.values().stream().filter(o -> o.binding().equals(query.binding()) &&
                Objects.equals(o.providerCredential(), query.providerCredential()) && current(query.binding()))
                .findFirst().map(o -> new Observation(o.binding(), o.commandId(), query.correlationId(),
                        o.providerCredential(), capabilities.supported().contains(Capability.STATUS_READBACK) ? o.outcome() : Outcome.UNSUPPORTED,
                        capabilities.supported().contains(Capability.STATUS_READBACK) ? o.evidence() : Evidence.NONE, o.observedAt()))
                .orElseThrow(() -> new IllegalArgumentException("No matching test observation"));
    }
    public synchronized Observation revokePin(Revoke request) {
        enabled(); var c = request.command();
        if (!current(c.binding()) || !clock.instant().isBefore(c.expiresAt()) || c.issuedAt().isAfter(clock.instant()))
            return result(c, null, Outcome.CONFLICT, Evidence.NONE);
        if (!capabilities.supported().contains(Capability.REVOKE)) return result(c, null, Outcome.UNSUPPORTED, Evidence.NONE);
        var issued = observations.values().stream().filter(o -> o.binding().equals(c.binding()) &&
                request.providerCredential().equals(o.providerCredential())).findFirst();
        if (issued.isEmpty() || receipts.containsKey(c.commandId())) return result(c, null, Outcome.CONFLICT, Evidence.NONE);
        var old = revocations.putIfAbsent(c.commandId(), request);
        if (old != null && !old.equals(request)) return result(c, null, Outcome.CONFLICT, Evidence.NONE);
        if (old != null) {
            var latest = issued.get();
            return new Observation(c.binding(), c.commandId(), c.correlationId(), request.providerCredential(),
                    latest.outcome(), latest.evidence(), latest.observedAt());
        }
        var revoked = result(c, request.providerCredential(), Outcome.REVOCATION_REQUESTED, Evidence.CONTRACT_SIMULATION);
        observations.replaceAll((id, o) -> o.binding().equals(c.binding()) ?
                new Observation(o.binding(), o.commandId(), o.correlationId(), o.providerCredential(),
                        revoked.outcome(), revoked.evidence(), revoked.observedAt()) : o);
        return revoked;
    }
    public synchronized Observation reconcile(Reconcile request) {
        enabled(); var original = request.original(); var c = original.command();
        if (!original.equals(receipts.get(c.commandId())) || !current(c.binding())) return result(c, null, Outcome.CONFLICT, Evidence.NONE);
        if (!capabilities.supported().contains(Capability.RECONCILE)) return result(c, null, Outcome.UNSUPPORTED, Evidence.NONE);
        var observed = observations.get(c.commandId());
        if (request.providerCredential() != null && !Objects.equals(request.providerCredential(), observed.providerCredential()))
            return result(c, null, Outcome.CONFLICT, Evidence.NONE);
        return observed;
    }
    /** Controlled test evidence only; terminal removal/revocation cannot be resurrected. */
    synchronized void observe(UUID originalCommand, Outcome outcome) {
        enabled(); var create = Objects.requireNonNull(receipts.get(originalCommand)); var c = create.command();
        var previous = observations.get(originalCommand);
        if (!current(c.binding()) || !clock.instant().isBefore(c.binding().purchasedWindow().end()) ||
                previous.outcome() == Outcome.REMOVAL_REPORTED ||
                (previous.outcome() == Outcome.REVOCATION_REQUESTED && outcome != Outcome.REMOVAL_REPORTED))
            throw new IllegalStateException("Stale test observation");
        observations.put(originalCommand, result(c, new ProviderCredentialReference("TEST-" + originalCommand),
                outcome, Evidence.CONTRACT_SIMULATION));
    }
    synchronized void changeBinding(Binding binding) { enabled(); current.put(binding.bookingId(), binding); }
    int creations() { return receipts.size(); }
}
