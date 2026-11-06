package com.cinenest.pricing.internal;

import com.cinenest.booking.QuotationHolds;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Pure, nonpayable observations. Neither a policy approval nor an issued quotation. */
final class CommercialQuotePreview {
    static final String CLASSIFICATION="SYNTHETIC_PREVIEW_NOT_OFFER";
    static final List<String> DISCLOSURES=List.of("SYNTHETIC POLICY PREVIEW","NOT A COMMERCIAL OFFER",
            "UNKNOWN TAX AND FEES ARE NOT ZERO","NO QUOTATION ISSUANCE, PAYMENT OR BOOKING CONFIRMATION");
    private static final Set<String> REQUIRED=Set.of("holdId","expectedHoldRevision","packageId",
            "packageVersionId","paymentMethod","optionalServiceIds");
    private static final Set<String> PINS=Set.of("policyId","policyRevision","policyContentHash");
    record Pin(UUID id,long revision,String hash) {}
    record Request(UUID hold,long holdRevision,UUID pkg,UUID version,String method,Set<UUID> options,Pin pin) {
        Request { options=Collections.unmodifiableSet(new TreeSet<>(options)); }
    }
    /** Publicly safe candidate projection only; no reviewer, grant or evidence-reference fields. */
    record PolicyCandidate(UUID id,long revision,String hash,UUID pkg,Instant from,Instant until,
            String status,boolean complete,boolean submitted) {
        PolicyCandidate {
            if(id==null||revision<1||hash==null||!hash.matches("[0-9a-f]{64}")
                    ||!Set.of("INCOMPLETE","DRAFT","PENDING_REVIEW","REJECTED","APPROVED").contains(status))
                throw new DraftFailure(503,"Policy candidate unavailable");
        }
        Map<String,Object> projection() {
            var result=new LinkedHashMap<String,Object>();
            result.put("policyId",id.toString());result.put("revision",Long.toString(revision));
            result.put("contentHash",hash);result.put("draftReviewStatus",status);
            result.put("scopeKind",pkg==null?"VENUE":"PACKAGE");result.put("packageId",pkg==null?null:pkg.toString());
            result.put("effectiveFromProposal",from==null?null:from.toString());
            result.put("effectiveUntilProposal",until==null?null:until.toString());
            return result;
        }
    }
    private CommercialQuotePreview() {}
    static Request parse(Map<String,List<String>> query) {
        if(query==null||!query.keySet().containsAll(REQUIRED)||query.size()>REQUIRED.size()+PINS.size())
            throw DraftRules.invalid("preview query");
        int bytes=0;
        for(var e:query.entrySet()) {
            if(!REQUIRED.contains(e.getKey())&&!PINS.contains(e.getKey())||e.getValue()==null
                    ||e.getValue().size()!=1||e.getValue().getFirst()==null)
                throw DraftRules.invalid("preview query");
            bytes=Math.addExact(bytes,e.getValue().getFirst().length());
        }
        if(bytes>8192)throw DraftRules.invalid("preview query size");
        String method=value(query,"paymentMethod");
        if(!Set.of("BANK","GATEWAY").contains(method))throw DraftRules.invalid("paymentMethod");
        String choices=value(query,"optionalServiceIds");
        if(choices.length()>3699)throw DraftRules.invalid("optionalServiceIds");
        var selected=choices.isEmpty()?Set.<UUID>of():QuoteRules.selections(Map.of("optionalServiceIds",List.of(choices.split(",",-1))));
        long pinFields=PINS.stream().filter(query::containsKey).count();
        if(pinFields!=0&&pinFields!=3)throw DraftRules.invalid("complete policy pin required");
        Pin pin=null;
        if(pinFields==3) {
            String hash=value(query,"policyContentHash");
            if(!hash.matches("[0-9a-f]{64}"))throw DraftRules.invalid("policyContentHash");
            pin=new Pin(DraftRules.id(value(query,"policyId")),revision(value(query,"policyRevision"),true),hash);
        }
        return new Request(DraftRules.id(value(query,"holdId")),revision(value(query,"expectedHoldRevision"),false),
                DraftRules.id(value(query,"packageId")),DraftRules.id(value(query,"packageVersionId")),method,selected,pin);
    }
    private static String value(Map<String,List<String>> query,String key){return query.get(key).getFirst();}
    private static long revision(String value,boolean positive) {
        if(!value.matches("0|[1-9][0-9]{0,18}"))throw DraftRules.invalid("revision");
        try {long n=Long.parseLong(value);if(positive&&n==0)throw DraftRules.invalid("policyRevision");return n;}
        catch(NumberFormatException e){throw DraftRules.invalid("revision overflow");}
    }
    static Map<String,Object> result(Request request,QuotationHolds.Facts hold,Map<String,Object> pkg,
            List<PolicyCandidate> candidates,Instant now) {
        if(!request.hold().equals(hold.id())||request.holdRevision()!=hold.revision()||hold.guests()<1
                ||!"ACTIVE".equals(hold.state())||!now.isBefore(hold.expires()))
            throw new DraftFailure(409,"Hold changed or expired; reload inventory");
        var lines=QuoteRules.knownComponents(pkg,hold,request.options());
        long known=0;var publicLines=new ArrayList<Map<String,Object>>();
        for(var line:lines) {
            known=DraftRules.add(known,Long.parseLong(line.get("amountMinor").toString()));
            var projection=new LinkedHashMap<String,Object>();
            for(String field:List.of("lineId","kind","label","quantity","unitMinor","amountMinor"))projection.put(field,line.get(field));
            publicLines.add(projection);
        }
        if(candidates==null)throw new DraftFailure(503,"Policy candidate unavailable");
        for(var candidate:candidates)if(candidate==null||candidate.pkg()!=null&&!request.pkg().equals(candidate.pkg())
                ||candidate.from()!=null&&hold.start().isBefore(candidate.from())
                ||candidate.until()!=null&&hold.end().isAfter(candidate.until()))
            throw new DraftFailure(409,"Policy proposal is outside this scope; reload the preview");
        if(request.pin()!=null&&(candidates.size()!=1||!candidates.getFirst().id().equals(request.pin().id())
                ||candidates.getFirst().revision()!=request.pin().revision()||!candidates.getFirst().hash().equals(request.pin().hash())))
            throw new DraftFailure(409,"Policy proposal changed or is outside this scope; reload the preview");
        var blockers=new ArrayList<String>(List.of("COMMERCIAL_ACTIVATION_NOT_AUTHORIZED",
                "TAX_TREATMENT_NOT_OPERATIONALLY_APPROVED","PAYMENT_FEES_NOT_OPERATIONALLY_APPROVED",
                "OPERATIONS_NOT_VERIFIED"));
        PolicyCandidate selected=candidates.size()==1?candidates.getFirst():null;
        if(candidates.isEmpty())blockers.add("POLICY_NOT_CONFIGURED");
        else if(candidates.size()!=1)blockers.add("POLICY_AMBIGUOUS");
        if(selected!=null) {
            blockers.add("POLICY_DRAFT_NOT_ACTIVE");
            if(!selected.complete())blockers.add("POLICY_DRAFT_INCOMPLETE");
            if(!selected.submitted()||!"APPROVED".equals(selected.status()))blockers.add("POLICY_DRAFT_REVIEWS_INCOMPLETE");
            if(selected.from()==null)blockers.add("POLICY_EFFECTIVE_PERIOD_UNRESOLVED");
        }
        if(!request.options().isEmpty())blockers.add("ADDON_EQUIPMENT_AND_USAGE_RIGHTS_NOT_VERIFIED");
        var result=new LinkedHashMap<String,Object>();
        result.putAll(Map.of("classification",CLASSIFICATION,"synthetic",true,"commerciallyEligible",false,
                "commercialIssuanceEnabled",false,"checkoutEnabled",false,"customerAccessEnabled",false,
                "currency","LKR","knownComponentsMinor",Long.toString(known),"lines",publicLines));
        result.put("disclosures",DISCLOSURES);result.put("blockers",List.copyOf(blockers));
        result.put("policyCandidate",selected==null?null:selected.projection());
        result.put("holdId",hold.id().toString());result.put("holdRevision",hold.revision());
        result.put("venueId",hold.venue().toString());result.put("roomId",hold.room().toString());
        result.put("packageId",request.pkg().toString());result.put("packageVersionId",request.version().toString());
        result.put("packageName",pkg.get("name"));result.put("paymentMethod",request.method());
        result.put("optionalServiceIds",request.options().stream().map(UUID::toString).toList());
        result.put("sessionStart",hold.start().toString());result.put("sessionEnd",hold.end().toString());
        result.put("durationMinutes",Math.toIntExact(Duration.between(hold.start(),hold.end()).toMinutes()));
        result.put("guests",hold.guests());result.put("timezone",hold.timezone());
        result.put("holdExpiresAt",hold.expires().toString());result.put("serverNow",now.toString());
        return result;
    }
}
