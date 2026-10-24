package com.cinenest.booking.internal;

import com.cinenest.booking.*;
import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.rooms.InventoryConstraints;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.time.*;import java.util.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Synthetic coverage only. Admission commits independently; business transitions remain atomic. */
@Service
class ExtensionReservationService implements ExtensionReservationContext {
 static final String POLICY="SYNTHETIC_EXTENSION_RESERVATION_V1";
 private final JdbcClient jdbc;private final BookingAccess bookings;private final AmendmentGuards.Access access;private final ScopedAuthorization identity;
 private final InventoryConstraints rooms;private final ExtensionPricing pricing;private final Environment environment;private final TransactionTemplate tx,admissionTx;
 private final boolean enabled;private final int rate,seconds;private final String approval;
 ExtensionReservationService(JdbcClient jdbc,BookingAccess bookings,AmendmentGuards.Access access,ScopedAuthorization identity,InventoryConstraints rooms,ExtensionPricing pricing,Environment environment,PlatformTransactionManager manager,
  @Value("${cinenest.booking.extension-reservations.enabled:false}") boolean enabled,
  @Value("${cinenest.booking.extension-reservations.rate-count:0}") int rate,
  @Value("${cinenest.booking.extension-reservations.rate-seconds:0}") int seconds,
  @Value("${cinenest.booking.extension-reservations.approval-reference:}") String approval){
  this.jdbc=jdbc;this.bookings=bookings;this.access=access;this.identity=identity;this.rooms=rooms;this.pricing=pricing;this.environment=environment;
  this.enabled=enabled;this.rate=rate;this.seconds=seconds;this.approval=approval;tx=new TransactionTemplate(manager);tx.setTimeout(10);admissionTx=new TransactionTemplate(manager);admissionTx.setTimeout(10);admissionTx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
 }
 private void scoped(){if(!Set.of(environment.getActiveProfiles()).equals(Set.of("synthetic-quotation")))throw new InventoryFailure(503,"Synthetic extension reservations unavailable");}
 private void configured(){if(!enabled||rate!=5||seconds!=60||approval.isBlank()||!Set.of(environment.getActiveProfiles()).equals(Set.of("synthetic-quotation")))throw new InventoryFailure(503,"Synthetic extension reservations unavailable");}
 private java.util.function.Supplier<Instant> clock=this::databaseNow;
 private Instant now(){return clock.get();}
 private Instant databaseNow(){return jdbc.sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single().toInstant();}
 static Instant instant(Object v){return v instanceof String s?Instant.parse(s):v instanceof java.sql.Timestamp t?t.toInstant():((OffsetDateTime)v).toInstant();}
 private static Map<String,Object> parse(String text){try{return JSONObjectUtils.parse(text);}catch(Exception invalid){throw InventoryRules.invalid();}}
 private static UUID uuid(Map<String,Object> b,String key){return InventoryRules.id(InventoryRules.text(b,key));}
 private static String json(Map<String,Object> m){return JSONObjectUtils.toJSONString(m);}
 private static String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private String payload(String action,String booking,String reservation,String body){
  String value;try{var m=parse(body);value=json(new TreeMap<>(m));}catch(RuntimeException malformed){value=Objects.toString(body,"");}
  return hash(action+":"+booking+":"+reservation+":"+value);
 }
 private void eligible(UUID actor){if(actor==null)throw new InventoryFailure(401,"Session required");if(!identity.eligible(actor,true))throw new InventoryFailure(403,"Current eligibility required");}
 private Map<String,Object> basic(UUID actor,UUID booking){
  var b=jdbc.sql("SELECT * FROM cinenest_booking.booking WHERE booking_id=:b AND synthetic FOR UPDATE").param("b",booking).query().listOfRows().stream().findFirst().orElseThrow(()->new InventoryFailure(404,"Booking unavailable"));
  if(!actor.equals(b.get("owner_id")))throw new InventoryFailure(404,"Booking unavailable");return b;
 }
 private Map<String,Object> row(UUID booking,UUID reservation){return jdbc.sql("SELECT * FROM cinenest_booking.booking_extension_reservation WHERE booking_id=:b AND reservation_id=:r FOR UPDATE").param("b",booking).param("r",reservation).query().listOfRows().stream().findFirst().orElseThrow(()->new InventoryFailure(404,"Reservation unavailable"));}
 private void audit(UUID owner,UUID booking,UUID reservation,String action,String outcome,Long revision){jdbc.sql("INSERT INTO cinenest_booking.booking_extension_audit(event_id,owner_id,booking_id,reservation_id,action,outcome,reservation_revision,correlation_id) VALUES(:e,:o,:b,:r,:a,:outcome,:rev,:c)")
  .param("e",UUID.randomUUID()).param("o",owner,java.sql.Types.OTHER).param("b",booking,java.sql.Types.OTHER).param("r",reservation,java.sql.Types.OTHER).param("a",action).param("outcome",outcome).param("rev",revision,java.sql.Types.BIGINT).param("c",UUID.randomUUID()).update();}
 private Optional<Map<String,Object>> receipt(UUID actor,UUID key){return jdbc.sql("SELECT * FROM cinenest_booking.booking_extension_command WHERE owner_id=:a AND command_key=:k").param("a",actor).param("k",key).query().listOfRows().stream().findFirst();}
 private Map<String,Object> replay(Map<String,Object> receipt,String digest){if(!digest.equals(receipt.get("payload_hash")))throw new InventoryFailure(409,"Command key payload changed");return parse(receipt.get("result").toString());}
 record Admission(boolean admitted,Map<String,Object> replay,boolean changedReplay){}
 private Admission admit(UUID actor,String booking,String body,String digest){return admissionTx.execute(status->{
  eligible(actor);UUID key=null;try{key=uuid(parse(body),"commandKey");}catch(RuntimeException invalid){/* Invalid eligible requests still count. */}
  boolean changedReplay=false;
  if(key!=null){var old=receipt(actor,key);changedReplay=old.isPresent();if(old.isPresent()&&old.get().get("payload_hash").equals(digest)){
   var result=replay(old.get(),digest);audit(actor,UUID.fromString(result.get("bookingId").toString()),(UUID)old.get().get("reservation_id"),"REPLAY","ALLOW",null);return new Admission(true,result,false);
  }}
  long count=jdbc.sql("SELECT count(*) FROM cinenest_booking.booking_extension_audit WHERE owner_id=:a AND action='ADMISSION' AND occurred_at>clock_timestamp()-INTERVAL '60 seconds'").param("a",actor).query(Long.class).single();
  audit(actor,null,null,"ADMISSION",count<5?"ALLOW":"DENY",null);return new Admission(count<5,null,changedReplay);
 });}
 Object create(UUID actor,String rawBooking,String rawBody,boolean invalidQuery){
  configured();String digest=payload("CREATE",rawBooking,invalidQuery?"UNSUPPORTED_QUERY":null,rawBody);var admission=admit(actor,rawBooking,rawBody,digest);
  if(admission.replay()!=null)return admission.replay();if(!admission.admitted())throw new InventoryFailure(429,"Too many extension requests;retry later");
  if(admission.changedReplay())throw new InventoryFailure(409,"Command key payload changed");
  if(invalidQuery||rawBody==null||rawBody.length()>8192)throw InventoryRules.invalid();
  UUID booking=InventoryRules.id(rawBooking);var body=parse(rawBody);
  if(!body.keySet().equals(Set.of("additionalMinutes","expectedPurchasedRevision","expectedLifecycleVersion","expectedPackageVersionId","expectedAdditionalMinor","expectedCurrency","expectedConfiguration","commandKey","termsVersion","acknowledged")))throw InventoryRules.invalid();
  if(!Boolean.TRUE.equals(body.get("acknowledged"))||!POLICY.equals(body.get("termsVersion")))throw InventoryRules.invalid();
  long minutes=ExtensionPreviewService.integer(body,"additionalMinutes"),revision=ExtensionPreviewService.integer(body,"expectedPurchasedRevision"),life=ExtensionPreviewService.integer(body,"expectedLifecycleVersion");
  if(minutes<1||minutes>Integer.MAX_VALUE)throw InventoryRules.invalid();UUID key=uuid(body,"commandKey");
  return tx.execute(status->{
   access.lock(booking);eligible(actor);var base=basic(actor,booking);
   var old=receipt(actor,key);if(old.isPresent())return replay(old.get(),digest);
   var b=bookings.lock(booking).orElseThrow(()->new InventoryFailure(409,"Booking no longer eligible"));
   if(b.revision()!=1||revision!=1||life!=0||((Number)base.get("lifecycle_version")).longValue()!=0||!now().isBefore(b.start())||!access.neverIssued(booking)||bookings.pendingChange(booking)||jdbc.sql("SELECT EXISTS(SELECT 1 FROM cinenest_booking.booking_amendment WHERE booking_id=:b AND state='PROPOSED')").param("b",booking).query(Boolean.class).single())throw new InventoryFailure(409,"Booking no longer eligible");
   if(jdbc.sql("SELECT EXISTS(SELECT 1 FROM cinenest_booking.booking_extension_reservation WHERE owner_id=:a AND state='ACTIVE')").param("a",actor).query(Boolean.class).single())throw new InventoryFailure(409,"Existing extension reservation must be resolved");
   var purchase=parse(jdbc.sql("SELECT snapshot FROM cinenest_booking.booking_revision WHERE booking_id=:b AND revision=1").param("b",booking).query(String.class).single());
   rooms.lock(b.venue());var f=rooms.inspect(b.room()).orElseThrow(()->new InventoryFailure(409,"Room unavailable"));
   ExtensionPricing.Preview price;try{price=pricing.preview(purchase,(int)minutes);}catch(ExtensionPricing.Unsupported invalid){throw new InventoryFailure(409,"Frozen pricing unavailable");}
   if(!f.enabled()||!f.venueId().equals(b.venue())||!f.timezone().equals(b.timezone())||!f.configuration().equals(body.get("expectedConfiguration")))throw new InventoryFailure(409,"Room configuration changed");
   var c=InventoryRules.candidate(f,b.start(),price.totalMinutes(),Math.toIntExact(((Number)purchase.get("guests")).longValue()));
   var original=jdbc.sql("SELECT i.* FROM cinenest_booking.inventory_block i JOIN cinenest_booking.reservation_hold h USING(block_id) WHERE h.hold_id=:h AND h.state='CONSUMED' AND h.owner_id=:o FOR UPDATE OF i,h").param("h",UUID.fromString(purchase.get("holdId").toString())).param("o",actor).query().listOfRows().stream().findFirst().orElseThrow(()->new InventoryFailure(409,"Purchased inventory unavailable"));
   Instant until=instant(original.get("blocked_until"));
   if(!Boolean.TRUE.equals(original.get("blocking"))||!"CONFIRMED".equals(original.get("kind"))||!b.room().equals(original.get("room_id"))||!b.venue().equals(original.get("venue_id"))||!b.start().equals(instant(original.get("session_start")))||!b.end().equals(instant(original.get("session_end")))||!Objects.equals(f.bufferMinutes(),original.get("buffer_minutes"))||!Objects.equals(purchase.get("blockedUntil"),until.toString())||!c.blockedUntil().isAfter(until))throw new InventoryFailure(409,"Purchased inventory context changed");
   if(!price.packageVersion().equals(body.get("expectedPackageVersionId"))||!price.additionalMinor().equals(body.get("expectedAdditionalMinor"))||!price.currency().equals(body.get("expectedCurrency")))throw new InventoryFailure(409,"Price preview changed");
   if(jdbc.sql("SELECT EXISTS(SELECT 1 FROM cinenest_booking.inventory_block WHERE room_id=:r AND blocking AND tstzrange(session_start,blocked_until,'[)') && tstzrange(CAST(:s AS timestamptz),CAST(:e AS timestamptz),'[)'))").param("r",b.room()).param("s",until.atOffset(ZoneOffset.UTC)).param("e",c.blockedUntil().atOffset(ZoneOffset.UTC)).query(Boolean.class).single())throw new InventoryFailure(409,"Additional inventory unavailable");
   Instant created=now(),expires=created.plusSeconds(600);if(expires.isAfter(b.start()))expires=b.start();if(!created.isBefore(expires))throw new InventoryFailure(409,"Session already started");
   UUID reservation=UUID.randomUUID(),tail=UUID.randomUUID();
   jdbc.sql("INSERT INTO cinenest_booking.inventory_block(block_id,venue_id,room_id,session_start,session_end,buffer_minutes,blocked_until,blocking,kind) VALUES(:id,:v,:r,:s,:e,0,:e,true,'EXTENSION_HOLD')").param("id",tail).param("v",b.venue()).param("r",b.room()).param("s",until.atOffset(ZoneOffset.UTC)).param("e",c.blockedUntil().atOffset(ZoneOffset.UTC)).update();
   var quotation=Map.<String,Object>of("additionalMinor",price.additionalMinor(),"currency",price.currency(),"packageVersionId",price.packageVersion(),"pricingBasis","FROZEN_PURCHASED_TERMS","additionalMinutes",minutes,"termsVersion",POLICY,"acknowledged",true);
   jdbc.sql("""
    INSERT INTO cinenest_booking.booking_extension_reservation(reservation_id,booking_id,owner_id,venue_id,room_id,purchased_revision,lifecycle_version,original_block,tail_block,purchase_snapshot,quotation,configuration,timezone,guests,buffer_minutes,original_start,original_end,original_until,proposed_end,proposed_until,created_at,expires_at,policy_version,approval_reference,state)
    VALUES(:id,:b,:o,:v,:r,1,0,:original,:tail,CAST(:purchase AS jsonb),CAST(:q AS jsonb),:cfg,:tz,:guests,:buffer,:start,:end,:until,:newend,:newuntil,:created,:expires,:policy,:approval,'ACTIVE')
    """).param("id",reservation).param("b",booking).param("o",actor).param("v",b.venue()).param("r",b.room()).param("original",original.get("block_id")).param("tail",tail).param("purchase",json(purchase)).param("q",json(quotation)).param("cfg",f.configuration()).param("tz",f.timezone()).param("guests",purchase.get("guests")).param("buffer",f.bufferMinutes()).param("start",b.start().atOffset(ZoneOffset.UTC)).param("end",b.end().atOffset(ZoneOffset.UTC)).param("until",until.atOffset(ZoneOffset.UTC)).param("newend",c.end().atOffset(ZoneOffset.UTC)).param("newuntil",c.blockedUntil().atOffset(ZoneOffset.UTC)).param("created",created.atOffset(ZoneOffset.UTC)).param("expires",expires.atOffset(ZoneOffset.UTC)).param("policy",POLICY).param("approval",approval).update();
   audit(actor,booking,reservation,"CREATE","ALLOW",0L);var result=view(row(booking,reservation),f,now());command(actor,key,reservation,"CREATE",digest,result);
   if(!now().isBefore(expires)||!identity.eligible(actor,false)||!access.neverIssued(booking))throw new InventoryFailure(409,"Reservation eligibility changed");return result;
  });
 }
 private void command(UUID actor,UUID key,UUID reservation,String action,String digest,Map<String,Object> result){jdbc.sql("INSERT INTO cinenest_booking.booking_extension_command(owner_id,command_key,reservation_id,action,payload_hash,result) VALUES(:a,:k,:r,:action,:hash,CAST(:result AS jsonb))").param("a",actor).param("k",key).param("r",reservation).param("action",action).param("hash",digest).param("result",json(result)).update();}
 /** Committed reference only; absence never proves an earlier command cannot subsequently commit. */
 record Recovery(UUID bookingId,UUID reservationId,UUID commandKey,String commandType,String recoveryStatus,boolean statusReadRequired) {}
 Object recover(UUID actor,String rawBooking,String rawCommand){
  scoped();UUID booking=InventoryRules.id(rawBooking),key=InventoryRules.id(rawCommand);
  return tx.execute(status->{
   // Same booking serialization as CREATE; no admission, replay, expiry or lifecycle transition.
   access.lock(booking);eligible(actor);basic(actor,booking);
   var rows=jdbc.sql("SELECT e.reservation_id,e.revision FROM cinenest_booking.booking_extension_command c JOIN cinenest_booking.booking_extension_reservation e ON e.reservation_id=c.reservation_id WHERE c.owner_id=:a AND c.command_key=:k AND c.action='CREATE' AND e.owner_id=:a AND e.booking_id=:b")
    .param("a",actor).param("k",key).param("b",booking).query().listOfRows();
   if(rows.isEmpty())throw new InventoryFailure(404,"Recovery unresolved; no committed reference available. Original command outcome remains unknown");
   if(rows.size()!=1)throw new InventoryFailure(503,"Recovery unavailable");
   var row=rows.getFirst();UUID reservation=(UUID)row.get("reservation_id");
   audit(actor,booking,reservation,"READ","ALLOW",((Number)row.get("revision")).longValue());
   // Recheck within the existing Identity lock before disclosing the protected reference.
   if(!identity.eligible(actor,false))throw new InventoryFailure(403,"Current eligibility required");
   return new Recovery(booking,reservation,key,"CREATE","COMMITTED_REFERENCE",true);
  });
 }
 Object read(UUID actor,String rawBooking,String rawReservation){
  UUID booking=InventoryRules.id(rawBooking),reservation=InventoryRules.id(rawReservation);
  return tx.execute(status->{try{return response(observeOwned(actor,booking,reservation));}
   catch(ExtensionReservationContext.Rejected denied){throw new InventoryFailure(denied.status(),denied.getMessage());}});
 }
 @Override public Observation observeOwned(UUID owner,UUID booking,UUID reservation){
  if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Caller transaction required");
  try{
   scoped();if(owner==null)throw new InventoryFailure(401,"Session required");if(booking==null||reservation==null)throw InventoryRules.invalid();
   access.lock(booking);eligible(owner);var b=basic(owner,booking);rooms.lock((UUID)b.get("venue_id"));var r=row(booking,reservation);
   var f=rooms.inspect((UUID)r.get("room_id")).orElseThrow(()->new InventoryFailure(503,"Room unavailable"));
   audit(owner,booking,reservation,"READ","ALLOW",((Number)r.get("revision")).longValue());return observation(r,f,now());
  }catch(InventoryFailure denied){throw new ExtensionReservationContext.Rejected(denied.status,denied.getMessage());}
   catch(RuntimeException unavailable){throw new ExtensionReservationContext.Rejected(503,"Extension reservation unavailable;reload");}
 }
 Object withdraw(UUID actor,String rawBooking,String rawReservation,String rawBody){scoped();UUID booking=InventoryRules.id(rawBooking),reservation=InventoryRules.id(rawReservation);var body=parse(rawBody);if(!body.keySet().equals(Set.of("expectedRevision","commandKey")))throw InventoryRules.invalid();UUID key=uuid(body,"commandKey");long revision=ExtensionPreviewService.integer(body,"expectedRevision");String digest=payload("WITHDRAW",rawBooking,rawReservation,rawBody);
  return tx.execute(status->{access.lock(booking);eligible(actor);var b=basic(actor,booking);var old=receipt(actor,key);if(old.isPresent())return replay(old.get(),digest);rooms.lock((UUID)b.get("venue_id"));var r=row(booking,reservation);
   if(((Number)r.get("revision")).longValue()!=revision)throw new InventoryFailure(409,"Reservation changed");
   if("ACTIVE".equals(r.get("state")))finish(r,!now().isBefore(instant(r.get("expires_at")))?"EXPIRED":"WITHDRAWN",actor);
   r=row(booking,reservation);var f=rooms.inspect((UUID)r.get("room_id")).orElseThrow(()->new InventoryFailure(503,"Room unavailable"));var result=view(r,f,now());command(actor,key,reservation,"WITHDRAW",digest,result);return result;
  });
 }
 private void finish(Map<String,Object> r,String state,UUID actor){
  if(jdbc.sql("UPDATE cinenest_booking.inventory_block SET blocking=false,revision=revision+1 WHERE block_id=:b AND kind='EXTENSION_HOLD' AND blocking").param("b",r.get("tail_block")).update()!=1)throw new InventoryFailure(503,"Extension inventory inconsistent");
  jdbc.sql("UPDATE cinenest_booking.booking_extension_reservation SET state=:s,revision=revision+1 WHERE reservation_id=:r AND state='ACTIVE'").param("s",state).param("r",r.get("reservation_id")).update();
  audit(actor,(UUID)r.get("booking_id"),(UUID)r.get("reservation_id"),state,"ALLOW",((Number)r.get("revision")).longValue()+1);
 }
 void maintenance(){if(!Set.of(environment.getActiveProfiles()).equals(Set.of("synthetic-quotation")))return;for(var candidate:jdbc.sql("SELECT booking_id,reservation_id,owner_id FROM cinenest_booking.booking_extension_reservation WHERE state='ACTIVE' ORDER BY expires_at,reservation_id LIMIT 100").query().listOfRows())tx.executeWithoutResult(status->{
  UUID booking=(UUID)candidate.get("booking_id");access.lock(booking);
  var b=jdbc.sql("SELECT venue_id FROM cinenest_booking.booking WHERE booking_id=:b FOR UPDATE").param("b",booking).query(UUID.class).single();rooms.lock(b);var r=row(booking,(UUID)candidate.get("reservation_id"));if(!"ACTIVE".equals(r.get("state")))return;
  var f=rooms.inspect((UUID)r.get("room_id")).orElseThrow(()->new InventoryFailure(503,"Room unavailable"));Instant time=now();
  if(!time.isBefore(instant(r.get("expires_at"))))finish(r,"EXPIRED",null);else if(!valid(r,f))finish(r,"INVALIDATED",null);
 });}
 static boolean valid(Map<String,Object> r,InventoryConstraints.Facts f){try{
  var c=InventoryRules.candidate(f,instant(r.get("original_start")),Math.toIntExact(Duration.between(instant(r.get("original_start")),instant(r.get("proposed_end"))).toMinutes()),((Number)r.get("guests")).intValue());
  return f.enabled()&&f.configuration().equals(r.get("configuration"))&&f.timezone().equals(r.get("timezone"))&&f.bufferMinutes()!=null&&f.bufferMinutes().intValue()==((Number)r.get("buffer_minutes")).intValue()&&c.blockedUntil().equals(instant(r.get("proposed_until")));
 }catch(RuntimeException invalid){return false;}}
 private Map<String,Object> view(Map<String,Object> r,InventoryConstraints.Facts f,Instant time){return response(observation(r,f,time));}
 private Observation observation(Map<String,Object> r,InventoryConstraints.Facts f,Instant time){
  boolean active="ACTIVE".equals(r.get("state")),expired=!time.isBefore(instant(r.get("expires_at"))),invalid=!valid(r,f);var q=parse(r.get("quotation").toString());
  return new Observation((UUID)r.get("booking_id"),(UUID)r.get("reservation_id"),(UUID)r.get("venue_id"),
   ((Number)r.get("purchased_revision")).longValue(),((Number)r.get("lifecycle_version")).longValue(),((Number)r.get("revision")).longValue(),
   State.valueOf(r.get("state").toString()),r.get("configuration").toString(),
   new Window(instant(r.get("original_start")),instant(r.get("original_end")),instant(r.get("original_until"))),
   new Window(instant(r.get("original_start")),instant(r.get("proposed_end")),instant(r.get("proposed_until"))),
   new Coverage(instant(r.get("original_until")),instant(r.get("proposed_until"))),Long.parseLong(q.get("additionalMinor").toString()),
   q.get("currency").toString(),UUID.fromString(q.get("packageVersionId").toString()),q.get("pricingBasis").toString(),
   Math.toIntExact(((Number)q.get("additionalMinutes")).longValue()),q.get("termsVersion").toString(),
   instant(r.get("created_at")),instant(r.get("expires_at")),time,active&&!expired&&!invalid,active&&(expired||invalid),
   active&&(expired||invalid)?List.of("PENDING_RECONCILIATION"):List.of());
 }
 private static Map<String,Object> window(Window w){return Map.of("start",w.start().toString(),"end",w.end().toString(),"blockedUntil",w.blockedUntil().toString());}
 private static Map<String,Object> response(Observation o){
  var out=new LinkedHashMap<String,Object>();out.put("bookingId",o.bookingId());out.put("reservationId",o.reservationId());
  out.put("purchasedRevision",o.purchasedRevision());out.put("proposalRevision",o.proposalRevision());out.put("state",o.state().name());
  out.put("originalSession",window(o.originalSession()));out.put("proposedSession",window(o.proposedSession()));
  out.put("additionalCoverage",Map.of("start",o.additionalCoverage().start().toString(),"end",o.additionalCoverage().end().toString()));
  out.put("additionalMinor",Long.toString(o.additionalMinor()));out.put("currency",o.currency());out.put("packageVersionId",o.packageVersionId().toString());
  out.put("pricingBasis",o.pricingBasis());out.put("additionalMinutes",o.additionalMinutes());out.put("termsVersion",o.termsVersion());
  out.put("createdAt",o.createdAt().toString());out.put("expiresAt",o.expiresAt().toString());out.put("observedAt",o.observedAt().toString());
  out.put("effective",o.effective());out.put("pendingReconciliation",o.pendingReconciliation());out.put("reasons",o.reasons());
  out.put("bookingChanged",false);out.put("paymentRequested",false);out.put("occupancy","UNKNOWN");out.put("readiness","UNVERIFIED");
  out.put("synthetic",true);out.put("warning","Temporary synthetic extension coverage only. Original booking unchanged. No payment requested or extension confirmed.");return out;
 }
}
