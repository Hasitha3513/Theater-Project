package com.cinenest.identity.internal;

import com.nimbusds.jose.util.JSONObjectUtils;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.client.RestClient;

/** Disabled-by-default local qualification boundary. Never creates identity associations. */
final class GoogleLiveProvider {
    static final URI DISCOVERY=URI.create("https://accounts.google.com/.well-known/openid-configuration");
    static final String ISSUER="https://accounts.google.com";
    static final String AUTHORIZATION="https://accounts.google.com/o/oauth2/v2/auth";
    static final String TOKEN="https://oauth2.googleapis.com/token";
    static final String JWKS="https://www.googleapis.com/oauth2/v3/certs";
    private GoogleLiveProvider() { }

    static RestClient discoveryClient() {
        var factory=new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).followRedirects(java.net.http.HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(2));
        return RestClient.builder().requestFactory(factory).build();
    }
    static Map<String,Object> discover(RestClient client) {
        try {
            return client.get().uri(DISCOVERY).exchange((request,response)-> {
                if(response.getStatusCode().value()!=200)throw new IllegalStateException();
                var type=response.getHeaders().getContentType();
                if(type==null || !org.springframework.http.MediaType.APPLICATION_JSON.isCompatibleWith(type))throw new IllegalStateException();
                byte[] bytes=response.getBody().readNBytes(65537);
                if(bytes.length>65536)throw new IllegalStateException();
                try(var parser=tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build().createParser(bytes)){
                    if(parser.nextToken()!=tools.jackson.core.JsonToken.START_OBJECT)throw new IllegalStateException();
                    parser.skipChildren();if(parser.nextToken()!=null)throw new IllegalStateException();
                }
                Map<String,Object> metadata;
                try{metadata=JSONObjectUtils.parse(new String(bytes,StandardCharsets.UTF_8));}catch(java.text.ParseException invalid){throw new IllegalStateException();}
                validateMetadata(metadata);return metadata;
            });
        } catch(Exception unavailable) { throw new IllegalStateException("Google qualification discovery unavailable or invalid"); }
    }
    static void validateMetadata(Map<String,Object> value) {
        var expected=Map.of("issuer",ISSUER,"authorization_endpoint",AUTHORIZATION,"token_endpoint",TOKEN,"jwks_uri",JWKS);
        for(var e:expected.entrySet())if(!e.getValue().equals(value.get(e.getKey())))throw new IllegalStateException("Google qualification endpoint rejected");
        for(var e:Map.of("response_types_supported","code","code_challenge_methods_supported","S256","id_token_signing_alg_values_supported","RS256","token_endpoint_auth_methods_supported","client_secret_basic").entrySet())
            if(!(value.get(e.getKey()) instanceof List<?> list) || !list.contains(e.getValue()))throw new IllegalStateException("Google qualification capability rejected");
    }
    static String secret(String reference) {
        try {
            Path p=Path.of(reference);
            if(!p.isAbsolute() || !p.normalize().equals(p) || !p.toRealPath().equals(p) || !Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(p).equals(Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE))
                || !Files.getPosixFilePermissions(p.getParent()).equals(Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE,PosixFilePermission.OWNER_EXECUTE)))throw new IllegalStateException();
            byte[] bytes;
            try(var stream=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)){bytes=stream.readNBytes(8193);}
            String value=new String(bytes,StandardCharsets.UTF_8).strip();
            if(bytes.length>8192 || value.isBlank() || value.chars().anyMatch(c->c<33 || c>126))throw new IllegalStateException();
            return value;
        } catch(Exception invalid) { throw new IllegalStateException("Private Google qualification secret file required"); }
    }
    static ClientRegistration registration(String client,String secret,String frontend,Map<String,Object> metadata) {
        validateMetadata(metadata);
        if(!client.matches("[A-Za-z0-9_-]{1,200}\\.apps\\.googleusercontent\\.com") || secret.isBlank())throw new IllegalStateException("Google qualification credentials invalid");
        String origin=GoogleOidcContract.frontendOrigin(frontend).toString().replaceAll("/$","");
        return ClientRegistration.withRegistrationId("google").clientId(client).clientSecret(secret)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri(origin+"/api/auth/google/callback")
            .scope("openid").authorizationUri(AUTHORIZATION).tokenUri(TOKEN).jwkSetUri(JWKS).issuerUri(ISSUER)
            .userNameAttributeName("sub").clientName("Google — local qualification; not production admission")
            .clientSettings(ClientRegistration.ClientSettings.builder().requireProofKey(true).build()).build();
    }
}
