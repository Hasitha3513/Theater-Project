package com.cinenest.payments.internal;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Inert admission contract for a future trusted bank-evidence adapter. Inputs must come from
 * trusted, current snapshots, not an HTTP body. This class performs no authentication, signature
 * verification, storage, durable deduplication, financial verification or financial mutation.
 */
final class BankCreditProvenance {
    private BankCreditProvenance() {}

    enum Origin { TRUSTED_BANK_SOURCE, CUSTOMER_RECEIPT, OCR, CUSTOMER_CLAIM }
    enum EvidenceState { UNMATCHED, MATCHED, DISPUTED, REVERSED }
    enum Intent { VERIFICATION_REVIEW, ALLOCATION_REVIEW }
    enum Classification { ELIGIBLE_FOR_INDEPENDENT_REVIEW, EXACT_REPLAY, DUPLICATE_MOVEMENT, CONFLICT, DENIED }
    enum Reason { REVIEW_ONLY, REPLAY_ONLY, DUPLICATE_REQUIRES_REVIEW, CHANGED_COMMAND,
        CHANGED_MOVEMENT_FACTS, DISABLED, INVALID_INPUT, INVALID_SCOPE, INVALID_EVIDENCE,
        INVALID_AUTHORITY, ROLE_CONFLICT, INVALID_EXISTING_OUTCOME }

    /** References are opaque internal UUIDs, never account numbers, credentials or documents. */
    record ReceivingAccountScope(UUID venueId, UUID accountRef, long revision, boolean approved, boolean active) {
        @Override public String toString() { return "ReceivingAccountScope[REDACTED]"; }
    }
    record SourceScope(UUID venueId, UUID sourceRef, long revision, boolean approved, boolean active) {
        @Override public String toString() { return "SourceScope[REDACTED]"; }
    }
    record Authority(UUID principalId, UUID venueId, Intent intent, boolean permitted,
                     Instant validFrom, Instant validUntil) {
        @Override public String toString() { return "Authority[REDACTED]"; }
    }
    /** Stable across revision, amount, currency, digest and command changes. */
    record MovementKey(UUID venueId, UUID accountRef, UUID sourceRef, UUID sourceMovementId) {
        @Override public String toString() { return "MovementKey[REDACTED]"; }
    }
    record Evidence(Origin origin, MovementKey movementKey, long accountRevision, long sourceRevision,
                    String currency, long amountMinor, Instant creditedAt, UUID protectedEvidenceRef,
                    String evidenceSha256, EvidenceState state) {
        @Override public String toString() { return "Evidence[REDACTED]"; }
    }
    record Command(UUID callerId, UUID commandKey, Intent intent) {
        @Override public String toString() { return "Command[REDACTED]"; }
    }
    record ReviewContext(boolean enabled, UUID venueId, UUID customerId, UUID claimantId,
                         ReceivingAccountScope accountScope, SourceScope sourceScope,
                         Authority verifier, Authority allocator, String expectedCurrency,
                         long expectedAmountMinor, Instant now) {
        @Override public String toString() { return "ReviewContext[REDACTED]"; }
    }
    /** At most one trusted prior outcome for this stable movement; never a caller-supplied receipt. */
    record ExistingOutcome(MovementKey movementKey, Command command, String evidenceFingerprint,
                           String commandFingerprint, Classification classification) {
        @Override public String toString() { return "ExistingOutcome[REDACTED]"; }
    }
    /**
     * Future adapters must atomically look up BOTH the global command key and the scoped movement.
     * Completeness flags are trusted adapter assertions, not cryptographic proof. A caller must not
     * supply these snapshots. Concurrent uniqueness/audit recording remains the adapter's duty.
     */
    record HistorySnapshot(boolean commandLookupComplete, boolean movementLookupComplete,
                           Optional<ExistingOutcome> commandOutcome, Optional<ExistingOutcome> movementOutcome) {
        @Override public String toString() { return "HistorySnapshot[REDACTED]"; }
    }
    record Result(Classification classification, Reason reason, String evidenceFingerprint, String commandFingerprint) {
        @Override public String toString() { return "Result[" + classification + "," + reason + "]"; }
    }

