package com.cinenest.pricing.internal;

import com.cinenest.booking.QuotationHolds;
import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.rooms.PackageConstraints;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Synthetic collaborator tests, not PostgreSQL/production qualification. */
class VerificationHandoffTest {
    final UUID actor=UUID.randomUUID(),id=UUID.randomUUID(),hold=UUID.randomUUID(),venue=UUID.randomUUID(),room=UUID.randomUUID(),pkg=UUID.randomUUID();
    final Instant now=Instant.parse("2026-10-19T12:00:00Z");
    final QuoteRepository store=mock(QuoteRepository.class);
    final PublicationRepository publications=mock(PublicationRepository.class);
    final DraftRepository drafts=mock(DraftRepository.class);
    final QuotationHolds holds=mock(QuotationHolds.class);
    final PackageConstraints rooms=mock(PackageConstraints.class);
    final ScopedAuthorization identity=mock(ScopedAuthorization.class);
    final Environment env=mock(Environment.class);
    QuoteService service;
    QuoteRepository.Revision revision;
    QuotationHolds.Facts facts;
    @BeforeEach void setup(){
        var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(x->new SimpleTransactionStatus());
        when(env.getActiveProfiles()).thenReturn(new String[]{"synthetic-quotation"});
        service=new QuoteService(store,publications,drafts,holds,rooms,identity,manager,env,true,"SYNTHETIC_FIXED_MINOR_V1",7,"SYNTHETIC TEST",100,60);
        when(identity.eligible(actor,true)).thenReturn(true);
        var q=new QuoteRepository.Quote(id,actor,hold,venue,room,pkg,1);
        when(store.quote(eq(id),anyBoolean())).thenReturn(Optional.of(q));
        when(store.accepted(id)).thenReturn(Optional.of(1L));when(store.now()).thenReturn(now);
        revision=new QuoteRepository.Revision(1,UUID.randomUUID(),UUID.randomUUID(),1,now.plusSeconds(60),"BANK",Map.of("package",Map.of("roomIds",List.of(room.toString()),"options",List.of())),"0".repeat(64));
        when(store.revision(id,1)).thenAnswer(x->revision);
        facts=facts("ACTIVE",1,now.plusSeconds(60));
        when(holds.lockAndInspect(actor,hold)).thenAnswer(x->Optional.ofNullable(facts));
        when(rooms.inspect(venue,Set.of(room),false,false)).thenReturn(Optional.of(new PackageConstraints.Facts(venue,1,UUID.randomUUID(),1,180,List.of(new PackageConstraints.RoomFact(room,5,1,Set.of())))));
        when(drafts.options(venue)).thenReturn(List.of());
    }
    QuotationHolds.Facts facts(String state,long revision,Instant expiry){return new QuotationHolds.Facts(hold,actor,venue,room,now.plusSeconds(600),now.plusSeconds(11400),expiry,state,revision,2,"Asia/Colombo",7,now.plusSeconds(11820),"SYNTHETIC");}
    @SuppressWarnings("unchecked") Map<String,Object> eligibility(){return (Map<String,Object>)service.read(actor,id).get("verificationHandoff");}
    void refused(String reason){assertThat(eligibility()).containsEntry("eligible",false).containsEntry("reason",reason);assertThat(service.verificationContext(actor,id)).isEmpty();verify(store,never()).accept(any(),any(),any());}
    @Test void eligibleBankAndReadUseSameRulesWithoutChangingHistory(){
        assertThat(eligibility()).containsEntry("eligible",true).containsEntry("reason","ELIGIBLE");
        assertThat(new VerificationQuotationAdapter(service).ownedSynthetic(actor,id)).isPresent();
        assertThat(service.read(actor,id).get("state")).isEqualTo("ACCEPTED");
        verify(store,never()).accept(any(),any(),any());verify(store,never()).append(any(),any());
        var order=inOrder(identity,publications,holds,store);
        order.verify(identity).eligible(actor,true);order.verify(store).quote(id,false);order.verify(publications).lock(venue,pkg);order.verify(holds).lockAndInspect(actor,hold);order.verify(store).quote(id,true);
    }
    @ParameterizedTest @ValueSource(strings={"RELEASED","INVALIDATED"}) void terminalHoldsNeverRevive(String state){facts=facts(state,1,now.plusSeconds(60));refused("HOLD_INELIGIBLE");assertThat(service.read(actor,id).get("state")).isEqualTo("ACCEPTED");}
    @Test void reconciledExpiredHoldIsStillRefused(){facts=facts("EXPIRED",2,now.minusSeconds(1));refused("HOLD_EXPIRED");}
    @Test void missingHoldFailsClosed(){facts=null;refused("HOLD_UNAVAILABLE");}
    @Test void holdRevisionChanged(){facts=facts("ACTIVE",2,now.plusSeconds(60));refused("HOLD_CHANGED");}
    @Test void holdDeadlineIsExclusive(){facts=facts("ACTIVE",1,now);refused("HOLD_EXPIRED");}
    @Test void quoteDeadlineIsExclusive(){revision=new QuoteRepository.Revision(1,revision.version(),revision.policy(),1,now,"BANK",revision.snapshot(),revision.hash());refused("QUOTE_EXPIRED");}
    @Test void issuedQuoteIsNotAdmission(){when(store.accepted(id)).thenReturn(Optional.empty());refused("QUOTE_NOT_ACCEPTED");}
    @Test void acceptedRevisionMustBeCurrent(){when(store.accepted(id)).thenReturn(Optional.of(2L));refused("QUOTE_REVISION_CHANGED");}
    @Test void gatewayIsNotBank(){revision=new QuoteRepository.Revision(1,revision.version(),revision.policy(),1,revision.expires(),"GATEWAY",revision.snapshot(),revision.hash());refused("BANK_METHOD_REQUIRED");}
    @Test void operationalCompatibilityIsRechecked(){when(rooms.inspect(venue,Set.of(room),false,false)).thenReturn(Optional.empty());refused("PACKAGE_INCOMPATIBLE");}
    @Test void withdrawalDoesNotRepriceOrRequireCurrentCatalog(){assertThat(service.verificationContext(actor,id)).isPresent();verify(publications,never()).catalog(any());}
    @Test void noForeignOwnerOrMissingQuoteInformation(){when(store.quote(eq(id),anyBoolean())).thenReturn(Optional.empty());assertThat(service.verificationContext(actor,id)).isEmpty();assertThatThrownBy(()->service.read(actor,id)).isInstanceOf(DraftFailure.class).hasMessage("Quote not found");verifyNoInteractions(holds);}
    @Test void currentAccountRequired(){when(identity.eligible(actor,true)).thenReturn(false);assertThat(new VerificationQuotationAdapter(service).ownedSynthetic(actor,id)).isEmpty();verifyNoInteractions(holds);}
    @Test void stateChangesAfterReadAreRevalidated(){assertThat(eligibility()).containsEntry("eligible",true);facts=facts("INVALIDATED",2,now.plusSeconds(60));assertThat(service.verificationContext(actor,id)).isEmpty();}
    @Test void clockIsRecheckedAfterCompatibility(){when(store.now()).thenReturn(now,now,now.plusSeconds(60));assertThat(service.verificationContext(actor,id)).isEmpty();}
}
