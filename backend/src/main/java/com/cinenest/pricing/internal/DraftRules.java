package com.cinenest.pricing.internal;
import java.util.*;

/** Exact configuration arithmetic; never an authoritative checkout quotation. */
final class DraftRules {
 private DraftRules(){}
 static DraftFailure invalid(String field){return new DraftFailure(400,"Invalid field: "+field);}
 static void keys(Map<String,Object> d,String... keys){if(!Set.of(keys).containsAll(d.keySet()))throw invalid("unknown property");}
 static String text(Map<String,Object> d,String k,int max,boolean required){Object raw=d.get(k);if(raw==null&&!required)return "";if(!(raw instanceof String))throw invalid(k);String s=((String)raw).trim();if(required&&s.isEmpty()||s.length()>max||s.codePoints().anyMatch(Character::isISOControl))throw invalid(k);return s;}
 static int integer(Map<String,Object>d,String k,int min,int max){Object v=d.get(k);if(!(v instanceof Integer||v instanceof Long)||((Number)v).longValue()<min||((Number)v).longValue()>max)throw invalid(k);return ((Number)v).intValue();}
 static long revision(Map<String,Object>d,String key){Object v=d.get(key);if(!(v instanceof Integer||v instanceof Long)||((Number)v).longValue()<0)throw invalid(key);return ((Number)v).longValue();}
 static UUID id(String value){try{UUID id=UUID.fromString(value);if(!id.toString().equals(value))throw invalid("id");return id;}catch(IllegalArgumentException e){throw invalid("id");}}
 static String code(Map<String,Object>d){String c=text(d,"code",64,true).toUpperCase(Locale.ROOT);if(!c.matches("[A-Z0-9_-]+"))throw invalid("code");return c;}
 static long minor(Object raw,boolean positive){if(!(raw instanceof String s)||!s.matches("[0-9]+"))throw invalid("minor-unit amount");try{long v=Long.parseLong(s);if(positive&&v==0)throw invalid("positive base price");return v;}catch(NumberFormatException e){throw invalid("amount overflow");}}

 static long add(long a,long b){try{return Math.addExact(a,b);}catch(ArithmeticException e){throw invalid("amount overflow");}}
 static long multiply(long a,long b){try{return Math.multiplyExact(a,b);}catch(ArithmeticException e){throw invalid("amount overflow");}}
 static boolean flag(Map<String,Object>d,String k){if(!(d.get(k) instanceof Boolean))throw invalid(k);return (Boolean)d.get(k);}
 @SuppressWarnings("unchecked") static List<Map<String,Object>> objects(Map<String,Object>d,String key){Object raw=d.getOrDefault(key,List.of());if(!(raw instanceof List<?> list)||list.size()>100)throw invalid(key);List<Map<String,Object>> out=new ArrayList<>();for(Object v:(List<?>)raw){if(!(v instanceof Map<?,?>))throw invalid(key);out.add(new HashMap<>((Map<String,Object>)v));}return out;}
 static Set<UUID> roomIds(Map<String,Object>d){Object raw=d.getOrDefault("roomIds",List.of());if(!(raw instanceof List<?> list)||list.size()>1000)throw invalid("roomIds");Set<UUID> out=new HashSet<>();for(Object v:(List<?>)raw){if(!(v instanceof String s)||!out.add(id((String)v)))throw invalid("roomIds");}return out;}
 static void neutral(String s){if(s.toLowerCase(Locale.ROOT).matches(".*(netflix|disney|unlimited streaming|licensed movies|screening rights|gaming content).*"))throw invalid("neutral description required");}
}
