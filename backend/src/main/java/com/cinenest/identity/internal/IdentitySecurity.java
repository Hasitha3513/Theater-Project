package com.cinenest.identity.internal;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "cinenest.identity.enabled", havingValue = "true")
@EnableJdbcHttpSession(tableName = "cinenest_identity.http_session")
class IdentitySecurity {
    @Bean
    SessionRepositoryCustomizer<JdbcIndexedSessionRepository> sessionPolicy(
            @Value("${cinenest.identity.session-timeout-seconds:0}") int seconds) {
        if (seconds <= 0) throw new IllegalStateException("An explicit positive identity session expiry is required");
        return repository -> repository.setDefaultMaxInactiveInterval(Duration.ofSeconds(seconds));
    }

    @Bean
    CookieSerializer identityCookie() {
        var cookie = new DefaultCookieSerializer();
        cookie.setCookieName("CINENEST_SESSION");
        cookie.setCookiePath("/");
        cookie.setUseSecureCookie(true);
        cookie.setUseHttpOnlyCookie(true);
        cookie.setSameSite("Lax");
        return cookie;
    }

    @Bean
    @ConditionalOnProperty(name="cinenest.identity.manual.enabled", havingValue="false", matchIfMissing=true)
    ClientRegistrationRepository identityClient(
            @Value("${cinenest.identity.issuer}") String issuer,
            @Value("${cinenest.identity.authorization-uri}") String authorization,
            @Value("${cinenest.identity.token-uri}") String token,
            @Value("${cinenest.identity.jwk-set-uri}") String jwks,
            @Value("${cinenest.identity.client-id}") String client,
            @Value("${cinenest.identity.client-secret}") String secret) {
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("cinenest")
            .clientId(client).clientSecret(secret).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}").scope("openid")
            .authorizationUri(authorization).tokenUri(token).jwkSetUri(jwks).issuerUri(issuer)
            .userNameAttributeName("sub").clientName("Associated test identities only").build());
    }

    @Bean
    SecurityFilterChain identityBoundary(HttpSecurity http, IdentityStore store, org.springframework.core.env.Environment env, org.springframework.beans.factory.ObjectProvider<FacebookLoginContract.Provider> facebookProviders, org.springframework.beans.factory.ObjectProvider<org.springframework.security.oauth2.client.web.AuthorizationRequestRepository<org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest>> transactions) throws Exception {
        boolean manual=env.getProperty("cinenest.identity.manual.enabled",Boolean.class,false);
        if(manual && !env.acceptsProfiles(org.springframework.core.env.Profiles.of("local-synthetic"))) throw new IllegalStateException("Local synthetic profile required");
        boolean facebook=env.getProperty("cinenest.identity.facebook.enabled",Boolean.class,false);
        var facebookProvider=facebookProviders.getIfAvailable();
        if(facebook && (!manual || facebookProvider==null || !env.acceptsProfiles(org.springframework.core.env.Profiles.of("local-synthetic"))))throw new IllegalStateException("Facebook S1 requires isolated manual coexistence");
        if(facebook && facebookProvider.live!=null)http.addFilterBefore(new FacebookLiveExecutionGate(facebookProvider.live.execution && facebookProvider.live.callbackVerified),org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter.class);
        boolean google=env.getProperty("cinenest.identity.google.enabled",Boolean.class,false);
        if(google && (!manual || !env.acceptsProfiles(org.springframework.core.env.Profiles.of("local-synthetic")))) throw new IllegalStateException("Google S1 requires isolated manual coexistence");
        if(google && env.getProperty("cinenest.identity.google.mode","").equals("LIVE_QUALIFICATION")) {
            http.addFilterBefore(new GoogleLiveExecutionGate(env.getProperty("cinenest.identity.google.live-execution",Boolean.class,false)),org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter.class);
        }
        var contexts = new HttpSessionSecurityContextRepository();
        var oidc = new OidcUserService();
        http.securityContext(context -> context.securityContextRepository(contexts))
            .requestCache(cache -> cache.disable())
            .authorizeHttpRequests(requests -> {
                if(facebook) requests.requestMatchers(org.springframework.http.HttpMethod.GET,"/api/auth/facebook/status","/oauth2/authorization/facebook","/login/oauth2/code/facebook").permitAll();
                if(google) requests.requestMatchers(org.springframework.http.HttpMethod.GET,"/api/auth/google/status","/oauth2/authorization/google","/login/oauth2/code/google").permitAll();
                if(manual) requests.requestMatchers(org.springframework.http.HttpMethod.GET,"/api/auth/csrf").permitAll()
                    .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/auth/registrations","/api/auth/email-verifications","/api/auth/login").permitAll();
                requests
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/accounts/me/profile").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.PUT,"/api/accounts/me/profile").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/catalog/banners", "/api/catalog/venues", "/api/catalog/rooms", "/api/catalog/rooms/*", "/api/venues/*/packages", "/api/venues/*/packages/*").permitAll()
                .requestMatchers("/oauth2/authorization/cinenest", "/login/oauth2/code/cinenest").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/admin/venues/*/accounts","/api/admin/venues/*/accounts/*","/api/admin/venues/*/accounts/*/grants","/api/admin/venues/*/audit","/api/admin/venues/*/audit/*").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                    "/api/accounts/me", "/api/accounts/{accountId}", "/api/session/csrf").authenticated()
                .requestMatchers("/api/admin/venues/*/packages/*/publication-preview", "/api/admin/venues/*/packages/*/publications", "/api/admin/venues/*/packages/*/publications/*", "/api/admin/venues/*/packages/*/publications/*/deactivate", "/api/admin/venues/*/packages/*/publications/*/retry").authenticated()
                .requestMatchers("/api/admin/venues/*/packages", "/api/admin/venues/*/packages/*", "/api/admin/venues/*/packages/*/draft", "/api/admin/venues/*/packages/*/duplicate", "/api/admin/venues/*/packages/*/preview", "/api/admin/venues/*/packages/*/history", "/api/admin/venues/*/package-options").authenticated()
                .requestMatchers("/api/admin/venues/*/banner-assets", "/api/admin/venues/*/banners", "/api/admin/venues/*/banners/*", "/api/admin/venues/*/banners/*/preview", "/api/admin/venues/*/banners/*/revisions", "/api/admin/venues/*/banners/*/publications", "/api/admin/venues/*/banners/*/unpublish").authenticated()
                .requestMatchers("/api/admin/venues", "/api/admin/venues/*", "/api/admin/venues/*/rooms", "/api/admin/venues/*/rooms/*").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/rooms/*/availability", "/api/rooms/*/availability/calendar", "/api/holds/*", "/api/admin/venues/*/inventory", "/api/admin/venues/*/rooms/*/maintenance-impact").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/admin/venues/*/rooms/*/blocks","/api/admin/venues/*/rooms/*/blocks/*").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/admin/venues/*/rooms/*/blocks").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/holds", "/api/holds/*/release").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/admin/venues/*/bookings","/api/admin/venues/*/bookings/*").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/bookings/*/guest-roster","/api/admin/venues/*/bookings/*/guest-roster").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/bookings/*/guest-roster","/api/bookings/*/guest-roster/companions").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/quotes/*").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/quotes", "/api/quotes/*/revisions", "/api/quotes/*/accept").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/verification/requests/*", "/api/admin/venues/*/verification/requests", "/api/admin/venues/*/verification/requests/*", "/api/admin/venues/*/verification/requests/*/evidence/*").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/verification/requests", "/api/verification/requests/*/uploads", "/api/verification/uploads/*/complete", "/api/verification/requests/*/resubmissions", "/api/admin/venues/*/verification/requests/*/decisions").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.PUT,"/api/verification/uploads/*/content").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/bank/requests","/api/bank/requests/*/refunds","/api/bank/requests/*","/api/admin/venues/*/bank/requests","/api/admin/venues/*/bank/requests/*","/api/admin/venues/*/bank/requests/*/evidence/*").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/bank/requests","/api/bank/requests/*/claims","/api/bank/requests/*/resubmissions","/api/bank/claims/*/uploads","/api/bank/uploads/*/complete","/api/admin/venues/*/bank/requests/*/decisions","/api/admin/venues/*/bank/requests/*/escalations").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/bookings","/api/bookings/*","/api/admin/venues/*/bank/credits","/api/admin/venues/*/bank/credits/*","/api/admin/venues/*/bank/exceptions","/api/admin/venues/*/bank/exceptions/*").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/admin/venues/*/bank/credits","/api/admin/venues/*/bank/credits/*/matches","/api/admin/venues/*/bank/requests/*/allocations","/api/admin/venues/*/bank/exceptions/*/actions","/api/admin/venues/*/bank/credits/*/corrections","/api/admin/venues/*/bank/corrections/*/decisions").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.PUT,"/api/bank/uploads/*/content").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/refunds/*","/api/admin/venues/*/refunds","/api/admin/venues/*/bookings/*/refunds").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/bank/requests/*/refunds","/api/admin/venues/*/refunds/*/destination-verifications","/api/admin/venues/*/refunds/*/decisions","/api/admin/venues/*/refunds/*/attempts","/api/admin/venues/*/refunds/*/attempts/*/evidence","/api/admin/venues/*/refunds/*/reconciliations").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/bookings/*/access","/api/admin/venues/*/bookings/*/access","/api/admin/venues/*/access/credentials","/api/admin/venues/*/access/credentials/*").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/bookings/*/access/credentials","/api/bookings/*/access/credentials/*/reveal","/api/bookings/*/access/simulated-attempts","/api/admin/venues/*/access/credentials/*/revocations","/api/admin/venues/*/access/credentials/*/simulations").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/notifications","/api/notifications/*","/api/admin/venues/*/notifications","/api/admin/venues/*/notifications/*").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/notifications/*/read","/api/admin/venues/*/notifications/*/retries","/api/admin/venues/*/notifications/*/reconciliations","/api/admin/venues/*/notifications/*/simulations").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/bookings/*/amendments/*").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/bookings/*/amendments","/api/bookings/*/amendments/*/confirm","/api/bookings/*/amendments/*/withdrawals").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/admin/venues/*/reports/synthetic-bank-balances","/api/admin/venues/*/reports/synthetic-booking-confirmations","/api/admin/venues/*/reports/synthetic-popular-session-starts").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/admin/venues/*/bookings/*/operations/cases").authenticated()
                .requestMatchers("/api/admin/venues/*/operations/cases", "/api/admin/venues/*/operations/cases/**").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/admin/venues/*/iot/lock-devices","/api/admin/venues/*/iot/lock-devices/*","/api/admin/venues/*/iot/lock-devices/*/revisions","/api/admin/venues/*/iot/lock-devices/*/revisions/*").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/admin/venues/*/iot/lock-devices").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/admin/venues/*/iot/devices","/api/admin/venues/*/iot/devices/*").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/admin/venues/*/iot/devices/*/commands","/api/admin/venues/*/iot/devices/*/commands/*/simulations","/api/admin/venues/*/iot/devices/*/commands/*/reconciliations").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/bookings/*/extension-reservations/*","/api/bookings/*/extension-reservations/commands/*").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/bookings/*/extension-reservations","/api/bookings/*/extension-reservations/*/withdrawals").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/bookings/*/extension-eligibility").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/bookings/*/extension-quotes").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/bookings/*/cancellation","/api/admin/venues/*/bookings/*/cancellation").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/bookings/*/cancellations","/api/admin/venues/*/bookings/*/cancellations").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/admin/venues/*/bank/instructions/*","/api/admin/venues/*/bank/requests/*/instructions").authenticated()
                .anyRequest().denyAll(); })
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, failure) -> {
                    store.audit(null, null, "HTTP_DENIAL", false);
                    response.setStatus(401);
                })
                .accessDeniedHandler((request, response, failure) -> {
                    store.audit(actor(), null, "HTTP_DENIAL", false);
                    response.setStatus(403);
                }))
            .sessionManagement(sessions -> sessions.sessionFixation(fixation -> fixation.changeSessionId()));
        if(!manual || google || facebook) http.oauth2Login(login -> login.loginPage(google?"/oauth2/authorization/google":facebook?"/oauth2/authorization/facebook":"/oauth2/authorization/cinenest")
                .authorizedClientRepository(discardProviderTokens())
                .authorizationEndpoint(endpoint -> {if(facebook)endpoint.authorizationRequestRepository(transactions.getObject());})
                .tokenEndpoint(token -> {if(google || facebook)token.accessTokenResponseClient(request -> request.getClientRegistration().getRegistrationId().equals("facebook") && facebook ? facebookProvider.exchange(request) : GoogleOidcContract.tokenClient().getTokenResponse(request));})
                .userInfoEndpoint(info -> info.userService(request -> {if(!facebook)throw new OAuth2AuthenticationException(new OAuth2Error("access_denied"));return facebookProvider.authenticate(request,store);}).oidcUserService(request -> {
                    var user = oidc.loadUser(request);
                    if (store.associatedEligibleAccount(user.getIssuer().toString(), user.getSubject()).isEmpty())
                        throw new OAuth2AuthenticationException(new OAuth2Error("access_denied"));
                    return user;
                }))
                .failureHandler((request, response, failure) -> {
                    if(google || facebook){SecurityContextHolder.clearContext();var session=request.getSession(false);if(session!=null)session.invalidate();}else clear(request);
                    store.audit(null, null, "AUTHENTICATION", false);
                    response.setStatus(403);
                })
                .successHandler((request, response, authentication) -> {
                    java.util.Optional<UUID> associated=java.util.Optional.empty();
                    if(authentication.getPrincipal() instanceof OidcUser external) associated=store.associatedEligibleAccount(external.getIssuer().toString(),external.getSubject());
                    else if(facebook && authentication instanceof org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken oauth && oauth.getAuthorizedClientRegistrationId().equals("facebook")) {
                        try {UUID account=UUID.fromString(authentication.getName());if(store.eligible(account))associated=java.util.Optional.of(account);}catch(IllegalArgumentException rejected){/* fail closed */}
                    }
                    if (associated.isEmpty()) {
                        clear(request);
                        store.audit(null, null, "AUTHENTICATION", false);
                        response.setStatus(403);
                        return;
                    }
                    // Persist only the local principal; provider tokens/claims are not an application session DTO.
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        associated.get().toString(), null, List.of()));
                    SecurityContextHolder.setContext(context);
                    try {
                        store.audit(associated.get(), null, "AUTHENTICATION", true);
                        contexts.saveContext(context, request, response);
                        response.setStatus(204);
                    } catch (RuntimeException failure) {
                        SecurityContextHolder.clearContext();
                        var session = request.getSession(false);
                        if (session != null) session.invalidate();
                        throw failure;
                    }
                }));
        http.logout(logout -> logout.logoutUrl("/api/session/logout")
                .addLogoutHandler((request, response, authentication) ->
                    store.audit(actor(authentication), null, "LOGOUT", true))
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
        // CSRF remains enabled, including logout. No HTTP Basic or broad API allow rule; manual login retains independent CSRF.
        return http.build();
    }

    private static UUID actor() { return actor(SecurityContextHolder.getContext().getAuthentication()); }
    private static UUID actor(Authentication authentication) {
        if (authentication == null) return null;
        try { return UUID.fromString(authentication.getName()); } catch (IllegalArgumentException invalid) { return null; }
    }
    private static void clear(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) {
            var previous = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (previous instanceof org.springframework.security.core.context.SecurityContext context
                    && context.getAuthentication() != null
                    && context.getAuthentication().getPrincipal() instanceof String) return;
            session.invalidate();
        }
    }
    private static OAuth2AuthorizedClientRepository discardProviderTokens() {
        return new OAuth2AuthorizedClientRepository() {
            @Override public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String id, Authentication principal, HttpServletRequest request) { return null; }
            @Override public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication principal, HttpServletRequest request, HttpServletResponse response) { }
            @Override public void removeAuthorizedClient(String id, Authentication principal, HttpServletRequest request, HttpServletResponse response) { }
        };
    }
}
