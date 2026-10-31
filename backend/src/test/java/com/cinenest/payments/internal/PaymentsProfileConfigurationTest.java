package com.cinenest.payments.internal;

import com.cinenest.identity.ScopedAuthorization;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Offline configuration/security regression; never authorizes real receipts or banking. */
class PaymentsProfileConfigurationTest {
    static Stream<Arguments> approvedProfiles(){return Stream.of(
        Arguments.of((Object)new String[]{"synthetic-quotation"}),
        Arguments.of((Object)new String[]{"local-synthetic","synthetic-quotation"}),
        Arguments.of((Object)new String[]{"synthetic-quotation","local-synthetic"}));}
    static Stream<Arguments> deniedProfiles(){return Stream.of(new String[]{},new String[]{"default"},new String[]{"prod"},new String[]{"production"},new String[]{"local-synthetic"},new String[]{"unknown"},new String[]{"synthetic-quotation","unknown"},new String[]{"local-synthetic","synthetic-quotation","production"},new String[]{"synthetic-quotation","local-synthetic","unknown"}).map(p->Arguments.of((Object)p));}
    MockEnvironment environment(String[] profiles){
        var e=new MockEnvironment();e.setActiveProfiles(profiles);
        e.withProperty("cinenest.bank.enabled","true").withProperty("cinenest.bank.storage-root","/tmp/cinenest-offline-bank-profile-test").withProperty("cinenest.bank.encryption-key",Base64.getEncoder().encodeToString(new byte[32])).withProperty("cinenest.bank.policy-version","SYNTHETIC-PROFILE-TEST").withProperty("cinenest.bank.review-opens","00:00").withProperty("cinenest.bank.review-closes","23:59").withProperty("cinenest.bank.fixture-sha256",String.join(",",SyntheticFixtures.hashes())).withProperty("cinenest.bank.instructions.enabled","true").withProperty("cinenest.bank.instructions.mode","STAGING_ONLY");
        for(String n:List.of("lead","request","initiation","submission","review","hold","escalation","upload","retention","cleanup"))e.withProperty("cinenest.bank."+n+"-seconds","60");return e;
    }
    BankInstructions instructions(MockEnvironment e,ScopedAuthorization auth){return new BankInstructions(mock(InstructionRepository.class),mock(InstructionResolver.class),auth,e,mock(PlatformTransactionManager.class));}
    void unavailable(Runnable action,int status){assertThatThrownBy(action::run).isInstanceOfSatisfying(BankFailure.class,e->assertThat(e.status).isEqualTo(status));}
    @ParameterizedTest @MethodSource("approvedProfiles") void exactApprovedProfilesPermitExistingSyntheticConfiguration(String[] profiles){var e=environment(profiles);assertThat(new BankPolicy(e).current().version()).isEqualTo("SYNTHETIC-PROFILE-TEST");assertThatCode(()->instructions(e,mock(ScopedAuthorization.class)).configured()).doesNotThrowAnyException();}
    @ParameterizedTest @MethodSource("deniedProfiles") void otherProfilesDenyBothPolicies(String[] profiles){var e=environment(profiles);unavailable(()->new BankPolicy(e).current(),503);unavailable(()->new BankPolicy(e).maintenance(),503);unavailable(()->instructions(e,mock(ScopedAuthorization.class)).configured(),503);}
    @ParameterizedTest @MethodSource("approvedProfiles") void instructionsRetainDisabledAndStagingOnlyGates(String[] profiles){var e=environment(profiles);e.withProperty("cinenest.bank.instructions.enabled","false");unavailable(()->instructions(e,mock(ScopedAuthorization.class)).configured(),404);e.withProperty("cinenest.bank.instructions.enabled","true");for(String mode:List.of("LIVE","PRODUCTION","")){e.withProperty("cinenest.bank.instructions.mode",mode);unavailable(()->instructions(e,mock(ScopedAuthorization.class)).configured(),503);}}
    @ParameterizedTest @MethodSource("approvedProfiles") void syntheticReceiptEncryptionAndCommandGatesRemainRequired(String[] profiles){
        for(var invalid:List.of(Map.entry("cinenest.bank.fixture-sha256","0".repeat(64)),Map.entry("cinenest.bank.encryption-key","invalid-key"),Map.entry("cinenest.bank.encryption-key",Base64.getEncoder().encodeToString(new byte[16])),Map.entry("cinenest.bank.storage-root","relative"),Map.entry("cinenest.bank.policy-version","COMMERCIAL"),Map.entry("cinenest.bank.request-seconds","0"),Map.entry("cinenest.bank.enabled","false"))){var e=environment(profiles);e.withProperty(invalid.getKey(),invalid.getValue());unavailable(()->new BankPolicy(e).current(),503);}
        assertThat(new ReceiptAdmissionPolicy().decision()).isEqualTo(ReceiptAdmissionPolicy.Decision.DENY);unavailable(()->new ReceiptAdmissionPolicy().requireAdmission(),503);
    }
    @ParameterizedTest @MethodSource("approvedProfiles") void profileAcceptanceDoesNotGrantEligibilityOrVenuePermissions(String[] profiles){
        var auth=mock(ScopedAuthorization.class);var actor=UUID.randomUUID();var venue=UUID.randomUUID();var foreign=UUID.randomUUID();var service=instructions(environment(profiles),auth);service.configured();
        assertThat(service.permitted(actor,venue,false,false)).isFalse();when(auth.eligible(actor,false)).thenReturn(true);assertThat(service.permitted(actor,venue,false,false)).isFalse();when(auth.member(actor,venue,false)).thenReturn(true);assertThat(service.permitted(actor,venue,false,false)).isFalse();when(auth.permits(actor,venue,"bank.instructions.read",false)).thenReturn(true);assertThat(service.permitted(actor,venue,false,false)).isTrue();assertThat(service.permitted(actor,foreign,false,false)).isFalse();assertThat(service.permitted(actor,venue,true,false)).isFalse();when(auth.permits(actor,venue,"bank.claim.review",false)).thenReturn(true);assertThat(service.permitted(actor,venue,true,false)).isFalse();when(auth.permits(actor,venue,"bank.evidence.read",false)).thenReturn(true);assertThat(service.permitted(actor,venue,true,false)).isTrue();
        verifyNoInteractions(service.repo,service.resolver);
    }
}
