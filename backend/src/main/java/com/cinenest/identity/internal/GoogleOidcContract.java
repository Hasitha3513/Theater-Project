package com.cinenest.identity.internal;

import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Local qualification only. No identity association writes or production admission. */
@Configuration(proxyBeanMethods=false)
@Profile("local-synthetic")
@ConditionalOnProperty(name="cinenest.identity.google.enabled",havingValue="true")
class GoogleOidcContract {
    static final String GOOGLE_ISSUER="https://accounts.google.com";
    static URI loopback(String value) {
        URI u=URI.create(value);
        if(!"http".equals(u.getScheme()) || !java.util.Set.of("127.0.0.1","localhost","[::1]").contains(u.getHost()) || u.getPort()<1 || u.getUserInfo()!=null || u.getQuery()!=null || u.getFragment()!=null || !(u.getPath().isEmpty()||u.getPath().equals("/")))throw new IllegalArgumentException("Explicit loopback contract origin required");
        return u;
    }
    static org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient tokenClient() {
        var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(2)).followRedirects(java.net.http.HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(java.time.Duration.ofSeconds(2));
        var client=new org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient();
        client.setRestClient(org.springframework.web.client.RestClient.builder().requestFactory(factory).messageConverters(c->{c.clear();c.add(new org.springframework.http.converter.FormHttpMessageConverter());c.add(new org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter());}).defaultStatusHandler(new org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler()).build());
        return client;
    }
    @Bean ClientRegistrationRepository googleClient(org.springframework.core.env.Environment env) {
        return configuredClient(env,()->GoogleLiveProvider.discover(GoogleLiveProvider.discoveryClient()));
    }
    ClientRegistrationRepository configuredClient(org.springframework.core.env.Environment env,java.util.function.Supplier<Map<String,Object>> discovery) {
        boolean manual=env.getProperty("cinenest.identity.manual.enabled",Boolean.class,false);
        String mode=env.getProperty("cinenest.identity.google.mode","DISABLED");
        if(mode.equals("LIVE_QUALIFICATION")) {
            if(!manual || !env.getProperty("cinenest.identity.google.client-secret","").isEmpty() || !env.getProperty("cinenest.identity.google.contract-issuer","").isEmpty())throw new IllegalStateException("Live qualification requires manual coexistence and dedicated private credentials");
            String client=env.getRequiredProperty("cinenest.identity.google.client-id");
            String frontend=env.getRequiredProperty("cinenest.identity.google.frontend-origin");
            // Validate local context and private credentials before any provider network operation.
            loopback(frontend);
            if(!client.matches("[A-Za-z0-9_-]{1,200}\\.apps\\.googleusercontent\\.com"))throw new IllegalStateException("Google qualification client invalid");
            String secret=GoogleLiveProvider.secret(env.getRequiredProperty("cinenest.identity.google.client-secret-file"));
            return new InMemoryClientRegistrationRepository(GoogleLiveProvider.registration(client,secret,frontend,discovery.get()));
        }
        return googleContractClient(manual,mode,env.getProperty("cinenest.identity.google.contract-issuer",""),env.getProperty("cinenest.identity.google.client-id",""),env.getProperty("cinenest.identity.google.client-secret",""),env.getProperty("cinenest.identity.google.frontend-origin",""));
    }
    ClientRegistrationRepository googleContractClient(
        @Value("${cinenest.identity.manual.enabled:false}") boolean manual,
        @Value("${cinenest.identity.google.mode:DISABLED}") String mode,
        @Value("${cinenest.identity.google.contract-issuer:}") String issuer,
        @Value("${cinenest.identity.google.client-id}") String client,
        @Value("${cinenest.identity.google.client-secret:}") String secret,
        @Value("${cinenest.identity.google.frontend-origin}") String frontend) {
        if(!manual || !mode.equals("CONTRACT_TEST") || client.isBlank() || secret.isBlank())throw new IllegalStateException("S1 requires manual coexistence and isolated contract mode;live provider activation is not authorized");
        String base=loopback(issuer).toString().replaceAll("/$","");
        String origin=loopback(frontend).toString().replaceAll("/$","");
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("google")
            .clientId(client).clientSecret(secret).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(origin+"/api/auth/google/callback").scope("openid")
            .authorizationUri(base+"/authorize").tokenUri(base+"/token").jwkSetUri(base+"/jwks").issuerUri(base)
            .userNameAttributeName("sub").clientName("SIMULATED Google OIDC qualification only")
            .clientSettings(ClientRegistration.ClientSettings.builder().requireProofKey(true).build()).build());
    }
    @RestController
    @Profile("local-synthetic")
    @ConditionalOnProperty(name="cinenest.identity.google.enabled",havingValue="true")
    static class Status {
        private final String mode;
        private final boolean execution;
        Status(@Value("${cinenest.identity.google.mode:DISABLED}") String mode,@Value("${cinenest.identity.google.live-execution:false}") boolean execution){this.mode=mode;this.execution=execution;}
        @GetMapping("/api/auth/google/status") ResponseEntity<?> status(jakarta.servlet.http.HttpServletRequest request) {
            if(!request.getParameterMap().isEmpty())return ResponseEntity.badRequest().header("Cache-Control","no-store").build();
            return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("mode",mode,"liveProviderQualified",false,"executionEnabled",mode.equals("LIVE_QUALIFICATION") && execution));
        }
    }
}
