package com.cinenest.booking.internal;

import com.cinenest.booking.*;
import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.rooms.InventoryConstraints;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Serialized observation only. No reconciliation, quote issuance, receipts or domain writes. */
@Service
class ExtensionPreviewService {
    static final String WARNING="Indicative synthetic extension preview. No additional time is reserved. No payment has been requested. Your booking has not changed.";
    private final JdbcClient jdbc;private final ScopedAuthorization identity;private final AmendmentGuards.Access access;
    private final BookingAccess bookings;private final ExtensionPricing pricing;private final InventoryConstraints rooms;
    private final Environment environment;private final TransactionTemplate tx;
    ExtensionPreviewService(JdbcClient jdbc,ScopedAuthorization identity,AmendmentGuards.Access access,BookingAccess bookings,ExtensionPricing pricing,InventoryConstraints rooms,Environment environment,PlatformTransactionManager manager){
        this.jdbc=jdbc;this.identity=identity;this.access=access;this.bookings=bookings;this.pricing=pricing;this.rooms=rooms;this.environment=environment;
        tx=new TransactionTemplate(manager);tx.setTimeout(10);
    }
    static long integer(Map<String,Object> input,String key){Object v=input.get(key);if(!(v instanceof Integer||v instanceof Long)||((Number)v).longValue()<0)throw InventoryRules.invalid();return ((Number)v).longValue();}
    Object read(UUID actor,UUID booking,Map<String,Object> input){
        if(!Set.of(environment.getActiveProfiles()).equals(Set.of("synthetic-quotation")))throw new InventoryFailure(503,"Synthetic extension preview unavailable");
        if(input!=null&&(!input.keySet().equals(Set.of("additionalMinutes","expectedPurchasedRevision","expectedLifecycleVersion"))||integer(input,"additionalMinutes")<1||integer(input,"additionalMinutes")>Integer.MAX_VALUE))throw InventoryRules.invalid();
        return tx.execute(status->{
            // Same serialization order as cancellation and issuance. No new extension proposals exist in this schema.
            access.lock(booking);
            var base=jdbc.sql("SELECT owner_id,venue_id,state,lifecycle_version FROM cinenest_booking.booking WHERE booking_id=:b AND synthetic").param("b",booking).query().listOfRows().stream().findFirst().orElseThrow(()->new InventoryFailure(404,"Booking unavailable"));
            if(!actor.equals(base.get("owner_id")))throw new InventoryFailure(404,"Booking unavailable");
            if(!identity.eligible(actor,true))throw new InventoryFailure(403,"Current eligibility required");
            var b=bookings.lock(booking).orElse(null);
            long revision=jdbc.sql("SELECT max(revision) FROM cinenest_booking.booking_revision WHERE booking_id=:b").param("b",booking).query(Long.class).single();
            long lifecycle=((Number)base.get("lifecycle_version")).longValue();
            if(input!=null&&(integer(input,"expectedPurchasedRevision")!=revision||integer(input,"expectedLifecycleVersion")!=lifecycle))throw new InventoryFailure(409,"Booking version changed");
            String reason=b==null?"BOOKING_NOT_CONFIRMED":revision!=1||lifecycle!=0?"PURCHASE_REVISION_UNSUPPORTED":
                bookings.extensionPending(booking)?"EXTENSION_PENDING":!now().isBefore(b.start())?"SESSION_STARTED":!access.neverIssued(booking)?"CREDENTIAL_EVER_ISSUED":
                jdbc.sql("SELECT EXISTS(SELECT 1 FROM cinenest_booking.booking_amendment WHERE booking_id=:b AND state='PROPOSED')").param("b",booking).query(Boolean.class).single()?"AMENDMENT_PENDING":"ELIGIBLE";
            ExtensionPricing.Preview price=null;Map<String,Object> purchase=null;
            if(reason.equals("ELIGIBLE")){
                try{purchase=JSONObjectUtils.parse(jdbc.sql("SELECT snapshot FROM cinenest_booking.booking_revision WHERE booking_id=:b AND revision=1").param("b",booking).query(String.class).single());}
                catch(java.text.ParseException invalid){throw new InventoryFailure(503,"Purchased context unavailable");}
                rooms.lock(b.venue());var f=rooms.inspect(b.room()).orElseThrow(()->new InventoryFailure(409,"Room context unavailable"));
                if(!f.enabled()||!f.venueId().equals(b.venue())||!f.timezone().equals(b.timezone()))reason="ROOM_CONTEXT_CHANGED";
                if(reason.equals("ELIGIBLE"))try{
                    int step=Math.toIntExact(((Number)((Map<?,?>)purchase.get("package")).get("extensionStepMinutes")).longValue());
                    price=pricing.preview(purchase,input==null?step:Math.toIntExact(integer(input,"additionalMinutes")));
                    InventoryRules.candidate(f,b.start(),price.totalMinutes(),Math.toIntExact(((Number)purchase.get("guests")).longValue()));
                }catch(ExtensionPricing.Unsupported|ArithmeticException|ClassCastException|NullPointerException unsupported){reason="FROZEN_PRICING_UNSUPPORTED";}
                catch(InventoryFailure invalid){reason="ROOM_CONSTRAINTS_UNSATISFIED";}
            }
            if(b!=null&&!now().isBefore(b.start()))reason="SESSION_STARTED";
            if(!identity.eligible(actor,false))throw new InventoryFailure(403,"Current eligibility required");
            if(input!=null&&!reason.equals("ELIGIBLE"))throw new InventoryFailure(409,"Extension preview unavailable;reload eligibility");
            var result=new LinkedHashMap<String,Object>();result.put("bookingId",booking);result.put("purchasedRevision",revision);result.put("lifecycleVersion",lifecycle);
            result.put("eligible",reason.equals("ELIGIBLE"));result.put("reason",reason);result.put("observedAt",now().toString());result.put("synthetic",true);
            result.put("warning",WARNING);result.put("timeReserved",false);result.put("paymentRequested",false);result.put("bookingChanged",false);
            result.put("occupancy","UNKNOWN");result.put("readiness","UNVERIFIED");
            if(reason.equals("ELIGIBLE")){
                result.put("durationUnits",Map.of("incrementMinutes",price.incrementMinutes(),"minimumAdditionalMinutes",price.incrementMinutes(),"maximumAdditionalMinutes",price.maximumAdditionalMinutes()));
                if(input!=null){result.put("currentDurationMinutes",price.currentMinutes());result.put("additionalMinutes",price.additionalMinutes());result.put("proposedDurationMinutes",price.totalMinutes());result.put("additionalMinor",price.additionalMinor());result.put("currency",price.currency());result.put("packageVersionId",price.packageVersion());result.put("pricingBasis","FROZEN_PURCHASED_TERMS");}
            }
            return result;
        });
    }
    private Instant now(){return jdbc.sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single().toInstant();}
}
