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
  service=new BankService(mock(LedgerService.class),repo,auth,quotes,holds,verification,policy,mock(PrivateEvidenceStore.class),mock(SyntheticEvidenceProcessor.class),mock(ReviewerNotice.class),mock(com.cinenest.notifications.NotificationFacts.class),manager);
  facts=new BankQuotation.Facts(quote,actor,venue,hold,3,1,"a".repeat(64),now.plusSeconds(600),Map.of("synthetic",true,"commercialIssuance",false,"currency","LKR","sessionStart",now.plusSeconds(3600).toString(),"sessionEnd",now.plusSeconds(7200).toString(),"timezone","Asia/Colombo","totalMinor",123L,"privateInternal","MUST_NOT_LEAK"));
  when(auth.eligible(actor,false)).thenReturn(true);when(quotes.observe(actor,quote)).thenAnswer(x->Optional.of(new BankQuotation.Observation(facts,"ELIGIBLE")));when(verification.observe(actor,quote,venue)).thenReturn(true);when(repo.now()).thenReturn(now);when(holds.conversionRefusal(eq(actor),eq(hold),eq(1L),any(),eq(false))).thenReturn("ELIGIBLE");
 }
 @Test void usesOnlyObservationPortsAndReadOnlyRepeatableRead(){var result=service.readiness(actor,quote);assertThat(result).containsEntry("eligible",true).containsEntry("quoteRevision",3L).containsEntry("contentHash",facts.hash()).containsEntry("verificationReadiness","ELIGIBLE").containsEntry("paymentVerified",false).containsEntry("bookingConfirmed",false);assertThat(result.toString()).doesNotContain("MUST_NOT_LEAK","evidence","grants");verify(manager).getTransaction(argThat(d->d.isReadOnly()&&d.getIsolationLevel()==TransactionDefinition.ISOLATION_REPEATABLE_READ));verify(auth).eligible(actor,false);verify(quotes).observe(actor,quote);verify(verification).observe(actor,quote,venue);verify(holds).conversionRefusal(actor,hold,1,now.plusSeconds(240),false);verify(repo,atLeastOnce()).now();verifyNoMoreInteractions(auth,quotes,verification,holds,repo);}
 @Test void unverifiedAndMissingHoldFailWithoutCreating(){when(verification.observe(actor,quote,venue)).thenReturn(false);assertThat(service.readiness(actor,quote)).containsEntry("eligible",false).containsEntry("reason","VERIFICATION_REQUIRED");verifyNoInteractions(holds);when(verification.observe(actor,quote,venue)).thenReturn(true);when(holds.conversionRefusal(eq(actor),eq(hold),eq(1L),any(),eq(false))).thenReturn("HOLD_UNAVAILABLE");assertThat(service.readiness(actor,quote)).containsEntry("reason","HOLD_UNAVAILABLE");verify(repo,atLeastOnce()).now();verifyNoMoreInteractions(repo);}
 @Test void noQuoteDetailsForUnownedOrIneligibleAccount(){when(quotes.observe(actor,quote)).thenReturn(Optional.empty());assertThatThrownBy(()->service.readiness(actor,quote)).isInstanceOf(BankFailure.class).hasMessage("Quotation not found");verifyNoInteractions(verification,holds);when(auth.eligible(actor,false)).thenReturn(false);assertThatThrownBy(()->service.readiness(actor,quote)).isInstanceOf(BankFailure.class).hasMessage("Current synthetic eligible account required");}
 @Test void clockIsRecheckedAfterVerification(){when(repo.now()).thenReturn(now,now.plusSeconds(600));assertThat(service.readiness(actor,quote)).containsEntry("eligible",false).containsEntry("reason","QUOTE_EXPIRED");verifyNoInteractions(holds);}
 @Test void commandDoesNotUseReadinessAsAuthority(){assertThat(service.readiness(actor,quote)).containsEntry("eligible",true);when(auth.eligible(actor,true)).thenReturn(true);when(repo.command(any(),any(),any())).thenReturn(Optional.empty());when(quotes.accepted(actor,quote,3,facts.hash())).thenReturn(Optional.empty());assertThatThrownBy(()->service.command(actor,null,null,"CREATE",Map.of("quoteId",quote.toString(),"quoteRevision",3,"contentHash",facts.hash(),"commandKey",UUID.randomUUID().toString()))).isInstanceOf(BankFailure.class).hasMessage("Current accepted synthetic BANK quotation required");verify(quotes).accepted(actor,quote,3,facts.hash());verify(holds,never()).convert(any(),any(),anyLong(),any(),any());}
}
