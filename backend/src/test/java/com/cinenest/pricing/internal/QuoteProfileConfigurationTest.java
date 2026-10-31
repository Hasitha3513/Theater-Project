package com.cinenest.pricing.internal;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Local synthetic configuration regression; does not qualify commercial issuance. */
class QuoteProfileConfigurationTest extends VerificationHandoffTest {
    static Stream<Arguments> approvedProfiles() {
        return Stream.of(
            Arguments.of((Object)new String[]{"synthetic-quotation"}),
            Arguments.of((Object)new String[]{"local-synthetic","synthetic-quotation"}),
            Arguments.of((Object)new String[]{"synthetic-quotation","local-synthetic"}));
    }
    static Stream<Arguments> deniedProfiles() {
        return Stream.of(new String[]{},new String[]{"default"},new String[]{"production"},
            new String[]{"prod"},new String[]{"local-synthetic"},
            new String[]{"synthetic-quotation","production"},
            new String[]{"local-synthetic","synthetic-quotation","production"},
            new String[]{"local-synthetic","synthetic-quotation","unrelated"},
            new String[]{"synthetic-quotation","unrelated"})
            .map(profiles->Arguments.of((Object)profiles));
    }
    @ParameterizedTest @MethodSource("approvedProfiles")
    void exactApprovedProfilesAllowTheExistingSyntheticRead(String[] profiles) {
        when(env.getActiveProfiles()).thenReturn(profiles);
        assertThat(service.read(actor,id)).containsEntry("state","ACCEPTED");
        verify(store,never()).accept(any(),any(),any());
        verify(store,never()).append(any(),any());
    }
    @ParameterizedTest @MethodSource("deniedProfiles")
    void everyOtherCombinationFailsBeforeAuthorizationOrPersistence(String[] profiles) {
        when(env.getActiveProfiles()).thenReturn(profiles);
        assertThatThrownBy(()->service.read(actor,id)).isInstanceOfSatisfying(DraftFailure.class,e->assertThat(e.status).isEqualTo(503));
        verifyNoInteractions(identity,store,holds,publications);
    }
    @ParameterizedTest @MethodSource("approvedProfiles")
    void approvedProfilesDoNotBypassCurrentEligibility(String[] profiles) {
        when(env.getActiveProfiles()).thenReturn(profiles);
        when(identity.eligible(actor,true)).thenReturn(false);
        assertThatThrownBy(()->service.read(actor,id)).isInstanceOfSatisfying(DraftFailure.class,e->assertThat(e.status).isEqualTo(403));
        verifyNoInteractions(store,holds,publications);
    }
    @ParameterizedTest @MethodSource("approvedProfiles")
    void approvedProfilesDoNotReviveAnExpiredHold(String[] profiles) {
        when(env.getActiveProfiles()).thenReturn(profiles);
        facts=facts("EXPIRED",2,now.minusSeconds(1));
        assertThat(service.verificationContext(actor,id)).isEmpty();
        verify(store,never()).accept(any(),any(),any());
    }
    static Stream<Arguments> unavailablePolicies() {
        return Stream.of(Arguments.of(false,"SYNTHETIC_FIXED_MINOR_V1",7L,"SYNTHETIC",100,60),
            Arguments.of(true,"COMMERCIAL",7L,"SYNTHETIC",100,60),
            Arguments.of(true,"SYNTHETIC_FIXED_MINOR_V1",-1L,"SYNTHETIC",100,60),
            Arguments.of(true,"SYNTHETIC_FIXED_MINOR_V1",7L,"",100,60),
            Arguments.of(true,"SYNTHETIC_FIXED_MINOR_V1",7L,"SYNTHETIC",0,60),
            Arguments.of(true,"SYNTHETIC_FIXED_MINOR_V1",7L,"SYNTHETIC",100,0));
    }
    @ParameterizedTest @MethodSource("unavailablePolicies")
    void combinedProfilesRetainEveryExistingSyntheticPolicyGate(boolean enabled,String policy,long tax,String reference,int rate,int seconds) {
        when(env.getActiveProfiles()).thenReturn(new String[]{"local-synthetic","synthetic-quotation"});
        var candidate=new QuoteService(store,publications,drafts,holds,rooms,identity,mock(PlatformTransactionManager.class),env,enabled,policy,tax,reference,rate,seconds);
        assertThatThrownBy(()->candidate.read(actor,id)).isInstanceOfSatisfying(DraftFailure.class,e->assertThat(e.status).isEqualTo(503));
        verifyNoInteractions(identity,store,holds,publications);
    }
}
