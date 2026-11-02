package com.cinenest.payments.internal;
import com.cinenest.booking.BankRequestHolds;
import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.pricing.BankQuotation;
import com.cinenest.verification.BankEligibility;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Application orchestration only; PostgreSQL behavior is covered in BankIntegrationTest. */
class BankReadinessTest {
 final UUID actor=UUID.randomUUID(),quote=UUID.randomUUID(),venue=UUID.randomUUID(),hold=UUID.randomUUID();
 final Instant now=Instant.parse("2026-10-19T04:30:00Z");
 final BankRepository repo=mock(BankRepository.class);final ScopedAuthorization auth=mock(ScopedAuthorization.class);final BankQuotation quotes=mock(BankQuotation.class);final BankRequestHolds holds=mock(BankRequestHolds.class);final BankEligibility verification=mock(BankEligibility.class);final BankPolicy policy=mock(BankPolicy.class);final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
 BankService service;BankQuotation.Facts facts;
 @BeforeEach void setup(){
  when(manager.getTransaction(any())).thenAnswer(x->new SimpleTransactionStatus());
  when(policy.current()).thenReturn(new BankPolicy.Policy("SYNTHETIC-TEST","test",UUID.randomUUID(),Duration.ofSeconds(60),Duration.ofSeconds(300),Duration.ofSeconds(10),Duration.ofSeconds(20),Duration.ofSeconds(30),Duration.ofSeconds(240),Duration.ofSeconds(40),Duration.ofSeconds(10),Duration.ofSeconds(60),Duration.ofSeconds(1),LocalTime.MIN,LocalTime.MAX,Set.of()));
  service=new BankService(mock(LedgerService.class),repo,auth,quotes,holds,verification,policy,mock(PrivateEvidenceStore.class),mock(SyntheticEvidenceProcessor.class),mock(ReviewerNotice.class),mock(com.cinenest.notifications.NotificationFacts.class),manager,mock(BankInstructions.class));
  facts=new BankQuotation.Facts(quote,actor,venue,hold,3,1,"a".repeat(64),now.plusSeconds(600),Map.of("synthetic",true,"commercialIssuance",false,"currency","LKR","sessionStart",now.plusSeconds(3600).toString(),"sessionEnd",now.plusSeconds(7200).toString(),"timezone","Asia/Colombo","totalMinor",123L,"privateInternal","MUST_NOT_LEAK"));
  when(auth.eligible(actor,false)).thenReturn(true);when(quotes.observe(actor,quote)).thenAnswer(x->Optional.of(new BankQuotation.Observation(facts,"ELIGIBLE")));when(verification.observe(actor,quote,venue)).thenReturn(true);when(repo.now()).thenReturn(now);when(holds.conversionRefusal(eq(actor),eq(hold),eq(1L),any(),eq(false))).thenReturn("ELIGIBLE");
 }
 @Test void usesOnlyObservationPortsAndReadOnlyRepeatableRead(){var result=service.readiness(actor,quote);assertThat(result).containsEntry("eligible",true).containsEntry("quoteRevision",3L).containsEntry("contentHash",facts.hash()).containsEntry("verificationReadiness","ELIGIBLE").containsEntry("paymentVerified",false).containsEntry("bookingConfirmed",false);assertThat(result.toString()).doesNotContain("MUST_NOT_LEAK","evidence","grants");verify(manager).getTransaction(argThat(d->d.isReadOnly()&&d.getIsolationLevel()==TransactionDefinition.ISOLATION_REPEATABLE_READ));verify(auth).eligible(actor,false);verify(quotes).observe(actor,quote);verify(verification).observe(actor,quote,venue);verify(holds).conversionRefusal(actor,hold,1,now.plusSeconds(240),false);verify(repo,atLeastOnce()).now();verifyNoMoreInteractions(auth,quotes,verification,holds,repo);}
 @Test void unverifiedAndMissingHoldFailWithoutCreating(){when(verification.observe(actor,quote,venue)).thenReturn(false);assertThat(service.readiness(actor,quote)).containsEntry("eligible",false).containsEntry("reason","VERIFICATION_REQUIRED");verifyNoInteractions(holds);when(verification.observe(actor,quote,venue)).thenReturn(true);when(holds.conversionRefusal(eq(actor),eq(hold),eq(1L),any(),eq(false))).thenReturn("HOLD_UNAVAILABLE");assertThat(service.readiness(actor,quote)).containsEntry("reason","HOLD_UNAVAILABLE");verify(repo,atLeastOnce()).now();verifyNoMoreInteractions(repo);}
 @Test void noQuoteDetailsForUnownedOrIneligibleAccount(){when(quotes.observe(actor,quote)).thenReturn(Optional.empty());assertThatThrownBy(()->service.readiness(actor,quote)).isInstanceOf(BankFailure.class).hasMessage("Quotation not found");verifyNoInteractions(verification,holds);when(auth.eligible(actor,false)).thenReturn(false);assertThatThrownBy(()->service.readiness(actor,quote)).isInstanceOf(BankFailure.class).hasMessage("Current synthetic eligible account required");}
 @Test void clockIsRecheckedAfterVerification(){when(repo.now()).thenReturn(now,now.plusSeconds(600));assertThat(service.readiness(actor,quote)).containsEntry("eligible",false).containsEntry("reason","QUOTE_EXPIRED");verifyNoInteractions(holds);}
 @Test void commandDoesNotUseReadinessAsAuthority(){assertThat(service.readiness(actor,quote)).containsEntry("eligible",true);when(auth.eligible(actor,true)).thenReturn(true);when(repo.command(any(),any(),any())).thenReturn(Optional.empty());when(quotes.accepted(actor,quote,3,facts.hash())).thenReturn(Optional.empty());assertThatThrownBy(()->service.command(actor,null,null,"CREATE",Map.of("quoteId",quote.toString(),"quoteRevision",3,"contentHash",facts.hash(),"commandKey",UUID.randomUUID().toString()))).isInstanceOf(BankFailure.class).hasMessage("Current accepted synthetic BANK quotation required");verify(quotes).accepted(actor,quote,3,facts.hash());verify(holds,never()).convert(any(),any(),anyLong(),any(),any());}

