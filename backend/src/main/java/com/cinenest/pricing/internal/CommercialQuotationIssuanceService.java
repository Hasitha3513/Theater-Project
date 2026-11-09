package com.cinenest.pricing.internal;

import com.cinenest.booking.QuotationHolds;
import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.rooms.PackageConstraints;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/** Internal fixture-only issuance. No Spring bean, controller or production caller. */
final class CommercialQuotationIssuanceService {
    private final ScopedAuthorization identity;
    private final PublicationRepository publications;
    private final DraftRepository drafts;
    private final PackageConstraints rooms;
    private final QuotationHolds holds;
    private final JdbcCommercialPolicyActivationRepository trust;
    private final JdbcCommercialQuotationRepository repository;
    private final TransactionTemplate tx;

    CommercialQuotationIssuanceService(ScopedAuthorization identity, PublicationRepository publications,
            DraftRepository drafts, PackageConstraints rooms, QuotationHolds holds,
            JdbcCommercialPolicyActivationRepository trust, JdbcCommercialQuotationRepository repository,
            TransactionTemplate tx) {
        this.identity = identity;
        this.publications = publications;
        this.drafts = drafts;
        this.rooms = rooms;
        this.holds = holds;
        this.trust = trust;
        this.repository = repository;
        this.tx = tx;
    }

    record Command(UUID hold, long holdRevision, UUID packageId, UUID version, UUID activation, UUID key,
                   String method, String channel, Set<UUID> services) {
        Command {
            services = Set.copyOf(services);
        }

        String hash() {
            return PublicationService.hash(Map.of("hold", hold.toString(), "revision", holdRevision,
                "package", packageId.toString(), "version", version.toString(), "activation", activation.toString(),
                "method", method, "channel", channel, "services", services.stream().map(UUID::toString).sorted().toList()));
        }
    }

    UUID issueFixture(UUID actor, Command command) {
        return tx.execute(status -> {
            if (actor == null || !identity.eligible(actor, true) || !repository.syntheticActor(actor)) {
                throw new DraftFailure(403, "Synthetic fixture actor required");
            }
            var located = holds.locate(command.hold()).filter(h -> actor.equals(h.owner()))
                .orElseThrow(() -> new DraftFailure(404, "Hold not found"));
            trust.lockTrust();
            publications.lock(located.venue(), command.packageId());
            var hold = holds.lockAndInspect(actor, command.hold())
                .filter(h -> actor.equals(h.owner()) && located.venue().equals(h.venue()) && located.room().equals(h.room()))
                .orElseThrow(() -> new DraftFailure(404, "Hold not found"));
            if (!"ACTIVE".equals(hold.state()) || hold.revision() != command.holdRevision()) {
                throw new DraftFailure(409, "Hold changed or expired");
            }
            var published = publications.catalog(hold.venue()).stream()
                .filter(p -> command.packageId().equals(p.get("packageId")) && command.version().equals(p.get("versionId"))
                    && !hold.start().isBefore(Instant.parse(p.get("sessionFrom").toString()))
                    && !hold.end().isAfter(Instant.parse(p.get("sessionTo").toString())))
                .findFirst().orElseThrow(() -> new DraftFailure(409, "Published package version unavailable"));
            var snapshot = PublicationService.object(published, "snapshot");
            verifyRoom(hold, snapshot);
            var now = trust.now();
            var policy = repository.active(command.activation(), hold, command.version(), now);
            var expiry = CommercialQuotationRules.expiry(trust.now(), hold.expires(), policy.until(), policy.approvalUntil());
            String hash = command.hash();
            var receipt = repository.replay(actor, command.key());
            if (receipt.isPresent()) {
                if (!hash.equals(receipt.get().hash())) {
                    throw new DraftFailure(409, "Quotation command payload changed");
                }
                return receipt.get().id();
            }
            var price = CommercialQuotationRules.price(snapshot, hold, command.services(), command.method(),
                command.channel(), fees(policy.terms()), tax(policy.terms()));
            var amount = price.amounts();
            var evidence = new java.util.LinkedHashMap<String,Object>();
            evidence.put("classification", "SYNTHETIC_INTERNAL_NOT_OFFER");
            evidence.put("currency", "LKR");
            evidence.put("scope", Map.of("venue", hold.venue().toString(), "room", hold.room().toString(),
                "hold", hold.id().toString(), "holdRevision", hold.revision(), "packageVersion", command.version().toString(),
                "activation", command.activation().toString(), "policy", policy.policy().toString(),
                "policyRevision", policy.revision(), "policyHash", policy.hash()));
            evidence.put("expiresAt", expiry.toString());
            evidence.put("package", snapshot);
            evidence.put("terms", policy.terms());
            evidence.put("paymentMethod", command.method());
            evidence.put("channel", command.channel());
            evidence.put("lines", price.packageLines());
            evidence.put("amounts", Map.of("packageMinor", amount.packageMinor(), "feeMinor", amount.feeMinor(),
                "taxMinor", amount.taxMinor(), "totalMinor", amount.totalMinor()));
            return repository.insert(actor, command.key(), hash, hold, command.version(), command.activation(),
                policy, evidence, amount.totalMinor(), expiry);
        });
    }

    private void verifyRoom(QuotationHolds.Facts hold, Map<String,Object> snapshot) {
        if (!PublicationService.rooms(snapshot).contains(hold.room())) {
            throw new DraftFailure(409, "Package does not cover held room");
        }
        var room = rooms.inspect(hold.venue(), Set.of(hold.room()), false, false)
            .orElseThrow(() -> new DraftFailure(409, "Room configuration unavailable")).rooms().getFirst();
        if (hold.guests() > room.capacity()) {
            throw new DraftFailure(409, "Room capacity changed");
        }
        var available = drafts.options(hold.venue());
        for (var option : DraftRules.objects(snapshot, "options")) {
            if (available.stream().noneMatch(rate -> rate.get("serviceId").toString().equals(option.get("serviceId").toString())
                    && rate.get("rateVersionId").toString().equals(option.get("rateVersionId").toString())
                    && room.facilities().contains(rate.get("requiredFacilityCode")))) {
                throw new DraftFailure(409, "Package option is no longer applicable");
            }
        }
    }

    private static List<CommercialQuotationRules.Fee> fees(Map<String, Object> terms) {
        return DraftRules.objects(terms, "fees").stream().map(f -> {
            if (!"LKR".equals(f.get("currency"))) {
                throw DraftRules.invalid("fee currency");
            }
            return new CommercialQuotationRules.Fee(f.get("method").toString(), f.get("channel").toString(),
                CommercialQuotationRules.FeeTreatment.valueOf(f.get("type").toString()),
                DraftRules.minor(f.get("amountMinor"), false));
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
}
