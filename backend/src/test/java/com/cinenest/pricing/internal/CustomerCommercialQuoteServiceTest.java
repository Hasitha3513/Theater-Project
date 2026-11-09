package com.cinenest.pricing.internal;

import static com.cinenest.pricing.internal.CommercialPolicyActivationPostgresTest.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cinenest.booking.QuotationHolds;
import com.cinenest.identity.ScopedAuthorization;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class CustomerCommercialQuoteServiceTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    @Test
    void customerEndpointRequiresSessionAndCsrfThenFailsClosed() throws Exception {
        var env = new MockEnvironment().withProperty("cinenest.identity.manual.enabled", "true");
        env.setActiveProfiles("local-synthetic");
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setEnvironment(env);
            context.setServletContext(new MockServletContext());
            context.register(SecurityHarness.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
            String route = "/api/commercial-quotes";
            var session = new MockHttpSession();
            var security = SecurityContextHolder.createEmptyContext();
            var epochs = Class.forName("com.cinenest.identity.internal.CredentialEpochSessions");
            var authenticate = epochs.getDeclaredMethod("authentication", UUID.class, long.class);
            authenticate.setAccessible(true);
            security.setAuthentication((Authentication) authenticate.invoke(null, id("customer"), 0L));
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, security);
            mvc.perform(post(route).session(session)).andExpect(status().isForbidden());
            var request = mvc.perform(get("/api/session/csrf").session(session)).andReturn().getRequest();
            var csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            assertThat(csrf).isNotNull();
            mvc.perform(post(route).session(session).header(csrf.getHeaderName(), csrf.getToken()))
                .andExpect(status().isServiceUnavailable());
            mvc.perform(post(route + "/unapproved").session(session).header(csrf.getHeaderName(), csrf.getToken()))
                .andExpect(status().isForbidden());
            mvc.perform(get(route).session(session)).andExpect(status().isForbidden());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    static class SecurityHarness {
        @Bean CustomerCommercialQuoteController controller() {
            return new CustomerCommercialQuoteController(new CustomerCommercialQuoteConfiguration());
        }

        @Bean SecurityFilterChain chain(HttpSecurity http, Environment env) throws Exception {
            // Use the actual Identity boundary without exposing its package-private implementation to Pricing.
            var type = Class.forName("com.cinenest.identity.internal.IdentitySecurity");
            var store = Class.forName("com.cinenest.identity.internal.IdentityStore");
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            var method = type.getDeclaredMethod("identityBoundary", HttpSecurity.class, store, Environment.class,
                ObjectProvider.class, ObjectProvider.class);
            method.setAccessible(true);
            var empty = new DefaultListableBeanFactory().getBeanProvider(Object.class);
            return (SecurityFilterChain) method.invoke(constructor.newInstance(), http, mock(store), env, empty, empty);
        }
    }

    @Test
    void holdOwnershipVenueRoomAndRevisionAreVerified() {
        var setup = new Setup(NOW);
        var repository = mock(JdbcCustomerCommercialQuoteRepository.class);
        when(repository.now()).thenReturn(NOW);
        when(repository.syntheticActor(setup.actor)).thenReturn(true);
        when(repository.syntheticActor(id("finance"))).thenReturn(true);
        var service = setup.service(repository, inlineTransaction());
        rejects(() -> service.qualify(id("finance"), setup.command), 404);
        rejects(() -> service.qualify(setup.actor, setup.command(2,id("venue_B"),setup.room,setup.version,"BANK")),404);
        rejects(() -> service.qualify(setup.actor, setup.command(2,setup.venue,id("room_A2"),setup.version,"BANK")),404);
        rejects(() -> service.qualify(setup.actor, setup.command(1,setup.venue,setup.room,setup.version,"BANK")),409);
        rejects(() -> service.qualify(setup.actor, setup.command(2,setup.venue,setup.room,id("wrong-version"),"BANK")),409);
        setup.lockedHold(NOW.minusSeconds(1));
        rejects(() -> service.qualify(setup.actor, setup.command),409);
    }

    @Test
    void missingExpiredAndRevokedAuthorityDenyQualification() {
        var setup = new Setup(NOW);
        var repository = mock(JdbcCustomerCommercialQuoteRepository.class);
        when(repository.now()).thenReturn(NOW);
        when(repository.syntheticActor(setup.actor)).thenReturn(true);
        var service = setup.service(repository, inlineTransaction());
        setup.current = Optional.empty();
        rejects(() -> service.qualify(setup.actor, setup.command),409);
        setup.current = Optional.of(setup.policy(Set.of(), NOW.minusSeconds(901)));
        rejects(() -> service.qualify(setup.actor, setup.command),409);
        setup.current = Optional.of(setup.policy(Set.of(id("FINANCE-appointment")), NOW));
        rejects(() -> service.qualify(setup.actor, setup.command),409);
        var policy = setup.policy(Set.of(), NOW);
        setup.current = Optional.of(new CustomerCommercialQuoteService.QualificationPolicy(
            policy.scope(), policy.approvals().subList(0,2), NOW, Set.of(), policy.tax(), policy.fees()));
        rejects(() -> service.qualify(setup.actor, setup.command),409);
    }

    @Test
    void eligibilityRequiresAnIsolatedSyntheticActor() {
        var setup = new Setup(NOW);
        var repository = mock(JdbcCustomerCommercialQuoteRepository.class);
        rejects(() -> setup.service(repository, inlineTransaction()).qualify(setup.actor, setup.command),403);
    }

    static void rejects(Runnable work, int status) {
        assertThatThrownBy(work::run).isInstanceOfSatisfying(DraftFailure.class, ex -> assertThat(ex.status).isEqualTo(status));
    }

    private static TransactionTemplate inlineTransaction() {
        return new TransactionTemplate() {
            @Override public <T> T execute(TransactionCallback<T> work) {
                return work.doInTransaction(new SimpleTransactionStatus());
            }
        };
    }

    static final class Setup {
        final Instant now;
        final UUID actor=id("submitter"), venue=id("venue_A"), room=id("room_A1");
        final UUID holdId=id("v41_hold"), packageId=id("package"), version=id("package_version"), key=id("v41_key");
        final ScopedAuthorization identity=mock(ScopedAuthorization.class);
        final QuotationHolds holds=mock(QuotationHolds.class);
        final PublicationRepository publications=mock(PublicationRepository.class);
        final CustomerCommercialQuoteService.Command command=command(2,venue,room,version,"BANK");
        Optional<CustomerCommercialQuoteService.QualificationPolicy> current;

        Setup(Instant now) {
            this.now=now;
            current=Optional.of(policy(Set.of(),now));
            when(identity.eligible(actor,true)).thenReturn(true);
            when(identity.eligible(id("finance"),true)).thenReturn(true);
            when(holds.locate(holdId)).thenReturn(Optional.of(hold(now.plusSeconds(300))));
            lockedHold(now.plusSeconds(300));
            var snapshot=new QuoteRulesTest().pkg("OPTIONAL");
            snapshot.put("options",List.of());
            snapshot.put("roomIds",List.of(room.toString()));
            when(publications.catalog(venue)).thenReturn(List.of(Map.of("packageId",packageId,"versionId",version,
                "sessionFrom",now.toString(),"sessionTo",now.plusSeconds(86400).toString(),"snapshot",snapshot)));
        }

        void lockedHold(Instant expires) {
            when(holds.lockAndInspect(actor,holdId)).thenReturn(Optional.of(hold(expires)));
        }

        private QuotationHolds.Facts hold(Instant expires) {
            return new QuotationHolds.Facts(holdId,actor,venue,room,now.plusSeconds(60),now.plusSeconds(10860),
                expires,"ACTIVE",2,4,"Asia/Colombo",15,now.plusSeconds(11760),"SYNTHETIC");
        }

        CustomerCommercialQuoteService.Command command(long revision, UUID venueId, UUID roomId, UUID versionId, String method) {
            return new CustomerCommercialQuoteService.Command(holdId,revision,venueId,roomId,packageId,versionId,
                key,method,"ONLINE",Set.of());
        }

        CustomerCommercialQuoteService.QualificationPolicy policy(Set<UUID> revoked, Instant observed) {
            var scope=new CustomerCommercialQuoteService.Scope(venue,room,version,id("v41_policy"),1,"a".repeat(64),
                now.minusSeconds(60),now.plusSeconds(86400));
            var approvals=Arrays.stream(CustomerCommercialQuoteService.Domain.values())
                .map(domain -> new CustomerCommercialQuoteService.Approval(domain,id(domain+"-appointment"),
                    id(domain+"-signer"),scope,now.minusSeconds(60),now.plusSeconds(600))).toList();
            var tax=new CommercialQuotationRules.Tax(CommercialQuotationRules.TaxTreatment.EXCLUSIVE,
                CommercialQuotationRules.TaxBasis.PACKAGE_COMPONENTS_AND_METHOD_FEE,1000,null,RoundingMode.HALF_UP);
            return new CustomerCommercialQuoteService.QualificationPolicy(scope,approvals,observed,revoked,tax,
                List.of(new CommercialQuotationRules.Fee("BANK","ONLINE",CommercialQuotationRules.FeeTreatment.FIXED_MINOR,1000)));
        }

        CustomerCommercialQuoteService service(JdbcCustomerCommercialQuoteRepository repository, TransactionTemplate tx) {
            return new CustomerCommercialQuoteService(identity,holds,publications,
                (venueId,roomId,versionId,instant) -> current,repository,tx);
        }
    }
}
