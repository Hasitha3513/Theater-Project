package com.cinenest.identity.internal;

import java.time.Instant;
import java.util.UUID;

/** Future Identity-owned adapters must lock and recheck these facts at commit time.
 * No implementation, persistence schema, delivery service or authentication endpoint is supplied.
 */
final class CustomerCredentialPorts {
    private CustomerCredentialPorts() {}
    interface TrustedTime { Instant databaseTime(); }
    interface CredentialRead { CustomerCredentialLifecycle.Snapshot lockedSnapshot(UUID account); }
    interface AbuseLimits { CustomerCredentialLifecycle.Permit reserve(UUID command, UUID account,
        CustomerCredentialLifecycle.Action action); }
    interface AtomicCommit {
        /** Recheck current policy, account, capability and abuse/hash lease under one transaction;
         * consume leases/capability once, append credential/audit and invalidate sessions atomically.
         * Unknown responses must be reconciled by command identity, never blindly retried.
         */
        CommitReceipt commit(CustomerCredentialLifecycle.Plan plan);
    }
    interface ProtectedDelivery {
        /** An approved future adapter receives a protected payload reference, never an audit token. */
        DeliveryReceipt deliver(DeliveryIntent intent);
    }
    record CommitReceipt(UUID command, UUID account, String planDigest, UUID audit, UUID permit, CustomerCredentialLifecycle.State state, UUID capability,
        long accountRevision, long credentialEpoch, boolean committed, boolean currentFactsRechecked,
        boolean abuseLeaseConsumed, boolean capabilityConsumed, boolean sessionsInvalidated) {}
    record DeliveryIntent(UUID command, UUID account, UUID protectedPayload, String policyRevision) {
        @Override public String toString() { return "DeliveryIntent[REDACTED]"; }
    }
    record DeliveryReceipt(UUID command, UUID account, UUID protectedPayload, String policyRevision,
        boolean accepted, boolean outcomeKnown) {
        @Override public String toString() { return "DeliveryReceipt[REDACTED]"; }
    }
    enum Outcome { RECORDED, RECONCILIATION_REQUIRED }
    static Outcome verify(CustomerCredentialLifecycle.Plan plan, CommitReceipt receipt) {
        if (!CustomerCredentialLifecycle.validPlan(plan) || receipt == null || !receipt.committed() || !receipt.currentFactsRechecked()
            || !receipt.abuseLeaseConsumed() || !plan.command().equals(receipt.command())
            || !plan.account().equals(receipt.account()) || !plan.digest().equals(receipt.planDigest())
            || !plan.audit().equals(receipt.audit()) || !plan.permit().equals(receipt.permit()) || plan.nextState() != receipt.state() || plan.nextAccountRevision() != receipt.accountRevision()
            || plan.nextEpoch() != receipt.credentialEpoch()
            || !java.util.Objects.equals(plan.capability(), receipt.capability())
            || plan.consumeCapability() != receipt.capabilityConsumed()
            || plan.invalidateSessions() != receipt.sessionsInvalidated()) return Outcome.RECONCILIATION_REQUIRED;
        return Outcome.RECORDED;
    }
    static Outcome verify(DeliveryIntent intent, DeliveryReceipt receipt) {
        if (intent == null || receipt == null || !receipt.outcomeKnown() || !receipt.accepted()
            || intent.command() == null || intent.account() == null || intent.protectedPayload() == null
            || intent.policyRevision() == null || intent.policyRevision().isBlank()
            || !intent.command().equals(receipt.command()) || !intent.account().equals(receipt.account())
            || !intent.protectedPayload().equals(receipt.protectedPayload())
            || !intent.policyRevision().equals(receipt.policyRevision())) return Outcome.RECONCILIATION_REQUIRED;
        return Outcome.RECORDED;
    }
}