    static Result assess(Command command, Evidence evidence, ReviewContext context,
                         HistorySnapshot history) {
        if (context == null || !context.enabled()) return denied(Reason.DISABLED);
        if (!validCommand(command) || evidence == null || history == null || !history.commandLookupComplete() || !history.movementLookupComplete()
                || history.commandOutcome() == null || history.movementOutcome() == null || context.now() == null
                || context.venueId() == null || context.customerId() == null || context.claimantId() == null
                || !currency(context.expectedCurrency()) || context.expectedAmountMinor() <= 0) {
            return denied(Reason.INVALID_INPUT);
        }
        var account = context.accountScope();
        var source = context.sourceScope();
        var movement = evidence.movementKey();
        if (account == null || source == null || !validMovement(movement)
                || !account.approved() || !account.active() || !source.approved() || !source.active()
                || account.revision() < 0 || source.revision() < 0
                || !context.venueId().equals(account.venueId()) || !context.venueId().equals(source.venueId())
                || !context.venueId().equals(movement.venueId())
                || !movement.accountRef().equals(account.accountRef())
                || !movement.sourceRef().equals(source.sourceRef())
                || evidence.accountRevision() != account.revision()
                || evidence.sourceRevision() != source.revision()) return denied(Reason.INVALID_SCOPE);
        if (evidence.origin() != Origin.TRUSTED_BANK_SOURCE || evidence.state() != EvidenceState.MATCHED
                || !currency(evidence.currency()) || !evidence.currency().equals(context.expectedCurrency())
                || evidence.amountMinor() <= 0 || evidence.amountMinor() != context.expectedAmountMinor()
                || evidence.creditedAt() == null || evidence.creditedAt().isAfter(context.now())
                || evidence.protectedEvidenceRef() == null || !digest(evidence.evidenceSha256())) {
            return denied(Reason.INVALID_EVIDENCE);
        }
        var verifier = context.verifier();
        var allocator = context.allocator();
        if (!authority(verifier, context, Intent.VERIFICATION_REVIEW)
                || !authority(allocator, context, Intent.ALLOCATION_REVIEW)) return denied(Reason.INVALID_AUTHORITY);
        if (verifier.principalId().equals(allocator.principalId())
                || conflicted(verifier.principalId(), context) || conflicted(allocator.principalId(), context)
                || conflicted(command.callerId(), context)) return denied(Reason.ROLE_CONFLICT);
        var selected = command.intent() == Intent.VERIFICATION_REVIEW ? verifier : allocator;
        if (!command.callerId().equals(selected.principalId())) return denied(Reason.INVALID_AUTHORITY);

        // Current authority/scope is checked before every replay. No stale authority is inherited.
        String evidenceFingerprint = fingerprint(evidence);
        String commandFingerprint = commandFingerprint(command, context, evidenceFingerprint);
        var commandPrior = history.commandOutcome();
        var movementPrior = history.movementOutcome();
        if (commandPrior.isPresent()) {
            var prior = commandPrior.get();
            if (!validOutcome(prior) || !prior.command().commandKey().equals(command.commandKey()))
                return denied(Reason.INVALID_EXISTING_OUTCOME);
            if (!prior.command().equals(command) || !prior.movementKey().equals(movement)
                    || !prior.commandFingerprint().equals(commandFingerprint))
                return new Result(Classification.CONFLICT, Reason.CHANGED_COMMAND, evidenceFingerprint, commandFingerprint);
            if (!prior.evidenceFingerprint().equals(evidenceFingerprint))
                return new Result(Classification.CONFLICT, Reason.CHANGED_MOVEMENT_FACTS, evidenceFingerprint, commandFingerprint);
            if (movementPrior.isEmpty() || !prior.equals(movementPrior.get())) return denied(Reason.INVALID_EXISTING_OUTCOME);
            return new Result(Classification.EXACT_REPLAY, Reason.REPLAY_ONLY, evidenceFingerprint, commandFingerprint);
        }
        if (movementPrior.isPresent()) {
            var prior = movementPrior.get();
            if (!validOutcome(prior) || !movement.equals(prior.movementKey())
                    || prior.command().commandKey().equals(command.commandKey())) return denied(Reason.INVALID_EXISTING_OUTCOME);
            if (!prior.evidenceFingerprint().equals(evidenceFingerprint))
                return new Result(Classification.CONFLICT, Reason.CHANGED_MOVEMENT_FACTS, evidenceFingerprint, commandFingerprint);
            return new Result(Classification.DUPLICATE_MOVEMENT, Reason.DUPLICATE_REQUIRES_REVIEW, evidenceFingerprint, commandFingerprint);
        }
        return new Result(Classification.ELIGIBLE_FOR_INDEPENDENT_REVIEW, Reason.REVIEW_ONLY, evidenceFingerprint, commandFingerprint);
    }

