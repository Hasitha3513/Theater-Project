package com.cinenest.pricing.internal;

import com.cinenest.booking.QuotationHolds;
import com.cinenest.identity.ScopedAuthorization;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/** Transactional qualification foundation. Only tests supply an authority and construct this service. */
final class CustomerCommercialQuoteService {
    enum Domain { FINANCE, LEGAL, OPERATIONS }

    record Scope(UUID venue, UUID room, UUID version, UUID policy, long revision, String hash,
                 Instant effectiveFrom, Instant effectiveUntil) {}

    record Approval(Domain domain, UUID appointment, UUID signer, Scope scope,
                    Instant validFrom, Instant validUntil) {}

    record QualificationPolicy(Scope scope, List<Approval> approvals, Instant revocationObservedAt,
                               Set<UUID> revoked, CommercialQuotationRules.Tax tax,
                               List<CommercialQuotationRules.Fee> fees) {
        QualificationPolicy {
            approvals = List.copyOf(approvals);
            revoked = Set.copyOf(revoked);
            fees = List.copyOf(fees);
        }
    }

    /** Test-only source of independently verified facts. No production bean or fixture-ledger adapter exists. */
    interface QualificationAuthority {
        Optional<QualificationPolicy> lockAndVerify(UUID venue, UUID room, UUID version, Instant now);
    }

    record Command(UUID hold, long revision, UUID venue, UUID room, UUID packageId, UUID version,
                   UUID key, String method, String channel, Set<UUID> services) {
        Command {
            services = Set.copyOf(services);
        }

        String hash() {
            return PublicationService.hash(Map.of("hold", hold.toString(), "revision", revision,
                "venue", venue.toString(), "room", room.toString(), "package", packageId.toString(),
                "version", version.toString(), "method", method, "channel", channel,
                "services", services.stream().map(UUID::toString).sorted().toList()));
        }
    }

    private final ScopedAuthorization identity;
    private final QuotationHolds holds;
    private final PublicationRepository publications;
    private final QualificationAuthority authority;
    private final JdbcCustomerCommercialQuoteRepository repository;
    private final TransactionTemplate transaction;

    CustomerCommercialQuoteService(ScopedAuthorization identity, QuotationHolds holds,
            PublicationRepository publications, QualificationAuthority authority,
            JdbcCustomerCommercialQuoteRepository repository, TransactionTemplate transaction) {
        this.identity = identity;
        this.holds = holds;
        this.publications = publications;
        this.authority = authority;
        this.repository = repository;
        this.transaction = transaction;
    }

