package com.cinenest.payments.internal;

import com.cinenest.booking.SyntheticBooking;
import com.cinenest.identity.ScopedAuthorization;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffBookingRefundQueryTest {
 final SyntheticBooking bookings=mock(SyntheticBooking.class);
 final BankRepository bank=mock(BankRepository.class);
 final RefundRepository refunds=mock(RefundRepository.class);
 final LedgerRepository ledger=mock(LedgerRepository.class);
 final LedgerService finance=mock(LedgerService.class);
 final RefundPolicy policy=mock(RefundPolicy.class);
 final ScopedAuthorization auth=mock(ScopedAuthorization.class);
 final StaffBookingRefundQuery service=new StaffBookingRefundQuery(bookings,bank,refunds,ledger,finance,policy,auth,mock(PlatformTransactionManager.class));
 final UUID actor=UUID.randomUUID(),owner=UUID.randomUUID(),venue=UUID.randomUUID(),booking=UUID.randomUUID(),request=UUID.randomUUID(),refund=UUID.randomUUID(),credit=UUID.randomUUID();
 SyntheticBooking.FinancialContext context;
 Map<String,Object> row;
 @BeforeEach void setup(){
  context=new SyntheticBooking.FinancialContext(booking,owner,venue,request,2);
  when(bookings.financialContext(booking)).thenReturn(Optional.of(context));
  when(auth.permits(eq(actor),eq(venue),eq("refund.synthetic.read"),anyBoolean())).thenReturn(true);
  when(auth.eligible(any(UUID.class),eq(true))).thenReturn(true);
  when(bank.request(request,true)).thenReturn(Optional.of(new BankRepository.Request(request,owner,venue,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1,"SIMULATED_CONFIRMED","REVIEWED",null,null,null,null,null,null,"Asia/Colombo","100","LKR",Map.of())));
  when(bank.now()).thenReturn(Instant.parse("2030-01-01T00:00:00Z"));
  when(refunds.forRequest(request,0,11)).thenReturn(List.of(refund));
  row=new HashMap<>(Map.of("refund_id",refund,"request_id",request,"owner_id",owner,"venue_id",venue,"credit_id",credit,"revision",3L,"state","APPROVED","amount_minor",100L,"currency","LKR"));
  when(refunds.refund(refund,false)).thenReturn(row);when(refunds.work(refund)).thenReturn(Optional.of("REVIEW_REQUIRED"));
  when(ledger.credit(anyMap(),eq(true))).thenReturn(Map.of("source_kind","SYNTHETIC_BANK"));
  when(ledger.matchedRequest(anyMap())).thenReturn(List.of(Map.of("request_id",request)));
 }
 void fails(int code){assertThatThrownBy(()->service.read(actor,venue,booking,0,10)).isInstanceOfSatisfying(BankFailure.class,f->assertThat(f.status).isEqualTo(code));}
 @Test void safeProjectionHasNoCommandOrBalanceDecision(){var result=service.read(actor,venue,booking,0,10);assertThat(result.items().getFirst().amountMinor()).isEqualTo("100");assertThat(result.items().getFirst().state()).isEqualTo("APPROVED");assertThat(result.bookingRevision()).isEqualTo(2);assertThat(result.realRepayment()).isFalse();verify(finance).financialAudit(eq(actor),eq(venue),eq(booking),eq("REFUND_BOOKING_READ"),eq("SYNTHETIC_ONLY"),any());verify(refunds,never()).command(any(),any(),any(),any());verify(bookings,never()).confirm(any());}
 @Test void missingPermissionDoesNotQueryBooking(){when(auth.permits(actor,venue,"refund.synthetic.read",false)).thenReturn(false);fails(403);verifyNoInteractions(bookings,refunds,ledger,finance);}
 @Test void expiredGrantAfterWaitIsDenied(){when(auth.permits(actor,venue,"refund.synthetic.read",true)).thenReturn(true,false);fails(403);verifyNoInteractions(finance);}
 @Test void bookingRevisionRaceIsConflict(){when(bookings.financialContext(booking)).thenReturn(Optional.of(context),Optional.of(new SyntheticBooking.FinancialContext(booking,owner,venue,request,3)));fails(409);verifyNoInteractions(finance);}
 @Test void fundingLineageRaceIsConflict(){when(bookings.financialContext(booking)).thenReturn(Optional.of(context),Optional.of(new SyntheticBooking.FinancialContext(booking,owner,venue,UUID.randomUUID(),2)));fails(409);}
 @Test void foreignOwnerRefundIsNotSilentlyFiltered(){row.put("owner_id",UUID.randomUUID());fails(409);}
 @Test void foreignVenueRefundIsNotSilentlyFiltered(){row.put("venue_id",UUID.randomUUID());fails(409);}
 @Test void missingFinanceAssociationIsConflict(){when(ledger.matchedRequest(anyMap())).thenReturn(List.of());fails(409);}
 @Test void gatewaySourceIsNotAccepted(){when(ledger.credit(anyMap(),eq(true))).thenReturn(Map.of("source_kind","GATEWAY"));fails(409);}
 @Test void missingWorkDoesNotFabricateResolution(){when(refunds.work(refund)).thenReturn(Optional.empty());fails(409);}
 @Test void ownerSuspensionFailsClosed(){when(auth.eligible(owner,true)).thenReturn(false);fails(403);}
 @Test void dependencyFailureDoesNotProduceEmptySuccess(){when(bookings.financialContext(booking)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private"));assertThatThrownBy(()->service.read(actor,venue,booking,0,10)).isInstanceOf(org.springframework.dao.DataAccessException.class);verifyNoInteractions(finance);}
 @Test void auditFailureIsUnavailable(){doThrow(new org.springframework.dao.DataIntegrityViolationException("private")).when(finance).financialAudit(any(),any(),any(),any(),any(),any());fails(503);}
 @Test void actualAttemptAndEvidencePhasesAreAllowlisted(){UUID attempt=UUID.randomUUID();when(refunds.attempts(refund)).thenReturn(List.of(Map.of("attempt_id",attempt,"sequence",2,"executor_id",actor,"operation_key",UUID.randomUUID())));when(refunds.evidence(attempt)).thenReturn(List.of(Map.of("phase","RECORDED","fixture_key","SENSITIVE"),Map.of("phase","VERIFIED","fixture_key","SENSITIVE")));var a=service.read(actor,venue,booking,0,10).items().getFirst().attempts().getFirst();assertThat(a.id()).isEqualTo(attempt);assertThat(a.sequence()).isEqualTo(2);assertThat(a.evidencePhases()).containsExactly("RECORDED","VERIFIED");}
}
