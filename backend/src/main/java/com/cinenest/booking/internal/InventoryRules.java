package com.cinenest.booking.internal;

import com.cinenest.rooms.InventoryConstraints;
import java.time.*;
import java.util.*;

/** Pure interval checks; neither prices nor physical readiness are inferred. */
final class InventoryRules {
    record Candidate(Instant start, Instant end, Instant blockedUntil, int buffer, String policyIds) {}
    private InventoryRules() {}
    static InventoryFailure invalid() { return new InventoryFailure(400,"Invalid inventory request"); }
    static void keys(Map<String,Object> body,String... allowed) { if(!Set.of(allowed).containsAll(body.keySet()))throw invalid(); }
    static String text(Map<String,Object> body,String field) {
        if(!(body.get(field) instanceof String s) || s.isBlank() || s.length()>2048)throw invalid();return s;
    }
    static UUID id(String value) { try {UUID id=UUID.fromString(value);if(!id.toString().equals(value))throw invalid();return id;}catch(IllegalArgumentException e){throw invalid();} }
    static int positive(Map<String,Object> body,String field) {
        if(!(body.get(field) instanceof Integer n) || n<=0)throw invalid();return n;
    }
    static long revision(Map<String,Object> body) {
        Object n=body.get("expectedRevision");if(!(n instanceof Integer || n instanceof Long) || ((Number)n).longValue()<0)throw invalid();return ((Number)n).longValue();
    }
    static Instant expires(Instant now,Instant sessionStart,int ttl) {
        if(ttl<=0 || !sessionStart.isAfter(now))throw invalid();
        Instant limit=now.plusSeconds(ttl);return limit.isAfter(sessionStart)?sessionStart:limit;
    }
    static Instant local(String value,String timezone) {
        try {
            LocalDateTime time=LocalDateTime.parse(value);ZoneId zone=ZoneId.of(timezone);
            if(time.getSecond()!=0 || time.getNano()!=0)throw invalid();
            var offsets=zone.getRules().getValidOffsets(time);if(offsets.size()!=1)throw invalid();
            return time.toInstant(offsets.getFirst());
        } catch(DateTimeException e) {throw invalid();}
    }
    static Candidate candidate(InventoryConstraints.Facts f,Instant start,int duration,int guests) {
        if(!f.enabled() || f.bufferMinutes()==null || f.bufferMinutes()<0 || f.minimums().isEmpty())
            throw new InventoryFailure(409,"Room configuration unavailable");
        if(duration<=0 || guests<=0 || guests>f.capacity())throw invalid();
        try {
            Instant end=start.plusSeconds(Math.multiplyExact((long)duration,60));
            Instant until=end.plusSeconds(Math.multiplyExact((long)f.bufferMinutes(),60));
            var zone=ZoneId.of(f.timezone());var date=start.atZone(zone).toLocalDate();
            var window=f.windows().stream().filter(w->w.weekday()==date.getDayOfWeek().getValue()).findFirst().orElseThrow(InventoryRules::invalid);
            Instant opening=local(date.atStartOfDay().plusMinutes(window.openingMinute()).toString(),f.timezone());
            Instant closing=local(date.atStartOfDay().plusMinutes(window.closingMinute()).toString(),f.timezone());
            if(start.isBefore(opening) || end.isAfter(closing) || !f.postCloseApproved() && until.isAfter(closing))throw invalid();
            var first=f.minimums().stream().filter(m->!m.effectiveFrom().isAfter(start)).reduce((a,b)->b).orElseThrow(()->new InventoryFailure(409,"Minimum policy unavailable"));
            var applicable=new ArrayList<InventoryConstraints.Minimum>();applicable.add(first);
            f.minimums().stream().filter(m->m.effectiveFrom().isAfter(start) && m.effectiveFrom().isBefore(end)).forEach(applicable::add);
            if(applicable.stream().anyMatch(m->duration<m.minutes()))throw new InventoryFailure(409,"Session violates minimum policy");
            return new Candidate(start,end,until,f.bufferMinutes(),applicable.stream().map(m->m.id().toString()).reduce((a,b)->a+","+b).orElseThrow());
        } catch(DateTimeException|ArithmeticException e) {throw invalid();}
    }
}