    UUID qualify(UUID actor, Command command) {
        return transaction.execute(status -> {
            if (actor == null || !identity.eligible(actor, true) || !repository.syntheticActor(actor)) {
                throw new DraftFailure(403, "Synthetic qualification identity required");
            }
            holds.locate(command.hold()).filter(hold -> ownsScope(actor, command, hold))
                .orElseThrow(CustomerCommercialQuoteService::holdNotFound);
            publications.lock(command.venue(), command.packageId());
            var hold = holds.lockAndInspect(actor, command.hold()).filter(h -> ownsScope(actor, command, h))
                .orElseThrow(CustomerCommercialQuoteService::holdNotFound);
            var now = repository.now();
            if (!"ACTIVE".equals(hold.state()) || command.revision() != hold.revision()
                    || !now.isBefore(hold.expires())) {
                throw new DraftFailure(409, "Hold changed or expired");
            }
            var policy = authority.lockAndVerify(hold.venue(), hold.room(), command.version(), now)
                .orElseThrow(CustomerCommercialQuoteService::noAuthority);
            var expiry = validatePolicy(policy, hold, command.version(), now);
            var publication = publications.catalog(hold.venue()).stream()
                .filter(row -> command.packageId().equals(row.get("packageId"))
                    && command.version().equals(row.get("versionId"))
                    && !hold.start().isBefore(Instant.parse(row.get("sessionFrom").toString()))
                    && !hold.end().isAfter(Instant.parse(row.get("sessionTo").toString())))
                .findFirst().orElseThrow(() -> new DraftFailure(409, "Published package version unavailable"));
            var packageSnapshot = PublicationService.object(publication, "snapshot");
            if (!PublicationService.rooms(packageSnapshot).contains(hold.room())) {
                throw new DraftFailure(409, "Package does not cover held room");
            }

            String hash = command.hash();
            var prior = repository.replay(actor, command.key());
            if (prior.isPresent()) {
                var receipt = prior.get();
                if (!hash.equals(receipt.commandHash())) {
                    throw new DraftFailure(409, "Quotation command payload changed");
                }
                if (!now.isBefore(receipt.expires()) || !policy.scope().policy().equals(receipt.policy())
                        || policy.scope().revision() != receipt.policyRevision()
                        || !policy.scope().hash().equals(receipt.policyHash())) {
                    throw new DraftFailure(409, "Stored quotation authority is no longer current");
                }
                return receipt.id();
            }
            var price = CommercialQuotationRules.price(packageSnapshot, hold, command.services(), command.method(),
                command.channel(), policy.fees(), policy.tax());
            var amounts = price.amounts();
            var snapshot = Map.of("classification", "SYNTHETIC_QUALIFICATION_NOT_OFFER", "currency", "LKR",
                "package", packageSnapshot, "lines", price.packageLines(),
                "amounts", Map.of("packageMinor", amounts.packageMinor(), "feeMinor", amounts.feeMinor(),
                    "taxMinor", amounts.taxMinor(), "totalMinor", amounts.totalMinor()),
                "method", command.method(), "channel", command.channel(),
                "expiresAt", expiry.toString(), "revocationObservedAt", policy.revocationObservedAt().toString(),
                "appointments", policy.approvals().stream().map(a -> a.appointment().toString()).sorted().toList());
            return repository.insert(actor, command, hash, policy.scope(), snapshot, amounts.totalMinor(), expiry);
        });
    }

    private static boolean ownsScope(UUID actor, Command command, QuotationHolds.Facts hold) {
        return actor.equals(hold.owner()) && command.hold().equals(hold.id())
            && command.venue().equals(hold.venue()) && command.room().equals(hold.room());
    }

    private static Instant validatePolicy(QualificationPolicy policy, QuotationHolds.Facts hold,
                                          UUID version, Instant now) {
        var scope = policy.scope();
        if (!scope.venue().equals(hold.venue()) || !scope.room().equals(hold.room())
                || !scope.version().equals(version) || scope.revision() < 0
                || !scope.hash().matches("[0-9a-f]{64}") || now.isBefore(scope.effectiveFrom())
                || hold.start().isBefore(scope.effectiveFrom()) || hold.end().isAfter(scope.effectiveUntil())
                || now.isBefore(policy.revocationObservedAt()) || policy.approvals().size() != Domain.values().length) {
            throw noAuthority();
        }
        var domains = EnumSet.noneOf(Domain.class);
        var signers = new java.util.HashSet<UUID>();
        var appointments = new java.util.HashSet<UUID>();
        Instant approvalsUntil = policy.revocationObservedAt().plusSeconds(900);
        for (var approval : policy.approvals()) {
            if (!scope.equals(approval.scope()) || !domains.add(approval.domain()) || !signers.add(approval.signer())
                    || !appointments.add(approval.appointment()) || now.isBefore(approval.validFrom())
                    || !now.isBefore(approval.validUntil()) || policy.revoked().contains(approval.appointment())) {
                throw noAuthority();
            }
            if (approval.validUntil().isBefore(approvalsUntil)) {
                approvalsUntil = approval.validUntil();
            }
        }
        return CommercialQuotationRules.expiry(now, hold.expires(), scope.effectiveUntil(), approvalsUntil);
    }

    private static DraftFailure holdNotFound() {
        return new DraftFailure(404, "Hold not found");
    }

    private static DraftFailure noAuthority() {
        return new DraftFailure(409, "Eligible commercial policy unavailable");
    }
}
