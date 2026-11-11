package com.cinenest.pricing.internal;

import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.identity.ScopedAuthorization.MatchedGrant;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
final class OperationalCommercialPolicyService {
    static final String ACTIVATE = "commercial.policy.activate";
    private final ScopedAuthorization identity;
    private final PublicationRepository publications;
    private final JdbcOperationalCommercialRepository repository;
    private final CustomerCommercialQuoteConfiguration configuration;
    private final ObjectProvider<OperationalCommercialAuthority> sources;
    private final TransactionTemplate transaction;
    private final TransactionTemplate revocations;

    OperationalCommercialPolicyService(ScopedAuthorization identity, PublicationRepository publications,
            JdbcOperationalCommercialRepository repository, CustomerCommercialQuoteConfiguration configuration,
            ObjectProvider<OperationalCommercialAuthority> sources, PlatformTransactionManager manager) {
        this.identity = identity;
        this.publications = publications;
        this.repository = repository;
        this.configuration = configuration;
        this.sources = sources;
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        revocations = new TransactionTemplate(manager);
        revocations.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        revocations.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    record Command(OperationalCommercialAuthority.Scope scope, UUID key, Instant from, Instant until) {
        static Command parse(UUID venue, UUID policy, Map<String, Object> input) {
            DraftRules.keys(input, "commandKey", "roomId", "packageVersionId", "policyRevision",
                "policyHash", "effectiveFrom", "effectiveUntil");
            try {
                var from = instant(input, "effectiveFrom");
                var until = instant(input, "effectiveUntil");
                if (!from.isBefore(until)) {
                    throw DraftRules.invalid("effective interval");
                }
                return new Command(new OperationalCommercialAuthority.Scope(venue, uuid(input, "roomId"),
                    uuid(input, "packageVersionId"), policy, DraftRules.revision(input, "policyRevision"),
                    DraftRules.text(input, "policyHash", 64, true)), uuid(input, "commandKey"), from, until);
            } catch (IllegalArgumentException | DateTimeException invalid) {
                throw DraftRules.invalid("activation scope or dates");
            }
        }

        String hash(boolean qualificationOnly) {
            return PublicationService.hash(Map.of("venue", scope.venue().toString(), "room", scope.room().toString(),
                "packageVersion", scope.version().toString(), "policy", scope.policy().toString(),
                "revision", scope.revision(), "policyHash", scope.policyHash(), "from", from.toString(),
                "until", until.toString(), "qualificationOnly", qualificationOnly));
        }

        private static Instant instant(Map<String, Object> input, String key) {
            Instant value = Instant.parse(DraftRules.text(input, key, 40, true));
            // PostgreSQL stores microseconds. Reject intervals that would silently change on persistence.
            if (value.getNano() % 1000 != 0) {
                throw DraftRules.invalid(key);
            }
            return value;
        }
    }

    boolean mayActivate(UUID actor, UUID venue) {
        return configuration.activationEnabled() && sources.getIfUnique() != null
            && matchingGrant(identity, actor, venue, ACTIVATE, false).isPresent();
    }

    Map<String, Object> activationContext(UUID actor, UUID venue, UUID policy) {
        if (!mayActivate(actor, venue)) {
            return Map.of("permitted", false);
        }
        return repository.activationContext(venue, policy).orElseGet(() -> Map.of("permitted", false));
    }

    UUID activate(UUID actor, Command command) {
        var scope = command.scope();
        requireGrant(identity, actor, scope.venue(), ACTIVATE, false);
        var authority = authority();
        var observation = observe(authority);
        return transaction.execute(status -> {
            var grant = requireGrant(identity, actor, scope.venue(), ACTIVATE, true);
            repository.acceptRevocations(observation);
            UUID packageId = repository.packageFor(scope);
            publications.lock(scope.venue(), packageId);
            CustomerCommercialQuoteConfiguration.publication(publications, scope.venue(), scope.room(),
                packageId, scope.version(), command.from(), command.until());
            repository.requireActivatable(scope, packageId, command.from(), command.until());
            var now = repository.now();
            var evidence = checkedAuthority(authority, observation, scope, now);
            if (!now.isBefore(command.until())) {
                throw new DraftFailure(409, "Policy activation interval ended");
            }
            var previous = repository.activationReplay(actor, scope.venue(), command.key());
            if (previous.isPresent()) {
                if (!previous.get().hash().equals(command.hash(authority.qualificationOnly()))) {
                    throw new DraftFailure(409, "Activation command payload changed");
                }
                return previous.get().id();
            }
            return repository.insertActivation(grant, scope, command.from(), command.until(),
                evidence, command.key(), command.hash(authority.qualificationOnly()), authority.qualificationOnly());
        });
    }

    OperationalCommercialAuthority authority() {
        var authority = sources.getIfUnique();
        if (!configuration.activationEnabled() || authority == null) {
            throw new DraftFailure(503, "Commercial authority unavailable");
        }
        return authority;
    }

    OperationalCommercialAuthority.Evidence observe(OperationalCommercialAuthority authority) {
        return revocations.execute(status -> {
            var observation = authority.verifyRevocations(repository.now());
            repository.acceptRevocations(observation);
            return observation;
        });
    }

    static OperationalCommercialAuthority.Evidence checkedAuthority(OperationalCommercialAuthority authority,
            OperationalCommercialAuthority.Evidence observation, OperationalCommercialAuthority.Scope scope, Instant now) {
        var evidence = authority.verify(scope, now);
        if (!evidence.namespace().equals(observation.namespace()) || evidence.sequence() != observation.sequence()
            || !evidence.revocationDigest().equals(observation.revocationDigest()) || !now.isBefore(evidence.validUntil())) {
            throw new DraftFailure(503, "Commercial authority changed");
        }
        return evidence;
    }

    static MatchedGrant requireGrant(ScopedAuthorization identity, UUID actor, UUID venue, String permission,
            boolean lock) {
        return matchingGrant(identity, actor, venue, permission, lock)
            .orElseThrow(() -> new DraftFailure(403, "Exact venue commercial authority required"));
    }

    private static java.util.Optional<MatchedGrant> matchingGrant(ScopedAuthorization identity,
            UUID actor, UUID venue, String permission, boolean lock) {
        return identity.matchedOperationalGrant(actor, venue, permission, lock).filter(grant ->
            actor.equals(grant.account()) && venue.equals(grant.venue()) && permission.equals(grant.permission()));
    }

    static UUID uuid(Map<String, Object> input, String name) {
        return DraftRules.id(DraftRules.text(input, name, 36, true));
    }
}
