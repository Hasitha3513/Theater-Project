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
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class AdminReadIntegrationTest {
    static final SyntheticOidcIssuer IDP = new SyntheticOidcIssuer();
    static final UUID A = id("customer-a"), B = id("customer-b"), STAFF = id("staff");
    static final UUID VA = id("venue-a"), VB = id("venue-b");
    @Autowired JdbcClient jdbc;
    @Autowired Environment environment;
    final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("cinenest.identity.admin-read.enabled", () -> "true");
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
        jdbc.sql("TRUNCATE cinenest_identity.admin_read_audit").update();
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


    String accounts(UUID venue){return "/api/admin/venues/"+venue+"/accounts";}
    String audit(UUID venue){return "/api/admin/venues/"+venue+"/audit";}
    String window(){return "&from=2000-01-01T00:00:00Z&to=2100-01-01T00:00:00Z";}
    @Test void sameVenueMinimumFieldsAndSuspendedTarget() throws Exception {
        grant("account.read","VENUE",VA);scope(B,VA);
        jdbc.sql("UPDATE cinenest_identity.account SET eligibility='SUSPENDED' WHERE account_id=:a").param("a",B).update();
        var browser=signedIn("staff");var response=browser.get(accounts(VA)+"/"+B);
        assertThat(response.statusCode()).isEqualTo(200);assertThat(response.headers().firstValue("Cache-Control").orElse("")).isEqualTo("no-store");
        var body=JSONObjectUtils.parse(response.body());assertThat(body.keySet()).containsExactlyInAnyOrder("accountId","synthetic","eligibility","revision","venueId");assertThat(body.get("eligibility")).isEqualTo("SUSPENDED");
        assertThat(browser.get(accounts(VA)+"/"+UUID.randomUUID()).statusCode()).isEqualTo(404);
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.admin_read_audit WHERE reason_code='NOT_FOUND' AND target_id IS NULL").query(Integer.class).single()).isEqualTo(1);
    }
    @Test void currentScopeRevocationSuspensionAndNoStitching() throws Exception {
        grant("account.read","VENUE",VA);grant("package.publish","VENUE",VB);var browser=signedIn("staff");
        assertThat(browser.get(accounts(VB)).statusCode()).isEqualTo(403);assertThat(browser.get(accounts(VA)+"/"+B).statusCode()).isEqualTo(404);
        jdbc.sql("UPDATE cinenest_identity.access_grant SET state='REVOKED' WHERE account_id=:a").param("a",STAFF).update();assertThat(browser.get(accounts(VA)).statusCode()).isEqualTo(403);
        jdbc.sql("UPDATE cinenest_identity.access_grant SET state='ACTIVE' WHERE account_id=:a").param("a",STAFF).update();jdbc.sql("UPDATE cinenest_identity.account SET eligibility='SUSPENDED' WHERE account_id=:a").param("a",STAFF).update();assertThat(browser.get(accounts(VA)).statusCode()).isEqualTo(403);
        assertThat(new Browser().get(accounts(VA)).statusCode()).isEqualTo(401);
    }
    @Test void paginationFiltersAndCursorBinding() throws Exception {
        grant("account.read","VENUE",VA);var browser=signedIn("staff");var first=browser.get(accounts(VA)+"?limit=1");assertThat(first.statusCode()).isEqualTo(200);
        var body=JSONObjectUtils.parse(first.body());String cursor=(String)body.get("nextCursor");assertThat(cursor).isNotBlank();
        var next=browser.get(accounts(VA)+"?limit=1&cursor="+cursor);assertThat(next.statusCode()).isEqualTo(200);assertThat(next.body()).isNotEqualTo(first.body());
        assertThat(browser.get(accounts(VA)+"?limit=1&cursor="+cursor+"x").statusCode()).isEqualTo(400);
        assertThat(browser.get(accounts(VA)+"?limit=2&cursor="+cursor).statusCode()).isEqualTo(400);
        for(String q:new String[]{"limit=101","limit=0","limit=1&limit=2","sort=password","eligibility=UNKNOWN","accountId=bad"})assertThat(browser.get(accounts(VA)+"?"+q).statusCode()).isEqualTo(400);
        assertThat(browser.get(accounts(VA)+"?accountId="+A).statusCode()).isEqualTo(200);
    }
    @Test void auditSourceIsolationAndLegacyExclusion() throws Exception {
        grant("account.read","VENUE",VA);grant("audit.account.read","VENUE",VA);var browser=signedIn("staff");browser.get(accounts(VA));
        var response=browser.get(audit(VA)+"?source=IDENTITY"+window());assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).contains("ACCOUNTS_LIST").doesNotContain("AUTHENTICATION","issuer","session_id");
        var list=(java.util.List<?>)JSONObjectUtils.parse(response.body()).get("items");var row=(Map<?,?>)list.getFirst();String event=row.get("eventId").toString();
        assertThat(browser.get(audit(VA)+"/"+event+"?source=IDENTITY"+window()).statusCode()).isEqualTo(200);
        assertThat(browser.get(audit(VB)+"/"+event+"?source=IDENTITY"+window()).statusCode()).isEqualTo(403);
        assertThat(browser.get(audit(VA)+"?source=VERIFICATION"+window()).statusCode()).isEqualTo(403);
        assertThat(browser.get(audit(VA)+"?source=IDENTITY,VERIFICATION"+window()).statusCode()).isEqualTo(400);
        assertThat(browser.get(audit(VA)+"?source=PAYMENTS"+window()).statusCode()).isEqualTo(400);
        assertThat(browser.get(audit(VA)+"?source=IDENTITY&from=bad&to=bad").statusCode()).isEqualTo(400);
        assertThat(row.keySet().stream().map(Object::toString).toList()).containsExactlyInAnyOrder("eventId","source","occurredAt","venueId","action","outcome","reasonCode","correlationId","actorId","resourceId");
    }
    @Test void appendOnlyAuditFailureAndNoBusinessMutation() throws Exception {
        grant("account.read","VENUE",VA);var browser=signedIn("staff");assertThat(browser.get(accounts(VA)).statusCode()).isEqualTo(200);
        assertThatThrownBy(()->jdbc.sql("DELETE FROM cinenest_identity.admin_read_audit").update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.sql("CREATE FUNCTION cinenest_identity.test_deny_admin() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test audit unavailable'; END $$").update();
        jdbc.sql("CREATE TRIGGER test_admin_failure BEFORE INSERT ON cinenest_identity.admin_read_audit FOR EACH ROW EXECUTE FUNCTION cinenest_identity.test_deny_admin()").update();
        try{assertThat(browser.get(accounts(VA)).statusCode()).isEqualTo(503);}finally{jdbc.sql("DROP TRIGGER test_admin_failure ON cinenest_identity.admin_read_audit").update();jdbc.sql("DROP FUNCTION cinenest_identity.test_deny_admin()").update();}
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.account WHERE eligibility='ELIGIBLE'").query(Integer.class).single()).isEqualTo(3);
        assertThat(browser.call("POST",accounts(VA),Map.of()).statusCode()).isIn(403,404,405);
    }
    @Test void verificationOwnerProjectionRedactsAndScopes() throws Exception {
        grant("audit.verification.read","VENUE",VA);var browser=signedIn("staff");UUID policy=UUID.randomUUID(),request=UUID.randomUUID(),event=UUID.randomUUID();
        jdbc.sql("INSERT INTO cinenest_verification.verification_policy_revision(policy_id,version,content_hash,policy,synthetic) VALUES(:p,:version,:hash,'{}',TRUE)").param("p",policy).param("version",policy.toString()).param("hash","0".repeat(64)).update();
        jdbc.sql("INSERT INTO cinenest_verification.verification_request(request_id,owner_id,venue_id,quote_id,policy_id,generation,revision,state) VALUES(:r,:o,:v,:q,:p,1,0,'REVIEW_REQUIRED')").param("r",request).param("o",A).param("v",VA).param("q",UUID.randomUUID()).param("p",policy).update();
        jdbc.sql("INSERT INTO cinenest_verification.verification_audit(event_id,actor_id,request_id,action,outcome,correlation_id) VALUES(:e,:a,:r,'PRIVATE_EVIDENCE_METADATA','PERMIT',:c)").param("e",event).param("a",STAFF).param("r",request).param("c",UUID.randomUUID()).update();
        var response=browser.get(audit(VA)+"?source=VERIFICATION"+window());assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).contains("VERIFICATION_EVENT").doesNotContain("PRIVATE_EVIDENCE_METADATA","policy_id","owner_id");
        assertThat(browser.get(audit(VA)+"/"+event+"?source=VERIFICATION"+window()).statusCode()).isEqualTo(200);
        assertThat(browser.get(audit(VA)+"?source=IDENTITY"+window()).statusCode()).isEqualTo(403);
        grant("audit.verification.read","VENUE",VB);assertThat(browser.get(audit(VB)+"/"+event+"?source=VERIFICATION"+window()).statusCode()).isEqualTo(404);
    }
    @Test void populatedV10UpgradeAndRepeatPreserveIdentity() throws Exception {
        String db="admin_upgrade_"+UUID.randomUUID().toString().replace("-","");String url=System.getenv("CINENEST_DB_URL"),user=System.getenv("CINENEST_DB_USER"),password=System.getenv("CINENEST_DB_PASSWORD");
        try(var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement()){statement.execute("CREATE DATABASE "+db);}
        String target=url.substring(0,url.lastIndexOf('/')+1)+db;
        try{
            Flyway.configure().dataSource(target,user,password).target("10").load().migrate();
            try(var connection=DriverManager.getConnection(target,user,password);var statement=connection.createStatement()){statement.execute("INSERT INTO cinenest_identity.account VALUES ('"+A+"',TRUE,'ELIGIBLE',3)");}
            var migration=Flyway.configure().dataSource(target,user,password).target("11").load();assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);assertThat(migration.migrate().migrationsExecuted).isZero();
            try(var connection=DriverManager.getConnection(target,user,password);var statement=connection.createStatement();var result=statement.executeQuery("SELECT revision FROM cinenest_identity.account")){assertThat(result.next()).isTrue();assertThat(result.getLong(1)).isEqualTo(3);}
        }finally{try(var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement()){statement.execute("DROP DATABASE "+db+" WITH (FORCE)");}}
    }
}
