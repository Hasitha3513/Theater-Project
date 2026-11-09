package com.cinenest.pricing.internal;

import com.cinenest.booking.QuotationHolds;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class CommercialQuotationRules {
    enum TaxTreatment { NONE, INCLUSIVE, EXCLUSIVE }
    enum TaxBasis { NONE, PACKAGE_COMPONENTS, PACKAGE_COMPONENTS_AND_METHOD_FEE }
    enum FeeTreatment { NONE, FIXED_MINOR }

    record Tax(TaxTreatment treatment, TaxBasis basis, Integer basisPoints, Long fixedMinor, RoundingMode rounding) {}
    record Fee(String method, String channel, FeeTreatment treatment, long amountMinor) {}
    record Amounts(long packageMinor, long feeMinor, long taxMinor, long totalMinor) {}
    record Price(List<Map<String, Object>> packageLines, Amounts amounts) {}

    private CommercialQuotationRules() {}

    static Price price(Map<String, Object> publication, QuotationHolds.Facts hold, Set<UUID> selected,
                       String method, String channel, List<Fee> fees, Tax tax) {
        if (hold.guests() < 1 || !hold.end().isAfter(hold.start())) {
            throw DraftRules.invalid("hold guests and interval");
        }
        var optionIds = new HashSet<UUID>();
        for (var option : DraftRules.objects(publication, "options")) {
            if (!optionIds.add(DraftRules.id(option.get("serviceId").toString()))) {
                throw DraftRules.invalid("duplicate package service");
            }
        }
        if (hold.end().isAfter(hold.start().plusSeconds(
                Math.multiplyExact(((Number) publication.get("includedMinutes")).longValue(), 60)))
                && publication.get("extensionPriceMinor") == null) {
            throw DraftRules.invalid("explicit extension price required");
        }

        var lines = QuoteRules.knownComponents(publication, hold, selected);
        long subtotal = 0;
        for (var line : lines) {
            subtotal = DraftRules.add(subtotal, DraftRules.minor(line.get("amountMinor"), false));
        }
        return new Price(List.copyOf(lines), calculate(subtotal, method, channel, fees, tax));
    }

    static Amounts calculate(long packageMinor, String method, String channel, List<Fee> fees, Tax tax) {
        if (packageMinor <= 0 || method == null || !Set.of("BANK", "GATEWAY").contains(method)
                || channel == null || !Set.of("ONLINE", "MANUAL").contains(channel)
                || fees == null || tax == null || tax.treatment() == null || tax.basis() == null) {
            throw DraftRules.invalid("complete LKR quotation terms required");
        }
        var matchingFees = fees.stream()
            .filter(fee -> method.equals(fee.method()) && channel.equals(fee.channel())).toList();
        if (matchingFees.size() != 1) {
            throw DraftRules.invalid("exactly one approved method and channel fee required");
        }
        var fee = matchingFees.getFirst();
        if (fee.treatment() == null || fee.amountMinor() < 0
                || (fee.treatment() == FeeTreatment.NONE) != (fee.amountMinor() == 0)) {
            throw DraftRules.invalid("explicit fee treatment required");
        }
        long gross = DraftRules.add(packageMinor, fee.amountMinor());
        long taxMinor = taxAmount(tax, packageMinor, gross);
        long total = tax.treatment() == TaxTreatment.EXCLUSIVE ? DraftRules.add(gross, taxMinor) : gross;
        return new Amounts(packageMinor, fee.amountMinor(), taxMinor, total);
    }

    private static long taxAmount(Tax tax, long packageMinor, long gross) {
        if (tax.treatment() == TaxTreatment.NONE) {
            if (tax.basis() != TaxBasis.NONE || tax.basisPoints() != null
                    || tax.fixedMinor() != null || tax.rounding() != null) {
                throw DraftRules.invalid("explicit no-tax terms conflict");
            }
            return 0;
        }
        if (tax.basis() == TaxBasis.NONE || (tax.rounding() != RoundingMode.HALF_UP
                && tax.rounding() != RoundingMode.HALF_EVEN)
                || (tax.basisPoints() == null) == (tax.fixedMinor() == null)) {
            throw DraftRules.invalid("complete approved tax terms required");
        }
        if (tax.fixedMinor() != null) {
            if (tax.treatment() != TaxTreatment.EXCLUSIVE || tax.fixedMinor() <= 0) {
                throw DraftRules.invalid("fixed tax must be positive and exclusive");
            }
            return tax.fixedMinor();
        }
        int rate = tax.basisPoints();
        if (rate < 1 || rate > 10_000) {
            throw DraftRules.invalid("tax basis points out of range");
        }
        long basis = tax.basis() == TaxBasis.PACKAGE_COMPONENTS ? packageMinor : gross;
        long denominator = tax.treatment() == TaxTreatment.INCLUSIVE ? 10_000L + rate : 10_000;
        return BigDecimal.valueOf(basis).multiply(BigDecimal.valueOf(rate))
            .divide(BigDecimal.valueOf(denominator), 0, tax.rounding()).longValueExact();
    }

    static Instant expiry(Instant now, Instant holdUntil, Instant policyUntil, Instant approvalsUntil) {
        var expiry = List.of(holdUntil, policyUntil, approvalsUntil).stream()
            .min(Instant::compareTo).orElseThrow();
        if (!now.isBefore(expiry)) {
            throw new DraftFailure(409, "Quotation validity has ended");
        }
        return expiry;
    }
}