 // These tests exercise orchestration with in-memory doubles, not SQL immutability or S3 versions.
 private BankRepository.Request receiptRequest(UUID request) {
  return new BankRepository.Request(request,actor,venue,quote,hold,policy.current().id(),1,
   "PENDING","PENDING",now.plusSeconds(10),now.plusSeconds(300),now.plusSeconds(20),
   now.plusSeconds(30),now.plusSeconds(40),now.plusSeconds(240),"Asia/Colombo","123","LKR",Map.of());
 }
 private BankRepository.Evidence receiptEvidence(UUID request,UUID claim) {
  return new BankRepository.Evidence(UUID.randomUUID(),request,claim,UUID.randomUUID(),UUID.randomUUID(),
   "READY",now.minusSeconds(2),now.minusSeconds(1),"image/png");
 }
 @Test void firstTwoAllocationsPersistButThirdIsDeniedWithoutChangingAcceptedRecords() {
  UUID request=UUID.randomUUID(),claimId=UUID.randomUUID();var requestRecord=receiptRequest(request);
  var claim=new BankRepository.Claim(claimId,request,1,now);var accepted=new ArrayList<BankRepository.Evidence>();
  when(auth.eligible(actor,true)).thenReturn(true);when(repo.claim(claimId)).thenReturn(Optional.of(claim));
  when(repo.request(request,true)).thenReturn(Optional.of(requestRecord));when(repo.latest(request)).thenReturn(Optional.of(claim));
  when(repo.evidence(claimId)).thenAnswer(call->List.copyOf(accepted));
  doAnswer(call->{int position=call.getArgument(1);assertThat(position).isEqualTo(accepted.size());accepted.add(call.getArgument(0));return null;}).when(repo).allocate(any(),anyInt());
  var commandService=spy(service);doReturn(Map.of("id",request.toString())).when(commandService).view(requestRecord);
  var first=commandService.allocate(actor,claimId,Map.of("revision",1,"commandKey",UUID.randomUUID().toString()));
  var second=commandService.allocate(actor,claimId,Map.of("revision",1,"commandKey",UUID.randomUUID().toString()));
  var before=List.copyOf(accepted);assertThat(before).hasSize(2);
  assertThat(first.get("uploadId")).isEqualTo(before.get(0).id().toString());
  assertThat(second.get("uploadId")).isEqualTo(before.get(1).id().toString());
  assertThatThrownBy(()->commandService.allocate(actor,claimId,Map.of("revision",1,"commandKey",UUID.randomUUID().toString())))
   .isInstanceOfSatisfying(BankFailure.class,f->{assertThat(f.status).isEqualTo(409);assertThat(f).hasMessage("Two images maximum");});
  assertThat(accepted).containsExactlyElementsOf(before);
  verify(repo,times(2)).allocate(any(),anyInt());verify(repo,times(2)).transition(request,"PENDING","PENDING");
  // Existing contract audits successful allocations; the rejected command has no success receipt.
  verify(repo,times(2)).audit(actor,request,"ALLOCATE_UPLOAD","PERMIT");
  verify(repo,times(2)).command(eq(actor),any(),anyString(),anyMap());
  verify(manager).rollback(any());verifyNoInteractions(service.storage);
 }
 @Test void cleanupSuccessDeletesOnlyBoundIdentitiesAuditsAndDoesNotRepeatCompletedCleanup() {
  UUID request=UUID.randomUUID();var e=receiptEvidence(request,UUID.randomUUID());var original=e;
  var maintenancePolicy=policy.current();var requestRecord=receiptRequest(request);
  when(policy.maintenance()).thenReturn(maintenancePolicy);when(repo.request(request,true)).thenReturn(Optional.of(requestRecord));
  when(repo.cleanup()).thenReturn(List.of(e),List.of());
  service.reconcile();service.reconcile();
  var ordered=inOrder(repo,service.storage);
  ordered.verify(repo).evidenceState(e.id(),"DELETION_PENDING");ordered.verify(repo).audit(null,request,"CLEANUP","PENDING");
  ordered.verify(service.storage).delete(e.object());ordered.verify(service.storage).delete(e.preview());
  ordered.verify(repo).evidenceState(e.id(),"DELETED");ordered.verify(repo).audit(null,request,"CLEANUP","LOCAL_OBJECTS_DELETED");
  verify(repo,times(1)).evidenceState(e.id(),"DELETED");verify(repo,never()).evidenceState(any(),eq("CLEANUP_FAILED"));
  // Two maintenance passes both enumerate orphans; neither returns any unrelated object.
  verify(service.storage,times(2)).olderThan(now.minus(policy.current().cleanup()));
  verifyNoMoreInteractions(service.storage);assertThat(e).isEqualTo(original);
 }
 @Test void cleanupFailureRecordsRetryStateWithoutFalseSuccessThenRetriesTheSameIdentities() {
  UUID request=UUID.randomUUID();var e=receiptEvidence(request,UUID.randomUUID());var original=e;
  var maintenancePolicy=policy.current();var requestRecord=receiptRequest(request);
  when(policy.maintenance()).thenReturn(maintenancePolicy);when(repo.request(request,true)).thenReturn(Optional.of(requestRecord));
  when(repo.cleanup()).thenReturn(List.of(e),List.of(e),List.of());
  var failure=new BankFailure(503,"Synthetic cleanup pending");
  doThrow(failure).doNothing().when(service.storage).delete(e.object());
  assertThatCode(service::reconcile).doesNotThrowAnyException();
  var ordered=inOrder(repo,service.storage);
  ordered.verify(repo).evidenceState(e.id(),"DELETION_PENDING");ordered.verify(repo).audit(null,request,"CLEANUP","PENDING");
  ordered.verify(service.storage).delete(e.object());ordered.verify(repo).evidenceState(e.id(),"CLEANUP_FAILED");
  verify(service.storage,never()).delete(e.preview());verify(repo,never()).evidenceState(any(),eq("DELETED"));
  verify(repo,never()).audit(any(),any(),eq("CLEANUP"),eq("LOCAL_OBJECTS_DELETED"));
  assertThat(failure.status).isEqualTo(503);assertThat(failure).hasMessage("Synthetic cleanup pending");
  service.reconcile();service.reconcile();
  verify(service.storage,times(2)).delete(e.object());verify(service.storage).delete(e.preview());
  verify(service.storage,times(3)).olderThan(now.minus(policy.current().cleanup()));verifyNoMoreInteractions(service.storage);
  verify(repo,times(1)).evidenceState(e.id(),"CLEANUP_FAILED");verify(repo,times(1)).evidenceState(e.id(),"DELETED");
  verify(repo,times(2)).audit(null,request,"CLEANUP","PENDING");verify(repo).audit(null,request,"CLEANUP","LOCAL_OBJECTS_DELETED");
  assertThat(e).isEqualTo(original);
 }
}
