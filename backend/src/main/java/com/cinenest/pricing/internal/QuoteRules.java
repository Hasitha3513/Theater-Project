package com.cinenest.pricing.internal;

import com.cinenest.booking.QuotationHolds;
import java.time.Duration;
import java.util.*;

/** Exact server calculation over immutable publication facts. No real tax model. */
final class QuoteRules {
    static final List<String> DISCLOSURES=List.of("TEST / SYNTHETIC QUOTATION","NOT A COMMERCIAL OFFER","NO PAYMENT OR BOOKING CONFIRMATION");
    private QuoteRules(){}
    static Set<UUID> selections(Map<String,Object> input) {
        Object value=input.get("optionalServiceIds");
        if(!(value instanceof List<?> list)||list.size()>100)throw DraftRules.invalid("optionalServiceIds");
        Set<UUID> result=new TreeSet<>();
        for(Object item:list)if(!(item instanceof String s)||!result.add(DraftRules.id(s)))throw DraftRules.invalid("optionalServiceIds");
        return result;
    }
    static Map<String,Object> calculate(Map<String,Object> pkg,QuotationHolds.Facts hold,Set<UUID> selected,String method,long tax) {
        if(!Set.of("BANK","GATEWAY").contains(method)||tax<0||!"LKR".equals(pkg.get("currency")))throw DraftRules.invalid("synthetic policy");
        long duration=Duration.between(hold.start(),hold.end()).toMinutes();
        if(!hold.start().plusSeconds(Math.multiplyExact(duration,60)).equals(hold.end()))throw DraftRules.invalid("whole-minute duration");
        int included=number(pkg,"includedMinutes"),maximum=number(pkg,"maximumMinutes"),minimum=number(pkg,"minimumMinutes");
        if(duration<included||duration<minimum||duration>maximum||hold.guests()>number(pkg,"maximumGuests"))throw DraftRules.invalid("package duration/guests");
        long units=0,delta=duration-included;
        if(delta>0){if(!Boolean.TRUE.equals(pkg.get("extensionsEnabled")))throw DraftRules.invalid("extensions disabled");int step=number(pkg,"extensionStepMinutes");if(step<=0||delta%step!=0)throw DraftRules.invalid("whole extension units");units=delta/step;}
        var lines=new ArrayList<Map<String,Object>>();
        line(lines,"BASE",String.valueOf(pkg.get("name")),1,DraftRules.minor(pkg.get("basePriceMinor"),true),Map.of());
        line(lines,"EXTENSION","Whole extension units",units,pkg.get("extensionPriceMinor")==null?0:DraftRules.minor(pkg.get("extensionPriceMinor"),false),Map.of());
        line(lines,"EXTRA_GUEST","Guests beyond allowance",Math.max(0,hold.guests()-number(pkg,"includedGuests")),DraftRules.minor(pkg.get("extraGuestPriceMinor"),false),Map.of());
        Set<UUID> available=new HashSet<>();
        for(var option:DraftRules.objects(pkg,"options")) {
            UUID service=DraftRules.id(option.get("serviceId").toString());String mode=option.get("mode").toString();
            if(!"PER_SESSION".equals(option.get("pricingBasis"))||!"LKR".equals(option.get("currency")))throw DraftRules.invalid("option basis");
            if(mode.equals("OPTIONAL"))available.add(service);else if(!mode.equals("INCLUDED"))throw DraftRules.invalid("option mode");
            long quantity=mode.equals("INCLUDED")||selected.contains(service)?1:0;
            line(lines,mode,option.get("label").toString(),quantity,mode.equals("INCLUDED")?0:DraftRules.minor(option.get("unitPriceMinor"),false),option);
        }
        if(!available.containsAll(selected))throw DraftRules.invalid("unavailable optional service");
        line(lines,"METHOD_FEE","CineNest-added "+method+" surcharge (external provider costs not represented)",1,0,Map.of("method",method));
        line(lines,"SYNTHETIC_TAX","SYNTHETIC FIXED TAX FIXTURE — not Sri Lankan tax policy",1,tax,Map.of("profile","SYNTHETIC_FIXED_MINOR_V1"));
        long total=0;for(var line:lines)total=DraftRules.add(total,Long.parseLong(line.get("amountMinor").toString()));
        var result=new LinkedHashMap<String,Object>();result.put("lines",lines);result.put("totalMinor",Long.toString(total));result.put("currency","LKR");result.put("package",pkg);result.put("optionalServiceIds",selected.stream().map(UUID::toString).toList());result.put("paymentMethod",method);result.put("disclosures",DISCLOSURES);result.put("synthetic",true);result.put("commercialIssuance",false);result.put("bookingConfirmed",false);result.put("checkoutEnabled",false);return result;
    }
    private static int number(Map<String,Object> pkg,String field){return Math.toIntExact(((Number)pkg.get(field)).longValue());}
    private static void line(List<Map<String,Object>> lines,String kind,String label,long quantity,long unit,Map<String,Object> reference){lines.add(Map.of("lineId",kind+":"+reference.getOrDefault("serviceId",kind),"kind",kind,"label",label,"quantity",quantity,"unitMinor",Long.toString(unit),"amountMinor",Long.toString(DraftRules.multiply(quantity,unit)),"reference",reference));}
}
