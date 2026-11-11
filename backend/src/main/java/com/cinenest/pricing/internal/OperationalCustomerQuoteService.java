package com.cinenest.pricing.internal;

import com.cinenest.booking.QuotationHolds;
import com.cinenest.identity.ScopedAuthorization;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
final class OperationalCustomerQuoteService {
    private static final String ISSUE = "commercial.quote.issue";
    private final ScopedAuthorization identity;
    private final QuotationHolds holds;
    private final PublicationRepository publications;
    private final JdbcOperationalCommercialRepository repository;
    private final CustomerCommercialQuoteConfiguration configuration;
    private final OperationalCommercialPolicyService policies;
    private final TransactionTemplate transaction;

    OperationalCustomerQuoteService(ScopedAuthorization identity, QuotationHolds holds,
            PublicationRepository publications, JdbcOperationalCommercialRepository repository,
            CustomerCommercialQuoteConfiguration configuration, OperationalCommercialPolicyService policies,
            PlatformTransactionManager manager) {
        this.identity = identity;
        this.holds = holds;
        this.publications = publications;
        this.repository = repository;
        this.configuration = configuration;
        this.policies = policies;
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    static CustomerCommercialQuoteService.Command command(Map<String, Object> input) {
        DraftRules.keys(input, "holdId", "holdRevision", "venueId", "roomId", "packageId",
            "packageVersionId", "commandKey", "method", "channel", "serviceIds");
        if (!(input.get("serviceIds") instanceof List<?> serviceIds) || serviceIds.size() > 100) {
            throw DraftRules.invalid("serviceIds");
        }
        Set<UUID> selected = new HashSet<>();
        for (Object value : serviceIds) {
            if (!(value instanceof String id) || !selected.add(DraftRules.id(id))) {
                throw DraftRules.invalid("serviceIds");
            }
        }
        return new CustomerCommercialQuoteService.Command(
            OperationalCommercialPolicyService.uuid(input, "holdId"), DraftRules.revision(input, "holdRevision"),
            OperationalCommercialPolicyService.uuid(input, "venueId"),
            OperationalCommercialPolicyService.uuid(input, "roomId"),
            OperationalCommercialPolicyService.uuid(input, "packageId"),
            OperationalCommercialPolicyService.uuid(input, "packageVersionId"),
            OperationalCommercialPolicyService.uuid(input, "commandKey"),
            DraftRules.text(input, "method", 16, true), DraftRules.text(input, "channel", 16, true), selected);
    }

    UUID issue(UUID actor, CustomerCommercialQuoteService.Command command) {
        configuration.requireOperationalIssuance();
        OperationalCommercialPolicyService.requireGrant(identity, actor, command.venue(), ISSUE, false);
        var authority = policies.authority();
        var observation = policies.observe(authority);
        return transaction.execute(status -> {
            var grant = OperationalCommercialPolicyService.requireGrant(identity, actor, command.venue(), ISSUE, true);
            repository.acceptRevocations(observation);
            holds.locateOperational(actor, command.hold()).filter(h -> owns(actor, command, h))
                .orElseThrow(OperationalCustomerQuoteService::holdNotFound);
            publications.lock(command.venue(), command.packageId());
            var hold = holds.lockAndInspectOperational(actor, command.hold()).filter(h -> owns(actor, command, h))
                .orElseThrow(OperationalCustomerQuoteService::holdNotFound).hold();
            var now = repository.now();
            if (!"ACTIVE".equals(hold.state()) || command.revision() != hold.revision() || !now.isBefore(hold.expires())) {
                throw new DraftFailure(409, "Hold changed or expired");
            }
            var activation = repository.active(hold, command.packageId(), command.version(), now,
                authority.qualificationOnly());
            var evidence = OperationalCommercialPolicyService.checkedAuthority(authority, observation,
                activation.scope(), repository.now());
            if (!activation.namespace().equals(evidence.namespace())) {
                throw new DraftFailure(409, "Commercial policy authority changed");
            }
            var expiry = CommercialQuotationRules.expiry(repository.now(), hold.expires(),
                activation.effectiveUntil(), evidence.validUntil());
            var published = CustomerCommercialQuoteConfiguration.publication(publications, hold.venue(), hold.room(),
                command.packageId(), command.version(), hold.start(), hold.end());
            var receipt = repository.quoteReplay(actor, command.key());
            if (receipt.isPresent()) {
                var saved = receipt.get();
                if (!command.hash().equals(saved.hash()) || !activation.id().equals(saved.activation())
                        || !repository.now().isBefore(saved.expires())) {
                    throw new DraftFailure(409, "Quotation command changed or its authority expired");
                }
                return saved.id();
            }

            var terms = activation.terms();
            var price = CommercialQuotationRules.price(published, hold, command.services(), command.method(),
                command.channel(), fees(terms), tax(terms));
            var amounts = price.amounts();
            var snapshot = Map.of(
                "classification", authority.qualificationOnly() ? "SYNTHETIC_QUALIFICATION_NOT_OFFER" : "OPERATIONAL_QUOTATION",
                "currency", "LKR", "package", published, "terms", terms, "lines", price.packageLines(),
                "amounts", Map.of("packageMinor", amounts.packageMinor(), "feeMinor", amounts.feeMinor(),
                    "taxMinor", amounts.taxMinor(), "totalMinor", amounts.totalMinor()),
                "expiresAt", expiry.toString(), "method", command.method(), "channel", command.channel(),
                "authority", Map.of("grantId", grant.grantId().toString(), "grantRevision", grant.grantRevision(),
                    "credentialEpoch", grant.credentialEpoch(), "revocationDigest", evidence.revocationDigest()));
            return repository.insertQuote(grant, hold, command.packageId(), activation, evidence, command.key(),
                command.hash(), snapshot, amounts.totalMinor(), expiry);
        });
    }

    private static boolean owns(UUID actor, CustomerCommercialQuoteService.Command command,
                                QuotationHolds.OperationalFacts operational) {
        var hold = operational.hold();
        return actor.equals(hold.owner()) && command.hold().equals(hold.id()) && command.venue().equals(hold.venue())
            && command.room().equals(hold.room()) && command.packageId().equals(operational.packageId())
            && command.version().equals(operational.versionId());
    }

    private static List<CommercialQuotationRules.Fee> fees(Map<String, Object> terms) {
        return DraftRules.objects(terms, "fees").stream().map(fee -> {
            if (!"LKR".equals(fee.get("currency"))) {
                throw DraftRules.invalid("fee currency");
            }
            return new CommercialQuotationRules.Fee(fee.get("method").toString(), fee.get("channel").toString(),
                CommercialQuotationRules.FeeTreatment.valueOf(fee.get("type").toString()),
                DraftRules.minor(fee.get("amountMinor"), false));
        }).toList();
    }

    private static CommercialQuotationRules.Tax tax(Map<String, Object> terms) {
        var treatment = CommercialQuotationRules.TaxTreatment.valueOf(terms.get("tax_treatment").toString());
        var basis = CommercialQuotationRules.TaxBasis.valueOf(terms.get("tax_basis").toString());
        var rate = (Number) terms.get("tax_rate_basis_points");
        var fixed = (Number) terms.get("tax_amount_minor");
        var rounding = treatment == CommercialQuotationRules.TaxTreatment.NONE
            ? null : RoundingMode.valueOf(terms.get("tax_rounding").toString());
        return new CommercialQuotationRules.Tax(treatment, basis, rate == null ? null : rate.intValue(),
            fixed == null ? null : fixed.longValue(), rounding);
    }

    private static DraftFailure holdNotFound() {
        return new DraftFailure(404, "Hold not found");
    }
}
