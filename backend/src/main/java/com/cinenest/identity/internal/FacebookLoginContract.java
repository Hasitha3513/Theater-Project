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

/** Standard Meta Web code/token contract, exclusively against an isolated synthetic provider. */
@Configuration(proxyBeanMethods=false)
@Profile("local-synthetic")
@ConditionalOnProperty(name="cinenest.identity.facebook.enabled",havingValue="true")
class FacebookLoginContract {
    @Bean Provider facebookProvider(Environment env) { return new Provider(env); }
    @Bean ClientRegistrationRepository facebookClients(Provider facebook,Environment env) {
        List<ClientRegistration> clients=new ArrayList<>();clients.add(facebook.registration());
        if(env.getProperty("cinenest.identity.google.enabled",Boolean.class,false)) {
            if(!env.getProperty("cinenest.identity.google.mode","").equals("CONTRACT_TEST"))throw new IllegalStateException("Facebook S1 requires isolated Google coexistence");
            var google=new GoogleOidcContract().configuredClient(env,()->{throw new IllegalStateException("Live discovery excluded from Facebook S1");});
            clients.add(google.findByRegistrationId("google"));
        }
        return new InMemoryClientRegistrationRepository(clients);
    }
    @Bean AuthorizationRequestRepository<OAuth2AuthorizationRequest> facebookTransactions(Provider provider) {
        return new Transactions(provider.origin+"/v26.0/dialog/oauth",provider.transactionSeconds,Clock.systemUTC());
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
        final String origin,frontend,appId,secret,namespace;final int transactionSeconds;
        private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
        Provider(Environment env) {
            try {
                if(!env.getProperty("cinenest.identity.manual.enabled",Boolean.class,false) || !env.getProperty("cinenest.identity.facebook.mode","").equals("CONTRACT_TEST")
                    || !env.getProperty("cinenest.identity.facebook.client-secret","").isEmpty() || !System.getProperty("jdk.httpclient.HttpClient.log","").isBlank())throw new IllegalStateException();
                origin=GoogleOidcContract.loopback(env.getRequiredProperty("cinenest.identity.facebook.contract-origin")).toString().replaceAll("/$","");
                frontend=GoogleOidcContract.loopback(env.getRequiredProperty("cinenest.identity.facebook.frontend-origin")).toString().replaceAll("/$","");
                appId=env.getRequiredProperty("cinenest.identity.facebook.client-id");if(!appId.matches("synthetic-[0-9a-f]{32}"))throw new IllegalStateException();
                secret=GoogleLiveProvider.secret(env.getRequiredProperty("cinenest.identity.facebook.client-secret-file"));
                namespace="urn:cinenest:identity:facebook:app:"+appId;
                transactionSeconds=env.getProperty("cinenest.identity.facebook.transaction-seconds",Integer.class,180);
                if(transactionSeconds<30 || transactionSeconds>300)throw new IllegalStateException();
            }catch(Exception invalid){throw new IllegalStateException("Isolated Facebook S1 configuration required;live activation is excluded");}
        }
        @Override public String toString(){return "Facebook synthetic provider configuration [redacted]";}
        ClientRegistration registration() {
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
                var value=get(uri("/v26.0/oauth/access_token",Map.of("client_id",appId,"client_secret",secret,"redirect_uri",frontend+"/api/auth/facebook/callback","code",request.getAuthorizationExchange().getAuthorizationResponse().getCode())));
                String token=(String)value.get("access_token");long lifetime=((Number)value.get("expires_in")).longValue();
                if(token==null || token.isBlank() || token.length()>8192 || lifetime<=0 || !"bearer".equalsIgnoreCase(Objects.toString(value.get("token_type"),"")))throw denied();
                return OAuth2AccessTokenResponse.withToken(token).tokenType(OAuth2AccessToken.TokenType.BEARER).expiresIn(lifetime).scopes(Set.of("public_profile")).build();
            }catch(Exception invalid){throw denied();}
        }
        OAuth2User authenticate(OAuth2UserRequest request,IdentityStore store) {
            try {
                if(!request.getClientRegistration().getRegistrationId().equals("facebook") || request.getAccessToken().getExpiresAt()==null || !request.getAccessToken().getExpiresAt().isAfter(Instant.now()))throw denied();
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
        @GetMapping("/api/auth/facebook/status") ResponseEntity<?> status(HttpServletRequest request) {
            if(!request.getParameterMap().isEmpty())return ResponseEntity.badRequest().header("Cache-Control","no-store").build();
            return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("mode","CONTRACT_TEST","liveProviderQualified",false,"syntheticOnly",true));
        }
    }
}
