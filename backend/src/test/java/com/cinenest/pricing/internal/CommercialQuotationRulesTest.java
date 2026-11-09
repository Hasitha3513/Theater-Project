package com.cinenest.pricing.internal;

import static com.cinenest.pricing.internal.CommercialQuotationRules.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CommercialQuotationRulesTest {
    private static final Fee ONLINE = new Fee("BANK", "ONLINE", FeeTreatment.FIXED_MINOR, 10_000);
    private static final Fee NO_FEE = new Fee("BANK", "ONLINE", FeeTreatment.NONE, 0);
    private static final Tax NO_TAX = new Tax(TaxTreatment.NONE, TaxBasis.NONE, null, null, null);

    @Test
    void usesQualifiedPackageArithmeticAndRejectsInvalidOrDuplicateComponents() {
        var fixtures = new QuoteRulesTest();
        var selected = Set.of(QuoteRulesTest.SERVICE);
        var publication = fixtures.pkg("OPTIONAL");
        var hold = fixtures.hold(240, 5);
        var price = price(publication, hold, selected, "BANK", "ONLINE", List.of(NO_FEE), NO_TAX);
        assertThat(price.amounts()).isEqualTo(new Amounts(530_000, 0, 0, 530_000));
        assertThat(price.packageLines()).extracting(line -> line.get("kind"))
            .containsExactly("BASE", "EXTENSION", "EXTRA_GUEST", "OPTIONAL");
        assertThatThrownBy(() -> price(publication, fixtures.hold(240, 6), selected, "BANK", "ONLINE",
            List.of(NO_FEE), NO_TAX)).isInstanceOf(DraftFailure.class);
        assertThatThrownBy(() -> price(publication, fixtures.hold(210, 5), selected, "BANK", "ONLINE",
            List.of(NO_FEE), NO_TAX)).isInstanceOf(DraftFailure.class);
        publication.put("extensionPriceMinor", null);
        assertThatThrownBy(() -> price(publication, hold, selected, "BANK", "ONLINE", List.of(NO_FEE), NO_TAX))
            .isInstanceOf(DraftFailure.class);
        publication.put("extensionPriceMinor", "100000");
        var option = ((List<?>) publication.get("options")).getFirst();
        publication.put("options", List.of(option, option));
        assertThatThrownBy(() -> price(publication, hold, selected, "BANK", "ONLINE", List.of(NO_FEE), NO_TAX))
            .isInstanceOf(DraftFailure.class);
    }

    @Test
    void computesExclusiveTaxWithExplicitFeeBasisInMinorUnits() {
        var tax = new Tax(TaxTreatment.EXCLUSIVE, TaxBasis.PACKAGE_COMPONENTS_AND_METHOD_FEE,
            1_000, null, RoundingMode.HALF_UP);
        assertThat(calculate(250_000, "BANK", "ONLINE", List.of(ONLINE), tax))
            .isEqualTo(new Amounts(250_000, 10_000, 26_000, 286_000));
    }

    @Test
    void disclosesInclusiveTaxWithoutChargingItTwice() {
        var tax = new Tax(TaxTreatment.INCLUSIVE, TaxBasis.PACKAGE_COMPONENTS, 1_000, null, RoundingMode.HALF_UP);
        assertThat(calculate(110_000, "BANK", "ONLINE", List.of(ONLINE), tax))
            .isEqualTo(new Amounts(110_000, 10_000, 10_000, 120_000));
    }

    @Test
    void honorsExplicitRoundingAndFixedTax() {
        var up = new Tax(TaxTreatment.EXCLUSIVE, TaxBasis.PACKAGE_COMPONENTS, 5_000, null, RoundingMode.HALF_UP);
        var even = new Tax(TaxTreatment.EXCLUSIVE, TaxBasis.PACKAGE_COMPONENTS, 5_000, null, RoundingMode.HALF_EVEN);
        var fixed = new Tax(TaxTreatment.EXCLUSIVE, TaxBasis.PACKAGE_COMPONENTS, null, 1L, RoundingMode.HALF_UP);
        assertThat(calculate(1, "BANK", "ONLINE", List.of(NO_FEE), up).taxMinor()).isEqualTo(1);
        assertThat(calculate(1, "BANK", "ONLINE", List.of(NO_FEE), even).taxMinor()).isZero();
        assertThat(calculate(1, "BANK", "ONLINE", List.of(NO_FEE), fixed).totalMinor()).isEqualTo(2);
    }

    @Test
    void refusesMissingDuplicateOrConflictingFeeAndTaxTerms() {
        assertThat(calculate(1, "BANK", "ONLINE", List.of(NO_FEE), NO_TAX).totalMinor()).isEqualTo(1);
        for (var fees : List.of(List.<Fee>of(), List.of(NO_FEE, ONLINE),
                List.of(new Fee("BANK", "ONLINE", FeeTreatment.NONE, 1)))) {
            assertThatThrownBy(() -> calculate(1, "BANK", "ONLINE", fees, NO_TAX))
                .isInstanceOf(DraftFailure.class);
        }
        assertThatThrownBy(() -> calculate(1, "BANK", "ONLINE", List.of(NO_FEE), null))
            .isInstanceOf(DraftFailure.class);
        assertThatThrownBy(() -> calculate(1, "BANK", "ONLINE", List.of(NO_FEE),
            new Tax(TaxTreatment.INCLUSIVE, TaxBasis.PACKAGE_COMPONENTS, null, 1L, RoundingMode.HALF_UP)))
            .isInstanceOf(DraftFailure.class);
    }

    @Test
    void rejectsSubtotalAndFinalTotalOverflow() {
        assertThatThrownBy(() -> calculate(Long.MAX_VALUE, "BANK", "ONLINE", List.of(ONLINE), NO_TAX))
            .isInstanceOf(DraftFailure.class);
        assertThatThrownBy(() -> calculate(Long.MAX_VALUE, "BANK", "ONLINE", List.of(NO_FEE),
            new Tax(TaxTreatment.EXCLUSIVE, TaxBasis.PACKAGE_COMPONENTS, 1, null, RoundingMode.HALF_EVEN)))
            .isInstanceOf(DraftFailure.class);
    }

    @Test
    void expiresAtTheEarliestAuthorityDeadlineAndDeniesAtTheBoundary() {
        var now = Instant.parse("2030-01-01T00:00:00Z");
        var early = now.plusSeconds(1);
        var late = now.plusSeconds(2);
        assertThat(expiry(now, early, late, late)).isEqualTo(early);
        assertThat(expiry(now, late, early, late)).isEqualTo(early);
        assertThat(expiry(now, late, late, early)).isEqualTo(early);
        assertThatThrownBy(() -> expiry(early, late, late, early)).isInstanceOf(DraftFailure.class);
    }
}
