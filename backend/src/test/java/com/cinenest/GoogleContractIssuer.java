package com.cinenest;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Local protocol fixture, never a production login route, provider or credential. */
final class GoogleContractIssuer implements AutoCloseable {
    final String clientId = "cinenest-test";
    final String clientSecret = UUID.randomUUID().toString();
    final HttpServer server;
    final RSAKey key;
    final Map<String, Map<String,String>> codes = new ConcurrentHashMap<>();
    String subject = "customer-a";
    String mode = "VALID";
    String lastIdToken = "";
    String lastAccessToken = "";

    GoogleContractIssuer() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            key = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate()).keyID(UUID.randomUUID().toString()).build();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/authorize", this::authorize);
            server.createContext("/token", this::token);
            server.createContext("/jwks", exchange -> json(exchange, 200, Map.of("keys", List.of(key.toPublicJWK().toJSONObject()))));
            server.start();
        } catch (Exception failure) { throw new IllegalStateException("Cannot start isolated OIDC fixture", failure); }
    }
    String issuer() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    static Map<String,String> parameters(String raw) {
        var result = new HashMap<String,String>();
        if (raw == null) return result;
        for (String item : raw.split("&")) {
            String[] pair = item.split("=", 2);
            result.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "");
        }
        return result;
    }
    private void authorize(HttpExchange exchange) throws IOException {
        var parameters = parameters(exchange.getRequestURI().getRawQuery());
        if(mode.equals("CANCEL")) {exchange.getResponseHeaders().set("Location",parameters.get("redirect_uri")+"?error=access_denied&state="+URLEncoder.encode(parameters.get("state"),StandardCharsets.UTF_8));exchange.sendResponseHeaders(302,-1);exchange.close();return;}
        String code = UUID.randomUUID().toString();
        parameters.put("subject", subject);
        codes.put(code, parameters);
        String location = parameters.get("redirect_uri") + "?code=" + code + "&state="
            + URLEncoder.encode(parameters.get("state"), StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }
    private void token(HttpExchange exchange) throws IOException {
        String expected = "Basic " + Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        if (!expected.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            json(exchange, 401, Map.of("error", "invalid_client")); return;
        }
        var body = parameters(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        var original = codes.remove(body.get("code"));
        if (original == null || !original.get("redirect_uri").equals(body.get("redirect_uri"))) {
            json(exchange, 400, Map.of("error", "invalid_grant")); return;
        }
        try {
            if(mode.equals("TIMEOUT"))Thread.sleep(3500);
            String verifier=body.get("code_verifier");
            String challenge=verifier==null?"":Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            if(!"S256".equals(original.get("code_challenge_method")) || !challenge.equals(original.get("code_challenge"))){json(exchange,400,Map.of("error","invalid_grant"));return;}
            Instant now = Instant.now();
            var claims = new JWTClaimsSet.Builder().issuer(mode.equals("ISSUER") ? issuer() + "/wrong" : issuer())
                .subject(original.get("subject")).audience(mode.equals("AUDIENCE") ? "wrong-client" : clientId)
                .issueTime(Date.from(now.minusSeconds(1)))
                .expirationTime(Date.from(mode.equals("EXPIRED") ? now.minusSeconds(600) : now.plusSeconds(120)))
                .claim("nonce", mode.equals("NONCE") ? UUID.randomUUID().toString() : original.get("nonce"))
                .claim("email", mode.equals("MISSING_EMAIL")?null:"same-address@example.test").claim("email_verified",!mode.equals("UNVERIFIED_EMAIL")).build();
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(key));
            lastIdToken = jwt.serialize();
            if (mode.equals("SIGNATURE")) {
                String[] parts = lastIdToken.split("\\.");
                parts[2] = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
                lastIdToken = String.join(".", parts);
            }
            lastAccessToken = UUID.randomUUID().toString();
            json(exchange, 200, Map.of("access_token", lastAccessToken, "token_type", "Bearer", "expires_in", 120, "id_token", lastIdToken));
        } catch (Exception failure) { json(exchange, 500, Map.of("error", "fixture_failure")); }
    }
    private static void json(HttpExchange exchange, int status, Map<String,?> body) throws IOException {
        byte[] bytes = JSONObjectUtils.toJSONString(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
    @Override public void close() { server.stop(0); }
}
