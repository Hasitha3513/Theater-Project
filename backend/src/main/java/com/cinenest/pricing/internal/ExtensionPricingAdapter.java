package com.cinenest.pricing.internal;

import com.cinenest.booking.ExtensionPricing;
import com.cinenest.booking.QuotationHolds;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Pure calculation: no current publication lookup, policy insertion or quotation persistence. */
@Component
class ExtensionPricingAdapter implements ExtensionPricing {
    public Preview preview(Map<String,Object> purchase,int additional) {
        try {
            var pkg=PublicationService.object(purchase,"package");
            if(!Boolean.TRUE.equals(pkg.get("extensionsEnabled")))throw new Unsupported();
            int step=DraftRules.integer(pkg,"extensionStepMinutes",1,Integer.MAX_VALUE);
            int maximum=DraftRules.integer(pkg,"maximumMinutes",1,Integer.MAX_VALUE);
            long unit=DraftRules.minor(pkg.get("extensionPriceMinor"),true);
            Instant start=Instant.parse(purchase.get("sessionStart").toString()),end=Instant.parse(purchase.get("sessionEnd").toString());
            int current=Math.toIntExact(Duration.between(start,end).toMinutes());
            if(current<=0||!start.plusSeconds(Math.multiplyExact((long)current,60)).equals(end)||additional<=0||additional%step!=0)throw new Unsupported();
            int total=Math.addExact(current,additional);
            if(total>maximum)throw new Unsupported();
            String version=DraftRules.id(purchase.get("packageVersionId").toString()).toString();
            int guests=DraftRules.integer(purchase,"guests",1,Integer.MAX_VALUE);
            var selected=QuoteRules.selections(purchase);
            String method=purchase.get("paymentMethod").toString();
            var lines=DraftRules.objects(purchase,"lines");
            var taxes=lines.stream().filter(l->"SYNTHETIC_TAX".equals(l.get("kind"))).toList();
            if(taxes.size()!=1)throw new Unsupported();
            long tax=DraftRules.minor(taxes.getFirst().get("amountMinor"),false);
            var before=QuoteRules.calculate(pkg,facts(purchase,start,end,guests),selected,method,tax);
            if(!Objects.equals(before.get("lines"),purchase.get("lines"))||!Objects.equals(before.get("currency"),purchase.get("currency"))
                ||!Objects.equals(before.get("totalMinor"),purchase.get("totalMinor")))throw new Unsupported();
            var after=QuoteRules.calculate(pkg,facts(purchase,start,start.plusSeconds(Math.multiplyExact((long)total,60)),guests),selected,method,tax);
            long delta=Math.subtractExact(DraftRules.minor(after.get("totalMinor"),true),DraftRules.minor(before.get("totalMinor"),true));
            if(delta<=0||delta!=Math.multiplyExact(additional/step,unit))throw new Unsupported();
            return new Preview(current,additional,total,step,Math.multiplyExact((maximum-current)/step,step),Long.toString(delta),"LKR",version);
        } catch(RuntimeException invalid) {throw new Unsupported();}
    }
    private static QuotationHolds.Facts facts(Map<String,Object> s,Instant start,Instant end,int guests) {
        // Value object for the existing pure calculator, not a new or reactivated hold.
        return new QuotationHolds.Facts(UUID.fromString(s.get("holdId").toString()),null,UUID.fromString(s.get("venueId").toString()),
            UUID.fromString(s.get("roomId").toString()),start,end,null,"CONSUMED",0,guests,s.get("timezone").toString(),0,end,"");
    }
}
