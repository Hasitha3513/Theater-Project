package com.cinenest.identity.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Offline Identity contract only: no bean, endpoint, capability issuer or persistence adapter.
 * Snapshot facts must eventually be read/locked by an approved Identity-owned adapter.
 * A plan is never a credential activation or authentication permit.
 */
final class LocalCredentialRecovery {
    static final Duration MAX_CAPABILITY_LIFETIME = Duration.ofMinutes(15);
    enum Reason { DISABLED, ENVIRONMENT, ACCOUNT, AUTHORITY, CAPABILITY, EXPIRED, REPLAY, REVISION, PASSWORD, UNAVAILABLE }
    static final class Denied extends RuntimeException {
        final Reason reason;
        Denied(Reason reason) { super("Local synthetic credential recovery denied"); this.reason=reason; }
    }
    record Policy(boolean enabled, Set<String> profiles, Duration maximumLifetime) {
        Policy { profiles=Set.copyOf(profiles); Objects.requireNonNull(maximumLifetime); }
        static Policy disabled() { return new Policy(false,Set.of(),MAX_CAPABILITY_LIFETIME); }
    }
    record Account(UUID id, boolean synthetic, boolean eligible, boolean verified,
                   long revision, long credentialRevision, Set<UUID> venues) {
        Account { Objects.requireNonNull(id); venues=Set.copyOf(venues); }
    }
    /** Authority is an independently verified fact, never a client role or a new grant. */
    record Actor(UUID id, boolean synthetic, boolean eligible, Set<UUID> venues) {
        Actor { Objects.requireNonNull(id); venues=Set.copyOf(venues); }
    }
    record Capability(UUID id, UUID account, UUID actor, UUID venue, long accountRevision,
                      long credentialRevision, String tokenDigest, Instant issuedAt,
                      Instant expiresAt, boolean consumed) {
        @Override public String toString() { return "Capability[REDACTED]"; }
    }
    record Command(UUID id, UUID account, UUID actor, UUID venue, long expectedAccountRevision,
                   long expectedCredentialRevision, String token, String newPassword) {
        @Override public String toString() { return "RecoveryCommand[REDACTED]"; }
    }
    record Snapshot(Account account, Actor actor, Capability capability) {}
    record Audit(UUID id, UUID command, UUID capability, UUID actor, UUID account, UUID venue,
                 long previousCredentialRevision, long nextCredentialRevision, Instant occurredAt) {}
    record SessionInvalidation(UUID account, long credentialRevision, boolean allSessions) {}
    record Append(UUID command, UUID capability, UUID account, UUID actor, UUID venue,
                  long expectedAccountRevision, long previousCredentialRevision,
                  long nextCredentialRevision, String encodedPassword, Audit audit,
                  SessionInvalidation sessionInvalidation) {
        @Override public String toString() { return "CredentialAppend[REDACTED]"; }
    }
    /** Future transaction must recheck current facts and expiry, consume once, append (never UPDATE
     * V28), audit and invalidate all sessions atomically. Any failure rolls back every write.
     * No implementation is authorized or supplied by this contract-only slice.
     */
    interface AtomicCommitPort { Receipt commit(Append append); }
    record Receipt(UUID command, UUID capability, UUID account, long credentialRevision,
                   UUID audit, SessionInvalidation sessionInvalidation, boolean capabilityConsumed) {}

