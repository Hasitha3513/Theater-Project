package com.cinenest.booking.internal;
import com.cinenest.booking.*;
import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.notifications.NotificationFacts;
import java.time.*;import java.util.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
/** One synthetic, zero-difference, pre-credential revision. No financial mutations. */
@Service
class AmendmentService {
 private final JdbcClient jdbc;private final BookingAccess bookings;private final AmendmentGuards.Access access;private final AmendmentGuards.Pricing pricing;private final AmendmentGuards.Funding funding;
 private final ScopedAuthorization identity;private final QuotationHolds holds;private final InventoryRepository inventory;private final TransactionTemplate tx;private final NotificationFacts notifications;
 private final Environment environment;private final boolean enabled;
 AmendmentService(JdbcClient jdbc,BookingAccess bookings,AmendmentGuards.Access access,AmendmentGuards.Pricing pricing,AmendmentGuards.Funding funding,ScopedAuthorization identity,QuotationHolds holds,InventoryRepository inventory,PlatformTransactionManager transactions,NotificationFacts notifications,Environment environment,@Value("${cinenest.booking.amendments.enabled:false}") boolean enabled){this.jdbc=jdbc;this.bookings=bookings;this.access=access;this.pricing=pricing;this.funding=funding;this.identity=identity;this.holds=holds;this.inventory=inventory;this.tx=new TransactionTemplate(transactions);this.notifications=notifications;this.environment=environment;this.enabled=enabled;}
 private void configured(){if(!enabled||!Set.of(environment.getActiveProfiles()).equals(Set.of("synthetic-quotation")))throw new InventoryFailure(503,"Synthetic amendments unavailable; real operations disabled");}
 private static UUID id(Map<String,Object> m,String k){return InventoryRules.id(InventoryRules.text(m,k));}
 private static long number(Map<String,Object> m,String k){Object n=m.get(k);if(!(n instanceof Integer||n instanceof Long)||((Number)n).longValue()<0)throw InventoryRules.invalid();return ((Number)n).longValue();}
 private static Instant instant(Object v){return v instanceof java.sql.Timestamp t?t.toInstant():((OffsetDateTime)v).toInstant();}
 private static String json(Map<String,Object> m){return com.nimbusds.jose.util.JSONObjectUtils.toJSONString(m);}
 private static Map<String,Object> parse(Object text){try{return com.nimbusds.jose.util.JSONObjectUtils.parse(text.toString());}catch(java.text.ParseException e){throw new IllegalStateException("Invalid purchased snapshot");}}
 private static String digest(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private BookingAccess.Context owner(UUID actor,UUID booking){if(!identity.eligible(actor,true))throw new InventoryFailure(403,"Access denied");var b=bookings.lock(booking).orElseThrow(()->new InventoryFailure(404,"Booking not found"));if(!actor.equals(b.owner()))throw new InventoryFailure(404,"Booking not found");return b;}
 private void eligible(BookingAccess.Context b){if(b.revision()!=1||!inventory.now().isBefore(b.start())||!access.neverIssued(b.booking())||!funding.eligible(b))throw new InventoryFailure(409,"Booking is not eligible for this synthetic reschedule");}
 private Map<String,Object> row(UUID booking,UUID amendment){return jdbc.sql("SELECT * FROM cinenest_booking.booking_amendment WHERE booking_id=:b AND amendment_id=:a FOR UPDATE").param("b",booking).param("a",amendment).query((rs,n)->{var meta=rs.getMetaData();var row=new LinkedHashMap<String,Object>();for(int i=1;i<=meta.getColumnCount();i++)row.put(meta.getColumnLabel(i),rs.getObject(i));return row;}).optional().orElseThrow(()->new InventoryFailure(404,"Amendment not found"));}
 private void audit(UUID actor,UUID booking,UUID amendment,String action){jdbc.sql("INSERT INTO cinenest_booking.booking_amendment_audit VALUES(:e,:actor,:b,:a,:action,'APPLIED',clock_timestamp())").param("e",UUID.randomUUID()).param("actor",actor,java.sql.Types.OTHER).param("b",booking).param("a",amendment).param("action",action).update();}
 private void finish(Map<String,Object> a,String state,UUID actor){UUID target=(UUID)a.get("target_hold");var h=inventory.hold(target).orElseThrow();holds.lockAndInspect((UUID)a.get("owner_id"),target);h=inventory.hold(target).orElseThrow();if(h.state().equals("ACTIVE"))inventory.transition(h,state.equals("EXPIRED")?"EXPIRED":"RELEASED",actor);
  jdbc.sql("UPDATE cinenest_booking.booking_amendment SET state=:s,revision=revision+1 WHERE amendment_id=:a AND state='PROPOSED'").param("s",state).param("a",a.get("amendment_id")).update();audit(actor,(UUID)a.get("booking_id"),(UUID)a.get("amendment_id"),state);
 }
 /** Separate committed maintenance means an expired confirmation cannot roll back expiry/release. */
 void expire(UUID booking){tx.executeWithoutResult(status->{access.lock(booking);for(var a:jdbc.sql("SELECT * FROM cinenest_booking.booking_amendment WHERE booking_id=:b AND state='PROPOSED' AND expires_at<=clock_timestamp() FOR UPDATE").param("b",booking).query().listOfRows())finish(a,"EXPIRED",null);});}
 void maintenance(){if(!enabled)return;for(UUID booking:jdbc.sql("SELECT DISTINCT booking_id FROM cinenest_booking.booking_amendment WHERE state='PROPOSED' AND expires_at<=clock_timestamp()").query(UUID.class).list())expire(booking);}
 Map<String,Object> read(UUID actor,UUID booking,UUID amendment){configured();expire(booking);return tx.execute(status->{access.lock(booking);owner(actor,booking);return view(row(booking,amendment));});}
 Map<String,Object> command(UUID actor,UUID booking,UUID amendment,String action,Map<String,Object> body){
  configured();InventoryRules.keys(body,action.equals("CREATE")?new String[]{"targetHoldId","quoteId","quoteRevision","quoteHash","expectedBookingRevision","expectedAmendmentRevision","commandKey","reason"}:new String[]{"expectedBookingRevision","expectedAmendmentRevision","proposalHash","commandKey","reason"});
  String reason=InventoryRules.text(body,"reason");if(reason.length()>240)throw InventoryRules.invalid();UUID key=id(body,"commandKey");long expectedBooking=number(body,"expectedBookingRevision"),expected=number(body,"expectedAmendmentRevision");
  if(!action.equals("CREATE")&&!InventoryRules.text(body,"proposalHash").matches("[a-f0-9]{64}"))throw InventoryRules.invalid();
  String hash=digest(action+":"+booking+":"+amendment+":"+new TreeMap<>(body));expire(booking);
  return tx.execute(status->{access.lock(booking);var b=owner(actor,booking);
   var prior=jdbc.sql("SELECT * FROM cinenest_booking.booking_amendment_command WHERE actor_id=:o AND command_key=:k").param("o",actor).param("k",key).query((rs,n)->{var meta=rs.getMetaData();var row=new LinkedHashMap<String,Object>();for(int i=1;i<=meta.getColumnCount();i++)row.put(meta.getColumnLabel(i),rs.getObject(i));return row;}).optional();
   if(prior.isPresent()){var p=prior.get();var a=row(booking,(UUID)p.get("amendment_id"));if(!hash.equals(p.get("payload_hash"))||!action.equals(p.get("action")))throw new InventoryFailure(409,"Idempotency key changed");return view(a);}
   if(expectedBooking!=b.revision())throw new InventoryFailure(409,"Booking revision changed");
   UUID result;
   if(action.equals("CREATE")){if(expected!=0)throw InventoryRules.invalid();eligible(b);result=create(actor,b,body);}else{
    var a=row(booking,amendment);if(((Number)a.get("revision")).longValue()!=expected||!a.get("proposal_hash").equals(body.get("proposalHash"))||!a.get("state").equals("PROPOSED")||!inventory.now().isBefore(instant(a.get("expires_at"))))throw new InventoryFailure(409,"Proposal changed or expired");
    if(action.equals("CONFIRM")){eligible(b);confirm(actor,b,a);}else finish(a,"WITHDRAWN",actor);result=amendment;
   }
   jdbc.sql("INSERT INTO cinenest_booking.booking_amendment_command(actor_id,command_key,amendment_id,action,payload_hash) VALUES(:o,:k,:a,:action,:hash)").param("o",actor).param("k",key).param("a",result).param("action",action).param("hash",hash).update();return view(row(booking,result));
  });
 }
 private UUID create(UUID actor,BookingAccess.Context b,Map<String,Object> body){
  if(jdbc.sql("SELECT count(*) FROM cinenest_booking.booking_amendment WHERE booking_id=:b AND state IN('PROPOSED','CONFIRMED')").param("b",b.booking()).query(Integer.class).single()!=0)throw new InventoryFailure(409,"Existing amendment must be resolved");
  UUID target=id(body,"targetHoldId"),quote=id(body,"quoteId");long revision=number(body,"quoteRevision");String hash=InventoryRules.text(body,"quoteHash");if(!hash.matches("[0-9a-f]{64}"))throw InventoryRules.invalid();
  var checked=pricing.validate(actor,b.quote(),target,quote,revision,hash,false);var h=target(b,target);var original=original(b.booking());
  samePurchase(original,checked.snapshot());Instant expiry=Collections.min(List.of(h.expires(),b.start(),h.start()));if(!inventory.now().isBefore(expiry))throw new InventoryFailure(409,"Proposal expired");
  UUID id=UUID.randomUUID();var snapshot=new LinkedHashMap<>(original);for(String k:List.of("holdId","roomId","venueId","sessionStart","sessionEnd","holdExpiresAt","timezone","guests","bufferMinutes","blockedUntil","configuration"))snapshot.put(k,checked.snapshot().get(k));
  String proposal=digest(id+":"+b.booking()+":1:"+quote+":"+revision+":"+hash+":"+expiry+":"+json(snapshot));
  jdbc.sql("INSERT INTO cinenest_booking.booking_amendment(amendment_id,booking_id,owner_id,venue_id,original_revision,target_hold,quote_id,quote_revision,quote_hash,proposal_hash,proposed_snapshot,expires_at,state,revision) VALUES(:a,:b,:o,:v,1,:h,:q,:r,:hash,:proposal,CAST(:s AS jsonb),:expiry,'PROPOSED',0)").param("a",id).param("b",b.booking()).param("o",actor).param("v",b.venue()).param("h",target).param("q",quote).param("r",revision).param("hash",hash).param("proposal",proposal).param("s",json(snapshot)).param("expiry",expiry.atOffset(ZoneOffset.UTC)).update();audit(actor,b.booking(),id,"PROPOSED");return id;
 }
 private Map<String,Object> original(UUID booking){return parse(jdbc.sql("SELECT snapshot FROM cinenest_booking.booking_revision WHERE booking_id=:b AND revision=1").param("b",booking).query(String.class).single());}
 private static void samePurchase(Map<String,Object> original,Map<String,Object> proposed){for(String k:List.of("package","packageVersionId","guests","optionalServiceIds","lines","totalMinor","currency","paymentMethod"))if(!Objects.equals(original.get(k),proposed.get(k)))throw new InventoryFailure(409,"Purchased terms or monetary lines differ");}
 private QuotationHolds.Facts target(BookingAccess.Context b,UUID target){
  var h=holds.lockAndInspect(b.owner(),target).orElseThrow(()->new InventoryFailure(409,"Target unavailable"));var s=original(b.booking());
  if(!h.state().equals("ACTIVE")||!h.expires().isAfter(inventory.now())||!h.start().isAfter(inventory.now())||!h.venue().equals(b.venue())||!h.room().equals(b.room())||!Duration.between(h.start(),h.end()).equals(Duration.between(b.start(),b.end()))||!h.timezone().equals(b.timezone())||!(h.blockedUntil().compareTo(b.start())<=0||h.start().compareTo(Instant.parse(s.get("blockedUntil").toString()))>=0))throw new InventoryFailure(409,"Target violates bounded rescheduling restrictions");return h;
 }
 private void confirm(UUID actor,BookingAccess.Context b,Map<String,Object> a){
  var quote=pricing.validate(actor,b.quote(),(UUID)a.get("target_hold"),(UUID)a.get("quote_id"),((Number)a.get("quote_revision")).longValue(),a.get("quote_hash").toString(),true);
  var h=target(b,(UUID)a.get("target_hold"));var proposed=parse(a.get("proposed_snapshot"));samePurchase(original(b.booking()),quote.snapshot());
  for(String k:List.of("sessionStart","sessionEnd","blockedUntil","configuration","bufferMinutes"))if(!Objects.equals(proposed.get(k),quote.snapshot().get(k)))throw new InventoryFailure(409,"Proposal changed");
  if(!access.neverIssued(b.booking())||!inventory.now().isBefore(instant(a.get("expires_at")))||!inventory.now().isBefore(b.start())||!identity.eligible(actor,false))throw new InventoryFailure(409,"Proposal no longer eligible");
  if(jdbc.sql("UPDATE cinenest_booking.reservation_hold SET state='CONSUMED',revision=revision+1 WHERE hold_id=:h AND state='ACTIVE' AND expires_at>clock_timestamp()").param("h",h.id()).update()!=1)throw new InventoryFailure(409,"Target changed");
  if(jdbc.sql("UPDATE cinenest_booking.inventory_block SET kind='CONFIRMED',revision=revision+1 WHERE block_id=(SELECT block_id FROM cinenest_booking.reservation_hold WHERE hold_id=:h) AND blocking AND kind='HOLD'").param("h",h.id()).update()!=1)throw new InventoryFailure(409,"Target unavailable");
  if(jdbc.sql("UPDATE cinenest_booking.inventory_block SET blocking=false,revision=revision+1 WHERE block_id=(SELECT h.block_id FROM cinenest_booking.booking b JOIN cinenest_booking.reservation_hold h USING(hold_id) WHERE b.booking_id=:b) AND blocking AND kind='CONFIRMED'").param("b",b.booking()).update()!=1)throw new InventoryFailure(409,"Original inventory changed");
  jdbc.sql("UPDATE cinenest_booking.booking_amendment SET state='CONFIRMED',revision=revision+1,accepted_at=clock_timestamp(),accepted_hash=proposal_hash,committed_revision=2 WHERE amendment_id=:a").param("a",a.get("amendment_id")).update();
  jdbc.sql("INSERT INTO cinenest_booking.booking_revision SELECT booking_id,2,quote_id,quote_revision,quote_hash,amount_minor,currency,CAST(:s AS jsonb) FROM cinenest_booking.booking_revision WHERE booking_id=:b AND revision=1").param("s",json(proposed)).param("b",b.booking()).update();
  audit(actor,b.booking(),(UUID)a.get("amendment_id"),"CONFIRMED");inventory.audit(actor,h.id(),"AMENDMENT_CONFIRM","ACTIVE","CONSUMED");
  UUID event=UUID.nameUUIDFromBytes(("BOOKING:"+b.booking()+":2:AMENDMENT_CONFIRMED").getBytes(StandardCharsets.UTF_8));notifications.record(new NotificationFacts.Fact(event,"BOOKING",b.booking(),2,b.owner(),b.venue(),NotificationFacts.Kind.AMENDMENT_CONFIRMED,false));
 }
 private Map<String,Object> view(Map<String,Object> a){var v=new LinkedHashMap<String,Object>();v.put("id",a.get("amendment_id"));v.put("bookingId",a.get("booking_id"));v.put("revision",a.get("revision"));v.put("state",a.get("state"));v.put("proposalHash",a.get("proposal_hash"));v.put("expiresAt",instant(a.get("expires_at")).toString());v.put("original",original((UUID)a.get("booking_id")));v.put("proposed",parse(a.get("proposed_snapshot")));v.put("differenceMinor","0");v.put("synthetic",true);v.put("realOperations",false);v.put("serverNow",inventory.now().toString());return v;}
}
