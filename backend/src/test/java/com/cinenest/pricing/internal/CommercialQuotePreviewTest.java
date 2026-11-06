package com.cinenest.pricing.internal;

import com.cinenest.booking.QuotationHolds;
import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.rooms.PackageConstraints;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommercialQuotePreviewTest {
    final UUID actor=UUID.randomUUID(),hold=UUID.randomUUID(),venue=UUID.randomUUID(),room=UUID.randomUUID(),pkg=UUID.randomUUID(),version=UUID.randomUUID(),policy=UUID.randomUUID();
    final Instant now=Instant.parse("2026-11-01T00:00:00Z"),start=Instant.parse("2030-02-02T04:30:00Z");
    final QuoteRepository store=mock(QuoteRepository.class);
    final PublicationRepository publications=mock(PublicationRepository.class);
    final DraftRepository drafts=mock(DraftRepository.class);
    final QuotationHolds holds=mock(QuotationHolds.class);
    final PackageConstraints rooms=mock(PackageConstraints.class);
    final ScopedAuthorization identity=mock(ScopedAuthorization.class);
    final CommercialPolicyDraftRepository policies=mock(CommercialPolicyDraftRepository.class);
    final Environment env=mock(Environment.class);
    final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    QuoteService service;Map<String,Object> snapshot;QuotationHolds.Facts facts;
    @BeforeEach void setup(){
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(env.getActiveProfiles()).thenReturn(new String[]{"local-synthetic","synthetic-quotation"});
        service=new QuoteService(store,publications,drafts,holds,rooms,identity,manager,env,true,"SYNTHETIC_FIXED_MINOR_V1",7,"SYNTHETIC",100,60);service.previewPolicies(policies);
        when(identity.eligible(actor,false)).thenReturn(true);when(store.now()).thenReturn(now);
        facts=new QuotationHolds.Facts(hold,actor,venue,room,start,start.plusSeconds(10800),now.plusSeconds(600),"ACTIVE",0,4,"Asia/Colombo",15,start.plusSeconds(11700),"FIXTURE");
        when(holds.observe(actor,hold)).thenAnswer(x->Optional.ofNullable(facts));
        snapshot=new QuoteRulesTest().pkg("OPTIONAL");snapshot.put("roomIds",List.of(room.toString()));snapshot.put("options",List.of());
        when(publications.catalog(venue)).thenReturn(List.of(Map.of("packageId",pkg,"versionId",version,"sessionFrom",start.minusSeconds(3600).toString(),"sessionTo",start.plusSeconds(86400).toString(),"snapshot",snapshot)));
        when(rooms.inspect(venue,Set.of(room),false,false)).thenReturn(Optional.of(new PackageConstraints.Facts(venue,0,UUID.randomUUID(),0,180,List.of(new PackageConstraints.RoomFact(room,5,0,Set.of())))));
        when(policies.previewCandidates(venue,pkg,start,start.plusSeconds(10800))).thenReturn(List.of());
    }
    Map<String,List<String>> query(){var q=new LinkedHashMap<String,List<String>>();q.put("holdId",List.of(hold.toString()));q.put("expectedHoldRevision",List.of("0"));q.put("packageId",List.of(pkg.toString()));q.put("packageVersionId",List.of(version.toString()));q.put("paymentMethod",List.of("BANK"));q.put("optionalServiceIds",List.of(""));return q;}
    CommercialQuotePreview.PolicyCandidate candidate(String status){return new CommercialQuotePreview.PolicyCandidate(policy,1,"a".repeat(64),null,start.minusSeconds(1),null,status,true,true);}
    void status(int expected,org.assertj.core.api.ThrowableAssert.ThrowingCallable action){assertThatThrownBy(action).isInstanceOfSatisfying(DraftFailure.class,e->assertThat(e.status).isEqualTo(expected));}
    @SuppressWarnings("unchecked") List<String> blockers(Map<String,Object> result){return (List<String>)result.get("blockers");}
    @Test void parserSupportsExactCanonicalQueryAndAnExplicitEmptySelection(){var parsed=CommercialQuotePreview.parse(query());assertThat(parsed.hold()).isEqualTo(hold);assertThat(parsed.holdRevision()).isZero();assertThat(parsed.options()).isEmpty();assertThat(parsed.pin()).isNull();}
    @Test void unknownDuplicateMissingAndOversizedQueryAreRejected(){var q=query();q.put("totalMinor",List.of("0"));status(400,()->CommercialQuotePreview.parse(q));q.remove("totalMinor");q.put("holdId",List.of(hold.toString(),hold.toString()));status(400,()->CommercialQuotePreview.parse(q));q.remove("holdId");status(400,()->CommercialQuotePreview.parse(q));var huge=query();huge.put("optionalServiceIds",List.of("x".repeat(8193)));status(400,()->CommercialQuotePreview.parse(huge));}
    @Test void revisionSignsLeadingZeroFractionAndOverflowDoNotCoerce(){for(String bad:List.of("-1","+1","01","1.0"," 0","9223372036854775808")){var q=query();q.put("expectedHoldRevision",List.of(bad));status(400,()->CommercialQuotePreview.parse(q));}}
    @Test void methodsAndUuidFormattingRemainStrict(){var q=query();q.put("paymentMethod",List.of("CARD"));status(400,()->CommercialQuotePreview.parse(q));var bad=query();bad.put("holdId",List.of("0-0-0-0-1"));status(400,()->CommercialQuotePreview.parse(bad));}
    @Test void duplicateUnavailableAndMalformedOptionalIdsDoNotPass(){for(String v:List.of(QuoteRulesTest.SERVICE+","+QuoteRulesTest.SERVICE,QuoteRulesTest.SERVICE+",","not-an-id")){var q=query();q.put("optionalServiceIds",List.of(v));status(400,()->CommercialQuotePreview.parse(q));}var q=query();q.put("optionalServiceIds",List.of(UUID.randomUUID().toString()));status(400,()->service.preview(actor,q));}
    @Test void policyPinsAreCompleteAndStrict(){var q=query();q.put("policyId",List.of(policy.toString()));status(400,()->CommercialQuotePreview.parse(q));q.put("policyRevision",List.of("1"));q.put("policyContentHash",List.of("a".repeat(64)));assertThat(CommercialQuotePreview.parse(q).pin().id()).isEqualTo(policy);q.put("policyRevision",List.of("0"));status(400,()->CommercialQuotePreview.parse(q));q.put("policyRevision",List.of("1"));q.put("policyContentHash",List.of("A".repeat(64)));status(400,()->CommercialQuotePreview.parse(q));}
    @Test void missingPolicyStillHasOnlyNonpayableKnownComponents(){var result=service.preview(actor,query());assertThat(result).containsEntry("knownComponentsMinor","250000").containsEntry("classification",CommercialQuotePreview.CLASSIFICATION).containsEntry("commerciallyEligible",false).containsEntry("commercialIssuanceEnabled",false).containsEntry("checkoutEnabled",false).containsEntry("customerAccessEnabled",false).containsEntry("policyCandidate",null);assertThat(result).doesNotContainKeys("totalMinor","quoteId","state","reviewerId","grantId");assertThat(blockers(result)).contains("POLICY_NOT_CONFIGURED","TAX_TREATMENT_NOT_OPERATIONALLY_APPROVED","PAYMENT_FEES_NOT_OPERATIONALLY_APPROVED");verify(manager).getTransaction(argThat(definition->definition.isReadOnly()&&definition.getIsolationLevel()==org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ));verify(identity,never()).eligible(any(),eq(true));verify(holds,never()).lockAndInspect(any(),any());verify(publications,never()).lock(any(),any());verify(store,never()).audit(any(),any(),any(),any());verify(store,never()).create(any());verify(store,never()).append(any(),any());verify(store,never()).policy(any(),any(),anyLong(),any());}
    @Test void evenReviewedCompletePolicyCannotBecomeCommercialAuthority(){when(policies.previewCandidates(any(),any(),any(),any())).thenReturn(List.of(candidate("APPROVED")));var result=service.preview(actor,query());assertThat(((Map<?,?>)result.get("policyCandidate")).get("draftReviewStatus")).isEqualTo("APPROVED");assertThat(blockers(result)).contains("POLICY_DRAFT_NOT_ACTIVE","COMMERCIAL_ACTIVATION_NOT_AUTHORIZED");assertThat(result.get("commerciallyEligible")).isEqualTo(false);}
    @Test void ambiguousPolicyHasNoArbitraryCurrentCandidate(){when(policies.previewCandidates(any(),any(),any(),any())).thenReturn(List.of(candidate("APPROVED"),new CommercialQuotePreview.PolicyCandidate(UUID.randomUUID(),1,"b".repeat(64),pkg,start,null,"DRAFT",true,false)));var result=service.preview(actor,query());assertThat(result.get("policyCandidate")).isNull();assertThat(blockers(result)).contains("POLICY_AMBIGUOUS");}
    @Test void matchingPinWorksWhileStaleHashRevisionIdentityAndScopeFailClosed(){when(policies.previewCandidates(any(),any(),any(),any())).thenReturn(List.of(candidate("APPROVED")));var q=query();q.put("policyId",List.of(policy.toString()));q.put("policyRevision",List.of("1"));q.put("policyContentHash",List.of("a".repeat(64)));assertThat(service.preview(actor,q).get("policyCandidate")).isNotNull();for(String field:List.of("policyId","policyRevision","policyContentHash")){var stale=new LinkedHashMap<>(q);stale.put(field,List.of(field.equals("policyId")?UUID.randomUUID().toString():field.equals("policyRevision")?"2":"b".repeat(64)));status(409,()->service.preview(actor,stale));}when(policies.previewCandidates(any(),any(),any(),any())).thenReturn(List.of(new CommercialQuotePreview.PolicyCandidate(policy,1,"a".repeat(64),UUID.randomUUID(),start,null,"APPROVED",true,true)));status(409,()->service.preview(actor,q));}
    @Test void wrongOwnerAndMissingHoldAreNotDisclosed(){facts=new QuotationHolds.Facts(hold,UUID.randomUUID(),venue,room,start,start.plusSeconds(10800),now.plusSeconds(600),"ACTIVE",0,4,"Asia/Colombo",15,start.plusSeconds(11700),"FIXTURE");status(404,()->service.preview(actor,query()));facts=null;status(404,()->service.preview(actor,query()));verifyNoInteractions(publications,policies);}
    @Test void expiryBoundaryAndRevisionChangeDenyPreview(){facts=new QuotationHolds.Facts(hold,actor,venue,room,start,start.plusSeconds(10800),now,"ACTIVE",0,4,"Asia/Colombo",15,start.plusSeconds(11700),"FIXTURE");status(409,()->service.preview(actor,query()));facts=new QuotationHolds.Facts(hold,actor,venue,room,start,start.plusSeconds(10800),now.plusSeconds(600),"ACTIVE",1,4,"Asia/Colombo",15,start.plusSeconds(11700),"FIXTURE");status(409,()->service.preview(actor,query()));}
    @Test void holdChangingDuringObservationIsRejected(){var original=facts;var changed=new QuotationHolds.Facts(hold,actor,venue,room,start,start.plusSeconds(10800),now.plusSeconds(600),"RELEASED",1,4,"Asia/Colombo",15,start.plusSeconds(11700),"FIXTURE");when(holds.observe(actor,hold)).thenReturn(Optional.of(original),Optional.of(changed));status(409,()->service.preview(actor,query()));}
    @Test void withdrawnOrForeignPackageVersionCannotBePreviewed(){when(publications.catalog(venue)).thenReturn(List.of());status(409,()->service.preview(actor,query()));verifyNoInteractions(policies);}
    @Test void roomCapacityAndOperatingConfigurationAreStillAuthoritative(){when(rooms.inspect(venue,Set.of(room),false,false)).thenReturn(Optional.of(new PackageConstraints.Facts(venue,0,UUID.randomUUID(),0,180,List.of(new PackageConstraints.RoomFact(room,3,0,Set.of())))));status(409,()->service.preview(actor,query()));when(rooms.inspect(venue,Set.of(room),false,false)).thenReturn(Optional.empty());status(409,()->service.preview(actor,query()));}
    @Test void eligibilityDenialPrecedesHoldAndPolicyReads(){when(identity.eligible(actor,false)).thenReturn(false);status(403,()->service.preview(actor,query()));verifyNoInteractions(holds,policies,publications);}
    @Test void productionOrDisabledConfigurationNeverReadsPreviewDependencies(){when(env.getActiveProfiles()).thenReturn(new String[]{"production"});status(503,()->service.preview(actor,query()));verifyNoInteractions(identity,holds,policies,publications,store);}
    @Test void policyReadOutageIsNotAnEmptyOrZeroPolicy(){when(policies.previewCandidates(any(),any(),any(),any())).thenReturn(null);status(503,()->service.preview(actor,query()));}
    @Test void unknownEffectiveDateAndIncompleteReviewsRemainExplicit(){when(policies.previewCandidates(any(),any(),any(),any())).thenReturn(List.of(new CommercialQuotePreview.PolicyCandidate(policy,1,"a".repeat(64),null,null,null,"INCOMPLETE",false,false)));assertThat(blockers(service.preview(actor,query()))).contains("POLICY_EFFECTIVE_PERIOD_UNRESOLVED","POLICY_DRAFT_INCOMPLETE","POLICY_DRAFT_REVIEWS_INCOMPLETE");}
    @Test void knownComponentOverflowIsNotWrappedOrAssumedZero(){snapshot.put("basePriceMinor",Long.toString(Long.MAX_VALUE));snapshot.put("extraGuestPriceMinor","1");facts=new QuotationHolds.Facts(hold,actor,venue,room,start,start.plusSeconds(10800),now.plusSeconds(600),"ACTIVE",0,5,"Asia/Colombo",15,start.plusSeconds(11700),"FIXTURE");status(400,()->service.preview(actor,query()));}
}
