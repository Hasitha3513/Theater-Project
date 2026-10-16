package com.cinenest;

import com.nimbusds.jose.util.JSONObjectUtils;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class IdentityIntegrationTest {
    static final SyntheticOidcIssuer IDP = new SyntheticOidcIssuer();
    static final UUID A = id("customer-a"), B = id("customer-b"), STAFF = id("staff");
    static final UUID VA = id("venue-a"), VB = id("venue-b");
    @Autowired JdbcClient jdbc;
    @Autowired Environment environment;
    final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("cinenest.identity.enabled", () -> "true");
        properties.add("cinenest.identity.session-timeout-seconds", () -> "120"); // Synthetic test value, not production policy.
        properties.add("cinenest.identity.issuer", IDP::issuer);
        properties.add("cinenest.identity.authorization-uri", () -> IDP.issuer() + "/authorize");
        properties.add("cinenest.identity.token-uri", () -> IDP.issuer() + "/token");
        properties.add("cinenest.identity.jwk-set-uri", () -> IDP.issuer() + "/jwks");
        properties.add("cinenest.identity.client-id", () -> IDP.clientId);
        properties.add("cinenest.identity.client-secret", () -> IDP.clientSecret);
    }
    @AfterAll static void stopIssuer() { IDP.close(); }
    static UUID id(String value) { return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    String base() { return "http://127.0.0.1:" + environment.getRequiredProperty("local.server.port"); }

    @BeforeEach void fixtures() {
        jdbc.sql("TRUNCATE cinenest_identity.account CASCADE").update();
        jdbc.sql("TRUNCATE cinenest_identity.http_session CASCADE").update();
        jdbc.sql("TRUNCATE cinenest_identity.security_audit").update();
        IDP.mode = "VALID";
        IDP.codes.clear();
        for (String subject : new String[]{"customer-a", "customer-b", "staff"}) {
            UUID account = id(subject);
            jdbc.sql("INSERT INTO cinenest_identity.account VALUES (:id,TRUE,'ELIGIBLE',0)").param("id", account).update();
            jdbc.sql("INSERT INTO cinenest_identity.external_identity VALUES (:issuer,:subject,:id)")
                .param("issuer", IDP.issuer()).param("subject", subject).param("id", account).update();
        }
        scope(A, VA); scope(B, VB); scope(STAFF, VA); scope(STAFF, VB);
    }
    void scope(UUID actor, UUID venue) {
        jdbc.sql("INSERT INTO cinenest_identity.account_scope VALUES (:id,:venue)").param("id",actor).param("venue",venue).update();
    }
    UUID grant(String permission, String kind, UUID venue) {
        UUID grant = UUID.randomUUID();
        jdbc.sql("INSERT INTO cinenest_identity.access_grant VALUES (:grant,:actor,:permission,:kind,:venue,'ACTIVE',CURRENT_TIMESTAMP - INTERVAL '1 minute',CURRENT_TIMESTAMP + INTERVAL '1 hour',0)")
            .param("grant",grant).param("actor",STAFF).param("permission",permission).param("kind",kind)
            .param("venue",venue,java.sql.Types.OTHER).update();
        return grant;
    }
    final class Browser {
        String cookie;
        String callback;
        HttpResponse<String> call(String method, String uri, Map<String,String> headers) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(uri.startsWith("http") ? uri : base()+uri))
                .method(method, HttpRequest.BodyPublishers.noBody());
            // Explicit loopback protocol harness: secure cookies remain secure in application configuration.
            if (cookie != null) builder.header("Cookie", cookie);
            headers.forEach(builder::header);
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            for (String header : response.headers().allValues("Set-Cookie")) {
                if (header.startsWith("CINENEST_SESSION=")) cookie = header.split(";",2)[0];
            }
            return response;
        }
        HttpResponse<String> get(String path) throws Exception { return call("GET",path,Map.of()); }
        HttpResponse<String> login(String subject) throws Exception {
            IDP.subject = subject;
            var start = get("/oauth2/authorization/cinenest");
            assertThat(start.statusCode()).isEqualTo(302);
            var authorization = client.send(HttpRequest.newBuilder(URI.create(start.headers().firstValue("Location").orElseThrow())).build(), HttpResponse.BodyHandlers.ofString());
            callback = authorization.headers().firstValue("Location").orElseThrow();
            return get(callback);
        }
        HttpResponse<String> logout() throws Exception {
            var csrf = JSONObjectUtils.parse(get("/api/session/csrf").body());
            return call("POST","/api/session/logout",Map.of((String)csrf.get("headerName"),(String)csrf.get("token")));
        }
    }
    Browser signedIn(String subject) throws Exception {
        var browser = new Browser();
        assertThat(browser.login(subject).statusCode()).isEqualTo(204);
        return browser;
    }

    @Test void associatedCustomerReadsOnlyMinimumOwnAccount() throws Exception {
        var response = signedIn("customer-a").get("/api/accounts/me");
        assertThat(response.statusCode()).isEqualTo(200);
        var body = JSONObjectUtils.parse(response.body());
        assertThat(body.keySet()).containsExactlyInAnyOrder("accountId","eligibility");
        assertThat(body.get("accountId")).isEqualTo(A.toString());
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    }
    @Test void unauthenticatedAndInvalidSessionCannotRead() throws Exception {
        var browser = new Browser();
        assertThat(browser.get("/api/accounts/me").statusCode()).isEqualTo(401);
        browser.cookie = "CINENEST_SESSION=invalid";
        assertThat(browser.get("/api/accounts/me").statusCode()).isEqualTo(401);
    }
    @Test void foreignUnknownAndMalformedIdsHaveSameOpaqueDenial() throws Exception {
        var browser = signedIn("customer-a");
        for (String path : new String[]{B.toString(),UUID.randomUUID().toString(),"not-a-uuid"}) {
            var response = browser.get("/api/accounts/"+path+"?owner="+A+"&role=ADMIN&venue="+VB);
            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(response.body()).isEmpty();
        }
    }
    @Test void matchingGrantAndAuthoritativeTargetVenuePermitRead() throws Exception {
        grant("account.read","VENUE",VA);
        assertThat(signedIn("staff").get("/api/accounts/"+A).statusCode()).isEqualTo(200);
    }
    @Test void wrongVenueDoesNotAuthorize() throws Exception {
        grant("account.read","VENUE",VB);
        assertThat(signedIn("staff").get("/api/accounts/"+A+"?venue="+VB).statusCode()).isEqualTo(403);
    }
    @Test void scopeWithoutRequiredPermissionDoesNotAuthorize() throws Exception {
        grant("other.read","VENUE",VA);
        assertThat(signedIn("staff").get("/api/accounts/"+A).statusCode()).isEqualTo(403);
    }
    @Test void permissionWithoutVenueScopeDoesNotAuthorize() throws Exception {
        grant("account.read","SELF",null);
        assertThat(signedIn("staff").get("/api/accounts/"+A).statusCode()).isEqualTo(403);
    }
    @Test void unrelatedGrantsCannotBeStitched() throws Exception {
        grant("account.read","VENUE",VA); grant("other.read","VENUE",VB);
        assertThat(signedIn("staff").get("/api/accounts/"+B).statusCode()).isEqualTo(403);
    }
    @ParameterizedTest @ValueSource(strings={"REVOKED","EXPIRED","FUTURE"})
    void grantChangesAffectNextRequest(String change) throws Exception {
        UUID grant = grant("account.read","VENUE",VA);
        var browser = signedIn("staff");
        assertThat(browser.get("/api/accounts/"+A).statusCode()).isEqualTo(200);
        String update = switch(change) {
            case "REVOKED" -> "state = 'REVOKED', revision = revision+1";
            case "EXPIRED" -> "valid_from = CURRENT_TIMESTAMP-INTERVAL '2 hours', valid_until = CURRENT_TIMESTAMP-INTERVAL '1 hour'";
            default -> "valid_from = CURRENT_TIMESTAMP+INTERVAL '1 hour', valid_until = CURRENT_TIMESTAMP+INTERVAL '2 hours'";
        };
        jdbc.sql("UPDATE cinenest_identity.access_grant SET "+update+" WHERE grant_id=:id").param("id",grant).update();
        assertThat(browser.get("/api/accounts/"+A).statusCode()).isEqualTo(403);
    }
    @ParameterizedTest @ValueSource(strings={"SUSPENDED","INELIGIBLE"})
    void changedAccountEligibilityAffectsNextRequest(String state) throws Exception {
        var browser = signedIn("customer-a");
        jdbc.sql("UPDATE cinenest_identity.account SET eligibility=:state,revision=revision+1 WHERE account_id=:id").param("state",state).param("id",A).update();
        assertThat(browser.get("/api/accounts/me").statusCode()).isEqualTo(403);
        assertThat(browser.get("/api/session/csrf").statusCode()).isEqualTo(403);
    }
    @Test void removedVenueAssignmentRemovesGrantAuthority() throws Exception {
        grant("account.read","VENUE",VA);var browser=signedIn("staff");
        jdbc.sql("DELETE FROM cinenest_identity.account_scope WHERE account_id=:id AND venue_id=:venue").param("id",STAFF).param("venue",VA).update();
        assertThat(browser.get("/api/accounts/"+A).statusCode()).isEqualTo(403);
    }
    @Test void unknownIdentityWithSameEmailCannotProvisionOrMerge() throws Exception {
        var browser=new Browser();assertThat(browser.login("unassociated").statusCode()).isEqualTo(403);
        assertThat(browser.get("/api/accounts/me").statusCode()).isEqualTo(401);
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.account").query(Integer.class).single()).isEqualTo(3);
    }
    @Test void authenticationAuditFailureDoesNotLeaveAuthenticatedSession() throws Exception {
        var browser = new Browser();
        jdbc.sql("ALTER TABLE cinenest_identity.security_audit RENAME TO unavailable_audit").update();
        try {
            assertThat(browser.login("customer-a").statusCode()).isIn(401,403,500);
        } finally {
            jdbc.sql("ALTER TABLE cinenest_identity.unavailable_audit RENAME TO security_audit").update();
        }
        assertThat(browser.get("/api/accounts/me").statusCode()).isEqualTo(401);
    }
    @Test void nonSyntheticAccountCannotAuthenticate() throws Exception {
        jdbc.sql("UPDATE cinenest_identity.account SET synthetic=FALSE WHERE account_id=:id").param("id",A).update();
        assertThat(new Browser().login("customer-a").statusCode()).isEqualTo(403);
    }
    @ParameterizedTest @ValueSource(strings={"ISSUER","AUDIENCE","EXPIRED","NONCE","SIGNATURE"})
    void invalidProviderTokenCannotAuthenticate(String mode) throws Exception {
        IDP.mode=mode;var browser=new Browser();assertThat(browser.login("customer-a").statusCode()).isEqualTo(403);
        assertThat(browser.get("/api/accounts/me").statusCode()).isEqualTo(401);
    }
    @Test void sessionIsPersistedAndExpiredOrDeletedStateIsDenied() throws Exception {
        var browser=signedIn("customer-a");
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.http_session WHERE principal_name=:id").param("id",A.toString()).query(Integer.class).single()).isEqualTo(1);
        jdbc.sql("UPDATE cinenest_identity.http_session SET last_access_time=0, expiry_time=0 WHERE principal_name=:id").param("id",A.toString()).update();
        assertThat(browser.get("/api/accounts/me").statusCode()).isEqualTo(401);
        browser=signedIn("customer-a");
        jdbc.sql("DELETE FROM cinenest_identity.http_session WHERE principal_name=:id").param("id",A.toString()).update();
        assertThat(browser.get("/api/accounts/me").statusCode()).isEqualTo(401);
    }
    @Test void persistedSessionSurvivesAnotherApplicationInstanceAndLogoutIsShared() throws Exception {
        var browser = signedIn("customer-a");
        Map<String,Object> configuration = new HashMap<>();
        for (String key : new String[]{"spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
                "cinenest.identity.enabled", "cinenest.identity.session-timeout-seconds", "cinenest.identity.issuer",
                "cinenest.identity.authorization-uri", "cinenest.identity.token-uri", "cinenest.identity.jwk-set-uri",
                "cinenest.identity.client-id", "cinenest.identity.client-secret"})
            configuration.put(key, environment.getRequiredProperty(key));
        configuration.put("server.port", "0");
        try (var other = new org.springframework.boot.builder.SpringApplicationBuilder(CineNestApplication.class)
                .initializers(context -> context.getEnvironment().getPropertySources().addFirst(
                    new org.springframework.core.env.MapPropertySource("isolated-session-recovery", configuration)))
                .run()) {
            String second = "http://127.0.0.1:" + ((org.springframework.boot.web.server.context.WebServerApplicationContext)other).getWebServer().getPort();
            assertThat(browser.get(second+"/api/accounts/me").statusCode()).isEqualTo(200);
            var csrf = JSONObjectUtils.parse(browser.get(second+"/api/session/csrf").body());
            String replay = browser.cookie;
            assertThat(browser.call("POST",second+"/api/session/logout",Map.of((String)csrf.get("headerName"),(String)csrf.get("token"))).statusCode()).isEqualTo(204);
            browser.cookie = replay;
            assertThat(browser.get("/api/accounts/me").statusCode()).isEqualTo(401);
        }
    }
    @Test void uncommittedRevocationCannotDirtyReadAndCommittedRevocationApplies() throws Exception {
        UUID permission = grant("account.read", "VENUE", VB);
        var browser = signedIn("staff");
        try (var transaction = DriverManager.getConnection(environment.getRequiredProperty("spring.datasource.url"),
                environment.getRequiredProperty("spring.datasource.username"), environment.getRequiredProperty("spring.datasource.password"))) {
            transaction.setAutoCommit(false);
            try (var statement = transaction.prepareStatement("UPDATE cinenest_identity.access_grant SET state='REVOKED' WHERE grant_id=?")) {
                statement.setObject(1, permission);
                statement.executeUpdate();
            }
            assertThat(browser.get("/api/accounts/"+B).statusCode()).isEqualTo(200);
            transaction.commit();
            assertThat(browser.get("/api/accounts/"+B).statusCode()).isEqualTo(403);
        }
    }
    @Test void csrfProtectedLogoutInvalidatesOnlyCurrentSession() throws Exception {
        var first=signedIn("customer-a");var second=signedIn("customer-a");
        assertThat(first.call("POST","/api/session/logout",Map.of()).statusCode()).isEqualTo(403);
        String replay=first.cookie;assertThat(first.logout().statusCode()).isEqualTo(204);
        first.cookie=replay;assertThat(first.get("/api/accounts/me").statusCode()).isEqualTo(401);
        assertThat(second.get("/api/accounts/me").statusCode()).isEqualTo(200);
    }
    @Test void loginRotatesSessionAndForgedOrReplayedCallbackDoesNotLogoutCurrentAccount() throws Exception {
        var browser=new Browser();browser.get("/oauth2/authorization/cinenest");String before=browser.cookie;
        assertThat(browser.login("customer-a").statusCode()).isEqualTo(204);
        assertThat(before.equals(browser.cookie)).isFalse();
        String valid=browser.cookie;browser.cookie=before;assertThat(browser.get("/api/accounts/me").statusCode()).isEqualTo(401);
        browser.cookie=valid;assertThat(browser.get(browser.callback).statusCode()).isEqualTo(403);
        assertThat(browser.get("/api/accounts/me").statusCode()).isEqualTo(200);
        assertThat(browser.get("/login/oauth2/code/cinenest?code=invalid&state=invalid").statusCode()).isEqualTo(403);
        assertThat(browser.get("/api/accounts/me").statusCode()).isEqualTo(200);
    }
    @Test void sessionCookiesAndAuditAreRedacted(CapturedOutput output) throws Exception {
        var browser=new Browser();var response=browser.login("customer-a");
        String header=response.headers().allValues("Set-Cookie").stream().filter(x->x.startsWith("CINENEST_SESSION=")).findFirst().orElseThrow();
        assertThat(header.contains("Secure")).isTrue();assertThat(header.contains("HttpOnly")).isTrue();assertThat(header.contains("SameSite=Lax")).isTrue();
        browser.get("/api/accounts/me");browser.get("/api/accounts/"+B);
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.security_audit WHERE actor_id=:id").param("id",A).query(Integer.class).single()).isGreaterThanOrEqualTo(3);
        String logged=output.getAll();assertThat(logged.contains(browser.cookie)).isFalse();assertThat(logged.contains(IDP.lastAccessToken)).isFalse();assertThat(logged.contains(IDP.lastIdToken)).isFalse();
        for (byte[] value:jdbc.sql("SELECT attribute_bytes FROM cinenest_identity.http_session_attributes").query(byte[].class).list()) {
            String serialized=new String(value,java.nio.charset.StandardCharsets.ISO_8859_1);
            assertThat(serialized.contains(IDP.lastAccessToken)).isFalse();assertThat(serialized.contains(IDP.lastIdToken)).isFalse();
        }
    }
    @Test void nonAuthRoutesAndUnsupportedMethodsRemainDenied() throws Exception {
        var browser=signedIn("customer-a");
        for(String path:new String[]{"/api/bookings","/api/accounts","/admin","/login","/api/grants"})assertThat(browser.get(path).statusCode()).isEqualTo(403);
        assertThat(browser.call("POST","/api/accounts/me",Map.of()).statusCode()).isEqualTo(403);
    }
    @Test void databaseConstraintsRejectDuplicateIdentityMissingScopesAndInvalidGrants() {
        assertThatThrownBy(()->jdbc.sql("INSERT INTO cinenest_identity.external_identity VALUES (:issuer,'customer-a',:id)").param("issuer",IDP.issuer()).param("id",B).update()).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->grant("account.read","VENUE",null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->grant("account.read","SELF",VA)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->grant("account.read","VENUE",UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void migrationUpgradesAnActualV1DatabaseAndRepeatsWithoutChanges() throws Exception {
        String name="cinenest_upgrade_"+UUID.randomUUID().toString().replace("-","");
        String url=environment.getRequiredProperty("spring.datasource.url");
        String user=environment.getRequiredProperty("spring.datasource.username");String password=environment.getRequiredProperty("spring.datasource.password");
        try(var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement()) {
            assertThat(connection.getCatalog()).isEqualTo("cinenest_test");
            statement.execute("CREATE DATABASE "+name);
            String upgraded=url.substring(0,url.lastIndexOf('/')+1)+name;
            try {
                var v1=Flyway.configure().dataSource(upgraded,user,password).target("1").load();assertThat(v1.migrate().migrationsExecuted).isEqualTo(1);
                var v2=Flyway.configure().dataSource(upgraded,user,password).target("2").load();assertThat(v2.migrate().migrationsExecuted).isEqualTo(1);v2.validate();assertThat(v2.migrate().migrationsExecuted).isZero();
            } finally { statement.execute("DROP DATABASE "+name); }
        }
    }
}
