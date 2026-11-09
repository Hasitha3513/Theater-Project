package com.cinenest.pricing.internal;

import com.cinenest.identity.ScopedAuthorization;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/** Internal fixture harness entry point. Deliberately absent from Spring's operational wiring. */
final class CommercialPolicyActivationService {
    private static final String CAPABILITY = "commercial.policy.edit";
    private final ScopedAuthorization identity;
    private final CommercialPolicyTrustVerifier verifier;
    private final JdbcCommercialPolicyActivationRepository repository;
    private final TransactionTemplate tx;
    private final UUID fixtureOperator;

    CommercialPolicyActivationService(ScopedAuthorization identity, CommercialPolicyTrustVerifier verifier,
            JdbcCommercialPolicyActivationRepository repository, TransactionTemplate tx, UUID fixtureOperator) {
        this.identity = identity;
        this.verifier = verifier;
        this.repository = repository;
        this.tx = tx;
        this.fixtureOperator = fixtureOperator;
    }

    UUID activateFixture(UUID actor, UUID command, CommercialPolicyTrustVerifier.Scope scope,
                         List<CommercialPolicyTrustVerifier.Approval> approvals,
                         CommercialPolicyTrustVerifier.Revocations revocations) {
        if (actor == null || !actor.equals(fixtureOperator) || command == null) {
            throw new DraftFailure(403, "Fixture activation authority denied");
        }
        return tx.execute(status -> {
            var grant = identity.matchedGrant(actor, scope.venue(), CAPABILITY, true)
                .filter(g -> actor.equals(g.account()) && scope.venue().equals(g.venue())
                    && CAPABILITY.equals(g.permission()))
                .orElseThrow(() -> new DraftFailure(403, "Exact venue activation authority required"));
            repository.lockTrust();
            var now = repository.now();
            var deadline = verifier.verifyFixture(scope, approvals, revocations, 1, actor,
                repository.authors(scope.policy(), scope.revision()), now);
            repository.checkpoint(revocations);
            String hash = PublicationService.hash(Map.of("scope", scope.canonical(), "proofs",
                approvals.stream().map(CommercialPolicyTrustVerifier.Approval::signature).sorted().toList()));
            var replay = repository.replay(actor, scope.venue(), command);
            if (replay.isPresent()) {
                if (!replay.get().hash().equals(hash)) {
                    throw new DraftFailure(409, "Activation command payload changed");
                }
                return replay.get().id();
            }
            return repository.insert(scope, grant, command, hash, deadline, revocations.sequence(), approvals);
        });
    }

    void reconcileFixtureRevocations(CommercialPolicyTrustVerifier.Revocations revocations) {
        tx.executeWithoutResult(status -> {
            repository.lockTrust();
            verifier.verifyFixtureRevocations(revocations, repository.now());
            repository.checkpoint(revocations);
        });
    }
}
