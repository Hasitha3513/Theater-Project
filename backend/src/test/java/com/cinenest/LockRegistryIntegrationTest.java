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
class LockRegistryIntegrationTest {
    static final SyntheticOidcIssuer IDP = new SyntheticOidcIssuer();
    static final UUID A = id("customer-a"), B = id("customer-b"), STAFF = id("staff");
    static final UUID VA = id("venue-a"), VB = id("venue-b");
    @Autowired JdbcClient jdbc;
    @Autowired Environment environment;
    final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("cinenest.identity.enabled", () -> "true");
        properties.add("cinenest.iot.lock-registry.enabled", () -> "true");
        properties.add("cinenest.iot.lock-registry.mode", () -> "QUALIFICATION_ONLY");
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

    static final java.util.concurrent.atomic.AtomicInteger FIXTURE=new java.util.concurrent.atomic.AtomicInteger();
    String root(UUID venue){return "/api/admin/venues/"+venue+"/iot/lock-devices";}
    String payload(int fixture,UUID command){return """
        {"commandKey":"%s","expectedRevision":0,"provider":"TUYA","providerDeviceReference":{"id":"%s","version":"%s"},"manufacturer":"Fixture manufacturer","model":"Unsupported qualification model","sku":null,"hardwareRevision":null,"firmwareRevision":null,"gatewayReference":null,"capabilityDeclaration":{"source":"CONTRACT_FIXTURE","sourceReference":null,"capabilities":["SCHEDULED_PIN"]}}
        """.formatted(command,id("LOCK-REF-"+fixture),id("LOCK-V1"));}
    String fresh(){return payload(FIXTURE.incrementAndGet(),UUID.randomUUID());}
    final Map<Browser,Map<String,Object>> tokens=new java.util.IdentityHashMap<>();
    synchronized Map<String,Object> csrf(Browser b) throws Exception {var token=tokens.get(b);if(token==null){token=JSONObjectUtils.parse(b.get("/api/session/csrf").body());tokens.put(b,token);}return token;}
    HttpResponse<String> post(Browser browser,UUID venue,String body,boolean csrf) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base()+root(venue))).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if(browser.cookie!=null)request.header("Cookie",browser.cookie);
        if(csrf){var token=csrf(browser);request.header((String)token.get("headerName"),(String)token.get("token"));}
        return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    void grants(){grant("iot.lock.registry.register","VENUE",VA);grant("iot.lock.registry.read","VENUE",VA);}
    UUID register(Browser b,String body) throws Exception {var r=post(b,VA,body,true);assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(201);return UUID.fromString((String)JSONObjectUtils.parse(r.body()).get("deviceId"));}
    int count(String table){return jdbc.sql("SELECT count(*) FROM cinenest_iot."+table).query(Integer.class).single();}
    @Test void authenticatedRegistrationAndEveryReadAreAuditedWithoutDisclosure() throws Exception {
        grants();var browser=signedIn("staff");int before=count("lock_registry_audit");UUID d=register(browser,fresh());
        for(String suffix:new String[]{"", "/"+d,"/"+d+"/revisions","/"+d+"/revisions/1"}){
            var r=browser.get(root(VA)+suffix);assertThat(r.statusCode()).isEqualTo(200);assertThat(r.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
            assertThat(r.body()).contains("UNQUALIFIED","NOT_VERIFIED","QUALIFICATION_ONLY","\"customerAccessEnabled\":false").doesNotContain("provider_reference","provider_scope","provider_identity","registered_by","sourceReference","LOCK-REF","credential","pinValue");
        }
        assertThat(count("lock_registry_audit")-before).isEqualTo(5);
        assertThat(jdbc.sql("SELECT room_id FROM cinenest_iot.assignment WHERE room_id=:d").param("d",d).query(UUID.class).list()).isEmpty();
    }
    @Test void writeDoesNotImplyReadAndReadDoesNotImplyWrite() throws Exception {
        var g=grant("iot.lock.registry.register","VENUE",VA);var b=signedIn("staff");UUID d=register(b,fresh());assertThat(b.get(root(VA)+"/"+d).statusCode()).isEqualTo(403);
        jdbc.sql("UPDATE cinenest_identity.access_grant SET state='REVOKED',revision=revision+1 WHERE grant_id=:g").param("g",g).update();grant("iot.lock.registry.read","VENUE",VA);
        assertThat(b.get(root(VA)+"/"+d).statusCode()).isEqualTo(200);assertThat(post(b,VA,fresh(),true).statusCode()).isEqualTo(403);
    }
    @Test void unrelatedRolesAndCrossVenueGrantsCannotSubstitute() throws Exception {
        for(String p:new String[]{"iot.synthetic.read","iot.synthetic.command","account.read","room.write","access.synthetic.review","Administrator"})grant(p,"VENUE",VA);
        grant("iot.lock.registry.read","VENUE",VB);grant("iot.lock.registry.register","VENUE",VB);var b=signedIn("staff");
        assertThat(b.get(root(VA)).statusCode()).isEqualTo(403);assertThat(post(b,VA,fresh(),true).statusCode()).isEqualTo(403);
        jdbc.sql("DELETE FROM cinenest_identity.account_scope WHERE account_id=:a AND venue_id=:v").param("a",STAFF).param("v",VB).update();assertThat(b.get(root(VB)).statusCode()).isEqualTo(403);
    }
    @Test void missingExpiredAndIneligibleSessionsAreDenied() throws Exception {
        grants();assertThat(new Browser().get(root(VA)).statusCode()).isEqualTo(401);var b=signedIn("staff");
        jdbc.sql("UPDATE cinenest_identity.http_session SET last_access_time=0, expiry_time=0").update();assertThat(b.get(root(VA)).statusCode()).isEqualTo(401);
        b=signedIn("staff");jdbc.sql("UPDATE cinenest_identity.account SET eligibility='SUSPENDED',revision=revision+1 WHERE account_id=:a").param("a",STAFF).update();assertThat(b.get(root(VA)).statusCode()).isIn(401,403);
    }
    @Test void csrfRequiredAndUnsupportedMethodsCannotMutate() throws Exception {
        grants();var b=signedIn("staff");int before=count("lock_registry_device");assertThat(post(b,VA,fresh(),false).statusCode()).isEqualTo(403);
        assertThat(b.call("DELETE",root(VA),Map.of()).statusCode()).isIn(403,405);assertThat(count("lock_registry_device")).isEqualTo(before);
    }
    @Test void identicalReplayPreservesIdentityAndChangedPayloadFails() throws Exception {
        grants();var b=signedIn("staff");String body=fresh();UUID d=register(b,body);int devices=count("lock_registry_device");
        var r=post(b,VA,body,true);assertThat(r.statusCode()).isEqualTo(200);assertThat(r.body()).contains(d.toString(),"\"replayed\":true");
        assertThat(post(b,VA,body.replace("Fixture manufacturer","Changed"),true).statusCode()).isEqualTo(409);assertThat(count("lock_registry_device")).isEqualTo(devices);
    }
    @Test void canonicalDeviceUniquenessRejectsAlternateReferenceAliases() throws Exception {
        grants();var b=signedIn("staff");int fixture=FIXTURE.incrementAndGet();register(b,payload(fixture,UUID.randomUUID()));
        String alias=payload(fixture,UUID.randomUUID()).replace(id("LOCK-REF-"+fixture).toString(),id("LOCK-ALIAS-"+fixture).toString());assertThat(post(b,VA,alias,true).statusCode()).isEqualTo(409);
    }
    @Test void concurrentSameCommandCommitsOneRegistrationAndReplays() throws Exception {
        grants();var b=signedIn("staff");String body=fresh();csrf(b);int count=count("lock_registry_device");
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var first=pool.submit(()->post(b,VA,body,true));var second=pool.submit(()->post(b,VA,body,true));
            assertThat(java.util.List.of(first.get().statusCode(),second.get().statusCode())).containsExactlyInAnyOrder(201,200);
        }assertThat(count("lock_registry_device")).isEqualTo(count+1);
    }
    @Test void concurrentDifferentCommandsForSameDeviceCannotDuplicate() throws Exception {
        grants();var a=signedIn("staff");int fixture=FIXTURE.incrementAndGet();csrf(a);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var x=pool.submit(()->post(a,VA,payload(fixture,UUID.randomUUID()),true));var y=pool.submit(()->post(a,VA,payload(fixture,UUID.randomUUID()),true));
            assertThat(java.util.List.of(x.get().statusCode(),y.get().statusCode())).containsExactlyInAnyOrder(201,409);
        }
    }
    @Test void revokedPermissionIsRecheckedAfterDatabaseLockWait() throws Exception {
        var g=grant("iot.lock.registry.read","VENUE",VA);var b=signedIn("staff");
        try(var db=DriverManager.getConnection(environment.getRequiredProperty("spring.datasource.url"),environment.getRequiredProperty("spring.datasource.username"),environment.getRequiredProperty("spring.datasource.password"));var pool=java.util.concurrent.Executors.newSingleThreadExecutor()){
            db.setAutoCommit(false);try(var s=db.prepareStatement("UPDATE cinenest_identity.access_grant SET state='REVOKED',revision=revision+1 WHERE grant_id=?")){s.setObject(1,g);s.executeUpdate();}
            var future=pool.submit(()->b.get(root(VA)));Thread.sleep(150);assertThat(future.isDone()).isFalse();db.commit();assertThat(future.get().statusCode()).isEqualTo(403);
        }
    }
    @Test void invalidQueriesPayloadsAndUnknownReferencesAreSanitized() throws Exception {
        grants();var b=signedIn("staff");for(String query:new String[]{"?page=-1","?page=no","?page=0&page=1","?limit=3","?page=10001"})assertThat(b.get(root(VA)+query).statusCode()).isEqualTo(400);
        assertThat(b.get(root(VA)+"/invalid").statusCode()).isEqualTo(400);assertThat(b.get(root(VA)+"/"+UUID.randomUUID()).statusCode()).isEqualTo(404);
        for(String body:new String[]{"{}",fresh().replace("\"expectedRevision\":0","\"expectedRevision\":0,\"expectedRevision\":0"),fresh().replace(id("LOCK-V1").toString(),UUID.randomUUID().toString()),fresh().replace("Unsupported qualification model","x".repeat(129))}){
            var r=post(b,VA,body,true);assertThat(r.statusCode()).isIn(400,404);assertThat(r.body()).doesNotContain("SQL","reference_id","stack","LOCK-V1");}
    }
    @Test void revisionHistoryAndReceiptsRejectMutationAndOrphanRecords() throws Exception {
        grants();var b=signedIn("staff");UUID d=register(b,fresh());
        for(String table:new String[]{"lock_registry_device","lock_registry_revision","lock_registry_command","lock_registry_audit"}){
            assertThatThrownBy(()->jdbc.sql("DELETE FROM cinenest_iot."+table).update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(()->jdbc.sql("TRUNCATE cinenest_iot."+table+" CASCADE").update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
        assertThatThrownBy(()->jdbc.sql("UPDATE cinenest_iot.lock_registry_revision SET model='changed' WHERE device_id=:d").param("d",d).update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.sql("INSERT INTO cinenest_iot.lock_registry_device(device_id,venue_id,provider,provider_scope_id,provider_identity_id,registered_by,registration_command_key) VALUES(:d,:v,'TUYA',:s,:i,:a,:k)").param("d",UUID.randomUUID()).param("v",VA).param("s",UUID.randomUUID()).param("i",UUID.randomUUID()).param("a",STAFF).param("k",UUID.randomUUID()).update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(b.get(root(VA)+"/"+d+"/revisions/2").statusCode()).isEqualTo(404);assertThat(b.get(root(VA)+"/"+d+"/revisions/0").statusCode()).isEqualTo(400);
    }
    @Test void auditFailureRollsBackRegistrationAndFailsClosedForReads() throws Exception {
        grants();var b=signedIn("staff");int devices=count("lock_registry_device"),commands=count("lock_registry_command"),revisions=count("lock_registry_revision");
        jdbc.sql("CREATE FUNCTION cinenest_iot.registry_test_audit_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic mandatory audit unavailable'; END $$").update();
        jdbc.sql("CREATE TRIGGER registry_test_audit_failure BEFORE INSERT ON cinenest_iot.lock_registry_audit FOR EACH STATEMENT EXECUTE FUNCTION cinenest_iot.registry_test_audit_fail()").update();
        try{assertThat(post(b,VA,fresh(),true).statusCode()).isEqualTo(503);assertThat(b.get(root(VA)).statusCode()).isEqualTo(503);assertThat(count("lock_registry_device")).isEqualTo(devices);assertThat(count("lock_registry_command")).isEqualTo(commands);assertThat(count("lock_registry_revision")).isEqualTo(revisions);}
        finally{jdbc.sql("DROP TRIGGER registry_test_audit_failure ON cinenest_iot.lock_registry_audit").update();jdbc.sql("DROP FUNCTION cinenest_iot.registry_test_audit_fail()").update();}
    }
    @Test void registeredDeviceDoesNotMutateFinancialBookingOrAccessState() throws Exception {
        grants();var b=signedIn("staff");var tables=java.util.List.of("cinenest_payments.incoming_payment_credit","cinenest_payments.payment_allocation","cinenest_booking.booking","cinenest_access.access_credential","cinenest_access.access_work","cinenest_iot.assignment","cinenest_iot.command");
        var before=tables.stream().map(t->jdbc.sql("SELECT count(*) FROM "+t).query(Integer.class).single()).toList();register(b,fresh());
        assertThat(tables.stream().map(t->jdbc.sql("SELECT count(*) FROM "+t).query(Integer.class).single()).toList()).isEqualTo(before);
    }
    @Test void listPaginationAndVenueIsolationUseFixedBoundedPages() throws Exception {
        grants();grant("iot.lock.registry.read","VENUE",VB);var b=signedIn("staff");
        for(int i=0;i<26;i++)register(b,fresh());
        var first=JSONObjectUtils.parse(b.get(root(VA)+"?page=0").body());assertThat((java.util.List<?>)first.get("items")).hasSize(25);assertThat(first.get("hasNext")).isEqualTo(true);
        var later=b.get(root(VA)+"?page=1");assertThat(later.statusCode()).isEqualTo(200);assertThat((java.util.List<?>)JSONObjectUtils.parse(later.body()).get("items")).isNotEmpty();
        var empty=JSONObjectUtils.parse(b.get(root(VB)).body());assertThat((java.util.List<?>)empty.get("items")).isEmpty();assertThat(empty.get("hasNext")).isEqualTo(false);
    }
    @Test void independentActorsRaceOnCanonicalDeviceIdentity() throws Exception {
        grants();jdbc.sql("INSERT INTO cinenest_identity.access_grant VALUES (:g,:a,'iot.lock.registry.register','VENUE',:v,'ACTIVE',clock_timestamp()-interval '1 minute',clock_timestamp()+interval '1 hour',0)").param("g",UUID.randomUUID()).param("a",A).param("v",VA).update();
        var staff=signedIn("staff");var customer=signedIn("customer-a");csrf(staff);csrf(customer);int fixture=FIXTURE.incrementAndGet();
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var x=pool.submit(()->post(staff,VA,payload(fixture,UUID.randomUUID()),true));var y=pool.submit(()->post(customer,VA,payload(fixture,UUID.randomUUID()),true));
            assertThat(java.util.List.of(x.get().statusCode(),y.get().statusCode())).containsExactlyInAnyOrder(201,409);
        }
    }
    @Test void v26UpgradePreservesPopulatedDataAndMigrationIsRepeatable() throws Exception {
        String url=environment.getRequiredProperty("spring.datasource.url"),user=environment.getRequiredProperty("spring.datasource.username"),password=environment.getRequiredProperty("spring.datasource.password");String dbName="registry_upgrade_"+UUID.randomUUID().toString().replace("-","");String target=url.substring(0,url.lastIndexOf('/')+1)+dbName;
        try(var db=DriverManager.getConnection(url,user,password);var s=db.createStatement()){s.execute("CREATE DATABASE "+dbName);}
        try{Flyway.configure().dataSource(target,user,password).target("26").load().migrate();try(var db=DriverManager.getConnection(target,user,password);var s=db.createStatement()){s.execute("INSERT INTO cinenest_rooms.venue(venue_id,code,display_name,timezone) VALUES('00000000-0000-4000-8000-000000000001','KEEP','Retained V26','Asia/Colombo')");s.execute("INSERT INTO cinenest_iot.device(device_id,venue_id,room_id,capability,synthetic,enabled,online) VALUES('00000000-0000-4000-8000-000000000002','00000000-0000-4000-8000-000000000001','00000000-0000-4000-8000-000000000003','LIGHT_SET_STATE',TRUE,TRUE,FALSE)");}
            var migration=Flyway.configure().dataSource(target,user,password).target("27").load();assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);migration.validate();assertThat(migration.migrate().migrationsExecuted).isZero();
            try(var db=DriverManager.getConnection(target,user,password);var s=db.createStatement();var rows=s.executeQuery("SELECT display_name FROM cinenest_rooms.venue WHERE code='KEEP'")){assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("Retained V26");}try(var db=DriverManager.getConnection(target,user,password);var q=db.createStatement();var retained=q.executeQuery("SELECT capability,synthetic,online FROM cinenest_iot.device")){assertThat(retained.next()).isTrue();assertThat(retained.getString(1)).isEqualTo("LIGHT_SET_STATE");assertThat(retained.getBoolean(2)).isTrue();assertThat(retained.getBoolean(3)).isFalse();}}
        finally{try(var db=DriverManager.getConnection(url,user,password);var s=db.createStatement()){s.execute("DROP DATABASE "+dbName);}}
    }
}
