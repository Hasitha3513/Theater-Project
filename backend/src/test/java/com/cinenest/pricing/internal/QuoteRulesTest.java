package com.cinenest.pricing.internal;
import com.cinenest.booking.QuotationHolds;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class QuoteRulesTest {
    static final UUID SERVICE=UUID.randomUUID();
    Map<String,Object> pkg(String mode){var p=new HashMap<String,Object>();p.putAll(Map.of("name","SYNTHETIC","currency","LKR","basePriceMinor","250000","includedMinutes",180,"minimumMinutes",180,"maximumMinutes",300,"extensionsEnabled",true,"extensionStepMinutes",60,"extensionPriceMinor","100000","includedGuests",4));p.put("maximumGuests",5);p.put("extraGuestPriceMinor","80000");p.put("options",List.of(Map.of("serviceId",SERVICE.toString(),"rateVersionId",UUID.randomUUID().toString(),"label","SYNTHETIC equipment","mode",mode,"pricingBasis","PER_SESSION","currency","LKR","unitPriceMinor","100000")));return p;}
    QuotationHolds.Facts hold(int minutes,int guests){Instant start=Instant.parse("2026-11-01T00:00:00Z");return new QuotationHolds.Facts(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),start,start.plusSeconds(minutes*60L),start.minusSeconds(10),"ACTIVE",0,guests,"Asia/Colombo",7,start.plusSeconds(minutes*60L+420),"SYNTHETIC");}
    @Test void exactBaseFifthGuestAndWholeExtensions(){assertThat(QuoteRules.calculate(pkg("OPTIONAL"),hold(240,5),Set.of(SERVICE),"BANK",7)).containsEntry("totalMinor","530007");}
    @Test void includedNeverDoubleChargesAndUnselectedOptionalIsZero(){assertThat(QuoteRules.calculate(pkg("INCLUDED"),hold(180,4),Set.of(),"BANK",7)).containsEntry("totalMinor","250007");assertThat(QuoteRules.calculate(pkg("OPTIONAL"),hold(180,4),Set.of(),"GATEWAY",7)).containsEntry("totalMinor","250007");}
    @Test void noShorterDurationProrationOrFractionalExtension(){for(int duration:List.of(179,181,210,301))assertThatThrownBy(()->QuoteRules.calculate(pkg("OPTIONAL"),hold(duration,4),Set.of(),"BANK",7)).isInstanceOf(DraftFailure.class);}
    @Test void unsupportedMethodTaxCurrencyAndSelectionsFailClosed(){assertThatThrownBy(()->QuoteRules.calculate(pkg("OPTIONAL"),hold(180,4),Set.of(),"CARD",7)).isInstanceOf(DraftFailure.class);assertThatThrownBy(()->QuoteRules.calculate(pkg("OPTIONAL"),hold(180,4),Set.of(),"BANK",-1)).isInstanceOf(DraftFailure.class);assertThatThrownBy(()->QuoteRules.calculate(pkg("INCLUDED"),hold(180,4),Set.of(SERVICE),"BANK",7)).isInstanceOf(DraftFailure.class);}
    @Test void exactSyntheticTaxBoundariesAndOverflow(){var p=pkg("OPTIONAL");assertThat(QuoteRules.calculate(p,hold(180,4),Set.of(),"BANK",0)).containsEntry("totalMinor","250000");assertThatThrownBy(()->QuoteRules.calculate(p,hold(180,4),Set.of(),"BANK",Long.MAX_VALUE)).isInstanceOf(DraftFailure.class);p.put("basePriceMinor","250000.1");assertThatThrownBy(()->QuoteRules.calculate(p,hold(180,4),Set.of(),"BANK",7)).isInstanceOf(DraftFailure.class);}
    @Test void disabledExtensionHasNoInventedRate(){var p=pkg("OPTIONAL");p.put("extensionsEnabled",false);p.put("extensionPriceMinor",null);p.put("extensionStepMinutes",null);assertThat(QuoteRules.calculate(p,hold(180,4),Set.of(),"BANK",7)).containsEntry("totalMinor","250007");assertThatThrownBy(()->QuoteRules.calculate(p,hold(240,4),Set.of(),"BANK",7)).isInstanceOf(DraftFailure.class);}
}