    private static boolean validOutcome(ExistingOutcome prior) {
        return validMovement(prior.movementKey()) && validCommand(prior.command())
                && digest(prior.evidenceFingerprint()) && digest(prior.commandFingerprint())
                && prior.classification() == Classification.ELIGIBLE_FOR_INDEPENDENT_REVIEW;
    }

    private static boolean validMovement(MovementKey key) {
        return key != null && key.venueId() != null && key.accountRef() != null
                && key.sourceRef() != null && key.sourceMovementId() != null;
    }
    private static boolean validCommand(Command command) {
        return command != null && command.callerId() != null && command.commandKey() != null && command.intent() != null;
    }
    private static boolean authority(Authority authority, ReviewContext context, Intent intent) {
        return authority != null && authority.principalId() != null && context.venueId().equals(authority.venueId())
                && authority.intent() == intent && authority.permitted() && authority.validFrom() != null
                && authority.validUntil() != null && !context.now().isBefore(authority.validFrom())
                && context.now().isBefore(authority.validUntil());
    }
    private static boolean conflicted(UUID actor, ReviewContext context) {
        return actor.equals(context.customerId()) || actor.equals(context.claimantId());
    }
    private static boolean currency(String value) { return value != null && value.matches("[A-Z]{3}"); }
    private static boolean digest(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static Result denied(Reason reason) { return new Result(Classification.DENIED, reason, null, null); }

    private static String fingerprint(Evidence evidence) {
        // Only fixed-size UUIDs/numbers/enums and validated ASCII strings enter this encoding.
        // No arbitrary UTF-16 input can collapse into replacement bytes during UTF-8 encoding.
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            write(out, "CINENEST-BANK-CREDIT-PROVENANCE-V1");
            var movement = evidence.movementKey();
            write(out, movement.venueId().toString()); write(out, movement.accountRef().toString());
            write(out, movement.sourceRef().toString()); write(out, movement.sourceMovementId().toString());
            out.writeLong(evidence.accountRevision()); out.writeLong(evidence.sourceRevision());
            write(out, evidence.origin().name()); write(out, evidence.currency()); out.writeLong(evidence.amountMinor());
            out.writeLong(evidence.creditedAt().getEpochSecond()); out.writeInt(evidence.creditedAt().getNano());
            write(out, evidence.protectedEvidenceRef().toString()); write(out, evidence.evidenceSha256());
            write(out, evidence.state().name()); out.flush();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Credit provenance fingerprint unavailable", impossible);
        }
    }
    private static String commandFingerprint(Command command, ReviewContext context, String evidenceFingerprint) {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            write(out, "CINENEST-BANK-CREDIT-REVIEW-COMMAND-V1");
            write(out, evidenceFingerprint); write(out, command.callerId().toString()); write(out, command.intent().name());
            write(out, context.customerId().toString()); write(out, context.claimantId().toString());
            write(out, context.verifier().principalId().toString()); write(out, context.allocator().principalId().toString());
            // Observation clock and current authority flags are deliberately excluded: they are
            // checked afresh above but are not immutable request payload facts.
            out.flush();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Credit review fingerprint unavailable", impossible);
        }
    }
    private static void write(DataOutputStream out, String value) throws java.io.IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); out.writeInt(bytes.length); out.write(bytes);
    }
}
