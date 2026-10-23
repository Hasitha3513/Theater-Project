package com.cinenest.booking.internal;
import com.cinenest.booking.*;
import com.cinenest.identity.ScopedAuthorization;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
@Component
class SyntheticBookingAdapter implements SyntheticBooking {
 private final JdbcClient jdbc;private final QuotationHolds holds;private final ScopedAuthorization auth;private final InventoryRepository inventory;private final AmendmentGuards.Pricing pricing;
 SyntheticBookingAdapter(JdbcClient jdbc,QuotationHolds holds,ScopedAuthorization auth,InventoryRepository inventory,AmendmentGuards.Pricing pricing){this.pricing=pricing;this.jdbc=jdbc;this.holds=holds;this.auth=auth;this.inventory=inventory;}
 public Optional<String> state(UUID booking){return jdbc.sql("SELECT state FROM cinenest_booking.booking WHERE booking_id=:b AND synthetic").param("b",booking).query(String.class).optional();}
 public Optional<UUID> forRequest(UUID request){return jdbc.sql("SELECT booking_id FROM cinenest_booking.booking WHERE request_id=:r").param("r",request).query(UUID.class).optional();}
 public Optional<FinancialContext> financialContext(UUID booking){
  return jdbc.sql("SELECT b.booking_id,b.owner_id,b.venue_id,b.request_id,(SELECT max(r.revision) FROM cinenest_booking.booking_revision r WHERE r.booking_id=b.booking_id) AS revision FROM cinenest_booking.booking b WHERE b.booking_id=:b AND b.synthetic AND b.state IN ('SIMULATED_CONFIRMED','SIMULATED_CANCELLED')")
   .param("b",booking).query((rs,n)->new FinancialContext(rs.getObject("booking_id",UUID.class),rs.getObject("owner_id",UUID.class),rs.getObject("venue_id",UUID.class),rs.getObject("request_id",UUID.class),rs.getLong("revision"))).optional();
 }
 public boolean entitled(UUID owner,UUID hold){var h=holds.lockAndInspect(owner,hold);return h.isPresent()&&h.get().state().equals("ACTIVE")&&h.get().expires().isAfter(inventory.now())&&h.get().start().isAfter(inventory.now());}
 public UUID confirm(Funding f){
  if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Caller transaction required");
  var prior=forRequest(f.request());if(prior.isPresent())return prior.get();
  if(f.actor().equals(f.owner())||!auth.permits(f.actor(),f.venue(),"bank.synthetic.allocate",false)||f.amount()<=0||!"LKR".equals(f.currency())||!Boolean.TRUE.equals(f.snapshot().get("synthetic"))||!Boolean.FALSE.equals(f.snapshot().get("commercialIssuance")))throw new BankRequestHolds.Rejected();
  var h=holds.lockAndInspect(f.owner(),f.hold()).orElseThrow(BankRequestHolds.Rejected::new);
  if(!h.state().equals("ACTIVE")||!h.expires().isAfter(inventory.now())||!h.venue().equals(f.venue())||!h.start().isAfter(inventory.now()))throw new BankRequestHolds.Rejected();
  if(jdbc.sql("SELECT count(*) FROM cinenest_booking.booking_bank_request_hold WHERE request_id=:r AND hold_id=:h AND owner_id=:o AND venue_id=:v").param("r",f.request()).param("h",f.hold()).param("o",f.owner()).param("v",f.venue()).query(Integer.class).single()!=1)throw new BankRequestHolds.Rejected();
  UUID id=UUID.randomUUID();jdbc.sql("INSERT INTO cinenest_booking.booking VALUES(:id,:r,:h,:o,:v,'SIMULATED_CONFIRMED',true,clock_timestamp())").param("id",id).param("r",f.request()).param("h",f.hold()).param("o",f.owner()).param("v",f.venue()).update();
  jdbc.sql("INSERT INTO cinenest_booking.booking_revision VALUES(:id,1,:q,:revision,:hash,:amount,:currency,CAST(:snapshot AS jsonb))").param("id",id).param("q",f.quote()).param("revision",f.quoteRevision()).param("hash",f.hash()).param("amount",f.amount()).param("currency",f.currency()).param("snapshot",com.nimbusds.jose.util.JSONObjectUtils.toJSONString(f.snapshot())).update();
  if(jdbc.sql("UPDATE cinenest_booking.reservation_hold SET state='CONSUMED',revision=revision+1 WHERE hold_id=:h AND state='ACTIVE' AND expires_at>clock_timestamp()").param("h",f.hold()).update()!=1)throw new BankRequestHolds.Rejected();
  if(jdbc.sql("UPDATE cinenest_booking.inventory_block SET kind='CONFIRMED',revision=revision+1 WHERE block_id=(SELECT block_id FROM cinenest_booking.reservation_hold WHERE hold_id=:h) AND blocking AND kind='HOLD'").param("h",f.hold()).update()!=1)throw new BankRequestHolds.Rejected();
  jdbc.sql("INSERT INTO cinenest_booking.booking_command(request_id,command_key,booking_id) VALUES(:r,:k,:id)").param("r",f.request()).param("k",f.command()).param("id",id).update();inventory.audit(f.actor(),f.hold(),"SYNTHETIC_CONFIRM","ACTIVE","CONSUMED");return id;
 }
 public CustomerPage ownedHistory(UUID actor,int page,int size){
  if(page<0||page>10000||size<1||size>25)throw new IllegalArgumentException("Invalid customer page");
  if(!auth.eligible(actor,true))throw new SecurityException("Current eligible account required");
  var rows=jdbc.sql("SELECT b.booking_id,b.request_id,b.venue_id,b.state,b.confirmed_at,r.revision,r.amount_minor,r.currency,r.snapshot FROM cinenest_booking.booking b JOIN cinenest_booking.booking_revision r USING(booking_id) WHERE b.owner_id=:a AND r.revision=(SELECT max(current.revision) FROM cinenest_booking.booking_revision current WHERE current.booking_id=b.booking_id) ORDER BY b.confirmed_at DESC,b.booking_id DESC LIMIT :limit OFFSET :offset").param("a",actor).param("limit",size+1).param("offset",page*size).query().listOfRows();
  var items=rows.stream().limit(size).map(row->new CustomerItem((UUID)row.get("booking_id"),(UUID)row.get("request_id"),(UUID)row.get("venue_id"),row.get("state").toString(),((Number)row.get("revision")).longValue(),((java.sql.Timestamp)row.get("confirmed_at")).toInstant().toString(),purchase(parse(row.get("snapshot").toString()),row.get("amount_minor").toString(),row.get("currency").toString()),true,false)).toList();
  return new CustomerPage(items,page,size,rows.size()>size,inventory.now().toString(),true);
 }
 @SuppressWarnings("unchecked")
 private static Purchase purchase(Map<String,Object> s,String amount,String currency){
  var pkg=(Map<String,Object>)s.get("package");var lines=((List<Map<String,Object>>)s.get("lines")).stream().map(l->new Line(l.get("lineId").toString(),l.get("kind").toString(),l.get("label").toString(),l.get("quantity").toString(),l.get("amountMinor").toString())).toList();
  return new Purchase(pkg.get("name").toString(),Objects.toString(pkg.get("description"),""),s.get("packageVersionId").toString(),s.get("sessionStart").toString(),s.get("sessionEnd").toString(),s.get("timezone").toString(),s.get("roomId").toString(),amount,currency,lines);
 }
 public Optional<Map<String,Object>> read(UUID actor,UUID id){
  if(!auth.eligible(actor,true))return Optional.empty();
  var rows=jdbc.sql("SELECT b.owner_id,b.venue_id,b.request_id,b.state,b.confirmed_at,r.quote_id,r.snapshot,r.revision FROM cinenest_booking.booking b JOIN cinenest_booking.booking_revision r USING(booking_id) WHERE booking_id=:id ORDER BY r.revision DESC").param("id",id).query().listOfRows();
  if(rows.isEmpty())return Optional.empty();var row=rows.getFirst();if(!row.get("owner_id").equals(actor)&&!auth.permits(actor,(UUID)row.get("venue_id"),"bank.synthetic.credit.read",true))return Optional.empty();
  var result=new LinkedHashMap<String,Object>(Map.of("id",id,"requestId",row.get("request_id"),"syntheticBookingStatus",row.get("state"),"synthetic",true,"bookingConfirmed",false,"confirmedAt",((java.sql.Timestamp)row.get("confirmed_at")).toInstant().toString(),"snapshot",parse(row.get("snapshot").toString())));var snapshot=parse(row.get("snapshot").toString());result.put("state",row.get("state"));result.put("revision",row.get("revision"));result.put("originalSnapshot",parse(rows.getLast().get("snapshot").toString()));result.put("observedAt",inventory.now().toString());result.put("venueId",row.get("venue_id"));result.put("purchase",purchase(snapshot,snapshot.get("totalMinor").toString(),snapshot.get("currency").toString()));if(actor.equals(row.get("owner_id"))&&"SIMULATED_CONFIRMED".equals(row.get("state"))){var original=parse(rows.getLast().get("snapshot").toString());result.put("amendmentPlanning",Map.of("packageId",pricing.packageId((UUID)row.get("quote_id"),actor),"guests",original.get("guests"),"optionalServiceIds",original.get("optionalServiceIds"),"paymentMethod",original.get("paymentMethod")));var amendments=jdbc.sql("SELECT amendment_id FROM cinenest_booking.booking_amendment WHERE booking_id=:b ORDER BY created_at DESC LIMIT 1").param("b",id).query(UUID.class).list();if(!amendments.isEmpty())result.put("latestAmendmentId",amendments.getFirst());}return Optional.of(result);
 }
 private static Map<String,Object> parse(String s){try{return com.nimbusds.jose.util.JSONObjectUtils.parse(s);}catch(java.text.ParseException e){throw new IllegalStateException(e);}}
}
