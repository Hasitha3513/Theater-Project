package com.cinenest.payments.internal;

import static com.cinenest.payments.internal.BankCreditProvenance.*;
import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.env.MockEnvironment;

/** Pure contract tests only: no trusted bank authentication, durable ledger or financial execution. */
class BankCreditProvenanceTest {
    final UUID venue = id(1), account = id(2), source = id(3), movement = id(4), customer = id(5),
            claimant = id(6), verifier = id(7), allocator = id(8), evidenceRef = id(9), key = id(10);
    final Instant now = Instant.parse("2026-11-27T00:00:00Z");
    static UUID id(long n) { return new UUID(0, n); }
    Command command() { return new Command(verifier, key, Intent.VERIFICATION_REVIEW); }
    MovementKey movement() { return new MovementKey(venue, account, source, movement); }
    ReceivingAccountScope account() { return new ReceivingAccountScope(venue, account, 1, true, true); }
    SourceScope source() { return new SourceScope(venue, source, 2, true, true); }
    Authority verifier() { return new Authority(verifier, venue, Intent.VERIFICATION_REVIEW, true, now.minusSeconds(60), now.plusSeconds(60)); }
    Authority allocator() { return new Authority(allocator, venue, Intent.ALLOCATION_REVIEW, true, now.minusSeconds(60), now.plusSeconds(60)); }
    Evidence evidence() { return new Evidence(Origin.TRUSTED_BANK_SOURCE, movement(), 1, 2, "LKR", 250000, now.minusSeconds(1), evidenceRef, "a".repeat(64), EvidenceState.MATCHED); }
    ReviewContext context() { return context(true, account(), source(), verifier(), allocator(), "LKR", 250000, now); }
    ReviewContext context(boolean enabled, ReceivingAccountScope a, SourceScope s, Authority v, Authority al, String currency, long amount, Instant time) {
        return new ReviewContext(enabled, venue, customer, claimant, a, s, v, al, currency, amount, time);
    }
    Evidence evidence(Origin origin, MovementKey m, long ar, long sr, String currency, long amount, Instant at, UUID ref, String digest, EvidenceState state) {
        return new Evidence(origin, m, ar, sr, currency, amount, at, ref, digest, state);
    }
    Result assess(Evidence e) { return BankCreditProvenance.assess(command(), e, context(), history()); }
    ExistingOutcome prior() {
        return new ExistingOutcome(movement(), command(), assess(evidence()).evidenceFingerprint(), assess(evidence()).commandFingerprint(), Classification.ELIGIBLE_FOR_INDEPENDENT_REVIEW);
    }
    static HistorySnapshot history() { return new HistorySnapshot(true, true, Optional.empty(), Optional.empty()); }
    static HistorySnapshot history(ExistingOutcome prior) { return new HistorySnapshot(true, true, Optional.of(prior), Optional.of(prior)); }
    static HistorySnapshot movementHistory(ExistingOutcome prior) { return new HistorySnapshot(true, true, Optional.empty(), Optional.of(prior)); }
    void denies(Evidence e) { assertThat(assess(e).classification()).isEqualTo(Classification.DENIED); }
    void denies(ReviewContext c) { assertThat(BankCreditProvenance.assess(command(), evidence(), c, history()).classification()).isEqualTo(Classification.DENIED); }

