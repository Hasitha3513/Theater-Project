package com.cinenest.pricing.internal;
import com.cinenest.booking.QuotationHolds;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BankQuotationObservationTest {
 final UUID actor=UUID.randomUUID(),id=UUID.randomUUID(),hold=UUID.randomUUID(),venue=UUID.randomUUID(),pkg=UUID.randomUUID();
 final Instant now=Instant.parse("2026-10-19T04:30:00Z");
 final QuoteRepository repo=mock(QuoteRepository.class);final PublicationRepository publications=mock(PublicationRepository.class);final QuotationHolds holds=mock(QuotationHolds.class);
 final BankQuotationAdapter adapter=new BankQuotationAdapter(repo,publications,holds);
 QuoteRepository.Revision revision;
 @BeforeEach void setup(){when(repo.quote(eq(id),anyBoolean())).thenReturn(Optional.of(new QuoteRepository.Quote(id,actor,hold,venue,UUID.randomUUID(),pkg,2)));revision=new QuoteRepository.Revision(2,UUID.randomUUID(),UUID.randomUUID(),1,now.plusSeconds(600),"BANK",Map.of("synthetic",true),"a".repeat(64));when(repo.revision(id,2)).thenAnswer(x->revision);when(repo.accepted(id)).thenReturn(Optional.of(2L));when(repo.now()).thenReturn(now);}
 String reason(){return adapter.observe(actor,id).orElseThrow().reason();}
 @Test void observationUsesCurrentOwnerTermsWithoutLocksOrCatalog(){var result=adapter.observe(actor,id).orElseThrow();assertThat(result.reason()).isEqualTo("ELIGIBLE");assertThat(result.facts().revision()).isEqualTo(2);assertThat(result.facts().hash()).isEqualTo(revision.hash());verify(repo).quote(id,false);verifyNoInteractions(publications,holds);verify(repo,never()).quote(id,true);}
 @Test void wrongOwnerAndUnknownAreIndistinguishable(){assertThat(adapter.observe(UUID.randomUUID(),id)).isEmpty();when(repo.quote(id,false)).thenReturn(Optional.empty());assertThat(adapter.observe(actor,id)).isEmpty();verifyNoInteractions(publications,holds);}
 @Test void unacceptedAndStaleAcceptanceAreRefused(){when(repo.accepted(id)).thenReturn(Optional.empty());assertThat(reason()).isEqualTo("QUOTE_NOT_ACCEPTED");when(repo.accepted(id)).thenReturn(Optional.of(1L));assertThat(reason()).isEqualTo("QUOTE_NOT_ACCEPTED");}
 @Test void expiryAndBankMethodAreRequired(){revision=new QuoteRepository.Revision(2,revision.version(),revision.policy(),1,now,"BANK",revision.snapshot(),revision.hash());assertThat(reason()).isEqualTo("QUOTE_EXPIRED");revision=new QuoteRepository.Revision(2,revision.version(),revision.policy(),1,now.plusSeconds(600),"GATEWAY",revision.snapshot(),revision.hash());assertThat(reason()).isEqualTo("BANK_METHOD_REQUIRED");}
 @Test void commandRejectsClientRevisionAndHashWhileKeepingLockOrder(){assertThat(adapter.accepted(actor,id,1,revision.hash())).isEmpty();assertThat(adapter.accepted(actor,id,2,"b".repeat(64))).isEmpty();var order=inOrder(repo,publications,holds);order.verify(repo).quote(id,false);order.verify(publications).lock(venue,pkg);order.verify(holds).lockAndInspect(actor,hold);order.verify(repo).quote(id,true);}
}