    private final Policy policy;
    private final Clock clock;
    LocalCredentialRecovery() { this(Policy.disabled(),Clock.systemUTC()); }
    LocalCredentialRecovery(Policy policy, Clock clock) {
        this.policy=Objects.requireNonNull(policy); this.clock=Objects.requireNonNull(clock);
    }
    Append prepare(Command command, Snapshot snapshot) {
        if(!policy.enabled()) throw new Denied(Reason.DISABLED);
        if(!Set.of(Set.of("local-synthetic"),Set.of("local-synthetic","synthetic-quotation")).contains(policy.profiles()))
            throw new Denied(Reason.ENVIRONMENT);
        try {
            if(policy.maximumLifetime().isZero() || policy.maximumLifetime().isNegative()
                    || policy.maximumLifetime().compareTo(MAX_CAPABILITY_LIFETIME)>0) throw new Denied(Reason.CAPABILITY);
            if(command==null || command.id()==null || command.account()==null || command.actor()==null || command.venue()==null)
                throw new Denied(Reason.CAPABILITY);
            if(snapshot==null || snapshot.account()==null) throw new Denied(Reason.ACCOUNT);
            var a=snapshot.account();var actor=snapshot.actor();var c=snapshot.capability();
            if(!a.id().equals(command.account()) || !a.synthetic() || !a.eligible() || !a.verified()) throw new Denied(Reason.ACCOUNT);
            if(actor==null || !actor.id().equals(command.actor()) || !actor.synthetic() || !actor.eligible()
                    || !actor.venues().contains(command.venue()) || !a.venues().contains(command.venue())) throw new Denied(Reason.AUTHORITY);
            if(c==null || c.id()==null || !command.account().equals(c.account()) || !command.actor().equals(c.actor())
                    || !command.venue().equals(c.venue())) throw new Denied(Reason.CAPABILITY);
            if(c.consumed()) throw new Denied(Reason.REPLAY);
            Instant now=clock.instant();
            if(c.issuedAt()==null || c.expiresAt()==null || c.issuedAt().isAfter(now) || !c.expiresAt().isAfter(now)
                    || !c.expiresAt().isAfter(c.issuedAt())
                    || Duration.between(c.issuedAt(),c.expiresAt()).compareTo(policy.maximumLifetime())>0) throw new Denied(Reason.EXPIRED);
            if(a.revision()<0 || a.credentialRevision()<0 || a.revision()!=command.expectedAccountRevision()
                    || a.credentialRevision()!=command.expectedCredentialRevision() || a.revision()!=c.accountRevision()
                    || a.credentialRevision()!=c.credentialRevision()) throw new Denied(Reason.REVISION);
            if(command.token()==null || !command.token().matches("[0-9a-f]{64}") || c.tokenDigest()==null
                    || !c.tokenDigest().matches("[0-9a-f]{64}")
                    || !MessageDigest.isEqual(LocalCustomerIdentity.digest(command.token()).getBytes(StandardCharsets.US_ASCII),
                        c.tokenDigest().getBytes(StandardCharsets.US_ASCII))) throw new Denied(Reason.CAPABILITY);
            String password;
            try { password=LocalCustomerIdentity.password(command.newPassword()); }
            catch(IllegalArgumentException invalid) { throw new Denied(Reason.PASSWORD); }
            long next=Math.addExact(a.credentialRevision(),1);
            UUID auditId=UUID.nameUUIDFromBytes(("LOCAL_CREDENTIAL_RECOVERY:"+command.id()).getBytes(StandardCharsets.US_ASCII));
            return new Append(command.id(),c.id(),a.id(),actor.id(),command.venue(),a.revision(),a.credentialRevision(),next,
                    "{pbkdf2-v1}"+LocalCustomerIdentity.credentialEncoder().encode(password),
                    new Audit(auditId,command.id(),c.id(),actor.id(),a.id(),command.venue(),a.credentialRevision(),next,now),
                    new SessionInvalidation(a.id(),next,true));
        } catch(Denied refusal) { throw refusal; }
        catch(RuntimeException unavailable) { throw new Denied(Reason.UNAVAILABLE); }
    }
    /** A malformed receipt never becomes an activation acknowledgement. Does not call a port. */
    static void verifyReceipt(Append append, Receipt receipt) {
        if(append==null || receipt==null || append.audit()==null || append.sessionInvalidation()==null
                || !Objects.equals(append.command(),receipt.command()) || !Objects.equals(append.capability(),receipt.capability())
                || !Objects.equals(append.account(),receipt.account()) || append.nextCredentialRevision()!=receipt.credentialRevision()
                || !Objects.equals(append.audit().id(),receipt.audit()) || !receipt.capabilityConsumed()
                || !append.sessionInvalidation().allSessions()
                || !Objects.equals(append.sessionInvalidation(),receipt.sessionInvalidation())) throw new Denied(Reason.UNAVAILABLE);
    }
}