    @Test void validTrustedSnapshotOnlyAdmitsIndependentReview() {
        assertThat(assess(evidence()).classification()).isEqualTo(Classification.ELIGIBLE_FOR_INDEPENDENT_REVIEW);
        assertThat(assess(evidence()).reason()).isEqualTo(Reason.REVIEW_ONLY);
        assertThat(assess(evidence()).evidenceFingerprint()).matches("[0-9a-f]{64}");
    }
    @Test void disabledAndMissingInputsDenyWithoutFingerprint() {
        for (var result : Arrays.asList(BankCreditProvenance.assess(null, evidence(), context(), history()),
                BankCreditProvenance.assess(command(), null, context(), history()),
                BankCreditProvenance.assess(command(), evidence(), null, history()),
                BankCreditProvenance.assess(command(), evidence(), context(), null),
                BankCreditProvenance.assess(command(), evidence(), context(false, account(), source(), verifier(), allocator(), "LKR", 250000, now), history()))) {
            assertThat(result.classification()).isEqualTo(Classification.DENIED); assertThat(result.evidenceFingerprint()).isNull();
        }
    }
    @ParameterizedTest @EnumSource(value=Origin.class, names={"CUSTOMER_RECEIPT", "OCR", "CUSTOMER_CLAIM"})
    void receiptOcrAndCustomerClaimNeverEstablishCredit(Origin origin) {
        denies(evidence(origin, movement(), 1, 2, "LKR", 250000, now.minusSeconds(1), evidenceRef, "a".repeat(64), EvidenceState.MATCHED));
    }
    @ParameterizedTest @EnumSource(value=EvidenceState.class, names={"UNMATCHED", "DISPUTED", "REVERSED"})
    void unresolvedOrAdverseMovementStateDenies(EvidenceState state) {
        denies(evidence(Origin.TRUSTED_BANK_SOURCE, movement(), 1, 2, "LKR", 250000, now.minusSeconds(1), evidenceRef, "a".repeat(64), state));
    }
    @Test void missingUnapprovedInactiveAndWrongVenueAccountScopesDeny() {
        for (var a : Arrays.asList(null, new ReceivingAccountScope(venue, account, 1, false, true),
                new ReceivingAccountScope(venue, account, 1, true, false), new ReceivingAccountScope(id(90), account, 1, true, true),
                new ReceivingAccountScope(venue, id(90), 1, true, true), new ReceivingAccountScope(venue, account, -1, true, true))) {
            denies(context(true, a, source(), verifier(), allocator(), "LKR", 250000, now));
        }
    }
    @Test void missingUnapprovedInactiveAndWrongVenueSourcesDeny() {
        for (var s : Arrays.asList(null, new SourceScope(venue, source, 2, false, true),
                new SourceScope(venue, source, 2, true, false), new SourceScope(id(90), source, 2, true, true),
                new SourceScope(venue, id(90), 2, true, true), new SourceScope(venue, source, -1, true, true))) {
            denies(context(true, account(), s, verifier(), allocator(), "LKR", 250000, now));
        }
    }
    @Test void zeroIsAValidInitialOptimisticRevisionWhenBothTrustedScopesMatch() {
        var e = evidence(Origin.TRUSTED_BANK_SOURCE, movement(), 0, 0, "LKR", 250000, now, evidenceRef, "a".repeat(64), EvidenceState.MATCHED);
        var c = context(true, new ReceivingAccountScope(venue, account, 0, true, true), new SourceScope(venue, source, 0, true, true), verifier(), allocator(), "LKR", 250000, now);
        assertThat(BankCreditProvenance.assess(command(), e, c, history()).classification()).isEqualTo(Classification.ELIGIBLE_FOR_INDEPENDENT_REVIEW);
    }
    @Test void staleRevisionsAndWrongMovementScopeDeny() {
        denies(evidence(Origin.TRUSTED_BANK_SOURCE, movement(), 2, 2, "LKR", 250000, now, evidenceRef, "a".repeat(64), EvidenceState.MATCHED));
        denies(evidence(Origin.TRUSTED_BANK_SOURCE, movement(), 1, 3, "LKR", 250000, now, evidenceRef, "a".repeat(64), EvidenceState.MATCHED));
        for (var m : Arrays.asList(null, new MovementKey(id(90), account, source, movement),
                new MovementKey(venue, null, source, movement), new MovementKey(venue, account, source, null))) {
            denies(evidence(Origin.TRUSTED_BANK_SOURCE, m, 1, 2, "LKR", 250000, now, evidenceRef, "a".repeat(64), EvidenceState.MATCHED));
        }
    }
    static Stream<String> invalidCurrencies() { return Stream.of(null, "", "lkr", "LKR ", "US:D", "L\ud800R", "L\udc00R", "a".repeat(1000)); }
    @ParameterizedTest @MethodSource("invalidCurrencies") void malformedStringsNeverEnterCanonicalEncoding(String currency) {
        denies(evidence(Origin.TRUSTED_BANK_SOURCE, movement(), 1, 2, currency, 250000, now, evidenceRef, "a".repeat(64), EvidenceState.MATCHED));
        denies(context(true, account(), source(), verifier(), allocator(), currency, 250000, now));
    }
    static Stream<String> invalidDigests() { return Stream.of(null, "", "A".repeat(64), "a".repeat(63), "a".repeat(65), "a".repeat(63)+"\ud800", "a".repeat(63)+"\udc00"); }
    @ParameterizedTest @MethodSource("invalidDigests") void onlyBoundedLowercaseAsciiDigestIsAccepted(String digest) {
        denies(evidence(Origin.TRUSTED_BANK_SOURCE, movement(), 1, 2, "LKR", 250000, now, evidenceRef, digest, EvidenceState.MATCHED));
    }
    @Test void mismatchedMoneyAndFutureOrMissingTimeDeny() {
        for (long amount : new long[]{0, -1, 249999, Long.MAX_VALUE}) denies(evidence(Origin.TRUSTED_BANK_SOURCE, movement(), 1, 2, "LKR", amount, now, evidenceRef, "a".repeat(64), EvidenceState.MATCHED));
        denies(evidence(Origin.TRUSTED_BANK_SOURCE, movement(), 1, 2, "USD", 250000, now, evidenceRef, "a".repeat(64), EvidenceState.MATCHED));
        for (var at : Arrays.asList(null, now.plusNanos(1))) denies(evidence(Origin.TRUSTED_BANK_SOURCE, movement(), 1, 2, "LKR", 250000, at, evidenceRef, "a".repeat(64), EvidenceState.MATCHED));
        denies(evidence(Origin.TRUSTED_BANK_SOURCE, movement(), 1, 2, "LKR", 250000, now, null, "a".repeat(64), EvidenceState.MATCHED));
    }
    @Test void authorityMustBeCurrentPermittedScopedAndCorrectlyTypedForBothRoles() {
        for (var invalid : Arrays.asList(null, new Authority(verifier, venue, Intent.VERIFICATION_REVIEW, false, now.minusSeconds(1), now.plusSeconds(1)),
                new Authority(verifier, id(90), Intent.VERIFICATION_REVIEW, true, now.minusSeconds(1), now.plusSeconds(1)),
                new Authority(verifier, venue, Intent.ALLOCATION_REVIEW, true, now.minusSeconds(1), now.plusSeconds(1)),
                new Authority(verifier, venue, Intent.VERIFICATION_REVIEW, true, now.plusNanos(1), now.plusSeconds(1)),
                new Authority(verifier, venue, Intent.VERIFICATION_REVIEW, true, now.minusSeconds(1), now))) {
            denies(context(true, account(), source(), invalid, allocator(), "LKR", 250000, now));
        }
        denies(context(true, account(), source(), verifier(), null, "LKR", 250000, now));
        denies(context(true, account(), source(), verifier(), new Authority(allocator, venue, Intent.ALLOCATION_REVIEW, false, now.minusSeconds(1), now.plusSeconds(1)), "LKR", 250000, now));
    }
    @Test void authorityIntervalIncludesStartAndExcludesEnd() {
        var v = new Authority(verifier, venue, Intent.VERIFICATION_REVIEW, true, now, now.plusNanos(1));
        assertThat(BankCreditProvenance.assess(command(), evidence(), context(true, account(), source(), v, allocator(), "LKR", 250000, now), history()).classification()).isEqualTo(Classification.ELIGIBLE_FOR_INDEPENDENT_REVIEW);
        denies(context(true, account(), source(), v, allocator(), "LKR", 250000, now.plusNanos(1)));
    }
    @Test void staffRoleAndCustomerClaimantConflictsDeny() {
        for (var principal : Arrays.asList(customer, claimant, allocator)) {
            var v = new Authority(principal, venue, Intent.VERIFICATION_REVIEW, true, now.minusSeconds(1), now.plusSeconds(1));
            var result = BankCreditProvenance.assess(new Command(principal, key, Intent.VERIFICATION_REVIEW), evidence(), context(true, account(), source(), v, allocator(), "LKR", 250000, now), history());
            assertThat(result.classification()).isEqualTo(Classification.DENIED);
        }
        for (var principal : Arrays.asList(customer, claimant)) {
            var a = new Authority(principal, venue, Intent.ALLOCATION_REVIEW, true, now.minusSeconds(1), now.plusSeconds(1));
            denies(context(true, account(), source(), verifier(), a, "LKR", 250000, now));
        }
    }
    @Test void onlySelectedCurrentActorMayReviewAndNeitherReviewMutatesFunds() {
        var a = new Command(allocator, id(11), Intent.ALLOCATION_REVIEW);
        assertThat(BankCreditProvenance.assess(a, evidence(), context(), history()).classification()).isEqualTo(Classification.ELIGIBLE_FOR_INDEPENDENT_REVIEW);
        for (var c : Arrays.asList(new Command(id(90), key, Intent.VERIFICATION_REVIEW), new Command(customer, key, Intent.VERIFICATION_REVIEW), new Command(verifier, key, Intent.ALLOCATION_REVIEW), new Command(null, key, Intent.VERIFICATION_REVIEW), new Command(verifier, null, Intent.VERIFICATION_REVIEW), new Command(verifier, key, null))) {
            assertThat(BankCreditProvenance.assess(c, evidence(), context(), history()).classification()).isEqualTo(Classification.DENIED);
        }
    }
    @Test void exactReplayIsStableButCurrentAuthorityMustStillPass() {
        assertThat(BankCreditProvenance.assess(command(), evidence(), context(), history(prior())).classification()).isEqualTo(Classification.EXACT_REPLAY);
        var revoked = context(true, account(), source(), new Authority(verifier, venue, Intent.VERIFICATION_REVIEW, false, now.minusSeconds(1), now.plusSeconds(1)), allocator(), "LKR", 250000, now);
        assertThat(BankCreditProvenance.assess(command(), evidence(), revoked, history(prior())).classification()).isEqualTo(Classification.DENIED);
        var later = context(true, account(), source(), verifier(), allocator(), "LKR", 250000, now.plusSeconds(1));
        assertThat(BankCreditProvenance.assess(command(), evidence(), later, history(prior())).classification()).isEqualTo(Classification.EXACT_REPLAY);
    }
    @Test void sameMovementAndDifferentKeyIsDuplicateNotANewCredit() {
        var retry = new Command(verifier, id(11), Intent.VERIFICATION_REVIEW);
        assertThat(BankCreditProvenance.assess(retry, evidence(), context(), movementHistory(prior())).classification()).isEqualTo(Classification.DUPLICATE_MOVEMENT);
    }
    @Test void sameKeyChangedIntentOrActorConflictsAfterCurrentAuthorityValidation() {
        assertThat(BankCreditProvenance.assess(new Command(allocator, key, Intent.ALLOCATION_REVIEW), evidence(), context(), history(prior())).classification()).isEqualTo(Classification.CONFLICT);
        var next = new Authority(id(90), venue, Intent.VERIFICATION_REVIEW, true, now.minusSeconds(1), now.plusSeconds(1));
        assertThat(BankCreditProvenance.assess(new Command(id(90), key, Intent.VERIFICATION_REVIEW), evidence(), context(true, account(), source(), next, allocator(), "LKR", 250000, now), history(prior())).classification()).isEqualTo(Classification.CONFLICT);
    }
    @Test void changedFactsUnderSameStableMovementConflictEvenWithDifferentCommandKey() {
        for (var changed : Arrays.asList(
                evidence(Origin.TRUSTED_BANK_SOURCE, movement(), 1, 2, "LKR", 250000, now, evidenceRef, "a".repeat(64), EvidenceState.MATCHED),
                evidence(Origin.TRUSTED_BANK_SOURCE, movement(), 1, 2, "LKR", 250000, now.minusSeconds(1), id(99), "a".repeat(64), EvidenceState.MATCHED),
                evidence(Origin.TRUSTED_BANK_SOURCE, movement(), 1, 2, "LKR", 250000, now.minusSeconds(1), evidenceRef, "b".repeat(64), EvidenceState.MATCHED))) {
            assertThat(changed.movementKey()).isEqualTo(movement());
            assertThat(BankCreditProvenance.assess(new Command(verifier, id(11), Intent.VERIFICATION_REVIEW), changed, context(), movementHistory(prior())).classification()).isEqualTo(Classification.CONFLICT);
        }
        var revised = evidence(Origin.TRUSTED_BANK_SOURCE, movement(), 2, 3, "USD", 250001, now.minusSeconds(1), evidenceRef, "a".repeat(64), EvidenceState.MATCHED);
        var current = context(true, new ReceivingAccountScope(venue, account, 2, true, true), new SourceScope(venue, source, 3, true, true), verifier(), allocator(), "USD", 250001, now);
        assertThat(BankCreditProvenance.assess(command(), revised, current, history(prior())).classification()).isEqualTo(Classification.CONFLICT);
    }
    @Test void corruptOrWrongScopePriorOutcomesAreNeverTreatedAsAbsent() {
        for (var p : Arrays.asList(new ExistingOutcome(movement(), null, prior().evidenceFingerprint(), prior().commandFingerprint(), Classification.ELIGIBLE_FOR_INDEPENDENT_REVIEW),
                new ExistingOutcome(movement(), command(), "invalid", prior().commandFingerprint(), Classification.ELIGIBLE_FOR_INDEPENDENT_REVIEW),
                new ExistingOutcome(movement(), command(), prior().evidenceFingerprint(), prior().commandFingerprint(), Classification.EXACT_REPLAY))) {
            assertThat(BankCreditProvenance.assess(command(), evidence(), context(), history(p)).classification()).isEqualTo(Classification.DENIED);
        }
    }
    @Test void commandKeyReuseAcrossMovementIsConflictNotANewReview() {
        var priorOtherMovement = new ExistingOutcome(new MovementKey(venue, account, source, id(99)), command(), prior().evidenceFingerprint(), prior().commandFingerprint(), Classification.ELIGIBLE_FOR_INDEPENDENT_REVIEW);
        var lookup = new HistorySnapshot(true, true, Optional.of(priorOtherMovement), Optional.empty());
        assertThat(BankCreditProvenance.assess(command(), evidence(), context(), lookup).classification()).isEqualTo(Classification.CONFLICT);
    }
    @Test void bothTrustedLookupsAreMandatoryAndInconsistentSnapshotsDeny() {
        for (var lookup : Arrays.asList(new HistorySnapshot(false, true, Optional.empty(), Optional.empty()),
                new HistorySnapshot(true, false, Optional.empty(), Optional.empty()),
                new HistorySnapshot(true, true, null, Optional.empty()),
                new HistorySnapshot(true, true, Optional.empty(), null),
                new HistorySnapshot(true, true, Optional.of(prior()), Optional.empty()),
                movementHistory(prior()))) {
            assertThat(BankCreditProvenance.assess(command(), evidence(), context(), lookup).classification()).isEqualTo(Classification.DENIED);
        }
        var wrongKey = new ExistingOutcome(movement(), new Command(verifier, id(99), Intent.VERIFICATION_REVIEW), prior().evidenceFingerprint(), prior().commandFingerprint(), Classification.ELIGIBLE_FOR_INDEPENDENT_REVIEW);
        assertThat(BankCreditProvenance.assess(command(), evidence(), context(), history(wrongKey)).classification()).isEqualTo(Classification.DENIED);
    }
    @Test void originalCommandAlsoBindsCustomerClaimantAndIndependentReviewPair() {
        for (var changed : Arrays.asList(
                new ReviewContext(true, venue, id(99), claimant, account(), source(), verifier(), allocator(), "LKR", 250000, now),
                new ReviewContext(true, venue, customer, id(99), account(), source(), verifier(), allocator(), "LKR", 250000, now),
                context(true, account(), source(), verifier(), new Authority(id(99), venue, Intent.ALLOCATION_REVIEW, true, now.minusSeconds(1), now.plusSeconds(1)), "LKR", 250000, now))) {
            var current = BankCreditProvenance.assess(command(), evidence(), changed, history());
            assertThat(current.classification()).isEqualTo(Classification.ELIGIBLE_FOR_INDEPENDENT_REVIEW);
            assertThat(current.evidenceFingerprint()).isEqualTo(prior().evidenceFingerprint());
            assertThat(current.commandFingerprint()).isNotEqualTo(prior().commandFingerprint());
            assertThat(BankCreditProvenance.assess(command(), evidence(), changed, history(prior())).classification()).isEqualTo(Classification.CONFLICT);
        }
    }
    @Test void valueRecordsAreImmutableAndDiagnosticStringsRedactAllPrivateReferences() {
        assertThat(evidence()).isEqualTo(evidence()); assertThat(assess(evidence())).isEqualTo(assess(evidence()));
        for (Object item : Arrays.asList(account(), source(), verifier(), allocator(), movement(), evidence(), command(), context(), prior(), history(prior()), assess(evidence()))) {
            assertThat(item.getClass().isRecord()).isTrue();
            assertThat(item.toString()).doesNotContain(venue.toString(), account.toString(), source.toString(), movement.toString(), customer.toString(), claimant.toString(), verifier.toString(), evidenceRef.toString(), "a".repeat(64), prior().evidenceFingerprint());
        }
    }
    @Test void architectureIsInertPackagePrivateAndExposesNoFinancialSuccessClaims() {
        assertThat(Modifier.isPublic(BankCreditProvenance.class.getModifiers())).isFalse();
        assertThat(BankCreditProvenance.class.getDeclaredAnnotations()).isEmpty();
        assertThat(BankCreditProvenance.class.getDeclaredConstructors()).allMatch(c -> Modifier.isPrivate(c.getModifiers()));
        assertThat(BankCreditProvenance.class.getDeclaredFields()).isEmpty();
        assertThat(Result.class.getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName).containsExactly("classification", "reason", "evidenceFingerprint", "commandFingerprint");
        assertThat(Classification.values()).allMatch(c -> !c.name().contains("PAID") && !c.name().contains("CONFIRMED") && !c.name().contains("REFUNDED") && !c.name().contains("ADMITTED"));
        for (var nested : BankCreditProvenance.class.getDeclaredClasses()) assertThat(nested.getDeclaredAnnotations()).isEmpty();
    }
    @Test void existingSyntheticCreditAndRealReceiptDenialRemainUntouched() {
        var env = new MockEnvironment(); env.setActiveProfiles("production");
        env.withProperty("cinenest.bank.ledger.enabled", "true");
        var synthetic = new SyntheticCreditSource(env);
        assertThatThrownBy(synthetic::configured).isInstanceOfSatisfying(BankFailure.class, f -> assertThat(f.status).isEqualTo(503));
        assertThatThrownBy(() -> synthetic.fixture("SYNTHETIC-BANK-100")).isInstanceOfSatisfying(BankFailure.class, f -> assertThat(f.status).isEqualTo(503));
        assertThat(new ReceiptAdmissionPolicy().decision()).isEqualTo(ReceiptAdmissionPolicy.Decision.DENY);
    }
}
