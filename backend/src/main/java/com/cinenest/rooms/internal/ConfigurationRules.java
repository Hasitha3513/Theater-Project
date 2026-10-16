package com.cinenest.rooms.internal;

import java.time.*;
import java.util.*;

/** Pure configuration constraints, not booking or physical-readiness rules. */
final class ConfigurationRules {
    private ConfigurationRules() {}
    static RoomFailure invalid(String field) { return new RoomFailure(400,"Invalid field: "+field); }
    static void keys(Map<String,Object> input, String... permitted) {
        if (!Set.of(permitted).containsAll(input.keySet())) throw invalid("unknown property");
    }
    static String text(Map<String,Object> input,String field,int max,boolean required) {
        Object raw=input.get(field);
        if(raw==null && !required)return "";
        if(!(raw instanceof String))throw invalid(field);
        String value=((String)raw).trim();
        if(value.length()>max || required && value.isEmpty() || value.codePoints().anyMatch(c -> Character.isISOControl(c)))throw invalid(field);
        return value;
    }
    static String code(Map<String,Object> input,String field) {
        String value=text(input,field,64,true).toUpperCase(Locale.ROOT);
        if(!value.matches("[A-Z0-9_-]+"))throw invalid(field);return value;
    }
    static int integer(Map<String,Object> input,String field,int min,int max) {
        Object value=input.get(field);
        if(!(value instanceof Integer) || (int)value<min || (int)value>max)throw invalid(field);return (int)value;
    }
    static long version(Map<String,Object> input) {
        Object v=input.get("expectedVersion");
        if(!(v instanceof Integer || v instanceof Long) || ((Number)v).longValue()<0)throw invalid("expectedVersion");return ((Number)v).longValue();
    }
    static boolean flag(Map<String,Object> input,String field) {
        if(!input.containsKey(field))return false;
        if(!(input.get(field) instanceof Boolean b))throw invalid(field);return (Boolean)input.get(field);
    }
    static UUID uuid(String value) {
        try { UUID id=UUID.fromString(value);if(!id.toString().equals(value))throw invalid("id");return id; }
        catch(IllegalArgumentException e){throw invalid("id");}
    }
    @SuppressWarnings("unchecked")
    static List<Map<String,Object>> objects(Map<String,Object> input,String key) {
        Object raw=input.getOrDefault(key,List.of());
        if(!(raw instanceof List<?> values) || values.size()>100)throw invalid(key);
        List<Map<String,Object>> result=new ArrayList<>();
        for(Object value:(List<?>)raw) {
            if(!(value instanceof Map<?,?>))throw invalid(key);result.add((Map<String,Object>)value);
        }
        return result;
    }
    static ZoneId zone(String zone) {
        if(!ZoneId.getAvailableZoneIds().contains(zone))throw invalid("timezone");return ZoneId.of(zone);
    }
    static void neutral(String text) {
        if(text.toLowerCase(Locale.ROOT).matches(".*(netflix|disney|unlimited streaming|licensed movies|screening rights|gaming content).*"))throw invalid("neutral physical description required");
    }
    static ZonedDateTime local(LocalDate date,int minute,String zone) {
        if(minute<0 || minute>1440)throw invalid("minute");
        LocalDateTime time=date.atStartOfDay().plusMinutes(minute);ZoneId id=zone(zone);
        var offsets=id.getRules().getValidOffsets(time);
        if(offsets.size()!=1)throw invalid("ambiguous/nonexistent local time");
        return ZonedDateTime.ofStrict(time,offsets.getFirst(),id);
    }
    static Instant blockedUntil(Instant start,int duration,int buffer) {
        if(duration<=0 || buffer<0)throw invalid("interval");
        try{return start.plusSeconds(Math.multiplyExact(Math.addExact((long)duration,buffer),60));}
        catch(ArithmeticException|DateTimeException e){throw invalid("interval");}
    }
}
