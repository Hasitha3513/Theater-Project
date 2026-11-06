package com.cinenest.pricing.internal;
import java.util.*;
import com.cinenest.identity.ScopedAuthorization;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class CommercialPolicyDraftServiceTest {
 final UUID actor=UUID.randomUUID(),venue=UUID.randomUUID();
 final ScopedAuthorization authorization=mock(ScopedAuthorization.class);
 final CommercialPolicyDraftRepository repository=mock(CommercialPolicyDraftRepository.class);
 final CommercialPolicyDraftService service=new CommercialPolicyDraftService(authorization,repository);
 Map<String,Object> create(){return new LinkedHashMap<>(Map.of("idempotencyKey",UUID.randomUUID().toString(),"reason","Synthetic test","scopeKind","VENUE","document",CommercialPolicyDraftRulesTest.draft()));}
 @Test void missingMatchedAuthorityNeverTouchesPersistence(){when(authorization.matchedGrant(actor,venue,"commercial.policy.edit",true)).thenReturn(Optional.empty());assertThatThrownBy(()->service.mutate(actor,venue,null,"CREATE",create())).isInstanceOf(CommercialPolicyDraft.Failure.class).extracting("status").isEqualTo(403);verifyNoInteractions(repository);}
 @Test void venueSubstitutionNeverTouchesPersistence(){var wrong=new ScopedAuthorization.MatchedGrant(actor,0,0,UUID.randomUUID(),UUID.randomUUID(),0,"commercial.policy.edit");when(authorization.matchedGrant(actor,venue,"commercial.policy.edit",true)).thenReturn(Optional.of(wrong));assertThatThrownBy(()->service.mutate(actor,venue,null,"CREATE",create())).isInstanceOf(CommercialPolicyDraft.Failure.class);verifyNoInteractions(repository);}
 @Test void privilegedInputsAreRejected(){var p=create();p.put("grantId",UUID.randomUUID().toString());assertThatThrownBy(()->service.mutate(actor,venue,null,"CREATE",p)).isInstanceOf(CommercialPolicyDraft.Failure.class);verifyNoInteractions(repository);}
 @Test void reviewReferenceDoesNotCreateAuthority(){var p=Map.<String,Object>of("idempotencyKey",UUID.randomUUID().toString(),"reason","Synthetic","expectedRevision","1","expectedContentHash","a".repeat(64),"domain","LEGAL","outcome","APPROVE","evidenceReference","OWNER-APPROVED");when(authorization.matchedGrant(actor,venue,"commercial.policy.review.legal",true)).thenReturn(Optional.empty());assertThatThrownBy(()->service.mutate(actor,venue,UUID.randomUUID(),"REVIEW",p)).isInstanceOf(CommercialPolicyDraft.Failure.class).extracting("status").isEqualTo(403);verifyNoInteractions(repository);}
 @Test void defaultPortFailsClosed(){var port=new ScopedAuthorization(){public boolean eligible(UUID a){return false;}public boolean eligible(UUID a,boolean l){return false;}public boolean permits(UUID a,UUID v,String p,boolean l){return false;}public Set<UUID> venues(UUID a,String p){return Set.of();}};assertThat(port.matchedGrant(actor,venue,"commercial.policy.read",false)).isEmpty();}
 @Test void readsNeedIndependentReadPermission(){when(authorization.matchedGrant(actor,venue,"commercial.policy.read",false)).thenReturn(Optional.empty());assertThatThrownBy(()->service.list(actor,venue,"",0,24)).isInstanceOf(CommercialPolicyDraft.Failure.class);verifyNoInteractions(repository);}
}
