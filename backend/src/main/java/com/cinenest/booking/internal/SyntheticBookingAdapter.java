package com.cinenest.booking.internal;
import com.cinenest.booking.*;
import com.cinenest.identity.ScopedAuthorization;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
@Component
class SyntheticBookingAdapter implements SyntheticBooking {
 private final JdbcClient jdbc;private final QuotationHolds holds;private final ScopedAuthorization auth;private final InventoryRepository inventory;
 SyntheticBookingAdapter(JdbcClient jdbc,QuotationHolds holds,ScopedAuthorization auth,InventoryRepository inventory){this.jdbc=jdbc;this.holds=holds;this.auth=auth;this.inventory=inventory;}
 public Optional<UUID> forRequest(UUID request){return jdbc.sql("SELECT booking_id FROM cinenest_booking.booking WHERE request_id=:r").param("r",request).query(UUID.class).optional();}
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
 public Optional<Map<String,Object>> read(UUID actor,UUID id){
  if(!auth.eligible(actor,true))return Optional.empty();
  var rows=jdbc.sql("SELECT b.owner_id,b.venue_id,b.request_id,b.state,b.confirmed_at,r.snapshot FROM cinenest_booking.booking b JOIN cinenest_booking.booking_revision r USING(booking_id) WHERE booking_id=:id").param("id",id).query().listOfRows();
  if(rows.isEmpty())return Optional.empty();var row=rows.getFirst();if(!row.get("owner_id").equals(actor)&&!auth.permits(actor,(UUID)row.get("venue_id"),"bank.synthetic.credit.read",true))return Optional.empty();
  return Optional.of(Map.of("id",id,"requestId",row.get("request_id"),"syntheticBookingStatus",row.get("state"),"synthetic",true,"bookingConfirmed",false,"confirmedAt",((java.sql.Timestamp)row.get("confirmed_at")).toInstant().toString(),"snapshot",parse(row.get("snapshot").toString())));
 }
 private static Map<String,Object> parse(String s){try{return com.nimbusds.jose.util.JSONObjectUtils.parse(s);}catch(java.text.ParseException e){throw new IllegalStateException(e);}}
}
