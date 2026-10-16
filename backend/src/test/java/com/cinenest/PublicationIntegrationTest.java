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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties="cinenest.pricing.publication-worker.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
class PublicationIntegrationTest {
    static final SyntheticOidcIssuer IDP = new SyntheticOidcIssuer();
    static final UUID A = id("customer-a"), B = id("customer-b"), STAFF = id("staff");
    static final UUID VA = id("venue-a"), VB = id("venue-b");
    @Autowired JdbcClient jdbc;
    @Autowired com.cinenest.identity.ScopedAuthorization scopedAuthorization;
    @Autowired com.cinenest.rooms.PackageConstraints packageConstraints;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
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
    static final UUID RID=id("pricing-room"),POLICY=id("pricing-policy"),SERVICE=id("pricing-service"),RATE=id("pricing-rate");
    String base() { return "http://127.0.0.1:" + environment.getRequiredProperty("local.server.port"); }

    @BeforeEach void fixtures() {
        jdbc.sql("TRUNCATE cinenest_pricing.package CASCADE").update();
        jdbc.sql("TRUNCATE cinenest_pricing.configuration_audit").update();
        jdbc.sql("TRUNCATE cinenest_pricing.service_definition CASCADE").update();
        jdbc.sql("TRUNCATE cinenest_rooms.venue CASCADE").update();
        jdbc.sql("TRUNCATE cinenest_rooms.configuration_audit").update();
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
        jdbc.sql("INSERT INTO cinenest_rooms.venue(venue_id,code,display_name,timezone,cleaning_buffer_minutes,enabled) VALUES(:id,'SYNTHETIC-A','Synthetic Venue A','Asia/Colombo',7,TRUE)").param("id",VA).update();
        jdbc.sql("INSERT INTO cinenest_rooms.venue(venue_id,code,display_name,timezone) VALUES(:id,'SYNTHETIC-B','Synthetic Venue B','Asia/Colombo')").param("id",VB).update();
        jdbc.sql("INSERT INTO cinenest_rooms.venue_operating_window VALUES(:id,1,600,1320)").param("id",VA).update();
        jdbc.sql("INSERT INTO cinenest_rooms.room(room_id,venue_id,code,name,capacity,enabled) VALUES(:id,:v,'SYNTHETIC-ROOM','Synthetic room',5,TRUE)").param("id",RID).param("v",VA).update();
        jdbc.sql("INSERT INTO cinenest_rooms.room_facility VALUES(:room,'HDMI','HDMI input',0)").param("room",RID).update();
        jdbc.sql("INSERT INTO cinenest_rooms.minimum_session_policy(policy_id,venue_id,revision,minimum_minutes,effective_from,approval_reference,approved_by) VALUES(:id,:v,1,180,clock_timestamp()-INTERVAL '1 hour','SYNTHETIC owner OD-21 fixture',:actor)").param("id",POLICY).param("v",VA).param("actor",STAFF).update();
        jdbc.sql("INSERT INTO cinenest_pricing.service_definition VALUES(:id,:v,'HDMI-SERVICE','HDMI equipment','HDMI',TRUE,'SYNTHETIC approved physical capability')").param("id",SERVICE).param("v",VA).update();
        jdbc.sql("INSERT INTO cinenest_pricing.service_rate_version(rate_version_id,service_id,revision,currency,unit_price_minor,pricing_basis) VALUES(:id,:service,1,'LKR',123,'PER_SESSION')").param("id",RATE).param("service",SERVICE).update();


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
        String csrfToken;
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



    String collection(){return "/api/admin/venues/"+VA+"/packages";}
    String draft(String code){return "{\"code\":\""+code+"\",\"name\":\"SYNTHETIC package\",\"description\":\"Neutral equipment\",\"currency\":\"LKR\",\"basePriceMinor\":\"12345\",\"includedMinutes\":180,\"minimumMinutes\":180,\"maximumMinutes\":240,\"extensionsEnabled\":true,\"extensionStepMinutes\":60,\"extensionPriceMinor\":\"100\",\"includedGuests\":4,\"maximumGuests\":5,\"extraGuestPriceMinor\":\"25\",\"venueWide\":false,\"roomIds\":[\""+RID+"\"],\"options\":[{\"serviceId\":\""+SERVICE+"\",\"rateVersionId\":\""+RATE+"\",\"mode\":\"INCLUDED\"}],\"reason\":\"SYNTHETIC test adjustment\"}";}
    HttpResponse<String> write(Browser browser,String method,String path,String json)throws Exception {
        synchronized(browser){if(browser.csrfToken==null)browser.csrfToken=(String)JSONObjectUtils.parse(browser.get("/api/session/csrf").body()).get("token");}
        return client.send(HttpRequest.newBuilder(URI.create(base()+path)).header("Cookie",browser.cookie).header("Content-Type","application/json").header("X-CSRF-TOKEN",browser.csrfToken).method(method,HttpRequest.BodyPublishers.ofString(json)).build(),HttpResponse.BodyHandlers.ofString());
    }
    UUID create(Browser b,String code)throws Exception {var response=write(b,"POST",collection(),draft(code));assertThat(response.statusCode()).as(response.body()).isEqualTo(201);return UUID.fromString((String)JSONObjectUtils.parse(response.body()).get("packageId"));}
    @Autowired org.springframework.context.ApplicationContext context;
    UUID grantTo(UUID actor,UUID venue,String permission){jdbc.sql("INSERT INTO cinenest_identity.account_scope VALUES(:a,:v) ON CONFLICT DO NOTHING").param("a",actor).param("v",venue).update();UUID grant=grant(permission,"VENUE",venue);jdbc.sql("UPDATE cinenest_identity.access_grant SET account_id=:a WHERE grant_id=:g").param("a",actor).param("g",grant).update();return grant;}
    Map<String,Object> request(boolean future){var now=java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Colombo")).withNano(0);var m=new HashMap<String,Object>();m.put("expectedRevision",0);m.put("idempotencyKey",UUID.randomUUID().toString());m.put("reason","SYNTHETIC independent publication");m.put("salesFromLocal",now.plusMinutes(future?5:-5).toString());m.put("salesToLocal",now.plusDays(2).toString());m.put("sessionFromLocal",now.plusDays(1).toString());m.put("sessionToLocal",now.plusDays(3).toString());return m;}
    String path(UUID pkg){return collection()+"/"+pkg;}
    Map<String,Object> reviewed(Browser pub,UUID pkg,Map<String,Object> req)throws Exception {var r=write(pub,"POST",path(pkg)+"/publication-preview",JSONObjectUtils.toJSONString(req));assertThat(r.statusCode()).as(r.body()).isEqualTo(200);req.put("contentHash",JSONObjectUtils.parse(r.body()).get("contentHash"));return req;}
    Map<String,Object> published(Browser pub,UUID pkg,Map<String,Object> req)throws Exception {reviewed(pub,pkg,req);var r=write(pub,"POST",path(pkg)+"/publications",JSONObjectUtils.toJSONString(req));assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return JSONObjectUtils.parse(r.body());}
    UUID setup(Browser editor)throws Exception {grant("package.edit","VENUE",VA);return create(editor,"PUBLIC");}
    void activate(UUID id){new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(s->org.springframework.test.util.ReflectionTestUtils.invokeMethod(context.getBean("publicationService"),"activate",id));}
    @Test void immutablePublicationPublicIsolationAndExactIncludedRate()throws Exception {
        var editor=signedIn("staff");UUID pkg=setup(editor);grantTo(A,VA,"package.publish");var pub=signedIn("customer-a");var body=request(false);var d=published(pub,pkg,body);
        var publicResponse=client.send(HttpRequest.newBuilder(URI.create(base()+"/api/venues/"+VA+"/packages?roomId="+RID)).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(publicResponse.statusCode()).isEqualTo(200);assertThat(publicResponse.body()).contains("12345","INDICATIVE","\"purchasable\":false","\"additionalChargeMinor\":\"0\"").doesNotContain("publisherId","authorId","reason","policyRefs","idempotencyKey");
        assertThat(write(pub,"POST",path(pkg)+"/publications",JSONObjectUtils.toJSONString(body)).body()).isEqualTo(JSONObjectUtils.toJSONString(d));
        var version=UUID.fromString(d.get("versionId").toString());assertThatThrownBy(()->jdbc.sql("UPDATE cinenest_pricing.package_version SET snapshot='{}' WHERE version_id=:id").param("id",version).update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
        jdbc.sql("INSERT INTO cinenest_pricing.service_rate_version(rate_version_id,service_id,revision,currency,unit_price_minor,pricing_basis) VALUES(:id,:s,2,'LKR',999,'PER_SESSION')").param("id",UUID.randomUUID()).param("s",SERVICE).update();assertThat(pub.get(path(pkg)+"/publications").body()).contains("12345","123").doesNotContain("999");
    }
    @Test void editorCannotPublishAndPublisherCannotEditOrSelfPublish()throws Exception {
        var editor=signedIn("staff");UUID pkg=setup(editor);assertThat(write(editor,"POST",path(pkg)+"/publication-preview",JSONObjectUtils.toJSONString(request(false))).statusCode()).isEqualTo(403);
        grant("package.publish","VENUE",VA);assertThat(write(editor,"POST",path(pkg)+"/publication-preview",JSONObjectUtils.toJSONString(request(false))).statusCode()).isEqualTo(403);
        grantTo(A,VA,"package.publish");var pub=signedIn("customer-a");assertThat(write(pub,"POST",collection(),draft("BAD")).statusCode()).isEqualTo(403);
        assertThat(write(pub,"POST",path(pkg).replace(VA.toString(),VB.toString())+"/publication-preview",JSONObjectUtils.toJSONString(request(false))).statusCode()).isEqualTo(403);
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_pricing.configuration_audit WHERE outcome='DENY'").query(Long.class).single()).isGreaterThan(0);
    }
    @Test void revokedSuspendedAndStitchedGrantsDenyDirectCalls()throws Exception {
        UUID pkg=setup(signedIn("staff"));var pub=signedIn("customer-a");grantTo(A,VB,"package.publish");grantTo(A,VA,"package.read");assertThat(write(pub,"POST",path(pkg)+"/publication-preview",JSONObjectUtils.toJSONString(request(false))).statusCode()).isEqualTo(403);
        UUID g=grantTo(A,VA,"package.publish");var req=reviewed(pub,pkg,request(false));jdbc.sql("UPDATE cinenest_identity.access_grant SET state='REVOKED' WHERE grant_id=:g").param("g",g).update();assertThat(write(pub,"POST",path(pkg)+"/publications",JSONObjectUtils.toJSONString(req)).statusCode()).isEqualTo(403);
        grantTo(A,VA,"package.publish");jdbc.sql("UPDATE cinenest_identity.account SET eligibility='SUSPENDED' WHERE account_id=:a").param("a",A).update();assertThat(write(pub,"POST",path(pkg)+"/publications",JSONObjectUtils.toJSONString(req)).statusCode()).isEqualTo(403);
    }
    @Test void concurrentIndependentPublishersConflictWithoutMixedTerms()throws Exception {
        UUID pkg=setup(signedIn("staff"));grantTo(A,VA,"package.publish");grantTo(B,VA,"package.publish");var a=signedIn("customer-a");var b=signedIn("customer-b");var r1=reviewed(a,pkg,request(false));var r2=reviewed(b,pkg,request(false));
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){var go=new java.util.concurrent.CountDownLatch(1);var f1=pool.submit(()->{go.await();return write(a,"POST",path(pkg)+"/publications",JSONObjectUtils.toJSONString(r1)).statusCode();});var f2=pool.submit(()->{go.await();return write(b,"POST",path(pkg)+"/publications",JSONObjectUtils.toJSONString(r2)).statusCode();});go.countDown();assertThat(java.util.List.of(f1.get(),f2.get())).containsExactlyInAnyOrder(200,409);}
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_pricing.package_version").query(Long.class).single()).isEqualTo(1);
    }
    @Test void contentHashAndDraftRevisionProtectReviewAndIdempotency()throws Exception {
        UUID pkg=setup(signedIn("staff"));grantTo(A,VA,"package.publish");var pub=signedIn("customer-a");var req=reviewed(pub,pkg,request(false));req.put("reason","Changed after review");assertThat(write(pub,"POST",path(pkg)+"/publications",JSONObjectUtils.toJSONString(req)).statusCode()).isEqualTo(409);
        req.put("expectedRevision",99);assertThat(write(pub,"POST",path(pkg)+"/publication-preview",JSONObjectUtils.toJSONString(req)).statusCode()).isEqualTo(409);
    }
    @Test void explicitReplacementAndWithdrawalPreserveImmutableHistory()throws Exception {
        UUID pkg=setup(signedIn("staff"));grantTo(A,VA,"package.publish");grantTo(A,VA,"package.deactivate");var pub=signedIn("customer-a");var first=published(pub,pkg,request(false));var next=request(false);next.put("replacesPublicationId",first.get("publicationId"));next.put("replacesRevision",0);var second=published(pub,pkg,next);
        assertThat(pub.get(path(pkg)+"/publications").body()).contains("INACTIVE","ACTIVE");
        var action=Map.of("expectedRevision",0,"reason","SYNTHETIC withdrawal","idempotencyKey",UUID.randomUUID().toString());assertThat(write(pub,"POST",path(pkg)+"/publications/"+second.get("publicationId")+"/deactivate",JSONObjectUtils.toJSONString(action)).statusCode()).isEqualTo(200);
        assertThat(client.send(HttpRequest.newBuilder(URI.create(base()+"/api/venues/"+VA+"/packages")).build(),HttpResponse.BodyHandlers.ofString()).body()).contains("\"items\":[]");assertThat(jdbc.sql("SELECT count(*) FROM cinenest_pricing.package_version").query(Long.class).single()).isEqualTo(2);
    }
    @Test void failedAtomicReplacementRestoresOldStateAndAuditFailureRollsBack()throws Exception {
        UUID pkg=setup(signedIn("staff"));grantTo(A,VA,"package.deactivate");grantTo(A,VA,"package.publish");var pub=signedIn("customer-a");var first=published(pub,pkg,request(false));var next=reviewed(pub,pkg,request(false));next.put("replacesPublicationId",first.get("publicationId"));next.put("replacesRevision",9);assertThat(write(pub,"POST",path(pkg)+"/publications",JSONObjectUtils.toJSONString(next)).statusCode()).isEqualTo(409);
        assertThat(pub.get(path(pkg)+"/publications").body()).contains("ACTIVE").doesNotContain("INACTIVE");
        // Real database failure in required audit, not a mocked repository.
        jdbc.sql("ALTER TABLE cinenest_pricing.configuration_audit RENAME TO unavailable_audit").update();try{var replacement=request(false);replacement.put("replacesPublicationId",first.get("publicationId"));replacement.put("replacesRevision",0);reviewed(pub,pkg,replacement);assertThat(write(pub,"POST",path(pkg)+"/publications",JSONObjectUtils.toJSONString(replacement)).statusCode()).isEqualTo(503);}finally{jdbc.sql("ALTER TABLE cinenest_pricing.unavailable_audit RENAME TO configuration_audit").update();}
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_pricing.package_version").query(Long.class).single()).isEqualTo(1);
    }
    @Test void scheduledPrivateAndWorkerReplayRevalidatesAuthority()throws Exception {
        UUID pkg=setup(signedIn("staff"));UUID grant=grantTo(A,VA,"package.publish");var pub=signedIn("customer-a");var req=request(true);var d=published(pub,pkg,req);UUID id=UUID.fromString(d.get("publicationId").toString());
        assertThat(client.send(HttpRequest.newBuilder(URI.create(base()+"/api/venues/"+VA+"/packages")).build(),HttpResponse.BodyHandlers.ofString()).body()).contains("\"items\":[]");
        // A future schedule cannot be activated early, even by replay.
        activate(id);assertThat(pub.get(path(pkg)+"/publications").body()).contains("SCHEDULED");
        jdbc.sql("UPDATE cinenest_identity.access_grant SET state='REVOKED' WHERE grant_id=:g").param("g",grant).update();activate(id);assertThat(jdbc.sql("SELECT state FROM cinenest_pricing.package_publication").query(String.class).single()).isEqualTo("SCHEDULED");
    }
    @Test void invalidationPersistsAndCannotSilentlyRestoreAfterCompatibilityReturns()throws Exception {
        UUID pkg=setup(signedIn("staff"));grantTo(A,VA,"package.publish");var pub=signedIn("customer-a");var d=published(pub,pkg,request(false));jdbc.sql("UPDATE cinenest_rooms.room SET capacity=1 WHERE room_id=:r").param("r",RID).update();String url=base()+"/api/venues/"+VA+"/packages";
        assertThat(client.send(HttpRequest.newBuilder(URI.create(url)).build(),HttpResponse.BodyHandlers.ofString()).body()).contains("\"items\":[]");assertThat(pub.get(path(pkg)+"/publications").body()).contains("BLOCKED","CATALOG:");jdbc.sql("UPDATE cinenest_rooms.room SET capacity=5 WHERE room_id=:r").param("r",RID).update();assertThat(client.send(HttpRequest.newBuilder(URI.create(url)).build(),HttpResponse.BodyHandlers.ofString()).body()).contains("\"items\":[]");
        var retry=Map.of("expectedRevision",1,"reason","SYNTHETIC","idempotencyKey",UUID.randomUUID().toString());assertThat(write(pub,"POST",path(pkg)+"/publications/"+d.get("publicationId")+"/retry",JSONObjectUtils.toJSONString(retry)).statusCode()).isEqualTo(409);
    }
    @Test void futureMinimumAndWithdrawnServiceBlockPublication()throws Exception {
        UUID pkg=setup(signedIn("staff"));grantTo(A,VA,"package.publish");var pub=signedIn("customer-a");jdbc.sql("INSERT INTO cinenest_rooms.minimum_session_policy VALUES(:id,:v,2,240,clock_timestamp()+interval '2 days','SYNTHETIC later policy',:a,clock_timestamp())").param("id",UUID.randomUUID()).param("v",VA).param("a",STAFF).update();assertThat(write(pub,"POST",path(pkg)+"/publication-preview",JSONObjectUtils.toJSONString(request(false))).statusCode()).isEqualTo(400);
        jdbc.sql("UPDATE cinenest_pricing.service_definition SET approved=FALSE WHERE service_id=:id").param("id",SERVICE).update();assertThat(write(pub,"POST",path(pkg)+"/publication-preview",JSONObjectUtils.toJSONString(request(false))).statusCode()).isEqualTo(400);
    }
    @Test void csrfAndUnknownAssertionsAndSessionBoundaries()throws Exception {
        UUID pkg=setup(signedIn("staff"));grantTo(A,VA,"package.publish");var pub=signedIn("customer-a");assertThat(pub.call("POST",path(pkg)+"/publication-preview",Map.of()).statusCode()).isEqualTo(403);var req=request(false);req.put("role","admin");assertThat(write(pub,"POST",path(pkg)+"/publication-preview",JSONObjectUtils.toJSONString(req)).statusCode()).isEqualTo(400);
        var d=published(pub,pkg,request(false));String url=base()+"/api/venues/"+VA+"/packages/"+pkg+"?roomId="+RID+"&sessionStart="+java.net.URLEncoder.encode(d.get("sessionFrom").toString(),java.nio.charset.StandardCharsets.UTF_8)+"&sessionEnd="+java.net.URLEncoder.encode(d.get("sessionTo").toString(),java.nio.charset.StandardCharsets.UTF_8);assertThat(client.send(HttpRequest.newBuilder(URI.create(url)).build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
    }
    @Test void populatedV4ToV5AndRepeatRetainsDrafts()throws Exception {
        String url=environment.getRequiredProperty("spring.datasource.url"),user=environment.getRequiredProperty("spring.datasource.username"),password=environment.getRequiredProperty("spring.datasource.password"),dbName="publication_upgrade_"+UUID.randomUUID().toString().replace("-","");try(var db=DriverManager.getConnection(url,user,password);var s=db.createStatement()){s.execute("CREATE DATABASE "+dbName);}String upgrade=url.substring(0,url.lastIndexOf('/')+1)+dbName;
        try{Flyway.configure().dataSource(upgrade,user,password).target("4").load().migrate();try(var db=DriverManager.getConnection(upgrade,user,password);var s=db.createStatement()){s.execute("INSERT INTO cinenest_pricing.package VALUES('00000000-0000-4000-8000-000000000001','00000000-0000-4000-8000-000000000002','KEEP',clock_timestamp())");}var f=Flyway.configure().dataSource(upgrade,user,password).load();assertThat(f.migrate().migrationsExecuted).isEqualTo(1);assertThat(f.migrate().migrationsExecuted).isZero();f.validate();try(var db=DriverManager.getConnection(upgrade,user,password);var s=db.createStatement();var r=s.executeQuery("SELECT code FROM cinenest_pricing.package")){assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo("KEEP");}}finally{try(var db=DriverManager.getConnection(url,user,password);var s=db.createStatement()){s.execute("DROP DATABASE "+dbName);}}
    }

    @Test void durableActivationRecoversAfterAuditFailureAndSurvivesReplay()throws Exception {
        UUID pkg=setup(signedIn("staff"));grantTo(A,VA,"package.publish");var pub=signedIn("customer-a");var req=request(true);req.put("salesFromLocal",java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Colombo")).plusSeconds(2).withNano(0).toString());var d=published(pub,pkg,req);UUID id=UUID.fromString(d.get("publicationId").toString());Thread.sleep(2100);
        assertThat(jdbc.sql("SELECT state FROM cinenest_pricing.package_publication_work").query(String.class).single()).isEqualTo("PENDING");
        jdbc.sql("ALTER TABLE cinenest_pricing.configuration_audit RENAME TO unavailable_audit").update();try{assertThatThrownBy(()->activate(id)).isInstanceOf(RuntimeException.class);}finally{jdbc.sql("ALTER TABLE cinenest_pricing.unavailable_audit RENAME TO configuration_audit").update();}
        assertThat(jdbc.sql("SELECT state FROM cinenest_pricing.package_publication").query(String.class).single()).isEqualTo("SCHEDULED");activate(id);activate(id);assertThat(jdbc.sql("SELECT state FROM cinenest_pricing.package_publication").query(String.class).single()).isEqualTo("ACTIVE");assertThat(jdbc.sql("SELECT count(*) FROM cinenest_pricing.configuration_audit WHERE action='ACTIVATE'").query(Long.class).single()).isEqualTo(1);
    }
    @Test void revokedScheduledPublisherBlocksAndExplicitRetryRevalidates()throws Exception {
        UUID pkg=setup(signedIn("staff"));UUID g=grantTo(A,VA,"package.publish");var pub=signedIn("customer-a");var req=request(true);req.put("salesFromLocal",java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Colombo")).plusSeconds(2).withNano(0).toString());var d=published(pub,pkg,req);UUID id=UUID.fromString(d.get("publicationId").toString());jdbc.sql("UPDATE cinenest_identity.access_grant SET state='REVOKED' WHERE grant_id=:g").param("g",g).update();Thread.sleep(2100);activate(id);assertThat(jdbc.sql("SELECT state FROM cinenest_pricing.package_publication").query(String.class).single()).isEqualTo("BLOCKED");grantTo(A,VA,"package.publish");var retry=Map.of("expectedRevision",1,"reason","SYNTHETIC explicit retry","idempotencyKey",UUID.randomUUID().toString());assertThat(write(pub,"POST",path(pkg)+"/publications/"+id+"/retry",JSONObjectUtils.toJSONString(retry)).statusCode()).isEqualTo(200);activate(id);assertThat(jdbc.sql("SELECT state FROM cinenest_pricing.package_publication").query(String.class).single()).isEqualTo("ACTIVE");
    }
    @Test void frozenRoomsDoNotExpandAndEquipmentChangePersistsInvalidation()throws Exception {
        UUID pkg=setup(signedIn("staff"));grantTo(A,VA,"package.publish");var pub=signedIn("customer-a");published(pub,pkg,request(false));UUID newRoom=UUID.randomUUID();jdbc.sql("INSERT INTO cinenest_rooms.room(room_id,venue_id,code,name,capacity,enabled) VALUES(:r,:v,'NEW','SYNTHETIC new room',5,TRUE)").param("r",newRoom).param("v",VA).update();String url=base()+"/api/venues/"+VA+"/packages";assertThat(client.send(HttpRequest.newBuilder(URI.create(url+"?roomId="+newRoom)).build(),HttpResponse.BodyHandlers.ofString()).body()).contains("\"items\":[]");jdbc.sql("DELETE FROM cinenest_rooms.room_facility WHERE room_id=:r").param("r",RID).update();assertThat(client.send(HttpRequest.newBuilder(URI.create(url)).build(),HttpResponse.BodyHandlers.ofString()).body()).contains("\"items\":[]");assertThat(pub.get(path(pkg)+"/publications").body()).contains("BLOCKED");
    }

    @Test void adjacentHalfOpenSalesWindowsAreAllowedAndKeyReuseConflicts()throws Exception {
        UUID pkg=setup(signedIn("staff"));grantTo(A,VA,"package.publish");var pub=signedIn("customer-a");var first=published(pub,pkg,request(false));var second=request(true);second.put("salesFromLocal",java.time.Instant.parse(first.get("salesTo").toString()).atZone(java.time.ZoneId.of("Asia/Colombo")).toLocalDateTime().toString());second.put("salesToLocal",java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Colombo")).plusDays(4).withNano(0).toString());published(pub,pkg,second);second.put("reason","Changed key payload");assertThat(write(pub,"POST",path(pkg)+"/publications",JSONObjectUtils.toJSONString(second)).statusCode()).isEqualTo(409);
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_pricing.package_publication").query(Long.class).single()).isEqualTo(2);
    }

    @Test void replacementCannotBypassWithdrawalPermissionAndCommandsBindKeys()throws Exception {
        UUID pkg=setup(signedIn("staff"));grantTo(A,VA,"package.publish");var pub=signedIn("customer-a");var first=published(pub,pkg,request(false));var replacement=request(false);replacement.put("replacesPublicationId",first.get("publicationId"));replacement.put("replacesRevision",0);assertThat(write(pub,"POST",path(pkg)+"/publication-preview",JSONObjectUtils.toJSONString(replacement)).statusCode()).isEqualTo(403);
        grantTo(A,VA,"package.deactivate");String url=path(pkg)+"/publications/"+first.get("publicationId")+"/deactivate";var command=new HashMap<String,Object>(Map.of("expectedRevision",0,"idempotencyKey",UUID.randomUUID().toString(),"reason","SYNTHETIC withdrawal"));assertThat(write(pub,"POST",url,JSONObjectUtils.toJSONString(command)).statusCode()).isEqualTo(200);assertThat(write(pub,"POST",url,JSONObjectUtils.toJSONString(command)).statusCode()).isEqualTo(200);command.put("reason","Changed request body");assertThat(write(pub,"POST",url,JSONObjectUtils.toJSONString(command)).statusCode()).isEqualTo(409);
    }
}
