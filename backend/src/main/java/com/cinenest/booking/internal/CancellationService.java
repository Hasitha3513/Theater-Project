package com.cinenest.booking.internal;
import com.cinenest.booking.*;import com.cinenest.identity.ScopedAuthorization;import com.cinenest.notifications.NotificationFacts;
import java.util.*;import java.time.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;
import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.stereotype.Service;import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;import org.springframework.transaction.support.TransactionTemplate;
/** Atomic synthetic pre-start cancellation. No outgoing payment or physical action. */
@Service
class CancellationService {
 static final String POLICY="SYNTHETIC_PRESTART_CANCEL_V1";
 private final JdbcClient jdbc;private final BookingAccess bookings;private final AmendmentGuards.Access access;private final CancellationFunding funding;private final ScopedAuthorization identity;private final InventoryRepository inventory;private final NotificationFacts notifications;private final Environment env;private final TransactionTemplate tx;
 CancellationService(JdbcClient jdbc,BookingAccess bookings,AmendmentGuards.Access access,CancellationFunding funding,ScopedAuthorization identity,InventoryRepository inventory,NotificationFacts notifications,Environment env,PlatformTransactionManager manager){this.jdbc=jdbc;this.bookings=bookings;this.access=access;this.funding=funding;this.identity=identity;this.inventory=inventory;this.notifications=notifications;this.env=env;tx=new TransactionTemplate(manager);}
 private void configured(){if(!Set.of(env.getActiveProfiles()).equals(Set.of("synthetic-quotation")))throw new InventoryFailure(503,"Synthetic cancellation unavailable");}
 private Map<String,Object> basic(UUID booking){return jdbc.sql("SELECT owner_id,venue_id,state,lifecycle_version FROM cinenest_booking.booking WHERE booking_id=:b AND synthetic").param("b",booking).query().listOfRows().stream().findFirst().orElseThrow(()->new InventoryFailure(404,"Booking unavailable"));}
 private void permit(UUID actor,UUID venue,Map<String,Object> b){
  var owner=(UUID)b.get("owner_id");var actual=(UUID)b.get("venue_id");
  if(actor==null)throw new InventoryFailure(401,"Session required");
  if(venue==null&&!actor.equals(owner)||venue!=null&&!venue.equals(actual))throw new InventoryFailure(404,"Booking unavailable");
  var principals=new TreeSet<UUID>();principals.add(actor);principals.add(owner);for(UUID p:principals)if(!identity.eligible(p,true))throw new InventoryFailure(403,"Current eligibility required");
  if(venue!=null&&(!identity.member(actor,venue,true)||!identity.permits(actor,venue,"booking.synthetic.cancel",true)))throw new InventoryFailure(403,"Cancellation permission required");
 }
 private String reason(BookingAccess.Context b){
  if(bookings.extensionPending(b.booking()))throw new InventoryFailure(409,"Resolve the pending extension reservation first");
  if(!inventory.now().isBefore(b.start()))return "SESSION_STARTED";
  if(!access.neverIssued(b.booking()))return "CREDENTIAL_EVER_ISSUED";
  if(jdbc.sql("SELECT EXISTS(SELECT 1 FROM cinenest_booking.booking_amendment WHERE booking_id=:b AND state='PROPOSED')").param("b",b.booking()).query(Boolean.class).single())return "AMENDMENT_PENDING";
  if(!funding.eligible(b))return "FINANCIAL_REVIEW_REQUIRED";
  return "ELIGIBLE";
 }
 private Map<String,Object> result(UUID actor,UUID booking,Map<String,Object> base,BookingAccess.Context b){
  var out=new LinkedHashMap<String,Object>();out.put("bookingId",booking);out.put("venueId",base.get("venue_id"));out.put("state",base.get("state"));out.put("lifecycleVersion",base.get("lifecycle_version"));out.put("policyVersion",POLICY);out.put("synthetic",true);out.put("realRepayment",false);out.put("physicalReadiness","UNVERIFIED");
  var cancelled=jdbc.sql("SELECT cancellation_id,purchased_revision,obligation_id,created_at FROM cinenest_booking.booking_cancellation WHERE booking_id=:b").param("b",booking).query().listOfRows().stream().findFirst();
  if(cancelled.isPresent()){var c=cancelled.get();out.put("eligible",false);out.put("reason","ALREADY_CANCELLED");out.put("purchasedRevision",c.get("purchased_revision"));out.put("cancellationId",c.get("cancellation_id"));out.put("refundObligationId",c.get("obligation_id"));out.put("refundStatus","RESERVED_REVIEW_REQUIRED");out.put("resolution","FINANCIAL_REVIEW_REQUIRED");out.put("inventoryReleased",true);out.put("accessState","NOT_ISSUED");return out;}
  String reason=b==null?"BOOKING_UNAVAILABLE":reason(b);out.put("eligible",reason.equals("ELIGIBLE"));out.put("reason",reason);if(b!=null){out.put("purchasedRevision",b.revision());out.put("sessionStart",b.start().toString());if(actor.equals(b.owner())){out.put("refundPrincipalMinor",Long.toString(b.amount()));out.put("currency",b.currency());}}out.put("refundStatus","NO_CANCELLATION_OBLIGATION");out.put("consequence","Full eligible allocated synthetic principal becomes a review-required obligation;no repayment or physical action.");return out;
 }
 Object read(UUID actor,UUID venue,UUID booking){configured();return tx.execute(s->{access.lock(booking);var base=basic(booking);permit(actor,venue,base);return result(actor,booking,base,bookings.lock(booking).orElse(null));});}
 private static long version(Map<String,Object> body,String name){Object v=body.get(name);if(!(v instanceof Integer||v instanceof Long)||((Number)v).longValue()<0)throw InventoryRules.invalid();return ((Number)v).longValue();}
 Object cancel(UUID actor,UUID venue,UUID booking,Map<String,Object> body){
  configured();InventoryRules.keys(body,"reason","commandKey","expectedPurchasedRevision","expectedLifecycleVersion");String reason=InventoryRules.text(body,"reason");if(reason.length()>240||reason.chars().anyMatch(Character::isISOControl))throw InventoryRules.invalid();UUID key=InventoryRules.id(InventoryRules.text(body,"commandKey"));long expected=version(body,"expectedPurchasedRevision"),life=version(body,"expectedLifecycleVersion");
  String payload=booking+":"+venue+":"+new TreeMap<>(body);String hash;try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}final String digest=hash;
  return tx.execute(s->{
   access.lock(booking);var base=basic(booking);permit(actor,venue,base);
   var replay=jdbc.sql("SELECT payload_hash,result FROM cinenest_booking.cancellation_command WHERE actor_id=:a AND command_key=:k").param("a",actor).param("k",key).query().listOfRows().stream().findFirst();
   if(replay.isPresent()){if(!digest.equals(replay.get().get("payload_hash")))throw new InventoryFailure(409,"Command identity changed");return parse(replay.get().get("result"));}
   var b=bookings.lock(booking).orElseThrow(()->new InventoryFailure(409,"Booking is not cancellable"));
   if(b.revision()!=expected||((Number)base.get("lifecycle_version")).longValue()!=life||life!=0)throw new InventoryFailure(409,"Booking version changed");
   String why=reason(b);if(!why.equals("ELIGIBLE"))throw new InventoryFailure(409,why);
   var snapshot=jdbc.sql("SELECT snapshot FROM cinenest_booking.booking_revision WHERE booking_id=:b AND revision=:r").param("b",booking).param("r",b.revision()).query(String.class).single();UUID hold=InventoryRules.id(parse(snapshot).get("holdId").toString());
   var block=jdbc.sql("SELECT i.* FROM cinenest_booking.reservation_hold h JOIN cinenest_booking.inventory_block i USING(block_id) WHERE h.hold_id=:h AND h.owner_id=:owner AND h.state='CONSUMED' FOR UPDATE OF h,i").param("h",hold).param("owner",b.owner()).query().listOfRows().stream().findFirst().orElseThrow(()->new InventoryFailure(409,"Purchased inventory unavailable"));
   if(!Boolean.TRUE.equals(block.get("blocking"))||!"CONFIRMED".equals(block.get("kind"))||!b.venue().equals(block.get("venue_id"))||!b.room().equals(block.get("room_id"))||!b.start().equals(((java.sql.Timestamp)block.get("session_start")).toInstant())||!b.end().equals(((java.sql.Timestamp)block.get("session_end")).toInstant()))throw new InventoryFailure(409,"Purchased inventory changed");
   UUID cancellation=UUID.randomUUID(),obligation=funding.reserve(b,cancellation);UUID blockId=(UUID)block.get("block_id");
   jdbc.sql("INSERT INTO cinenest_booking.booking_cancellation(cancellation_id,booking_id,actor_id,purchased_revision,lifecycle_version,policy_version,reason,obligation_id,block_id,notification_revision) VALUES(:id,:b,:a,:r,1,:p,:why,:o,:block,:n)").param("id",cancellation).param("b",booking).param("a",actor).param("r",b.revision()).param("p",POLICY).param("why",reason).param("o",obligation).param("block",blockId).param("n",b.revision()+1).update();
   if(jdbc.sql("UPDATE cinenest_booking.inventory_block SET blocking=false,revision=revision+1 WHERE block_id=:id AND blocking AND kind='CONFIRMED'").param("id",blockId).update()!=1)throw new InventoryFailure(409,"Inventory changed");
   permit(actor,venue,base);if(!inventory.now().isBefore(b.start())||!access.neverIssued(booking))throw new InventoryFailure(409,"Cancellation eligibility changed");
   if(jdbc.sql("UPDATE cinenest_booking.booking SET state='SIMULATED_CANCELLED',lifecycle_version=1 WHERE booking_id=:b AND state='SIMULATED_CONFIRMED' AND lifecycle_version=0").param("b",booking).update()!=1)throw new InventoryFailure(409,"Booking changed");
   jdbc.sql("INSERT INTO cinenest_booking.cancellation_audit(event_id,cancellation_id,actor_id,booking_id,venue_id) VALUES(:id,:c,:a,:b,:v)").param("id",UUID.randomUUID()).param("c",cancellation).param("a",actor).param("b",booking).param("v",b.venue()).update();inventory.audit(actor,hold,"SYNTHETIC_CANCEL","CONFIRMED","RELEASED");
   UUID event=UUID.nameUUIDFromBytes(("BOOKING:"+booking+":"+(b.revision()+1)+":CANCELLED").getBytes(StandardCharsets.UTF_8));notifications.record(new NotificationFacts.Fact(event,"BOOKING",booking,b.revision()+1,b.owner(),b.venue(),NotificationFacts.Kind.BOOKING_CANCELLED,false));
   var out=result(actor,booking,basic(booking),null);
   jdbc.sql("INSERT INTO cinenest_booking.cancellation_command VALUES(:a,:key,:hash,:c,CAST(:out AS jsonb))").param("a",actor).param("key",key).param("hash",digest).param("c",cancellation).param("out",com.nimbusds.jose.util.JSONObjectUtils.toJSONString(out)).update();
   permit(actor,venue,base);if(!inventory.now().isBefore(b.start()))throw new InventoryFailure(409,"Session started");return out;
  });
 }
 private static Map<String,Object> parse(Object value){try{return com.nimbusds.jose.util.JSONObjectUtils.parse(value.toString());}catch(Exception e){throw new InventoryFailure(503,"Cancellation unavailable");}}
}
