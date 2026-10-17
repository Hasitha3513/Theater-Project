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
import org.springframework.dao.DataAccessException;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"cinenest.pricing.synthetic-quotes.enabled=true","cinenest.pricing.synthetic-quotes.tax-profile=SYNTHETIC_FIXED_MINOR_V1","cinenest.pricing.synthetic-quotes.tax-minor=7","cinenest.pricing.synthetic-quotes.approval-reference=SYNTHETIC test fixture only","cinenest.pricing.synthetic-quotes.rate-count=100","cinenest.pricing.synthetic-quotes.rate-seconds=60","cinenest.pricing.publication-worker.enabled=false","cinenest.booking.holds.enabled=true","cinenest.booking.holds.rate-count=100","cinenest.booking.holds.rate-seconds=60","cinenest.booking.holds.approval-reference=SYNTHETIC owner-approved inventory test"})
@ExtendWith(OutputCaptureExtension.class)
@org.springframework.test.context.ActiveProfiles("synthetic-quotation")
class QuoteIntegrationTest {
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
        jdbc.sql("TRUNCATE cinenest_booking.hold_policy CASCADE").update();
        jdbc.sql("TRUNCATE cinenest_booking.inventory_block CASCADE").update();
        jdbc.sql("TRUNCATE cinenest_booking.inventory_audit").update();
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
        jdbc.sql("INSERT INTO cinenest_rooms.venue_operating_window SELECT :id,day,0,1440 FROM generate_series(1,7) day").param("id",VA).update();
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




