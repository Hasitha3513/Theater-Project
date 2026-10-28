package com.cinenest.identity.internal;

import com.nimbusds.jose.util.JSONObjectUtils;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.web.client.RestClient;

/** Fixed Meta Web endpoints. No discovery, account writes or implicit execution permission. */
final class FacebookLiveProvider {
    static final String AUTHORIZATION="https://www.facebook.com/v26.0/dialog/oauth";
    static final String TOKEN="https://graph.facebook.com/v26.0/oauth/access_token";
    static final String INSPECTION="https://graph.facebook.com/debug_token";
    final String app,secret,frontend;final boolean execution,callbackVerified;
    private final RestClient client;
    FacebookLiveProvider(Environment env){this(env,GoogleLiveProvider.discoveryClient());}
    FacebookLiveProvider(Environment env,RestClient client){
        try {
            if(!env.getProperty("cinenest.identity.manual.enabled",Boolean.class,false)
                || !env.getProperty("cinenest.identity.facebook.mode","").equals("LIVE_QUALIFICATION")
                || !env.getProperty("cinenest.identity.facebook.contract-origin","").isEmpty()
                || !env.getProperty("cinenest.identity.facebook.client-secret","").isEmpty()
                || !System.getProperty("jdk.httpclient.HttpClient.log","").isBlank())throw new IllegalStateException();
            app=env.getRequiredProperty("cinenest.identity.facebook.client-id");if(!app.matches("[0-9]{1,32}"))throw new IllegalStateException();
            secret=GoogleLiveProvider.secret(env.getRequiredProperty("cinenest.identity.facebook.client-secret-file"));
            URI origin=URI.create(env.getRequiredProperty("cinenest.identity.facebook.frontend-origin"));
            if(origin.getHost()==null || origin.getUserInfo()!=null || origin.getQuery()!=null || origin.getFragment()!=null || !(origin.getPath().isEmpty() || origin.getPath().equals("/")))throw new IllegalStateException();
            if(!origin.getScheme().equals("https"))GoogleOidcContract.loopback(origin.toString());
            frontend=origin.toString().replaceAll("/$","");
            execution=env.getProperty("cinenest.identity.facebook.live-execution",Boolean.class,false);
            callbackVerified=env.getProperty("cinenest.identity.facebook.callback-verified",Boolean.class,false);
            if(execution && (!callbackVerified || !origin.getScheme().equals("https")))throw new IllegalStateException();
            this.client=client;
        }catch(Exception rejected){throw new IllegalStateException("Private Facebook qualification configuration required");}
    }
    @Override public String toString(){return "Facebook live qualification configuration [redacted]";}
    ClientRegistration registration(){return ClientRegistration.withRegistrationId("facebook").clientId(app).clientSecret(secret)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri(frontend+"/api/auth/facebook/callback").scope("public_profile")
        .authorizationUri(AUTHORIZATION).tokenUri(TOKEN).userInfoUri(INSPECTION).userNameAttributeName("account_id")
        .clientName("Facebook — configured;qualification not established")
        .clientSettings(ClientRegistration.ClientSettings.builder().requireProofKey(false).build()).build();}
    private Map<String,Object> get(String endpoint,Map<String,String> values){
        if(!execution || !callbackVerified || !(endpoint.equals(TOKEN)||endpoint.equals(INSPECTION)))throw denied();
        try {
            StringJoiner query=new StringJoiner("&");values.forEach((k,v)->query.add(URLEncoder.encode(k,StandardCharsets.UTF_8)+"="+URLEncoder.encode(v,StandardCharsets.UTF_8)));
            return client.get().uri(URI.create(endpoint+"?"+query)).exchange((request,response)->{
                if(response.getStatusCode().value()!=200 || response.getHeaders().getContentType()==null || !org.springframework.http.MediaType.APPLICATION_JSON.isCompatibleWith(response.getHeaders().getContentType()))throw denied();
                byte[] bytes=response.getBody().readNBytes(65537);if(bytes.length>65536)throw denied();
                try(var parser=tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build().createParser(bytes)){
                    if(parser.nextToken()!=tools.jackson.core.JsonToken.START_OBJECT)throw denied();parser.skipChildren();if(parser.nextToken()!=null)throw denied();
                }
                try{return JSONObjectUtils.parse(new String(bytes,StandardCharsets.UTF_8));}catch(java.text.ParseException rejected){throw denied();}
            });
        }catch(Exception rejected){throw denied();}
    }
    private static long integer(Object value){if(!(value instanceof Long || value instanceof Integer))throw denied();return ((Number)value).longValue();}
    OAuth2AccessTokenResponse exchange(String code,String callback){
        if(code==null || code.isBlank() || code.length()>2048 || !callback.equals(frontend+"/api/auth/facebook/callback"))throw denied();
        var value=get(TOKEN,Map.of("client_id",app,"client_secret",secret,"redirect_uri",callback,"code",code));
        Object token=value.get("access_token");long seconds=integer(value.get("expires_in"));
        if(!(token instanceof String text) || text.isBlank() || text.length()>8192 || seconds<=0 || !"bearer".equalsIgnoreCase(Objects.toString(value.get("token_type"),"")))throw denied();
        return OAuth2AccessTokenResponse.withToken((String)token).tokenType(OAuth2AccessToken.TokenType.BEARER).expiresIn(seconds).scopes(Set.of("public_profile")).build();
    }
    String inspect(String token){
        if(token==null || token.isBlank() || token.length()>8192)throw denied();
        var value=get(INSPECTION,Map.of("input_token",token,"access_token",app+"|"+secret));
        if(!(value.get("data") instanceof Map<?,?> data) || !Boolean.TRUE.equals(data.get("is_valid")) || !app.equals(Objects.toString(data.get("app_id"),"")) || !"USER".equals(data.get("type"))
            || integer(data.get("expires_at"))<=Instant.now().getEpochSecond())throw denied();
        if(data.containsKey("data_access_expires_at") && integer(data.get("data_access_expires_at"))<=Instant.now().getEpochSecond())throw denied();
        Object subject=data.get("user_id");if(!(subject instanceof String id) || !id.matches("[0-9]{1,200}"))throw denied();return id;
    }
    private static OAuth2AuthenticationException denied(){return new OAuth2AuthenticationException(new OAuth2Error("access_denied"));}
}
