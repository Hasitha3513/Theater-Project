package com.cinenest.pricing.internal;
import com.cinenest.booking.*;import java.time.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;
class ExtensionPricingTest {
 Map<String,Object> purchase(){
  UUID room=UUID.randomUUID(),venue=UUID.randomUUID(),hold=UUID.randomUUID(),service=UUID.randomUUID();Instant start=Instant.parse("2030-01-01T04:00:00Z"),end=start.plusSeconds(180*60);
  var pkg=new HashMap<String,Object>();pkg.put("currency","LKR");pkg.put("name","Synthetic");pkg.put("basePriceMinor","12345");pkg.put("includedMinutes",180);pkg.put("minimumMinutes",180);pkg.put("maximumMinutes",360);pkg.put("extensionsEnabled",true);pkg.put("extensionStepMinutes",45);pkg.put("extensionPriceMinor","170");pkg.put("includedGuests",2);pkg.put("maximumGuests",5);pkg.put("extraGuestPriceMinor","25");
  pkg.put("options",List.of(Map.of("serviceId",service.toString(),"mode","OPTIONAL","pricingBasis","PER_SESSION","currency","LKR","unitPriceMinor","123","label","Synthetic extra")));
  var f=new QuotationHolds.Facts(hold,null,venue,room,start,end,null,"CONSUMED",0,4,"Asia/Colombo",7,end.plusSeconds(420),"");
  var s=QuoteRules.calculate(pkg,f,Set.of(service),"BANK",10);s.put("holdId",hold.toString());s.put("venueId",venue.toString());s.put("roomId",room.toString());s.put("sessionStart",start.toString());s.put("sessionEnd",end.toString());s.put("timezone","Asia/Colombo");s.put("guests",4);s.put("packageVersionId",UUID.randomUUID().toString());return s;
 }
 @Test void exactFrozenDeltaDoesNotRechargeBaseGuestsExtrasOrTax(){var s=purchase();var before=new HashMap<>(s);var r=new ExtensionPricingAdapter().preview(s,90);assertThat(r.additionalMinor()).isEqualTo("340");assertThat(r.incrementMinutes()).isEqualTo(45);assertThat(r.totalMinutes()).isEqualTo(270);assertThat(s).isEqualTo(before);}
 @Test void rejectsInvalidUnitsMaximumAndOverflow(){for(int n:List.of(0,-1,1,60,225,Integer.MAX_VALUE)){var s=purchase();assertThatThrownBy(()->new ExtensionPricingAdapter().preview(s,n)).isInstanceOf(ExtensionPricing.Unsupported.class);}}
 @SuppressWarnings("unchecked") @Test void rejectsInvalidFrozenMoneyAndZero(){for(String n:List.of("0","-1","1.5","9223372036854775808")){var s=purchase();((Map<String,Object>)s.get("package")).put("extensionPriceMinor",n);assertThatThrownBy(()->new ExtensionPricingAdapter().preview(s,45)).isInstanceOf(ExtensionPricing.Unsupported.class);}}
 @Test void rejectsPurchaseWhoseLinesOrTotalCannotBeReproduced(){for(String field:List.of("totalMinor","lines","currency")){var s=purchase();s.put(field,field.equals("lines")?List.of():"wrong");assertThatThrownBy(()->new ExtensionPricingAdapter().preview(s,45)).isInstanceOf(ExtensionPricing.Unsupported.class);}}
 @SuppressWarnings("unchecked") @Test void rejectsDisabledExtensions(){var s=purchase();((Map<String,Object>)s.get("package")).put("extensionsEnabled",false);assertThatThrownBy(()->new ExtensionPricingAdapter().preview(s,45)).isInstanceOf(ExtensionPricing.Unsupported.class);}
}
