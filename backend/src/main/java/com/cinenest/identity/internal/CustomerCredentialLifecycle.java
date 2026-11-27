package com.cinenest.identity.internal;

import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Inert provider-neutral planner. A plan is not authentication, delivery or account activation.
 * All snapshots/permits/time are trusted adapter facts, never caller declarations. No adapter is wired.
 */
final class CustomerCredentialLifecycle {
    enum Action { REGISTER, VERIFY, LOGIN, RECOVERY_REQUEST, RECOVER }
    enum State { ABSENT, PENDING, VERIFIED, SUSPENDED }
    enum AuditEvidence { ANONYMOUS_REQUEST, CREDENTIAL_PROOF, CAPABILITY_PROOF }
    static final class Denied extends RuntimeException {
        Denied() { super("Customer credential operation unavailable"); }
    }
    record Policy(boolean enabled, String revision, String privacyRevision, String deliveryRevision,
        Duration maximumCapabilityLifetime, int maximumHashConcurrency) {
        static Policy disabled() { return new Policy(false, null, null, null, null, 0); }
    }
    record Account(UUID id, State state, long revision, long epoch, UUID protectedAddress,
        String acceptedPrivacyRevision, String encodedPassword) {
        @Override public String toString() { return "CredentialAccount[REDACTED]"; }
    }
    record Capability(UUID id, UUID account, Action purpose, long accountRevision, long epoch,
        String digest, Instant issued, Instant expires, boolean consumed, String policyRevision, String privacyRevision, String deliveryRevision) {
        @Override public String toString() { return "CredentialCapability[REDACTED]"; }
    }
    record Permit(UUID id, UUID command, UUID account, Action action, String policyRevision,
        Instant issued, Instant expires, boolean current, boolean consumed, int activeHashes) {}
    record Snapshot(Account account, Capability capability, Permit permit, String currentPolicyRevision) {
        @Override public String toString() { return "CredentialSnapshot[REDACTED]"; }
    }
    record Command(UUID id, UUID account, Action action, long expectedRevision, long expectedEpoch,
        String password, String token) {
        @Override public String toString() { return "CredentialCommand[REDACTED]"; }
    }
    record Plan(UUID command, UUID account, Action action, UUID permit, UUID audit,
        long expectedAccountRevision, long nextAccountRevision, State expectedState, State nextState, long previousEpoch, long nextEpoch,
        UUID capability, boolean consumeCapability, boolean invalidateSessions, boolean deliveryRequired,
        UUID protectedAddress, String policyRevision, String privacyRevision, String deliveryRevision, String encodedPassword, Instant occurredAt, String digest) {
        @Override public String toString() { return "CredentialPlan[REDACTED]"; }
        /** Account is the subject, never an asserted authenticated actor. A future adapter must
         * attribute its actual authenticated execution principal separately in the atomic audit.
         */
        AuditEvidence evidence() { return switch (action) {
            case REGISTER, RECOVERY_REQUEST -> AuditEvidence.ANONYMOUS_REQUEST;
            case LOGIN -> AuditEvidence.CREDENTIAL_PROOF;
            case VERIFY, RECOVER -> AuditEvidence.CAPABILITY_PROOF;
        }; }
    }
    private final Policy policy;
    private final CustomerCredentialPorts.TrustedTime time;
    CustomerCredentialLifecycle() { this(Policy.disabled(), () -> { throw new Denied(); }); }
    CustomerCredentialLifecycle(Policy policy, CustomerCredentialPorts.TrustedTime time) {
        this.policy = Objects.requireNonNull(policy); this.time = Objects.requireNonNull(time);
    }
    Plan prepare(Command command, Snapshot snapshot) {
        if (!policy.enabled()) throw new Denied();
        try {
            require(text(policy.revision()) && text(policy.privacyRevision()) && text(policy.deliveryRevision())
                && policy.maximumCapabilityLifetime() != null && !policy.maximumCapabilityLifetime().isNegative()
                && !policy.maximumCapabilityLifetime().isZero() && policy.maximumHashConcurrency() > 0);
            require(command != null && command.id() != null && command.account() != null && command.action() != null);
            require(snapshot != null && snapshot.account() != null && policy.revision().equals(snapshot.currentPolicyRevision()));
            var account = snapshot.account(); var permit = snapshot.permit(); var capability = snapshot.capability();
            require(account.id() != null && account.id().equals(command.account()) && account.state() != null
                && account.revision() >= 0 && account.epoch() >= 0 && account.protectedAddress() != null
                && account.revision() == command.expectedRevision() && account.epoch() == command.expectedEpoch()
                && policy.privacyRevision().equals(account.acceptedPrivacyRevision())
                && (account.state() == State.ABSENT ? account.encodedPassword() == null : CustomerCredentialSecurity.encodedShape(account.encodedPassword())));
            Instant now = Objects.requireNonNull(time.databaseTime());
            require(permit != null && permit.id() != null && command.id().equals(permit.command())
                && account.id().equals(permit.account()) && command.action() == permit.action()
                && policy.revision().equals(permit.policyRevision()) && permit.current() && !permit.consumed()
                && permit.issued() != null && permit.expires() != null && !permit.issued().isAfter(now)
                && permit.expires().isAfter(now) && permit.activeHashes() >= 0);
            boolean hashes = command.action() == Action.REGISTER || command.action() == Action.LOGIN || command.action() == Action.RECOVER;
            require(!hashes || permit.activeHashes() < policy.maximumHashConcurrency());
            boolean consumes = command.action() == Action.VERIFY || command.action() == Action.RECOVER;
            if (consumes) {
                require(capability != null && capability.id() != null && account.id().equals(capability.account())
                    && command.action() == capability.purpose() && !capability.consumed()
                    && policy.revision().equals(capability.policyRevision()) && policy.privacyRevision().equals(capability.privacyRevision())
                    && policy.deliveryRevision().equals(capability.deliveryRevision())
                    && capability.accountRevision() == account.revision() && capability.epoch() == account.epoch()
                    && capability.issued() != null && capability.expires() != null && !capability.issued().isAfter(now)
                    && capability.expires().isAfter(now) && capability.expires().isAfter(capability.issued())
                    && Duration.between(capability.issued(), capability.expires()).compareTo(policy.maximumCapabilityLifetime()) <= 0
                    && CustomerCredentialSecurity.tokenMatches(command.token(), capability.digest()));
            }
            String encoded = null; long nextEpoch = account.epoch();
            switch (command.action()) {
                case REGISTER -> {
                    require(account.state() == State.ABSENT && account.revision() == 0 && account.epoch() == 0
                        && account.encodedPassword() == null && command.token() == null);
                    encoded = CustomerCredentialSecurity.encode(command.password());
                }
                case VERIFY -> require(account.state() == State.PENDING && command.password() == null);
                case LOGIN -> require(account.state() == State.VERIFIED && command.token() == null
                    && CustomerCredentialSecurity.matches(command.password(), account.encodedPassword()));
                case RECOVERY_REQUEST -> require(account.state() == State.VERIFIED && command.password() == null && command.token() == null);
                case RECOVER -> {
                    require(account.state() == State.VERIFIED);
                    encoded = CustomerCredentialSecurity.encode(command.password()); nextEpoch = Math.addExact(account.epoch(), 1);
                }
            }
            long nextRevision = command.action() == Action.REGISTER || command.action() == Action.VERIFY || command.action() == Action.RECOVER
                ? Math.addExact(account.revision(), 1) : account.revision();
            UUID audit = auditId(command.id());
            boolean invalidates = command.action() == Action.RECOVER;
            boolean delivery = command.action() == Action.REGISTER || command.action() == Action.RECOVERY_REQUEST;
            UUID capId = consumes ? capability.id() : null;
            State nextState = command.action() == Action.REGISTER ? State.PENDING
                : command.action() == Action.VERIFY ? State.VERIFIED : account.state();
            var unsigned = new Plan(command.id(), account.id(), command.action(), permit.id(), audit, account.revision(), nextRevision,
                account.state(), nextState, account.epoch(), nextEpoch, capId, consumes, invalidates, delivery, account.protectedAddress(), policy.revision(), policy.privacyRevision(), policy.deliveryRevision(),
                encoded, now, null);
            return new Plan(unsigned.command(), unsigned.account(), unsigned.action(), unsigned.permit(), unsigned.audit(), unsigned.expectedAccountRevision(),
                unsigned.nextAccountRevision(), unsigned.expectedState(), unsigned.nextState(), unsigned.previousEpoch(), unsigned.nextEpoch(),
                unsigned.capability(), unsigned.consumeCapability(), unsigned.invalidateSessions(), unsigned.deliveryRequired(), unsigned.protectedAddress(),
                unsigned.policyRevision(), unsigned.privacyRevision(), unsigned.deliveryRevision(), unsigned.encodedPassword(), unsigned.occurredAt(), fingerprint(unsigned));
        } catch (Denied denied) { throw denied; }
        catch (RuntimeException unavailable) { throw new Denied(); }
    }
    static boolean validPlan(Plan p) {
        if (p == null || p.command() == null || p.account() == null || p.action() == null || p.permit() == null || p.audit() == null
            || p.expectedState() == null || p.nextState() == null || p.protectedAddress() == null || p.occurredAt() == null
            || !text(p.policyRevision()) || !text(p.privacyRevision()) || !text(p.deliveryRevision()) || p.expectedAccountRevision() < 0 || p.nextAccountRevision() < 0
            || p.previousEpoch() < 0 || p.nextEpoch() < 0 || p.digest() == null || !p.digest().matches("[0-9a-f]{64}")) return false;
        try {
            boolean mutate = p.action() == Action.REGISTER || p.action() == Action.VERIFY || p.action() == Action.RECOVER;
            boolean recover = p.action() == Action.RECOVER;
            boolean consumes = p.action() == Action.VERIFY || recover;
            boolean delivery = p.action() == Action.REGISTER || p.action() == Action.RECOVERY_REQUEST;
            State next = p.action() == Action.REGISTER ? State.PENDING : p.action() == Action.VERIFY ? State.VERIFIED : p.expectedState();
            State expected = p.action() == Action.REGISTER ? State.ABSENT : p.action() == Action.VERIFY ? State.PENDING : State.VERIFIED;
            boolean encoded = p.action() == Action.REGISTER || recover;
            return p.audit().equals(auditId(p.command())) && p.expectedState() == expected && p.nextState() == next
                && (p.action() != Action.REGISTER || p.expectedAccountRevision() == 0 && p.previousEpoch() == 0)
                && p.nextAccountRevision() == (mutate ? Math.addExact(p.expectedAccountRevision(), 1) : p.expectedAccountRevision())
                && p.nextEpoch() == (recover ? Math.addExact(p.previousEpoch(), 1) : p.previousEpoch())
                && p.consumeCapability() == consumes && (consumes ? p.capability() != null : p.capability() == null)
                && p.invalidateSessions() == recover && p.deliveryRequired() == delivery
                && (encoded ? CustomerCredentialSecurity.encodedShape(p.encodedPassword()) : p.encodedPassword() == null)
                && p.digest().equals(fingerprint(p));
        } catch (RuntimeException invalid) { return false; }
    }
    private static UUID auditId(UUID command) {
        return UUID.nameUUIDFromBytes(("CUSTOMER_CREDENTIAL:" + command).getBytes(StandardCharsets.US_ASCII));
    }
    private static String fingerprint(Plan p) {
        // Length-prefix every field: policy references may contain separators and must never alias.
        var material = new StringBuilder();
        for (Object value : new Object[] {p.command(), p.account(), p.action(), p.permit(), p.audit(),
            p.expectedAccountRevision(), p.nextAccountRevision(), p.expectedState(), p.nextState(), p.previousEpoch(), p.nextEpoch(),
            p.capability(), p.consumeCapability(), p.invalidateSessions(), p.deliveryRequired(), p.protectedAddress(),
            p.policyRevision(), p.privacyRevision(), p.deliveryRevision(), p.encodedPassword(), p.occurredAt()}) {
            if (value == null) material.append("-1:");
            else { String text = value.toString(); material.append(text.length()).append(':').append(text); }
        }
        return CustomerCredentialSecurity.digest(material.toString());
    }
    private static boolean text(String value) {
        return value != null && !value.isBlank() && value.length() <= 128
            && value.codePoints().noneMatch(c -> Character.isISOControl(c) || c >= 0xd800 && c <= 0xdfff);
    }
    private static void require(boolean allowed) { if (!allowed) throw new Denied(); }
}
