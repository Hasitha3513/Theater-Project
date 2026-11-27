package com.cinenest.identity.internal;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CustomerCredentialPortsTest {
    final CustomerCredentialLifecycleTest fixture = new CustomerCredentialLifecycleTest();
    CustomerCredentialLifecycle.Plan plan() { return fixture.core.prepare(fixture.command(CustomerCredentialLifecycle.Action.RECOVER), fixture.snapshot(CustomerCredentialLifecycle.Action.RECOVER)); }
    CustomerCredentialPorts.CommitReceipt receipt(CustomerCredentialLifecycle.Plan p, boolean consumed, boolean invalidated, boolean rechecked) {
        return new CustomerCredentialPorts.CommitReceipt(p.command(), p.account(), p.digest(), p.audit(), p.permit(), p.nextState(), p.capability(), p.nextAccountRevision(), p.nextEpoch(), true, rechecked, true, consumed, invalidated);
    }
    @Test void exactCommitReceiptIsRecordedWithoutCallingAnyAdapter() {
        var p = plan(); assertThat(CustomerCredentialPorts.verify(p, receipt(p, true, true, true))).isEqualTo(CustomerCredentialPorts.Outcome.RECORDED);
    }
    @Test void partialAtomicEvidenceAndUnknownReceiptRequireReconciliation() {
        var p = plan();
        for (var r : new CustomerCredentialPorts.CommitReceipt[] {null, receipt(p, false, true, true), receipt(p, true, false, true), receipt(p, true, true, false),
            new CustomerCredentialPorts.CommitReceipt(p.command(), p.account(), p.digest(), p.audit(), p.permit(), p.nextState(), p.capability(), p.nextAccountRevision(), p.nextEpoch(), false, true, true, true, true),
            new CustomerCredentialPorts.CommitReceipt(p.command(), p.account(), p.digest(), p.audit(), p.permit(), p.nextState(), p.capability(), p.nextAccountRevision(), p.nextEpoch(), true, true, false, true, true)})
            assertThat(CustomerCredentialPorts.verify(p, r)).isEqualTo(CustomerCredentialPorts.Outcome.RECONCILIATION_REQUIRED);
    }
    @Test void commandAccountPlanAuditRevisionAndEpochMustMatch() {
        var p = plan();
        var good = receipt(p, true, true, true);
        for (var r : new CustomerCredentialPorts.CommitReceipt[] {
            new CustomerCredentialPorts.CommitReceipt(UUID.randomUUID(), p.account(), p.digest(), p.audit(), p.permit(), p.nextState(), p.capability(), p.nextAccountRevision(), p.nextEpoch(), true, true, true, true, true),
            new CustomerCredentialPorts.CommitReceipt(p.command(), UUID.randomUUID(), p.digest(), p.audit(), p.permit(), p.nextState(), p.capability(), p.nextAccountRevision(), p.nextEpoch(), true, true, true, true, true),
            new CustomerCredentialPorts.CommitReceipt(p.command(), p.account(), "0".repeat(64), p.audit(), p.permit(), p.nextState(), p.capability(), p.nextAccountRevision(), p.nextEpoch(), true, true, true, true, true),
            new CustomerCredentialPorts.CommitReceipt(p.command(), p.account(), p.digest(), UUID.randomUUID(), p.permit(), p.nextState(), p.capability(), p.nextAccountRevision(), p.nextEpoch(), true, true, true, true, true),
            new CustomerCredentialPorts.CommitReceipt(p.command(), p.account(), p.digest(), p.audit(), p.permit(), p.nextState(), p.capability(), p.nextAccountRevision()+1, p.nextEpoch(), true, true, true, true, true),
            new CustomerCredentialPorts.CommitReceipt(p.command(), p.account(), p.digest(), p.audit(), p.permit(), p.nextState(), p.capability(), p.nextAccountRevision(), p.nextEpoch()+1, true, true, true, true, true)})
            assertThat(CustomerCredentialPorts.verify(p, r)).isEqualTo(CustomerCredentialPorts.Outcome.RECONCILIATION_REQUIRED);
        assertThat(CustomerCredentialPorts.verify(p, good)).isEqualTo(CustomerCredentialPorts.Outcome.RECORDED);
    }
    @Test void deliveryAcknowledgementRequiresExactProtectedReferenceAndKnownOutcome() {
        var command = UUID.randomUUID(); var account = UUID.randomUUID(); var payload = UUID.randomUUID();
        var intent = new CustomerCredentialPorts.DeliveryIntent(command, account, payload, "delivery-1");
        var good = new CustomerCredentialPorts.DeliveryReceipt(command, account, payload, "delivery-1", true, true);
        assertThat(CustomerCredentialPorts.verify(intent, good)).isEqualTo(CustomerCredentialPorts.Outcome.RECORDED);
        for (var r : new CustomerCredentialPorts.DeliveryReceipt[] {null,
            new CustomerCredentialPorts.DeliveryReceipt(command, account, payload, "delivery-1", true, false),
            new CustomerCredentialPorts.DeliveryReceipt(command, account, UUID.randomUUID(), "delivery-1", true, true),
            new CustomerCredentialPorts.DeliveryReceipt(command, account, payload, "stale", true, true),
            new CustomerCredentialPorts.DeliveryReceipt(command, account, payload, "delivery-1", false, true)})
            assertThat(CustomerCredentialPorts.verify(intent, r)).isEqualTo(CustomerCredentialPorts.Outcome.RECONCILIATION_REQUIRED);
        assertThat(intent.toString()).doesNotContain(payload.toString());
        assertThat(good.toString()).doesNotContain(payload.toString());
    }
    CustomerCredentialLifecycle.Plan changed(CustomerCredentialLifecycle.Plan p, String field, Object replacement) throws Exception {
        var components = CustomerCredentialLifecycle.Plan.class.getRecordComponents();
        var types = new Class<?>[components.length]; var values = new Object[components.length];
        for (int i=0;i<components.length;i++) { types[i]=components[i].getType(); values[i]=components[i].getName().equals(field) ? replacement : components[i].getAccessor().invoke(p); }
        return CustomerCredentialLifecycle.Plan.class.getDeclaredConstructor(types).newInstance(values);
    }
    @Test void manuallyMalformedOrTamperedPlansNeverBecomeRecordedOrThrow() throws Exception {
        var p = plan(); var r = receipt(p, true, true, true);
        for (String field : new String[] {"command", "account", "action", "permit", "audit", "expectedState", "nextState", "policyRevision", "privacyRevision", "deliveryRevision", "occurredAt", "digest"})
            assertThat(CustomerCredentialPorts.verify(changed(p, field, null), r)).isEqualTo(CustomerCredentialPorts.Outcome.RECONCILIATION_REQUIRED);
        assertThat(CustomerCredentialPorts.verify(changed(p, "protectedAddress", UUID.randomUUID()), r)).isEqualTo(CustomerCredentialPorts.Outcome.RECONCILIATION_REQUIRED);
        assertThat(CustomerCredentialPorts.verify(null, r)).isEqualTo(CustomerCredentialPorts.Outcome.RECONCILIATION_REQUIRED);
    }
    @Test void recomputedDigestDoesNotPermitInvalidActionEffectsOrState() throws Exception {
        var p = plan(); var method = CustomerCredentialLifecycle.class.getDeclaredMethod("fingerprint", CustomerCredentialLifecycle.Plan.class); method.setAccessible(true);
        for (var change : java.util.Map.of("nextEpoch", (Object)99L, "nextState", CustomerCredentialLifecycle.State.PENDING,
            "consumeCapability", false, "invalidateSessions", false, "deliveryRequired", true, "audit", UUID.randomUUID()).entrySet()) {
            var bad = changed(p, change.getKey(), change.getValue()); bad = changed(bad, "digest", method.invoke(null, bad));
            assertThat(CustomerCredentialPorts.verify(bad, receipt(bad, bad.consumeCapability(), bad.invalidateSessions(), true))).isEqualTo(CustomerCredentialPorts.Outcome.RECONCILIATION_REQUIRED);
        }
    }
    @Test void policyReferencesCannotAliasThroughDelimiterConcatenation() throws Exception {
        var p = plan(); var method = CustomerCredentialLifecycle.class.getDeclaredMethod("fingerprint", CustomerCredentialLifecycle.Plan.class); method.setAccessible(true);
        var one = changed(changed(p, "policyRevision", "a|b"), "privacyRevision", "c");
        var two = changed(changed(p, "policyRevision", "a"), "privacyRevision", "b|c");
        assertThat(method.invoke(null, one)).isNotEqualTo(method.invoke(null, two));
    }
    @Test void malformedUnicodePolicyPlanCannotBeAcknowledgedEvenWithRecomputedDigest() throws Exception {
        var p = plan(); var method = CustomerCredentialLifecycle.class.getDeclaredMethod("fingerprint", CustomerCredentialLifecycle.Plan.class); method.setAccessible(true);
        for (String field : new String[] {"policyRevision", "privacyRevision", "deliveryRevision"}) {
            var invalid = changed(p, field, "bad\uD800");
            invalid = changed(invalid, "digest", method.invoke(null, invalid));
            assertThat(CustomerCredentialPorts.verify(invalid, receipt(invalid, true, true, true)))
                .isEqualTo(CustomerCredentialPorts.Outcome.RECONCILIATION_REQUIRED);
        }
    }
    @Test void wrongPermitCapabilityStateAndSurplusEffectsDeny() {
        var p = plan();
        for (var r : new CustomerCredentialPorts.CommitReceipt[] {
            new CustomerCredentialPorts.CommitReceipt(p.command(), p.account(), p.digest(), p.audit(), UUID.randomUUID(), p.nextState(), p.capability(), p.nextAccountRevision(), p.nextEpoch(), true, true, true, true, true),
            new CustomerCredentialPorts.CommitReceipt(p.command(), p.account(), p.digest(), p.audit(), p.permit(), CustomerCredentialLifecycle.State.PENDING, p.capability(), p.nextAccountRevision(), p.nextEpoch(), true, true, true, true, true),
            new CustomerCredentialPorts.CommitReceipt(p.command(), p.account(), p.digest(), p.audit(), p.permit(), p.nextState(), UUID.randomUUID(), p.nextAccountRevision(), p.nextEpoch(), true, true, true, true, true)})
            assertThat(CustomerCredentialPorts.verify(p, r)).isEqualTo(CustomerCredentialPorts.Outcome.RECONCILIATION_REQUIRED);
        var request = fixture.core.prepare(fixture.command(CustomerCredentialLifecycle.Action.RECOVERY_REQUEST), fixture.snapshot(CustomerCredentialLifecycle.Action.RECOVERY_REQUEST));
        assertThat(CustomerCredentialPorts.verify(request, receipt(request, true, false, true))).isEqualTo(CustomerCredentialPorts.Outcome.RECONCILIATION_REQUIRED);
        assertThat(CustomerCredentialPorts.verify(request, receipt(request, false, true, true))).isEqualTo(CustomerCredentialPorts.Outcome.RECONCILIATION_REQUIRED);
    }
}
