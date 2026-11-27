package com.cinenest.identity.internal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
class CustomerCredentialHttpSecurityTest {
    MockHttpServletRequest request(String raw){var r=new MockHttpServletRequest();r.setContentType("application/json");r.setContent(raw.getBytes(StandardCharsets.UTF_8));return r;}
    @Test void duplicateJsonMembersRejected(){assertThatThrownBy(()->CustomerCredentialController.parse(request("{\"commandId\":\"a\",\"commandId\":\"b\"}"))).isInstanceOf(Exception.class);}
    @Test void trailingJsonAndNonObjectDenied(){for(String raw:List.of("{} {}","[]","null"))assertThatThrownBy(()->CustomerCredentialController.parse(request(raw))).isInstanceOf(Exception.class);}
    @Test void oversizedBodyDenied(){assertThatThrownBy(()->CustomerCredentialController.parse(request("{\"x\":\""+"a".repeat(4097)+"\"}"))).isInstanceOf(Exception.class);}
    @Test void malformedUtf8Denied(){var r=request("");r.setContent(new byte[]{'{','"','a','"',':','"',(byte)0xc3,'"','}'});assertThatThrownBy(()->CustomerCredentialController.parse(r)).isInstanceOf(Exception.class);}
    @Test void queryAndContentTypeDenied(){var r=request("{}");r.setParameter("token","private");assertThatThrownBy(()->CustomerCredentialController.parse(r)).isInstanceOf(Exception.class);var other=request("{}");other.setContentType("text/plain");assertThatThrownBy(()->CustomerCredentialController.parse(other)).isInstanceOf(Exception.class);}
    @Test void unknownOrMissingFieldsDenied(){assertThatThrownBy(()->CustomerCredentialController.keys(Map.of("commandId","a","eligibility","ELIGIBLE"),"commandId")).isInstanceOf(IllegalArgumentException.class);}
    @Test void safeResponseIsAlwaysNoStore(){assertThat(CustomerCredentialController.response(503).getHeaders().getCacheControl()).isEqualTo("no-store");assertThat(CustomerCredentialController.response(503).getBody()).isNull();}    CustomerCredentialConfiguration.DeliveryGateway qualified(){return new CustomerCredentialConfiguration.DeliveryGateway(){public boolean qualified(){return true;}public boolean qualificationOnly(){return true;}public CustomerCredentialConfiguration.DeliveryStatus send(CustomerCredentialConfiguration.DeliveryMessage message){throw new AssertionError("No delivery in configuration tests");}};}
    org.springframework.mock.env.MockEnvironment customerEnvironment(){var env=new org.springframework.mock.env.MockEnvironment();env.setActiveProfiles("customer-qualification");return env.withProperty("spring.flyway.enabled","false").withProperty("spring.sql.init.mode","never").withProperty("cinenest.identity.enabled","true").withProperty("cinenest.identity.customer.policy-revision","TEST_POLICY_V1");}
    @Test void missingProtectedProviderPreventsConfiguration(){new org.springframework.boot.test.context.runner.ApplicationContextRunner().withUserConfiguration(CustomerCredentialConfiguration.class).withPropertyValues("cinenest.identity.customer.enabled=true").run(context->{assertThat(context).hasFailed();assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(org.springframework.beans.factory.NoSuchBeanDefinitionException.class);});}
    @Test void missingPrivacyApprovalPreventsConfiguration(){assertThatThrownBy(()->new CustomerCredentialConfiguration().customerCredentialSettings(customerEnvironment(),qualified())).isInstanceOf(IllegalStateException.class);}
    @Test void customerAndSyntheticModesCannotCoexist(){var env=customerEnvironment().withProperty("cinenest.identity.manual.enabled","true");assertThatThrownBy(()->new CustomerCredentialConfiguration().customerCredentialSettings(env,qualified())).isInstanceOf(IllegalStateException.class);}
    @Test void explicitBoundedPoliciesHaveNoProductionDefaults(){assertThatThrownBy(()->new CustomerCredentialConfiguration.Settings("TEST_POLICY_V1","TEST_PRIVACY_V1","TEST_DELIVERY_V1","ASCII_CASEFOLD_V1",0,100,60,2,3600,1,true)).isInstanceOf(IllegalStateException.class);}
    @Test void customerVerificationRequiresPasswordProofField(){assertThatThrownBy(()->CustomerCredentialController.keys(Map.of("commandId",UUID.randomUUID().toString(),"token","a".repeat(64)),"commandId","token","password")).isInstanceOf(IllegalArgumentException.class);}

    @Test void customerModeRejectsLegacyBindingBeforeAnyLegacyCredentialLookup(){
        var store=org.mockito.Mockito.mock(IdentityStore.class);org.mockito.Mockito.when(store.customerMode()).thenReturn(true);
        var legacy=CredentialEpochSessions.authentication(UUID.randomUUID(),0);
        for(boolean lock:List.of(false,true))assertThatThrownBy(()->CredentialEpochSessions.check(legacy,store,lock)).isInstanceOf(CredentialEpochSessions.Denied.class);
        org.mockito.Mockito.verify(store,org.mockito.Mockito.never()).credentialEpoch(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyBoolean());
    }
    @Test void currentCustomerBindingStillRequiresCurrentCredentialEpoch(){
        var store=org.mockito.Mockito.mock(IdentityStore.class);org.mockito.Mockito.when(store.customerMode()).thenReturn(true);var id=UUID.randomUUID();
        org.mockito.Mockito.when(store.customerCredential(id,false)).thenReturn(Optional.of(new IdentityStore.CustomerCredential(id,2,4)));
        assertThatCode(()->CredentialEpochSessions.check(CredentialEpochSessions.customerAuthentication(id,2),store,false)).doesNotThrowAnyException();
        assertThatThrownBy(()->CredentialEpochSessions.check(CredentialEpochSessions.customerAuthentication(id,1),store,false)).isInstanceOf(CredentialEpochSessions.Denied.class);
    }
    @Test void customerModeFilterInvalidatesPersistedLegacyContext(){
        var store=org.mockito.Mockito.mock(IdentityStore.class);org.mockito.Mockito.when(store.customerMode()).thenReturn(true);
        var request=new org.springframework.mock.web.MockHttpServletRequest("GET","/api/accounts/me");var session=new org.springframework.mock.web.MockHttpSession();request.setSession(session);var response=new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(CredentialEpochSessions.authentication(UUID.randomUUID(),0));
        try{CredentialEpochSessions.requests(store).doFilter(request,response,(a,b)->{throw new AssertionError("Legacy context reached customer endpoint");});assertThat(response.getStatus()).isEqualTo(401);assertThat(session.isInvalid()).isTrue();}catch(java.io.IOException|jakarta.servlet.ServletException failure){throw new AssertionError(failure);}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }

}
