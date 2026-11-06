package com.cinenest.pricing.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import com.nimbusds.jose.util.JSONObjectUtils;

final class CommercialPolicyDraftRules {
    static UUID id(Object value){try{if(!(value instanceof String s)||!UUID.fromString(s).toString().equals(s))throw CommercialPolicyDraft.invalid();return UUID.fromString(s);}catch(IllegalArgumentException e){throw CommercialPolicyDraft.invalid();}}
    static String text(Object value,int max,boolean empty){if(!(value instanceof String s)||s.length()>max||(!empty&&s.isBlank())||s.chars().anyMatch(Character::isISOControl))throw CommercialPolicyDraft.invalid();return s;}
    static long number(Object value,long max){if(!(value instanceof String s)||!s.matches("0|[1-9][0-9]{0,18}"))throw CommercialPolicyDraft.invalid();try{long n=Long.parseLong(s);if(n>max)throw CommercialPolicyDraft.invalid();return n;}catch(NumberFormatException e){throw CommercialPolicyDraft.invalid();}}
    static void keys(Map<String,Object> value,Set<String> allowed,Set<String> required){if(!allowed.containsAll(value.keySet())||!value.keySet().containsAll(required))throw CommercialPolicyDraft.invalid();}
    @SuppressWarnings("unchecked") static Map<String,Object> object(Object v){if(!(v instanceof Map<?,?> m)||m.keySet().stream().anyMatch(k->!(k instanceof String)))throw CommercialPolicyDraft.invalid();return (Map<String,Object>)m;}
    static List<String> strings(Object value,int max,Set<String> allowed){if(!(value instanceof List<?> l)||l.size()>max)throw CommercialPolicyDraft.invalid();var result=new ArrayList<String>();for(Object v:l){String s=text(v,500,false);if(allowed!=null&&!allowed.contains(s)||result.contains(s))throw CommercialPolicyDraft.invalid();result.add(s);}return result;}
    static void choice(Map<String,Object>d,String key,String... allowed){if(!Set.of(allowed).contains(d.get(key)))throw CommercialPolicyDraft.invalid();}
    static Map<String,Object> document(Object value){
        var d=new LinkedHashMap<>(object(value));keys(d,CommercialPolicyDraft.FIELDS,CommercialPolicyDraft.FIELDS);
        text(d.get("title"),200,false);text(d.get("exceptionNotes"),2000,true);
        for(String k:List.of("taxAmountMinor","refundAmountMinor"))if(d.get(k)!=null)number(d.get(k),Long.MAX_VALUE);
        for(String k:List.of("taxRateBasisPoints","refundRateBasisPoints"))if(d.get(k)!=null)number(d.get(k),10000);
        if(d.get("cancellationCutoffMinutes")!=null)number(d.get("cancellationCutoffMinutes"),525600);
        Instant from=null,to=null;try{if(d.get("effectiveFromProposal")!=null)from=Instant.parse(text(d.get("effectiveFromProposal"),40,false));if(d.get("effectiveUntilProposal")!=null)to=Instant.parse(text(d.get("effectiveUntilProposal"),40,false));}catch(java.time.DateTimeException e){throw CommercialPolicyDraft.invalid();}
        if(to!=null&&(from==null||!to.isAfter(from)))throw CommercialPolicyDraft.invalid();
        choice(d,"taxApplicability","UNRESOLVED","APPLICABLE","NOT_APPLICABLE","EXEMPT");choice(d,"taxType","UNRESOLVED","NONE","PERCENTAGE","FIXED_PER_SESSION");
        choice(d,"taxTreatment","UNRESOLVED","NONE","INCLUSIVE","EXCLUSIVE");choice(d,"taxBasis","UNRESOLVED","NONE","PACKAGE_COMPONENTS","PACKAGE_COMPONENTS_AND_METHOD_FEE");choice(d,"taxRounding","UNRESOLVED","NONE","HALF_UP","HALF_EVEN");
        choice(d,"cancellationEligibility","UNRESOLVED","NOT_ALLOWED","BEFORE_START","REVIEW_REQUIRED");choice(d,"cancellationTimeBasis","VENUE_TIMEZONE");choice(d,"noShowTreatment","UNRESOLVED","NO_REFUND","REVIEW_REQUIRED");choice(d,"exceptionHandling","UNRESOLVED","NONE","MANUAL_REVIEW_REQUIRED");
        choice(d,"refundEligibility","UNRESOLVED","NONE","ELIGIBLE_CANCELLATION","MANUAL_REVIEW");choice(d,"refundCalculation","UNRESOLVED","NONE","PERCENTAGE","FIXED_MINOR");choice(d,"refundBasis","UNRESOLVED","NONE","ELIGIBLE_PRINCIPAL");choice(d,"refundDestination","UNRESOLVED","NONE","ORIGINAL_VERIFIED_PAYER");
        var methods=strings(d.get("paymentMethods"),2,Set.of("BANK","GATEWAY"));var channels=strings(d.get("feeChannels"),2,Set.of("ONLINE","MANUAL"));
        strings(d.get("suppliedReferences"),20,null);if(d.get("nonRefundableComponents")!=null)strings(d.get("nonRefundableComponents"),6,Set.of("BASE","EXTENSION","EXTRA_GUEST","ADDON","METHOD_FEE","TAX"));
        if(!(d.get("submittedForReview") instanceof Boolean))throw CommercialPolicyDraft.invalid();
        if(!(d.get("fees") instanceof List<?> fees)||fees.size()>4)throw CommercialPolicyDraft.invalid();var pairs=new HashSet<String>();
        for(Object v:fees){var f=object(v);var fields=Set.of("method","channel","type","amountMinor","currency","refundability");keys(f,fields,fields);choice(f,"type","UNRESOLVED","NONE","FIXED_MINOR");choice(f,"currency","LKR");choice(f,"refundability","UNRESOLVED","REFUNDABLE","NON_REFUNDABLE");if(!methods.contains(f.get("method"))||!channels.contains(f.get("channel"))||!pairs.add(f.get("method")+":"+f.get("channel")))throw CommercialPolicyDraft.invalid();if(f.get("amountMinor")!=null)number(f.get("amountMinor"),Long.MAX_VALUE);if(f.get("type").equals("NONE")&&!"0".equals(f.get("amountMinor"))||f.get("type").equals("UNRESOLVED")&&f.get("amountMinor")!=null)throw CommercialPolicyDraft.invalid();}
        return Collections.unmodifiableMap(d);
    }
    static Object canonical(Object v){if(v instanceof Map<?,?> m){var out=new TreeMap<String,Object>();m.forEach((k,x)->out.put(k.toString(),canonical(x)));return out;}if(v instanceof List<?> l)return l.stream().map(CommercialPolicyDraftRules::canonical).toList();return v;}
    static String hash(Map<String,Object> payload){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSONObjectUtils.toJSONString(object(canonical(payload))).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    static String snake(String s){return s.replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);}
    static String camel(String s){var b=new StringBuilder();boolean upper=false;for(char c:s.toCharArray()){if(c=='_')upper=true;else{b.append(upper?Character.toUpperCase(c):c);upper=false;}}return b.toString();}
    static Map<String,Object> transport(Map<String,Object> row){var out=new LinkedHashMap<String,Object>();row.forEach((k,v)->out.put(camel(k),v instanceof Number?v.toString():v));return out;}
}
