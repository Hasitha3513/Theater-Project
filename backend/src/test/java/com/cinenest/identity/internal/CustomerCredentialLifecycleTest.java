package com.cinenest.identity.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;
import static com.cinenest.identity.internal.CustomerCredentialLifecycle.*;

class CustomerCredentialLifecycleTest {
    final UUID account = UUID.randomUUID(), commandId = UUID.randomUUID(), address = UUID.randomUUID(), lease = UUID.randomUUID();
    final Instant now = Instant.parse("2026-11-27T00:00:00Z");
    final String password = "Synthetic credential sample 2026!", token = "a".repeat(64);
    final Policy policy = new Policy(true, "policy-1", "privacy-1", "delivery-1", Duration.ofMinutes(5), 2);
    final CustomerCredentialLifecycle core = new CustomerCredentialLifecycle(policy, () -> now);
    Command command(Action action) { return new Command(commandId, account, action, 2, 3,
        action == Action.REGISTER || action == Action.LOGIN || action == Action.RECOVER ? password : null,
        action == Action.VERIFY || action == Action.RECOVER ? token : null); }
    Snapshot snapshot(Action action) {
        return new Snapshot(new Account(account, action == Action.VERIFY ? State.PENDING : State.VERIFIED,
            2, 3, address, "privacy-1", CustomerCredentialSecurity.encode(password)),
            new Capability(UUID.randomUUID(), account, action, 2, 3, CustomerCredentialSecurity.digest(token),
                now.minusSeconds(10), now.plusSeconds(60), false, "policy-1", "privacy-1", "delivery-1"),
            new Permit(lease, commandId, account, action, "policy-1", now.minusSeconds(1), now.plusSeconds(30), true, false, 0), "policy-1");
    }
    void denied(Command c, Snapshot s) {
        assertThatThrownBy(() -> core.prepare(c, s)).isInstanceOf(Denied.class)
            .hasMessage("Customer credential operation unavailable");
    }
    @ParameterizedTest @EnumSource(Action.class)
    void disabledByDefaultBeforeAnyExternalOrHashWork(Action action) {
        var calls = new AtomicInteger();
        var disabled = new CustomerCredentialLifecycle(Policy.disabled(), () -> { calls.incrementAndGet(); return now; });
        assertThatThrownBy(() -> disabled.prepare(command(action), null)).isInstanceOf(Denied.class);
        assertThat(calls).hasValue(0);
    }
    @Test void registrationCreatesPendingCredentialAndDeliveryPlanOnly() {
        var cmd = new Command(commandId, account, Action.REGISTER, 0, 0, password, null);
        var s = snapshot(Action.REGISTER);
        var absent = new Account(account, State.ABSENT, 0, 0, address, "privacy-1", null);
        var plan = core.prepare(cmd, new Snapshot(absent, null, s.permit(), s.currentPolicyRevision()));
        assertThat(plan.nextAccountRevision()).isEqualTo(1);
        assertThat(plan.nextEpoch()).isZero(); assertThat(plan.deliveryRequired()).isTrue();
        assertThat(plan.nextState()).isEqualTo(State.PENDING);
        assertThat(plan.evidence()).isEqualTo(AuditEvidence.ANONYMOUS_REQUEST);
        assertThat(plan.consumeCapability()).isFalse(); assertThat(plan.invalidateSessions()).isFalse();
        assertThat(CustomerCredentialSecurity.matches(password, plan.encodedPassword())).isTrue();
        assertThat(plan.toString()).doesNotContain(password, plan.encodedPassword(), address.toString());
        denied(command(Action.REGISTER), snapshot(Action.REGISTER));
    }
    @Test void verificationConsumesBoundCapabilityWithoutAuthenticating() {
        var plan = core.prepare(command(Action.VERIFY), snapshot(Action.VERIFY));
        assertThat(plan.consumeCapability()).isTrue(); assertThat(plan.encodedPassword()).isNull();
        assertThat(plan.nextAccountRevision()).isEqualTo(3); assertThat(plan.nextEpoch()).isEqualTo(3);
        assertThat(plan.deliveryRequired()).isFalse();
        assertThat(plan.nextState()).isEqualTo(State.VERIFIED);
        assertThat(plan.evidence()).isEqualTo(AuditEvidence.CAPABILITY_PROOF);
    }
    @Test void loginRequiresVerifiedCurrentAccountAndMatchingPassword() {
        var s = snapshot(Action.LOGIN); var c = command(Action.LOGIN);
        var plan = core.prepare(c, s); assertThat(plan.nextAccountRevision()).isEqualTo(2);
        assertThat(plan.encodedPassword()).isNull();
        denied(new Command(commandId, account, Action.LOGIN, 2, 3, "Incorrect credential sample!", null), s);
        for (State state : State.values()) if (state != State.VERIFIED)
            denied(c, new Snapshot(new Account(account, state, 2, 3, address, "privacy-1", s.account().encodedPassword()), null, s.permit(), "policy-1"));
    }
    @Test void recoveryChangesEpochAndRequiresAtomicSessionInvalidation() {
        var s = snapshot(Action.RECOVER); var plan = core.prepare(command(Action.RECOVER), s);
        assertThat(plan.nextEpoch()).isEqualTo(4); assertThat(plan.previousEpoch()).isEqualTo(3);
        assertThat(plan.invalidateSessions()).isTrue(); assertThat(plan.consumeCapability()).isTrue();
        assertThat(plan.audit()).isNotNull(); assertThat(plan.occurredAt()).isEqualTo(now);
        assertThat(s.account().epoch()).isEqualTo(3);
    }
    @Test void recoveryRequestPlansDeliveryWithoutChangingCredentials() {
        var p = core.prepare(command(Action.RECOVERY_REQUEST), snapshot(Action.RECOVERY_REQUEST));
        assertThat(p.deliveryRequired()).isTrue(); assertThat(p.encodedPassword()).isNull();
        assertThat(p.nextEpoch()).isEqualTo(p.previousEpoch());
    }
    @Test void staleAccountCredentialOrPolicyRevisionAndMissingPrivacyDeny() {
        var c = command(Action.RECOVER); var s = snapshot(Action.RECOVER);
        denied(new Command(commandId, account, Action.RECOVER, 3, 3, password, token), s);
        denied(new Command(commandId, account, Action.RECOVER, 2, 4, password, token), s);
        denied(c, new Snapshot(s.account(), s.capability(), s.permit(), "old-policy"));
        denied(c, new Snapshot(new Account(account, State.VERIFIED, 2, 3, address, "old-privacy", s.account().encodedPassword()), s.capability(), s.permit(), "policy-1"));
    }
    @Test void capabilityPurposeAccountEpochRevisionAndTokenAreBound() {
        var c = command(Action.RECOVER); var s = snapshot(Action.RECOVER); var p = s.capability();
        for (var invalid : new Capability[] {
            new Capability(p.id(), UUID.randomUUID(), p.purpose(), 2, 3, p.digest(), p.issued(), p.expires(), false, "policy-1", "privacy-1", "delivery-1"),
            new Capability(p.id(), account, Action.VERIFY, 2, 3, p.digest(), p.issued(), p.expires(), false, "policy-1", "privacy-1", "delivery-1"),
            new Capability(p.id(), account, p.purpose(), 3, 3, p.digest(), p.issued(), p.expires(), false, "policy-1", "privacy-1", "delivery-1"),
            new Capability(p.id(), account, p.purpose(), 2, 4, p.digest(), p.issued(), p.expires(), false, "policy-1", "privacy-1", "delivery-1"),
            new Capability(p.id(), account, p.purpose(), 2, 3, p.digest(), p.issued(), p.expires(), true, "policy-1", "privacy-1", "delivery-1") })
            denied(c, new Snapshot(s.account(), invalid, s.permit(), "policy-1"));
        denied(new Command(commandId, account, Action.RECOVER, 2, 3, password, "b".repeat(64)), s);
    }
    @Test void expiredFutureOversizedAndEqualityCapabilityWindowsDeny() {
        var s = snapshot(Action.RECOVER); var p = s.capability();
        for (Instant[] window : new Instant[][] {{now.minusSeconds(10), now}, {now.plusSeconds(1), now.plusSeconds(2)},
            {now.minusSeconds(300), now.plusSeconds(1)}, {now, now}})
            denied(command(Action.RECOVER), new Snapshot(s.account(), new Capability(p.id(), account, Action.RECOVER, 2, 3,
                p.digest(), window[0], window[1], false, "policy-1", "privacy-1", "delivery-1"), s.permit(), "policy-1"));
    }
    @Test void missingRevokedConsumedWrongCommandAndSaturatedAbusePermitsDeny() {
        var s = snapshot(Action.RECOVER); var p = s.permit();
        Permit[] invalid = {null,
            new Permit(lease, commandId, account, Action.RECOVER, "policy-1", p.issued(), p.expires(), false, false, 0),
            new Permit(lease, commandId, account, Action.RECOVER, "policy-1", p.issued(), p.expires(), true, true, 0),
            new Permit(lease, UUID.randomUUID(), account, Action.RECOVER, "policy-1", p.issued(), p.expires(), true, false, 0),
            new Permit(lease, commandId, account, Action.RECOVER, "policy-1", p.issued(), p.expires(), true, false, 2),
            new Permit(lease, commandId, account, Action.RECOVER, "policy-1", p.issued(), now, true, false, 0),
            new Permit(lease, commandId, UUID.randomUUID(), Action.RECOVER, "policy-1", p.issued(), p.expires(), true, false, 0),
            new Permit(lease, commandId, account, Action.VERIFY, "policy-1", p.issued(), p.expires(), true, false, 0)};
        for (var permit : invalid) denied(command(Action.RECOVER), new Snapshot(s.account(), s.capability(), permit, "policy-1"));
    }
    @Test void policyMustHaveExplicitConfigurationAndTimeFailureIsSanitized() {
        var invalid = new Policy(true, "policy-1", "privacy-1", null, Duration.ofMinutes(5), 2);
        assertThatThrownBy(() -> new CustomerCredentialLifecycle(invalid, () -> now).prepare(command(Action.RECOVER), snapshot(Action.RECOVER))).isInstanceOf(Denied.class);
        assertThatThrownBy(() -> new CustomerCredentialLifecycle(policy, () -> { throw new IllegalStateException(password); })
            .prepare(command(Action.RECOVER), snapshot(Action.RECOVER))).hasMessage("Customer credential operation unavailable");
    }
    @Test void sensitiveRecordsDoNotRenderCredentialsOrProtectedAddress() {
        var s = snapshot(Action.LOGIN); var c = command(Action.LOGIN);
        for (Object x : new Object[] {s, s.account(), s.capability(), c, core.prepare(c, s)})
            assertThat(x.toString()).doesNotContain(password, token, address.toString());
    }
    @Test void capabilityCannotSurviveIndependentPolicyPrivacyOrDeliveryRevisionChanges() {
        var c = command(Action.RECOVER); var s = snapshot(Action.RECOVER);
        for (var changed : new Policy[] {
            new Policy(true, "policy-2", "privacy-1", "delivery-1", Duration.ofMinutes(5), 2),
            new Policy(true, "policy-1", "privacy-2", "delivery-1", Duration.ofMinutes(5), 2),
            new Policy(true, "policy-1", "privacy-1", "delivery-2", Duration.ofMinutes(5), 2)}) {
            var a = s.account(); var p = s.permit();
            var current = new Snapshot(new Account(a.id(), a.state(), a.revision(), a.epoch(), a.protectedAddress(), changed.privacyRevision(), a.encodedPassword()),
                s.capability(), new Permit(p.id(), p.command(), p.account(), p.action(), changed.revision(), p.issued(), p.expires(), p.current(), p.consumed(), p.activeHashes()), changed.revision());
            assertThatThrownBy(() -> new CustomerCredentialLifecycle(changed, () -> now).prepare(c, current)).isInstanceOf(Denied.class);
        }
    }
    @Test void existingCredentialStatesRequireQualifiedHashShape() {
        for (var action : new Action[] {Action.VERIFY, Action.RECOVERY_REQUEST, Action.RECOVER}) {
            var s = snapshot(action); var a = s.account();
            denied(command(action), new Snapshot(new Account(a.id(), a.state(), a.revision(), a.epoch(), address, "privacy-1", null), s.capability(), s.permit(), "policy-1"));
        }
    }
    @Test void revisionOverflowCannotProduceAPlan() {
        var s = snapshot(Action.RECOVER); var a = s.account(); var cap = s.capability();
        var overflow = new Account(a.id(), State.VERIFIED, Long.MAX_VALUE, 3, address, "privacy-1", a.encodedPassword());
        var capability = new Capability(cap.id(), account, Action.RECOVER, Long.MAX_VALUE, 3, cap.digest(), cap.issued(), cap.expires(), false, "policy-1", "privacy-1", "delivery-1");
        denied(new Command(commandId, account, Action.RECOVER, Long.MAX_VALUE, 3, password, token), new Snapshot(overflow, capability, s.permit(), "policy-1"));
    }
    @Test void malformedUnicodeAndControlPolicyReferencesDeny() {
        for (String ref : new String[] {"bad\uD800", "bad\uDC00", "bad\nref", "bad\u0000ref"}) {
            for (Policy invalid : new Policy[] {
                new Policy(true, ref, "privacy-1", "delivery-1", Duration.ofMinutes(5), 2),
                new Policy(true, "policy-1", ref, "delivery-1", Duration.ofMinutes(5), 2),
                new Policy(true, "policy-1", "privacy-1", ref, Duration.ofMinutes(5), 2)})
                assertThatThrownBy(() -> new CustomerCredentialLifecycle(invalid, () -> now)
                    .prepare(command(Action.RECOVER), snapshot(Action.RECOVER))).isInstanceOf(Denied.class);
        }
    }
    @Test void validUnicodeAndSeparatorReferencesRemainSupported() {
        var s = snapshot(Action.RECOVER); var a = s.account(); var cap = s.capability(); var p = s.permit();
        var unicode = new Policy(true, "p|\u03c0\uD83D\uDE00", "privacy-1", "delivery-1", Duration.ofMinutes(5), 2);
        var current = new Snapshot(a, new Capability(cap.id(), account, Action.RECOVER, 2, 3, cap.digest(), cap.issued(), cap.expires(), false,
            unicode.revision(), unicode.privacyRevision(), unicode.deliveryRevision()),
            new Permit(p.id(), p.command(), p.account(), p.action(), unicode.revision(), p.issued(), p.expires(), true, false, 0), unicode.revision());
        var plan = new CustomerCredentialLifecycle(unicode, () -> now).prepare(command(Action.RECOVER), current);
        assertThat(CustomerCredentialLifecycle.validPlan(plan)).isTrue();
    }
}
