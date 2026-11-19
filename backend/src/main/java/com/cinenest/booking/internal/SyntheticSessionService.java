package com.cinenest.booking.internal;

import com.cinenest.identity.ScopedAuthorization;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
class SyntheticSessionService {
    private static final String TABLE="cinenest_booking.synthetic_staff_session_event";
    private static final String POLICY="SYNTHETIC-SESSION-V1";
    private final JdbcClient jdbc;
    private final BookingAccessAdapter bookings;
    private final GuestAdmissionService admission;
    private final GuestRosterStore rosters;
    private final ScopedAuthorization auth;
    private final Environment environment;
    private final boolean enabled;

    SyntheticSessionService(JdbcClient jdbc, BookingAccessAdapter bookings, GuestAdmissionService admission,
            GuestRosterStore rosters, ScopedAuthorization auth, Environment environment,
            @Value("${cinenest.booking.staff-session.enabled:false}") boolean enabled) {
        this.jdbc=jdbc;this.bookings=bookings;this.admission=admission;this.rosters=rosters;
        this.auth=auth;this.environment=environment;this.enabled=enabled;
    }
    record Event(UUID eventId,UUID bookingId,long bookingRevision,long rosterRevision,UUID venueId,UUID roomId,
                 UUID checkInActorId,UUID checkInCommandKey,UUID actorId,String eventType,
                 UUID startEventId,Instant recordedAt,boolean lateCheckout) {}
    private ScopedAuthorization.MatchedGrant permit(UUID actor,UUID venue,String action) {
        if(!enabled||!Set.of(environment.getActiveProfiles()).equals(Set.of("synthetic-quotation")))
            throw new InventoryFailure(503,"Synthetic session recording unavailable");
        return auth.matchedGrant(actor,venue,"booking.session.synthetic."+action,true)
            .orElseThrow(()->new InventoryFailure(403,"Session authority required"));
    }
    private com.cinenest.booking.BookingAccess.Context current(UUID venue,UUID booking) {
        return bookings.lock(booking).filter(b->b.venue().equals(venue))
            .orElseThrow(()->new InventoryFailure(404,"Current confirmed booking unavailable"));
    }
    public Map<String,Object> read(UUID actor,UUID venue,UUID booking) {
        permit(actor,venue,"read");
        var b=current(venue,booking);
        var events=jdbc.sql("SELECT * FROM "+TABLE+" WHERE booking_id=:b ORDER BY recorded_at,event_id")
            .param("b",booking).query((rs,n)->event(rs,b.end())).list();
        var out=new LinkedHashMap<String,Object>();
        out.put("bookingId",booking);out.put("venueId",venue);out.put("roomId",b.room());
        out.put("bookingRevision",b.revision());out.put("rosterRevision",rosters.revision(booking).orElse(null));
        var checkIn=jdbc.sql("SELECT actor_id,command_key FROM cinenest_booking.synthetic_staff_check_in WHERE booking_id=:b AND booking_revision=:r AND roster_revision=:rr")
            .param("b",booking).param("r",b.revision()).param("rr",rosters.revision(booking).orElse(-1L))
            .query((rs,n)->Map.of("actorId",rs.getObject("actor_id",UUID.class),"commandKey",rs.getObject("command_key",UUID.class))).optional();
        boolean started=events.stream().anyMatch(e->e.eventType().equals("START"));
        boolean ended=events.stream().anyMatch(e->e.eventType().equals("CHECKOUT"));
        Instant now=jdbc.sql("SELECT clock_timestamp()").query((rs,n)->rs.getTimestamp(1).toInstant()).single();
        boolean startAuthority=auth.permits(actor,venue,"booking.session.synthetic.start",false)
            && auth.permits(actor,venue,"booking.synthetic.read",false)
            && auth.permits(actor,venue,"guest.admission.synthetic.read",false);
        boolean startAvailable=!started && checkIn.isPresent() && within(now,b.start(),b.end()) && startAuthority
            && admission.read(actor,venue,booking).status()==GuestAdmissionService.Status.ELIGIBLE_FOR_STAFF_REVIEW;
        out.put("startAvailable",startAvailable);
        out.put("checkoutAvailable",started&&!ended&&auth.permits(actor,venue,"booking.session.synthetic.checkout",false));
        out.put("checkIn",checkIn.orElse(null));
        out.put("events",events);out.put("synthetic",true);out.put("physicalOccupancy","UNKNOWN");
        out.put("physicalReadiness","UNVERIFIED");out.put("inventoryChanged",false);
        permit(actor,venue,"read");
        return out;
    }
    public Event record(UUID actor,UUID venue,UUID booking,String type,Map<String,Object> input) {
        String action=switch(type){case "START"->"start";case "CHECKOUT"->"checkout";default->throw InventoryRules.invalid();};
        var grant=permit(actor,venue,action);
        Set<String> fields=new HashSet<>(Set.of("commandKey","bookingRevision","rosterRevision","checkInActorId","checkInCommandKey"));
        if(type.equals("CHECKOUT"))fields.add("startEventId");
        if(!input.keySet().equals(fields))throw InventoryRules.invalid();
        UUID key=id(input,"commandKey"),checkActor=id(input,"checkInActorId"),checkKey=id(input,"checkInCommandKey");
        long revision=number(input.get("bookingRevision")),roster=number(input.get("rosterRevision"));
        UUID start=type.equals("CHECKOUT")?id(input,"startEventId"):null;
        String payload=venue+":"+booking+":"+type+":"+revision+":"+roster+":"+checkActor+":"+checkKey+":"+start;
        String hash=hash(payload);
        var b=current(venue,booking);
        var old=jdbc.sql("SELECT * FROM "+TABLE+" WHERE actor_id=:a AND command_key=:k")
            .param("a",actor).param("k",key).query().listOfRows().stream().findFirst();
        if(old.isPresent()){
            if(!hash.equals(old.get().get("payload_sha256").toString().trim()))throw new InventoryFailure(409,"Command key conflicts");
            permit(actor,venue,action);
            return fromRow(old.get(),b.end());
        }
        if(b.revision()!=revision||rosters.revision(booking).orElse(-1L)!=roster)
            throw new InventoryFailure(409,"Current revision required");
        if(type.equals("START")){
            var assessment=admission.read(actor,venue,booking);
            if(assessment.status()!=GuestAdmissionService.Status.ELIGIBLE_FOR_STAFF_REVIEW)
                throw new InventoryFailure(409,"Current guest checks required");
        }else{
            boolean matches=jdbc.sql("SELECT EXISTS(SELECT 1 FROM "+TABLE+" WHERE event_id=:e AND booking_id=:b AND booking_revision=:r AND event_type='START')")
                .param("e",start).param("b",booking).param("r",revision).query(Boolean.class).single();
            if(!matches)throw new InventoryFailure(409,"Matching START required");
        }
        grant=permit(actor,venue,action);
        UUID event=UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO cinenest_booking.synthetic_staff_session_event
            (event_id,booking_id,booking_revision,roster_revision,venue_id,room_id,check_in_actor_id,
             check_in_command_key,actor_id,command_key,payload_sha256,policy_version,
             authorization_grant_id,authorization_grant_revision,event_type,start_event_id)
            VALUES(:e,:b,:r,:rr,:v,:room,:ca,:ck,:a,:k,:hash,:policy,:g,:gr,:type,:start)
            """).param("e",event).param("b",booking).param("r",revision).param("rr",roster).param("v",venue)
            .param("room",b.room()).param("ca",checkActor).param("ck",checkKey).param("a",actor).param("k",key)
            .param("hash",hash).param("policy",POLICY).param("g",grant.grantId()).param("gr",grant.grantRevision())
            .param("type",type).param("start",start,java.sql.Types.OTHER).update();
        return jdbc.sql("SELECT * FROM "+TABLE+" WHERE event_id=:e").param("e",event)
            .query((rs,n)->event(rs,b.end())).single();
    }
    private static UUID id(Map<String,Object> v,String field){return InventoryRules.id(InventoryRules.text(v,field));}
    private static long number(Object v){
        if((v instanceof Integer||v instanceof Long)&&((Number)v).longValue()>=0)return ((Number)v).longValue();
        throw InventoryRules.invalid();
    }
    static boolean within(Instant now,Instant start,Instant end){return !now.isBefore(start)&&now.isBefore(end);}
    private static Event event(java.sql.ResultSet r,Instant end)throws java.sql.SQLException{
        Instant at=r.getTimestamp("recorded_at").toInstant();String type=r.getString("event_type");
        return new Event(r.getObject("event_id",UUID.class),r.getObject("booking_id",UUID.class),
            r.getLong("booking_revision"),r.getLong("roster_revision"),r.getObject("venue_id",UUID.class),
            r.getObject("room_id",UUID.class),r.getObject("check_in_actor_id",UUID.class),
            r.getObject("check_in_command_key",UUID.class),r.getObject("actor_id",UUID.class),type,
            r.getObject("start_event_id",UUID.class),at,type.equals("CHECKOUT")&&!at.isBefore(end));
    }
    private static Event fromRow(Map<String,Object> r,Instant end){
        Instant at=((java.sql.Timestamp)r.get("recorded_at")).toInstant();String type=r.get("event_type").toString();
        return new Event((UUID)r.get("event_id"),(UUID)r.get("booking_id"),((Number)r.get("booking_revision")).longValue(),
            ((Number)r.get("roster_revision")).longValue(),(UUID)r.get("venue_id"),(UUID)r.get("room_id"),
            (UUID)r.get("check_in_actor_id"),(UUID)r.get("check_in_command_key"),(UUID)r.get("actor_id"),type,
            (UUID)r.get("start_event_id"),at,type.equals("CHECKOUT")&&!at.isBefore(end));
    }
    private static String hash(String value){
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
