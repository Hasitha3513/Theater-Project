package com.cinenest.identity.internal;

import java.net.*;import java.net.http.*;import java.nio.charset.StandardCharsets;import java.time.*;import java.util.*;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.*;
import org.springframework.security.oauth2.core.user.*;
import org.springframework.web.bind.annotation.*;
import com.nimbusds.jose.util.JSONObjectUtils;

/** Meta Web registration composition, confined to local qualification. */
@Configuration(proxyBeanMethods=false)
@Profile("local-synthetic")
@ConditionalOnProperty(name="cinenest.identity.facebook.enabled",havingValue="true")
class FacebookLoginContract {
    @Bean Provider facebookProvider(Environment env) { return new Provider(env); }
    @Bean ClientRegistrationRepository facebookClients(Provider facebook,Environment env) {
        return configuredClients(facebook,env,()->GoogleLiveProvider.discover(GoogleLiveProvider.discoveryClient()));
    }
    ClientRegistrationRepository configuredClients(Provider facebook,Environment env,java.util.function.Supplier<Map<String,Object>> discovery) {
        List<ClientRegistration> clients=new ArrayList<>();clients.add(facebook.registration());
        if(env.getProperty("cinenest.identity.google.enabled",Boolean.class,false)) {
            var google=new GoogleOidcContract().configuredClient(env,discovery);
            clients.add(google.findByRegistrationId("google"));
        }
        return new InMemoryClientRegistrationRepository(clients);
    }
    @Bean AuthorizationRequestRepository<OAuth2AuthorizationRequest> facebookTransactions(Provider provider) {
        return new Transactions(provider.registration().getProviderDetails().getAuthorizationUri(),provider.transactionSeconds,Clock.systemUTC());
    }
    static final class Transactions implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
        private final HttpSessionOAuth2AuthorizationRequestRepository delegate=new HttpSessionOAuth2AuthorizationRequestRepository();
        private final String authorization;private final long seconds;private final Clock clock;
        Transactions(String authorization,long seconds,Clock clock){this.authorization=authorization;this.seconds=seconds;this.clock=clock;}
        private OAuth2AuthorizationRequest checked(OAuth2AuthorizationRequest value) {
            if(value!=null && authorization.equals(value.getAuthorizationUri())) {
                Object created=value.getAttribute("facebook_created_at");long now=clock.instant().getEpochSecond();
                if(!(created instanceof Long issued) || issued>now || now-issued>=seconds)return null;
            }
            return value;
        }
        public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request){return checked(delegate.loadAuthorizationRequest(request));}
        public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,HttpServletResponse response){return checked(delegate.removeAuthorizationRequest(request,response));}
        public void saveAuthorizationRequest(OAuth2AuthorizationRequest value,HttpServletRequest request,HttpServletResponse response){
            if(value!=null && authorization.equals(value.getAuthorizationUri()))value=OAuth2AuthorizationRequest.from(value).attributes(a->a.put("facebook_created_at",clock.instant().getEpochSecond())).build();
            delegate.saveAuthorizationRequest(value,request,response);
        }
    }
    static final class Provider {
        final String origin,frontend,appId,secret,namespace;final int transactionSeconds;final FacebookLiveProvider live;
        private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
        Provider(Environment env) {this(env,GoogleLiveProvider.discoveryClient());}
        Provider(Environment env,org.springframework.web.client.RestClient transport) {
            try {
                String mode=env.getProperty("cinenest.identity.facebook.mode","");
                if(!env.getProperty("cinenest.identity.manual.enabled",Boolean.class,false) || !(mode.equals("CONTRACT_TEST") || mode.equals("LIVE_QUALIFICATION"))
                    || !env.getProperty("cinenest.identity.facebook.client-secret","").isEmpty() || !System.getProperty("jdk.httpclient.HttpClient.log","").isBlank())throw new IllegalStateException();
                live=mode.equals("LIVE_QUALIFICATION")?new FacebookLiveProvider(env,transport):null;
                if(live!=null){origin="https://www.facebook.com";frontend=live.frontend;appId=live.app;secret=live.secret;}
                else {
                origin=GoogleOidcContract.loopback(env.getRequiredProperty("cinenest.identity.facebook.contract-origin")).toString().replaceAll("/$","");
                frontend=GoogleOidcContract.frontendOrigin(env.getRequiredProperty("cinenest.identity.facebook.frontend-origin")).toString().replaceAll("/$","");
                appId=env.getRequiredProperty("cinenest.identity.facebook.client-id");if(!appId.matches("synthetic-[0-9a-f]{32}"))throw new IllegalStateException();
                secret=GoogleLiveProvider.secret(env.getRequiredProperty("cinenest.identity.facebook.client-secret-file"));
                }
                namespace="urn:cinenest:identity:facebook:app:"+appId;
                transactionSeconds=env.getProperty("cinenest.identity.facebook.transaction-seconds",Integer.class,180);
                if(transactionSeconds<30 || transactionSeconds>300)throw new IllegalStateException();
            }catch(Exception invalid){throw new IllegalStateException("Isolated Facebook qualification configuration invalid;execution requires separate authority");}
        }
        @Override public String toString(){return "Facebook qualification configuration [redacted]";}
        ClientRegistration registration() {
            if(live!=null)return live.registration();
            return ClientRegistration.withRegistrationId("facebook").clientId(appId).clientSecret(secret)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri(frontend+"/api/auth/facebook/callback")
                .scope("public_profile").authorizationUri(origin+"/v26.0/dialog/oauth").tokenUri(origin+"/v26.0/oauth/access_token")
                .userInfoUri(origin+"/debug_token").userNameAttributeName("account_id").clientName("SIMULATED Facebook Web Login only")
                // Selected Meta manual Web contract uses state + confidential server exchange, not assumed Google PKCE/OIDC claims.
                .clientSettings(ClientRegistration.ClientSettings.builder().requireProofKey(false).build()).build();
        }
        private URI uri(String path,Map<String,String> values) {
            StringJoiner query=new StringJoiner("&");values.forEach((k,v)->query.add(URLEncoder.encode(k,StandardCharsets.UTF_8)+"="+URLEncoder.encode(v,StandardCharsets.UTF_8)));
            return URI.create(origin+path+"?"+query);
        }
        private Map<String,Object> get(URI uri) {
            // Meta's manual Web contract uses server-to-server GET. No URL/body logging or redirects.
            try {
                var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(2)).header("Accept","application/json").GET().build();
                var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
                try(var body=response.body()) {
                    if(response.statusCode()!=200 || !response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"))throw new IllegalStateException();
                    byte[] bytes=body.readNBytes(65537);if(bytes.length>65536)throw new IllegalStateException();
                    try(var parser=tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build().createParser(bytes)){
                        if(parser.nextToken()!=tools.jackson.core.JsonToken.START_OBJECT)throw new IllegalStateException();parser.skipChildren();if(parser.nextToken()!=null)throw new IllegalStateException();
                    }
                    return JSONObjectUtils.parse(new String(bytes,StandardCharsets.UTF_8));
                }
            }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw denied();}
            catch(Exception invalid){throw denied();}
        }
        OAuth2AccessTokenResponse exchange(OAuth2AuthorizationCodeGrantRequest request) {
            try {
                if(!request.getClientRegistration().getRegistrationId().equals("facebook") || !request.getAuthorizationExchange().getAuthorizationRequest().getRedirectUri().equals(frontend+"/api/auth/facebook/callback"))throw denied();
                if(live!=null)return live.exchange(request.getAuthorizationExchange().getAuthorizationResponse().getCode(),request.getAuthorizationExchange().getAuthorizationRequest().getRedirectUri());
                var value=get(uri("/v26.0/oauth/access_token",Map.of("client_id",appId,"client_secret",secret,"redirect_uri",frontend+"/api/auth/facebook/callback","code",request.getAuthorizationExchange().getAuthorizationResponse().getCode())));
                String token=(String)value.get("access_token");long lifetime=((Number)value.get("expires_in")).longValue();
                if(token==null || token.isBlank() || token.length()>8192 || lifetime<=0 || !"bearer".equalsIgnoreCase(Objects.toString(value.get("token_type"),"")))throw denied();
                return OAuth2AccessTokenResponse.withToken(token).tokenType(OAuth2AccessToken.TokenType.BEARER).expiresIn(lifetime).scopes(Set.of("public_profile")).build();
            }catch(Exception invalid){throw denied();}
        }
        OAuth2User authenticate(OAuth2UserRequest request,IdentityStore store) {
            try {
                if(!request.getClientRegistration().getRegistrationId().equals("facebook") || request.getAccessToken().getExpiresAt()==null || !request.getAccessToken().getExpiresAt().isAfter(Instant.now()))throw denied();
                if(live!=null){String id=live.inspect(request.getAccessToken().getTokenValue());UUID account=store.associatedEligibleAccount(namespace,id).orElseThrow(FacebookLoginContract::denied);return new DefaultOAuth2User(List.of(),Map.of("account_id",account.toString()),"account_id");}
                var value=get(uri("/debug_token",Map.of("input_token",request.getAccessToken().getTokenValue(),"access_token",appId+"|"+secret)));
                if(!(value.get("data") instanceof Map<?,?> data) || !Boolean.TRUE.equals(data.get("is_valid")) || !appId.equals(Objects.toString(data.get("app_id"),"")) || !"USER".equals(data.get("type")))throw denied();
                long expiry=((Number)data.get("expires_at")).longValue();if(expiry<=Instant.now().getEpochSecond())throw denied();
                if(data.containsKey("data_access_expires_at") && ((Number)data.get("data_access_expires_at")).longValue()<=Instant.now().getEpochSecond())throw denied();
                String id=(String)data.get("user_id");if(id==null || !id.matches("[A-Za-z0-9_-]{1,200}"))throw denied();
                UUID account=store.associatedEligibleAccount(namespace,id).orElseThrow(FacebookLoginContract::denied);
                return new DefaultOAuth2User(List.of(),Map.of("account_id",account.toString()),"account_id");
            }catch(Exception invalid){throw denied();}
        }
    }
    private static OAuth2AuthenticationException denied(){return new OAuth2AuthenticationException(new OAuth2Error("access_denied"));}
    @RestController
    @Profile("local-synthetic")
    @ConditionalOnProperty(name="cinenest.identity.facebook.enabled",havingValue="true")
    static class Status {
        private final Provider provider;
        Status(Provider provider){this.provider=provider;}
        @GetMapping("/api/auth/facebook/status") ResponseEntity<?> status(HttpServletRequest request) {
            if(!request.getParameterMap().isEmpty())return ResponseEntity.badRequest().header("Cache-Control","no-store").build();
            return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("mode",provider.live==null?"CONTRACT_TEST":"LIVE_QUALIFICATION","liveProviderQualified",false,"syntheticOnly",provider.live==null,"executionEnabled",provider.live!=null && provider.live.execution,"callbackVerified",provider.live!=null && provider.live.callbackVerified));
        }
    }
}
