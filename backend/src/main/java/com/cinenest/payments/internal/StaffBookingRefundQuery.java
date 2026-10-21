package com.cinenest.payments.internal;

import com.cinenest.booking.SyntheticBooking;
import com.cinenest.identity.ScopedAuthorization;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Finance-owned observation, never a refund eligibility or command decision. */
@Service
class StaffBookingRefundQuery {
 record Attempt(UUID id,int sequence,List<String> evidencePhases){}
 record Refund(UUID id,long revision,String state,String resolution,String amountMinor,String currency,
               boolean destinationVerified,List<Attempt> attempts){}
 record Page(UUID bookingId,UUID venueId,long bookingRevision,UUID originatingRequestId,String observedAt,
             List<Refund> items,int page,int size,boolean hasNext,boolean synthetic,boolean realRepayment){}
 private final SyntheticBooking bookings;
 private final BankRepository bank;
 private final RefundRepository refunds;
 private final LedgerRepository ledger;
 private final LedgerService finance;
 private final RefundPolicy policy;
 private final ScopedAuthorization auth;
 private final TransactionTemplate tx;
 StaffBookingRefundQuery(SyntheticBooking bookings,BankRepository bank,RefundRepository refunds,
     LedgerRepository ledger,LedgerService finance,RefundPolicy policy,ScopedAuthorization auth,
     PlatformTransactionManager manager){
  this.bookings=bookings;this.bank=bank;this.refunds=refunds;this.ledger=ledger;
  this.finance=finance;this.policy=policy;this.auth=auth;this.tx=new TransactionTemplate(manager);
 }
 private void permit(UUID actor,UUID venue,boolean lock){
  if(!auth.permits(actor,venue,"refund.synthetic.read",lock))throw new BankFailure(403,"Current refund read grant required");
 }
 private static void require(boolean value){if(!value)throw new BankFailure(409,"Inconsistent financial observation");}
 private SyntheticBooking.FinancialContext context(UUID id){
  var c=bookings.financialContext(id).orElseThrow(()->new BankFailure(404,"Booking not found"));
  require(id.equals(c.bookingId())&&c.ownerId()!=null&&c.venueId()!=null&&c.originatingRequestId()!=null&&c.bookingRevision()>0);
  return c;
 }
 Page read(UUID actor,UUID venue,UUID booking,int page,int size){
  CustomerReads.bounds(page,size);
  return tx.execute(s->{
   policy.ceiling();permit(actor,venue,false);
   var initial=context(booking);
   if(!venue.equals(initial.venueId()))throw new BankFailure(404,"Booking not found");
   // Same identity -> request -> credit order as Finance commands. No Booking lock.
   for(UUID principal:new TreeSet<>(List.of(actor,initial.ownerId())))
    if(!auth.eligible(principal,true))throw new BankFailure(403,"Current eligibility required");
   permit(actor,venue,true);
   var request=bank.request(initial.originatingRequestId(),true).orElseThrow(()->new BankFailure(409,"Missing funding lineage"));
   require(request.id().equals(initial.originatingRequestId())&&request.owner().equals(initial.ownerId())&&request.venue().equals(venue));
   var ids=refunds.forRequest(request.id(),page*size,size+1);
   var rows=ids.stream().map(id->refunds.refund(id,false)).toList();
   // Lock credits deterministically before observing refund state. Request lock serializes
   // request/approval/allocation/reconciliation, including an empty result.
   var credits=new TreeSet<UUID>();
   for(var row:rows){
    require(request.id().equals(row.get("request_id"))&&venue.equals(row.get("venue_id"))&&request.owner().equals(row.get("owner_id")));
    credits.add((UUID)row.get("credit_id"));
   }
   for(UUID credit:credits){
    var c=ledger.credit(Map.of("c",credit,"v",venue),true);
    require("SYNTHETIC_BANK".equals(c.get("source_kind"))&&ledger.matchedRequest(Map.of("c",credit)).stream().anyMatch(m->request.id().equals(m.get("request_id"))));
   }
   var items=rows.stream().limit(size).map(row->{
    UUID id=(UUID)row.get("refund_id");
    var attempts=refunds.attempts(id).stream().map(a->new Attempt((UUID)a.get("attempt_id"),((Number)a.get("sequence")).intValue(),
     refunds.evidence((UUID)a.get("attempt_id")).stream().map(e->e.get("phase").toString()).distinct().toList())).toList();
    String resolution=refunds.work(id).orElseThrow(()->new BankFailure(409,"Missing refund resolution"));
    return new Refund(id,((Number)row.get("revision")).longValue(),row.get("state").toString(),resolution,
     row.get("amount_minor").toString(),row.get("currency").toString(),refunds.destination(id).isPresent(),attempts);
   }).toList();
   permit(actor,venue,true);
   require(initial.equals(context(booking)));
   String observed=bank.now().toString();
   try{finance.financialAudit(actor,venue,booking,"REFUND_BOOKING_READ","SYNTHETIC_ONLY",UUID.randomUUID());}
   catch(DataAccessException e){throw new BankFailure(503,"Required read audit unavailable");}
   return new Page(booking,venue,initial.bookingRevision(),request.id(),observed,items,page,size,ids.size()>size,true,false);
  });
 }
}