    @Autowired org.springframework.context.ApplicationContext context;
    HttpResponse<String> write(Browser browser,String method,String path,String json)throws Exception {
        synchronized(browser){if(browser.csrfToken==null)browser.csrfToken=(String)JSONObjectUtils.parse(browser.get("/api/session/csrf").body()).get("token");}
        return client.send(HttpRequest.newBuilder(URI.create(base()+path)).header("Cookie",browser.cookie).header("Content-Type","application/json").header("X-CSRF-TOKEN",browser.csrfToken).method(method,HttpRequest.BodyPublishers.ofString(json)).build(),HttpResponse.BodyHandlers.ofString());
    }
    String local(){return java.time.LocalDate.now(java.time.ZoneId.of("Asia/Colombo")).plusDays(2).atTime(10,0).toString();}
    Map<String,Object> inquiry(Browser b,String start,int minutes,int guests)throws Exception {
        var response=b.get("/api/rooms/"+RID+"/availability?sessionStartLocal="+start+"&durationMinutes="+minutes+"&guests="+guests);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);return JSONObjectUtils.parse(response.body());
    }
    Map<String,Object> request(Browser b,String start)throws Exception {
        return new HashMap<>(Map.of("roomId",RID.toString(),"sessionStartLocal",start,"durationMinutes",180,"guests",4,"expectedConfiguration",inquiry(b,start,180,4).get("configuration"),"idempotencyKey",UUID.randomUUID().toString()));
    }
    HttpResponse<String> create(Browser b,Map<String,Object> req)throws Exception {return write(b,"POST","/api/holds",JSONObjectUtils.toJSONString(req));}
    Map<String,Object> held(Browser b,Map<String,Object> req)throws Exception {var r=create(b,req);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return JSONObjectUtils.parse(r.body());}
    String holdPath(Map<String,Object> h){return "/api/holds/"+h.get("holdId");}
    void reconcile(){org.springframework.test.util.ReflectionTestUtils.invokeMethod(context.getBean("inventoryService"),"reconcile",VA);}
    void expire(Map<String,Object> h){jdbc.sql("UPDATE cinenest_booking.reservation_hold SET created_at=clock_timestamp()-INTERVAL '20 minutes',expires_at=clock_timestamp()-INTERVAL '1 minute' WHERE hold_id=:id").param("id",UUID.fromString(h.get("holdId").toString())).update();}
    String collection(){return "/api/admin/venues/"+VA+"/packages";}
    String draft(String code){return "{\"code\":\""+code+"\",\"name\":\"SYNTHETIC package\",\"description\":\"Neutral equipment\",\"currency\":\"LKR\",\"basePriceMinor\":\"12345\",\"includedMinutes\":180,\"minimumMinutes\":180,\"maximumMinutes\":240,\"extensionsEnabled\":true,\"extensionStepMinutes\":60,\"extensionPriceMinor\":\"100\",\"includedGuests\":4,\"maximumGuests\":5,\"extraGuestPriceMinor\":\"25\",\"venueWide\":false,\"roomIds\":[\""+RID+"\"],\"options\":[{\"serviceId\":\""+SERVICE+"\",\"rateVersionId\":\""+RATE+"\",\"mode\":\"INCLUDED\"}],\"reason\":\"SYNTHETIC test adjustment\"}";}
    UUID create(Browser b,String code)throws Exception {var response=write(b,"POST",collection(),draft(code));assertThat(response.statusCode()).as(response.body()).isEqualTo(201);return UUID.fromString((String)JSONObjectUtils.parse(response.body()).get("packageId"));}
    UUID grantTo(UUID actor,UUID venue,String permission){jdbc.sql("INSERT INTO cinenest_identity.account_scope VALUES(:a,:v) ON CONFLICT DO NOTHING").param("a",actor).param("v",venue).update();UUID grant=grant(permission,"VENUE",venue);jdbc.sql("UPDATE cinenest_identity.access_grant SET account_id=:a WHERE grant_id=:g").param("a",actor).param("g",grant).update();return grant;}
    Map<String,Object> request(boolean future){var now=java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Colombo")).withNano(0);var m=new HashMap<String,Object>();m.put("expectedRevision",0);m.put("idempotencyKey",UUID.randomUUID().toString());m.put("reason","SYNTHETIC independent publication");m.put("salesFromLocal",now.plusMinutes(future?5:-5).toString());m.put("salesToLocal",now.plusDays(2).toString());m.put("sessionFromLocal",now.plusDays(1).toString());m.put("sessionToLocal",now.plusDays(3).toString());return m;}
    String path(UUID pkg){return collection()+"/"+pkg;}
    Map<String,Object> reviewed(Browser pub,UUID pkg,Map<String,Object> req)throws Exception {var r=write(pub,"POST",path(pkg)+"/publication-preview",JSONObjectUtils.toJSONString(req));assertThat(r.statusCode()).as(r.body()).isEqualTo(200);req.put("contentHash",JSONObjectUtils.parse(r.body()).get("contentHash"));return req;}
    Map<String,Object> published(Browser pub,UUID pkg,Map<String,Object> req)throws Exception {reviewed(pub,pkg,req);var r=write(pub,"POST",path(pkg)+"/publications",JSONObjectUtils.toJSONString(req));assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return JSONObjectUtils.parse(r.body());}
    UUID setup(Browser editor)throws Exception {grant("package.edit","VENUE",VA);return create(editor,"PUBLIC");}
    void activate(UUID id){new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(s->org.springframework.test.util.ReflectionTestUtils.invokeMethod(context.getBean("publicationService"),"activate",id));}

    Map<String,Object> issueRequest(Browser b,Map<String,Object> h)throws Exception {
        UUID pkg=setup(signedIn("staff"));grantTo(B,VA,"package.publish");var pub=signedIn("customer-b");var published=published(pub,pkg,request(false));
        return new HashMap<>(Map.of("holdId",h.get("holdId"),"packageId",pkg.toString(),"packageVersionId",published.get("versionId"),"expectedHoldRevision",h.get("revision"),"optionalServiceIds",java.util.List.of(),"paymentMethod","BANK","idempotencyKey",UUID.randomUUID().toString()));
    }
    Map<String,Object> quote(Browser b,Map<String,Object> req)throws Exception {var r=write(b,"POST","/api/quotes",JSONObjectUtils.toJSONString(req));assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return JSONObjectUtils.parse(r.body());}
    String quotePath(Map<String,Object> q){return "/api/quotes/"+q.get("quoteId");}
    Map<String,Object> acceptance(Map<String,Object> q){return Map.of("expectedRevision",q.get("revision"),"contentHash",q.get("contentHash"),"disclosures",q.get("disclosures"),"idempotencyKey",UUID.randomUUID().toString());}
    HttpResponse<String> accept(Browser b,Map<String,Object> q,Map<String,Object> input)throws Exception{return write(b,"POST",quotePath(q)+"/accept",JSONObjectUtils.toJSONString(input));}
    @Test void exactSyntheticQuoteAcceptanceAndImmutableHistory()throws Exception {
        var b=signedIn("customer-a");var h=held(b,request(b,local()));var req=issueRequest(b,h);var q=quote(b,req);
        assertThat(q).containsEntry("totalMinor","12352").containsEntry("synthetic",true).containsEntry("commercialIssuance",false).containsEntry("bookingConfirmed",false).containsEntry("checkoutEnabled",false).containsEntry("expiresAt",h.get("expiresAt"));
        assertThat(q.get("disclosures").toString()).contains("TEST / SYNTHETIC QUOTATION","NOT A COMMERCIAL OFFER","NO PAYMENT OR BOOKING CONFIRMATION");
        var input=acceptance(q);assertThat(accept(b,q,input).statusCode()).isEqualTo(200);assertThat(accept(b,q,input).body()).contains("ACCEPTED");
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_pricing.quotation_acceptance").query(Long.class).single()).isEqualTo(1);
        assertThatThrownBy(()->jdbc.sql("UPDATE cinenest_pricing.quotation_revision SET total_minor=1").update()).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.sql("UPDATE cinenest_pricing.quotation_acceptance SET synthetic=FALSE").update()).isInstanceOf(DataAccessException.class);
        assertThat(b.get(quotePath(q)).body()).doesNotContain("actorId","ownerId","approval_reference","jdbc:");
    }
    @Test void missingConfigurationAndProductionProfileFailClosed()throws Exception {
        var service=context.getBean("quoteService");boolean original=(boolean)org.springframework.test.util.ReflectionTestUtils.getField(service,"enabled");
        try{org.springframework.test.util.ReflectionTestUtils.setField(service,"enabled",false);var b=signedIn("customer-a");assertThat(write(b,"POST","/api/quotes","{}").statusCode()).isEqualTo(503);}finally{org.springframework.test.util.ReflectionTestUtils.setField(service,"enabled",original);}
        var env=(org.springframework.core.env.ConfigurableEnvironment)environment;String[] profiles=env.getActiveProfiles();
        try{env.setActiveProfiles("production","synthetic-quotation");assertThat(write(signedIn("customer-a"),"POST","/api/quotes","{}").statusCode()).isEqualTo(503);}finally{env.setActiveProfiles(profiles);}
        String profile=(String)org.springframework.test.util.ReflectionTestUtils.getField(service,"profile");try{org.springframework.test.util.ReflectionTestUtils.setField(service,"profile","UNAPPROVED_REAL_TAX");assertThat(write(signedIn("customer-a"),"POST","/api/quotes","{}").statusCode()).isEqualTo(503);}finally{org.springframework.test.util.ReflectionTestUtils.setField(service,"profile",profile);}
    }
    @Test void wrongOwnerSuspensionDirectApiAndCsrfAreDenied()throws Exception {
        var a=signedIn("customer-a");var h=held(a,request(a,local()));var req=issueRequest(a,h);var q=quote(a,req);var b=signedIn("customer-b");
        assertThat(b.get(quotePath(q)).statusCode()).isEqualTo(404);assertThat(accept(b,q,acceptance(q)).statusCode()).isEqualTo(404);
        var wrong=new HashMap<>(req);wrong.put("idempotencyKey",UUID.randomUUID().toString());assertThat(write(b,"POST","/api/quotes",JSONObjectUtils.toJSONString(wrong)).statusCode()).isEqualTo(404);
        assertThat(a.call("POST",quotePath(q)+"/accept",Map.of()).statusCode()).isEqualTo(403);
        assertThat(new Browser().get(quotePath(q)).statusCode()).isEqualTo(401);
        jdbc.sql("UPDATE cinenest_identity.account SET eligibility='SUSPENDED' WHERE account_id=:id").param("id",A).update();assertThat(a.get(quotePath(q)).statusCode()).isEqualTo(403);
    }
    @Test void exactRevisionsMethodsIdempotencyAndReviewBinding()throws Exception {
        var a=signedIn("customer-a");var h=held(a,request(a,local()));var req=issueRequest(a,h);var q=quote(a,req);assertThat(quote(a,req).get("quoteId")).isEqualTo(q.get("quoteId"));
        var recreated=context.getAutowireCapableBeanFactory().createBean(Class.forName("com.cinenest.pricing.internal.QuoteService"));
        Map<?,?> replay=org.springframework.test.util.ReflectionTestUtils.invokeMethod(recreated,"command",A,null,"CREATE",req);assertThat(replay.get("quoteId").toString()).isEqualTo(q.get("quoteId"));
        var rev=new HashMap<String,Object>(Map.of("expectedRevision",1,"optionalServiceIds",java.util.List.of(),"paymentMethod","GATEWAY","idempotencyKey",UUID.randomUUID().toString()));
        var r=write(a,"POST",quotePath(q)+"/revisions",JSONObjectUtils.toJSONString(rev));assertThat(r.statusCode()).as(r.body()).isEqualTo(200);var revised=JSONObjectUtils.parse(r.body());assertThat(revised).containsEntry("revision",2L).containsEntry("paymentMethod","GATEWAY").containsEntry("totalMinor","12352").containsEntry("expiresAt",h.get("expiresAt"));
        assertThat(accept(a,q,acceptance(q)).statusCode()).isEqualTo(409);var bad=new HashMap<>(acceptance(revised));bad.put("contentHash",q.get("contentHash"));assertThat(accept(a,revised,bad).statusCode()).isEqualTo(409);
        assertThat(accept(a,revised,acceptance(revised)).statusCode()).isEqualTo(200);
        assertThat(write(a,"POST",quotePath(q)+"/revisions",JSONObjectUtils.toJSONString(rev)).statusCode()).isEqualTo(200);
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_pricing.quotation_revision").query(Long.class).single()).isEqualTo(2);
    }
    @Test void clientTotalsUnsupportedMethodAndReusedKeyCannotOverrideServerAuthority()throws Exception {
        var a=signedIn("customer-a");var h=held(a,request(a,local()));var req=issueRequest(a,h);req.put("totalMinor","1");assertThat(write(a,"POST","/api/quotes",JSONObjectUtils.toJSONString(req)).statusCode()).isEqualTo(400);req.remove("totalMinor");req.put("paymentMethod","CARD");assertThat(write(a,"POST","/api/quotes",JSONObjectUtils.toJSONString(req)).statusCode()).isEqualTo(400);req.put("paymentMethod","BANK");var q=quote(a,req);req.put("paymentMethod","GATEWAY");assertThat(write(a,"POST","/api/quotes",JSONObjectUtils.toJSONString(req)).statusCode()).isEqualTo(409);assertThat(a.get(quotePath(q)).body()).contains("12352");
    }
    @Test void originalHoldExpiryAndOperationalInvalidationDenyAcceptance()throws Exception {
        var a=signedIn("customer-a");var h=held(a,request(a,local()));var q=quote(a,issueRequest(a,h));expire(h);assertThat(accept(a,q,acceptance(q)).statusCode()).isEqualTo(409);assertThat(a.get(quotePath(q)).body()).contains("INVALIDATED");
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_pricing.quotation_acceptance").query(Long.class).single()).isZero();
    }
    @Test void quoteExpiresAtOriginalDeadlineAndLockWaitCannotPermitLateAcceptance()throws Exception {
        var a=signedIn("customer-a");var h=held(a,request(a,local()));var req=issueRequest(a,h);
        jdbc.sql("UPDATE cinenest_booking.reservation_hold SET expires_at=clock_timestamp()+INTERVAL '1 second' WHERE hold_id=:h").param("h",UUID.fromString(h.get("holdId").toString())).update();
        var q=quote(a,req);
        try(var db=DriverManager.getConnection(environment.getRequiredProperty("spring.datasource.url"),environment.getRequiredProperty("spring.datasource.username"),environment.getRequiredProperty("spring.datasource.password"))){
            db.setAutoCommit(false);try(var lock=db.prepareStatement("SELECT venue_id FROM cinenest_rooms.venue WHERE venue_id=? FOR UPDATE")){lock.setObject(1,VA);lock.executeQuery().close();}
            try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
                var result=pool.submit(()->accept(a,q,acceptance(q)).statusCode());
                try(var delay=db.createStatement()){delay.execute("SELECT pg_sleep(1.1)");}db.commit();assertThat(result.get()).isEqualTo(409);
            }
        }
        assertThat(a.get(quotePath(q)).body()).contains("EXPIRED");assertThat(jdbc.sql("SELECT count(*) FROM cinenest_pricing.quotation_acceptance").query(Long.class).single()).isZero();
    }
    @Test void roomDisableDeniesUnsafeAcceptanceWithoutRewritingSnapshot()throws Exception {
        var a=signedIn("customer-a");var h=held(a,request(a,local()));var q=quote(a,issueRequest(a,h));jdbc.sql("UPDATE cinenest_rooms.room SET enabled=FALSE WHERE room_id=:r").param("r",RID).update();
        assertThat(accept(a,q,acceptance(q)).statusCode()).isEqualTo(409);assertThat(a.get(quotePath(q)).body()).contains("INVALIDATED","12352");
    }
    @Test void ordinaryWithdrawalHonorsIssuedFrozenVersion()throws Exception {
        var a=signedIn("customer-a");var h=held(a,request(a,local()));var req=issueRequest(a,h);var q=quote(a,req);
        jdbc.sql("UPDATE cinenest_pricing.package_publication SET state='INACTIVE',revision=revision+1").update();
        jdbc.sql("INSERT INTO cinenest_pricing.service_rate_version(rate_version_id,service_id,revision,currency,unit_price_minor,pricing_basis) VALUES(:id,:s,2,'LKR',999,'PER_SESSION')").param("id",UUID.randomUUID()).param("s",SERVICE).update();
        assertThat(accept(a,q,acceptance(q)).statusCode()).isEqualTo(200);assertThat(a.get(quotePath(q)).body()).contains("12352");
    }
    @Test void auditFailureRollsBackAcceptanceAndRetryRecovers()throws Exception {
        var a=signedIn("customer-a");var h=held(a,request(a,local()));var q=quote(a,issueRequest(a,h));var input=acceptance(q);
        jdbc.sql("CREATE FUNCTION cinenest_pricing.quote_test_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='ACCEPT' THEN RAISE EXCEPTION 'synthetic audit failure'; END IF; RETURN NEW; END $$").update();jdbc.sql("CREATE TRIGGER quote_test_fault BEFORE INSERT ON cinenest_pricing.quotation_audit FOR EACH ROW EXECUTE FUNCTION cinenest_pricing.quote_test_fault()").update();
        try{assertThat(accept(a,q,input).statusCode()).isEqualTo(503);}finally{jdbc.sql("DROP TRIGGER quote_test_fault ON cinenest_pricing.quotation_audit").update();jdbc.sql("DROP FUNCTION cinenest_pricing.quote_test_fault()").update();}
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_pricing.quotation_acceptance").query(Long.class).single()).isZero();assertThat(accept(a,q,input).statusCode()).isEqualTo(200);
    }
    @Test void concurrentSameKeyAndCompetingRevisionCommandsAreSerialized()throws Exception {
        var a=signedIn("customer-a");var h=held(a,request(a,local()));var req=issueRequest(a,h);
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){var f1=pool.submit(()->quote(a,req));var f2=pool.submit(()->quote(a,req));assertThat(f1.get().get("quoteId")).isEqualTo(f2.get().get("quoteId"));}
        var q=quote(a,req);var input=acceptance(q);var rev=Map.of("expectedRevision",1,"optionalServiceIds",java.util.List.of(),"paymentMethod","GATEWAY","idempotencyKey",UUID.randomUUID().toString());
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){var f1=pool.submit(()->accept(a,q,input).statusCode());var f2=pool.submit(()->write(a,"POST",quotePath(q)+"/revisions",JSONObjectUtils.toJSONString(rev)).statusCode());assertThat(java.util.List.of(f1.get(),f2.get())).containsExactlyInAnyOrder(200,409);}
    }
    @Test void holdReleaseAndAcceptanceSerializeWithoutConfirmationOrRenewal()throws Exception {
        var a=signedIn("customer-a");var h=held(a,request(a,local()));var q=quote(a,issueRequest(a,h));
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){var f1=pool.submit(()->accept(a,q,acceptance(q)).statusCode());var f2=pool.submit(()->write(a,"POST",holdPath(h)+"/release",JSONObjectUtils.toJSONString(Map.of("expectedRevision",0,"idempotencyKey",UUID.randomUUID().toString()))).statusCode());assertThat(f2.get()).isEqualTo(200);assertThat(f1.get()).isIn(200,409);}
        assertThat(a.get(holdPath(h)).body()).contains("RELEASED");assertThat(a.get(quotePath(q)).body()).contains("\"bookingConfirmed\":false");
    }
    @Test void populatedV6ToV7AndRepeatPreserveInventory()throws Exception {
        String url=environment.getRequiredProperty("spring.datasource.url"),user=environment.getRequiredProperty("spring.datasource.username"),password=environment.getRequiredProperty("spring.datasource.password"),name="quote_upgrade_"+UUID.randomUUID().toString().replace("-","");
        try(var db=DriverManager.getConnection(url,user,password);var sql=db.createStatement()){sql.execute("CREATE DATABASE "+name);}String target=url.substring(0,url.lastIndexOf('/')+1)+name;
        try{Flyway.configure().dataSource(target,user,password).target("6").load().migrate();try(var db=DriverManager.getConnection(target,user,password);var sql=db.createStatement()){sql.execute("INSERT INTO cinenest_rooms.venue(venue_id,code,display_name,timezone) VALUES('00000000-0000-4000-8000-000000000001','KEEP','Retained','Asia/Colombo')");sql.execute("INSERT INTO cinenest_booking.inventory_block VALUES('00000000-0000-4000-8000-000000000002','00000000-0000-4000-8000-000000000001','00000000-0000-4000-8000-000000000003','2026-11-01T00:00Z','2026-11-01T03:00Z',7,'2026-11-01T03:07Z',TRUE,'MAINTENANCE',0)");}
            var f=Flyway.configure().dataSource(target,user,password).target("7").load();assertThat(f.migrate().migrationsExecuted).isEqualTo(1);assertThat(f.migrate().migrationsExecuted).isZero();f.validate();try(var db=DriverManager.getConnection(target,user,password);var sql=db.createStatement();var row=sql.executeQuery("SELECT kind,buffer_minutes FROM cinenest_booking.inventory_block")){assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("MAINTENANCE");assertThat(row.getInt(2)).isEqualTo(7);}
            try(var db=DriverManager.getConnection(target,user,password);var sql=db.createStatement();var row=sql.executeQuery("SELECT code FROM cinenest_rooms.venue")){assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("KEEP");}
        }finally{try(var db=DriverManager.getConnection(url,user,password);var sql=db.createStatement()){sql.execute("DROP DATABASE "+name);}}
    }
}
