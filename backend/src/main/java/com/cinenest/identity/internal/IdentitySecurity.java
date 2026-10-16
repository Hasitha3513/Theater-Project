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
    SecurityFilterChain identityBoundary(HttpSecurity http, IdentityStore store) throws Exception {
        var contexts = new HttpSessionSecurityContextRepository();
        var oidc = new OidcUserService();
        http.securityContext(context -> context.securityContextRepository(contexts))
            .requestCache(cache -> cache.disable())
            .authorizeHttpRequests(requests -> requests
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/catalog/venues", "/api/catalog/rooms", "/api/catalog/rooms/*").permitAll()
                .requestMatchers("/oauth2/authorization/cinenest", "/login/oauth2/code/cinenest").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                    "/api/accounts/me", "/api/accounts/{accountId}", "/api/session/csrf").authenticated()
                .requestMatchers("/api/admin/venues/*/packages", "/api/admin/venues/*/packages/*", "/api/admin/venues/*/packages/*/draft", "/api/admin/venues/*/packages/*/duplicate", "/api/admin/venues/*/packages/*/preview", "/api/admin/venues/*/packages/*/history", "/api/admin/venues/*/package-options").authenticated()
                .requestMatchers("/api/admin/venues", "/api/admin/venues/*", "/api/admin/venues/*/rooms", "/api/admin/venues/*/rooms/*").authenticated()
                .anyRequest().denyAll())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, failure) -> {
                    store.audit(null, null, "HTTP_DENIAL", false);
                    response.setStatus(401);
                })
                .accessDeniedHandler((request, response, failure) -> {
                    store.audit(actor(), null, "HTTP_DENIAL", false);
                    response.setStatus(403);
                }))
            .sessionManagement(sessions -> sessions.sessionFixation(fixation -> fixation.changeSessionId()))
            .oauth2Login(login -> login.loginPage("/oauth2/authorization/cinenest")
                .authorizedClientRepository(discardProviderTokens())
                .userInfoEndpoint(info -> info.oidcUserService(request -> {
                    var user = oidc.loadUser(request);
                    if (store.associatedEligibleAccount(user.getIssuer().toString(), user.getSubject()).isEmpty())
                        throw new OAuth2AuthenticationException(new OAuth2Error("access_denied"));
                    return user;
                }))
                .failureHandler((request, response, failure) -> {
                    clear(request);
                    store.audit(null, null, "AUTHENTICATION", false);
                    response.setStatus(403);
                })
                .successHandler((request, response, authentication) -> {
                    var external = (OidcUser) authentication.getPrincipal();
                    var associated = store.associatedEligibleAccount(external.getIssuer().toString(), external.getSubject());
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
                }))
            .logout(logout -> logout.logoutUrl("/api/session/logout")
                .addLogoutHandler((request, response, authentication) ->
                    store.audit(actor(authentication), null, "LOGOUT", true))
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
        // CSRF remains enabled, including logout. No password/Basic or broad API allow rule.
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
