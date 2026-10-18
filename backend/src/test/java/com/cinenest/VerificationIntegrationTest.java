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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"cinenest.verification.enabled=true","cinenest.verification.worker-enabled=false","cinenest.verification.policy-version=SYNTHETIC-V1","cinenest.verification.upload-seconds=60","cinenest.verification.retention-seconds=3600","cinenest.verification.decision-seconds=600","cinenest.verification.cleanup-seconds=1","cinenest.pricing.synthetic-quotes.enabled=true","cinenest.pricing.synthetic-quotes.tax-profile=SYNTHETIC_FIXED_MINOR_V1","cinenest.pricing.synthetic-quotes.tax-minor=7","cinenest.pricing.synthetic-quotes.approval-reference=SYNTHETIC test fixture only","cinenest.pricing.synthetic-quotes.rate-count=100","cinenest.pricing.synthetic-quotes.rate-seconds=60","cinenest.pricing.publication-worker.enabled=false","cinenest.booking.holds.enabled=true","cinenest.booking.holds.rate-count=100","cinenest.booking.holds.rate-seconds=60","cinenest.booking.holds.approval-reference=SYNTHETIC owner-approved inventory test"})
@ExtendWith(OutputCaptureExtension.class)
@org.springframework.test.context.ActiveProfiles("synthetic-quotation")
class VerificationIntegrationTest {
    static final SyntheticOidcIssuer IDP = new SyntheticOidcIssuer();
    static final UUID A = id("customer-a"), B = id("customer-b"), STAFF = id("staff");
    static final UUID VA = id("venue-a"), VB = id("venue-b");
    @Autowired JdbcClient jdbc;
    @Autowired com.cinenest.identity.ScopedAuthorization scopedAuthorization;
    @Autowired com.cinenest.rooms.PackageConstraints packageConstraints;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired Environment environment;
    final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    static final byte[] IMAGE=image();
    static final java.nio.file.Path STORE=store();
    static java.nio.file.Path store(){try{return java.nio.file.Files.createTempDirectory("cinenest-synthetic-evidence-");}catch(Exception e){throw new IllegalStateException(e);}}
    static byte[] image(){try{var img=new java.awt.image.BufferedImage(320,120,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=img.createGraphics();g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,320,120);g.setColor(java.awt.Color.BLACK);g.drawString("SYNTHETIC NIC - NOT A REAL PERSON",10,40);g.drawString("TEST ONLY / AGE 18+ / NO ADMISSION",10,70);g.dispose();var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(img,"png",out);return out.toByteArray();}catch(Exception e){throw new IllegalStateException(e);}}
    static String hash(byte[] bytes){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("cinenest.verification.fixture-sha256",()->hash(IMAGE));
        properties.add("cinenest.verification.storage-root",STORE::toString);
        properties.add("cinenest.verification.encryption-key",()->java.util.Base64.getEncoder().encodeToString(new byte[32])); // Disposable synthetic test key, never production configuration.

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
        jdbc.sql("TRUNCATE cinenest_verification.verification_policy_revision CASCADE").update();
        jdbc.sql("TRUNCATE cinenest_verification.verification_command,cinenest_verification.verification_audit").update();
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


    Map<String,Object> createCase(Browser customer)throws Exception{
        var h=held(customer,request(customer,local()));var q=quote(customer,issueRequest(customer,h));
        return json(write(customer,"POST","/api/verification/requests",JSONObjectUtils.toJSONString(Map.of("commandKey",UUID.randomUUID().toString(),"quoteId",q.get("quoteId"),"policyVersion","SYNTHETIC-V1","consent",true))));
    }
    Map<String,Object> json(HttpResponse<String> response)throws Exception{assertThat(response.statusCode()).as(response.body()).isEqualTo(200);return JSONObjectUtils.parse(response.body());}
    String casePath(Map<String,Object> request){return "/api/verification/requests/"+request.get("id");}
    String reviewPath(Map<String,Object> request){return "/api/admin/venues/"+VA+"/verification/requests/"+request.get("id");}
    Map<String,Object> cmd(long revision){return new HashMap<>(Map.of("revision",revision,"commandKey",UUID.randomUUID().toString()));}
    Map<String,Object> allocate(Browser customer,Map<String,Object> request)throws Exception{var b=cmd(((Number)request.get("revision")).longValue());b.put("documentType","NIC");return json(write(customer,"POST",casePath(request)+"/uploads",JSONObjectUtils.toJSONString(b)));}
    HttpResponse<String> upload(Browser customer,String id,byte[] image,String mime)throws Exception{
        customer.csrfToken=(String)JSONObjectUtils.parse(customer.get("/api/session/csrf").body()).get("token");
        return client.send(HttpRequest.newBuilder(URI.create(base()+"/api/verification/uploads/"+id+"/content")).header("Cookie",customer.cookie).header("X-CSRF-TOKEN",customer.csrfToken).header("Content-Type",mime).PUT(HttpRequest.BodyPublishers.ofByteArray(image)).build(),HttpResponse.BodyHandlers.ofString());
    }
    void work(){org.springframework.test.util.ReflectionTestUtils.invokeMethod(context.getBean("verificationService"),"reconcile");}
    Map<String,Object> ready(Browser customer)throws Exception{var r=allocate(customer,createCase(customer));assertThat(upload(customer,r.get("uploadId").toString(),IMAGE,"image/png").statusCode()).isEqualTo(200);json(write(customer,"POST","/api/verification/uploads/"+r.get("uploadId")+"/complete",JSONObjectUtils.toJSONString(cmd(((Number)r.get("revision")).longValue()))));work();return json(customer.get(casePath(r)));}
    void reviewerGrants(){grant("verification.review","VENUE",VA);grant("verification.evidence.read","VENUE",VA);}
    Map<String,Object> decision(Map<String,Object> r,String verdict){var b=cmd(((Number)r.get("revision")).longValue());b.put("verdict",verdict);b.put("reason",verdict.equals("VERIFIED")?"SYNTHETIC_MATCH_18_PLUS":"CORRECTION_REQUIRED");return b;}
    @Test void manualReviewIsSeparateFromAccountBookingAndPhysicalAdmission()throws Exception{
        var customer=signedIn("customer-a");var reviewer=signedIn("staff");reviewerGrants();var r=ready(customer);assertThat(r.get("state")).isEqualTo("REVIEW_REQUIRED");assertThat(r.get("physicalAdmission")).isEqualTo(false);
        var body=decision(r,"VERIFIED");var response=write(reviewer,"POST",reviewPath(r)+"/decisions",JSONObjectUtils.toJSONString(body));var decided=json(response);assertThat(decided.get("state")).isEqualTo("VERIFIED");assertThat(json(write(reviewer,"POST",reviewPath(r)+"/decisions",JSONObjectUtils.toJSONString(body))).get("revision")).isEqualTo(decided.get("revision"));
        assertThat(jdbc.sql("SELECT eligibility FROM cinenest_identity.account WHERE account_id=:id").param("id",A).query(String.class).single()).isEqualTo("ELIGIBLE");assertThat(jdbc.sql("SELECT count(*) FROM cinenest_verification.verification_decision").query(Integer.class).single()).isEqualTo(1);
        assertThat(customer.get("/api/bookings").statusCode()).isEqualTo(401); // Booking read capability disabled in this verification-only context.
    }
    @Test void ownerVenueSelfReviewRevocationAndStitchingAreDenied()throws Exception{
        var a=signedIn("customer-a");var b=signedIn("customer-b");var staff=signedIn("staff");var r=ready(a);
        assertThat(b.get(casePath(r)).statusCode()).isEqualTo(404);assertThat(staff.get(reviewPath(r)).statusCode()).isEqualTo(403);
        grant("verification.review","VENUE",VA);grant("verification.evidence.read","VENUE",VB);assertThat(staff.get(reviewPath(r)).statusCode()).isEqualTo(403);
        var read=grant("verification.evidence.read","VENUE",VA);assertThat(staff.get(reviewPath(r)).statusCode()).isEqualTo(200);assertThat(staff.get(reviewPath(r).replace(VA.toString(),VB.toString())).statusCode()).isEqualTo(403);
        grantTo(A,VA,"verification.review");grantTo(A,VA,"verification.evidence.read");assertThat(a.get(reviewPath(r)).statusCode()).isEqualTo(403);
        jdbc.sql("UPDATE cinenest_identity.access_grant SET state='REVOKED' WHERE grant_id=:id").param("id",read).update();assertThat(staff.get(reviewPath(r)).statusCode()).isEqualTo(403);
        jdbc.sql("UPDATE cinenest_identity.account SET eligibility='SUSPENDED' WHERE account_id=:id").param("id",A).update();assertThat(a.get(casePath(r)).statusCode()).isEqualTo(403);
    }
    @Test void privatePreviewRequiresCurrentReviewPurposeAndScan()throws Exception{
        var a=signedIn("customer-a");var staff=signedIn("staff");reviewerGrants();var allocated=allocate(a,createCase(a));String eid=allocated.get("uploadId").toString();String preview=reviewPath(allocated)+"/evidence/"+eid;
        assertThat(staff.get(preview).statusCode()).isEqualTo(409);assertThat(a.get(preview).statusCode()).isEqualTo(403);assertThat(upload(a,eid,IMAGE,"image/png").statusCode()).isEqualTo(200);assertThat(staff.get(preview).statusCode()).isEqualTo(409);
        json(write(a,"POST","/api/verification/uploads/"+eid+"/complete",JSONObjectUtils.toJSONString(cmd(((Number)allocated.get("revision")).longValue()))));work();assertThat(staff.get(preview).statusCode()).isEqualTo(200);
        try(var files=java.nio.file.Files.list(STORE)){for(var file:files.toList())assertThat(java.util.HexFormat.of().formatHex(java.nio.file.Files.readAllBytes(file))).doesNotContain(java.util.HexFormat.of().formatHex(IMAGE));}
        assertThat(staff.get(preview).headers().firstValue("Cache-Control")).contains("no-store");
    }
    @Test void unknownOversizedAndInterruptedUploadsNeverBecomeEligible()throws Exception{
        var a=signedIn("customer-a");var r=allocate(a,createCase(a));String eid=r.get("uploadId").toString();assertThat(upload(a,eid,new byte[5*1024*1024+1],"image/png").statusCode()).isEqualTo(413);
        // An interrupted/failed authorization cannot be reused concurrently. A new generation is explicit.
        assertThat(upload(a,eid,IMAGE,"image/png").statusCode()).isEqualTo(409);
        var body=cmd(((Number)r.get("revision")).longValue());body.put("quoteId",r.get("quoteId"));body.put("policyVersion","SYNTHETIC-V1");body.put("consent",true);var next=json(write(a,"POST",casePath(r)+"/resubmissions",JSONObjectUtils.toJSONString(body)));var newUpload=allocate(a,next);assertThat(upload(a,newUpload.get("uploadId").toString(),"not an image".getBytes(),"image/png").statusCode()).isEqualTo(415);
        assertThat(json(a.get(casePath(r))).get("superseded")).isEqualTo(true);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_verification.verification_decision").query(Integer.class).single()).isZero();
    }
    @Test void csrfAndCrossOwnerUploadCannotBypassServerBoundary()throws Exception{
        var a=signedIn("customer-a");var b=signedIn("customer-b");var r=allocate(a,createCase(a));assertThat(upload(b,r.get("uploadId").toString(),IMAGE,"image/png").statusCode()).isEqualTo(404);assertThat(a.call("POST",casePath(r)+"/resubmissions",Map.of()).statusCode()).isEqualTo(403);
    }
    @Test void concurrentDecisionsConflictAndRejectionPreservesHistoryAndIdentity()throws Exception{
        var a=signedIn("customer-a");var staff=signedIn("staff");reviewerGrants();var r=ready(a);String body=JSONObjectUtils.toJSONString(decision(r,"REJECTED"));
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){var one=pool.submit(()->write(staff,"POST",reviewPath(r)+"/decisions",body));var two=pool.submit(()->write(staff,"POST",reviewPath(r)+"/decisions",JSONObjectUtils.toJSONString(decision(r,"VERIFIED"))));assertThat(java.util.List.of(one.get().statusCode(),two.get().statusCode())).containsExactlyInAnyOrder(200,409);}
        var current=json(a.get(casePath(r)));assertThat((java.util.List<?>)current.get("history")).hasSize(1);assertThat(jdbc.sql("SELECT eligibility FROM cinenest_identity.account WHERE account_id=:id").param("id",A).query(String.class).single()).isEqualTo("ELIGIBLE");
        var correction=cmd(((Number)current.get("revision")).longValue());correction.put("quoteId",r.get("quoteId"));correction.put("consent",true);correction.put("policyVersion","SYNTHETIC-V1");var next=json(write(a,"POST",casePath(r)+"/resubmissions",JSONObjectUtils.toJSONString(correction)));assertThat(next.get("generation")).isEqualTo(2L);assertThat((java.util.List<?>)json(a.get(casePath(r))).get("history")).hasSize(1);
    }
    @Test void requiredAuditFailureRollsBackDecisionAndCommand()throws Exception{
        var a=signedIn("customer-a");var staff=signedIn("staff");reviewerGrants();var r=ready(a);jdbc.sql("CREATE FUNCTION cinenest_verification.test_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='DECIDE' THEN RAISE EXCEPTION 'synthetic audit failure'; END IF; RETURN NEW; END $$").update();jdbc.sql("CREATE TRIGGER test_failure BEFORE INSERT ON cinenest_verification.verification_audit FOR EACH ROW EXECUTE FUNCTION cinenest_verification.test_fail()").update();
        try{assertThat(write(staff,"POST",reviewPath(r)+"/decisions",JSONObjectUtils.toJSONString(decision(r,"VERIFIED"))).statusCode()).isEqualTo(503);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_verification.verification_decision").query(Integer.class).single()).isZero();}finally{jdbc.sql("DROP TRIGGER test_failure ON cinenest_verification.verification_audit").update();jdbc.sql("DROP FUNCTION cinenest_verification.test_fail()").update();}
        assertThat(json(a.get(casePath(r))).get("state")).isEqualTo("REVIEW_REQUIRED");
    }
    @Test void retentionCleanupAndOrphanRecoveryAreDurable()throws Exception{
        var a=signedIn("customer-a");var r=ready(a);jdbc.sql("UPDATE cinenest_verification.identity_evidence SET retain_until=clock_timestamp()-INTERVAL '1 second'").update();
        var service=context.getBean("verificationService");Object original=org.springframework.test.util.ReflectionTestUtils.getField(service,"storage");org.springframework.test.util.ReflectionTestUtils.setField(service,"storage",com.cinenest.verification.internal.VerificationFaults.deletionUnavailable(original));
        try{work();assertThat(jdbc.sql("SELECT state FROM cinenest_verification.identity_evidence").query(String.class).single()).isEqualTo("CLEANUP_FAILED");}finally{org.springframework.test.util.ReflectionTestUtils.setField(service,"storage",original);}work();assertThat(jdbc.sql("SELECT state FROM cinenest_verification.identity_evidence").query(String.class).single()).isEqualTo("DELETED");
        java.nio.file.Path unrelated=STORE.resolve(UUID.randomUUID().toString());java.nio.file.Files.write(unrelated,new byte[]{42});java.nio.file.Files.setLastModifiedTime(unrelated,java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(10)));
        java.nio.file.Path orphan=STORE.resolve("cinenest-verification-"+UUID.randomUUID()+".enc");java.nio.file.Files.write(orphan,new byte[]{1});java.nio.file.Files.setLastModifiedTime(orphan,java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(10)));work();assertThat(java.nio.file.Files.exists(orphan)).isFalse();assertThat(java.nio.file.Files.readAllBytes(unrelated)).isEqualTo(new byte[]{42});work();assertThat(jdbc.sql("SELECT count(*) FROM cinenest_verification.verification_request").query(Integer.class).single()).isEqualTo(1);
    }
    @Test void logAndAuditNeverExposeFixtureBytesKeysOrRawIdentity(CapturedOutput output)throws Exception{
        var a=signedIn("customer-a");ready(a);String audits=jdbc.sql("SELECT string_agg(action||outcome,' ') FROM cinenest_verification.verification_audit").query(String.class).single();assertThat(audits).doesNotContain("SYNTHETIC NIC",hash(IMAGE),STORE.toString());assertThat(output.getAll()).doesNotContain(hash(IMAGE),STORE.toString(),java.util.Base64.getEncoder().encodeToString(IMAGE));
    }
    @Test void populatedV7UpgradeRepeatAndImmutableHistoryConstraints()throws Exception{
        String url=System.getenv("CINENEST_DB_URL"),user=System.getenv("CINENEST_DB_USER"),password=System.getenv("CINENEST_DB_PASSWORD"),database="verify_upgrade_"+UUID.randomUUID().toString().replace("-","");String target=url.substring(0,url.lastIndexOf('/')+1)+database;
        try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()){sql.execute("CREATE DATABASE "+database);}
        try{Flyway.configure().dataSource(target,user,password).target("7").load().migrate();try(var connection=DriverManager.getConnection(target,user,password);var sql=connection.createStatement()){sql.execute("INSERT INTO cinenest_rooms.venue(venue_id,code,display_name,timezone) VALUES('00000000-0000-4000-8000-000000000001','KEEP','Retained V7','Asia/Colombo')");}var flyway=Flyway.configure().dataSource(target,user,password).target("8").load();assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);assertThat(flyway.migrate().migrationsExecuted).isZero();flyway.validate();try(var connection=DriverManager.getConnection(target,user,password);var sql=connection.createStatement();var row=sql.executeQuery("SELECT display_name FROM cinenest_rooms.venue WHERE code='KEEP'")){assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("Retained V7");}}
        finally{try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()){sql.execute("DROP DATABASE "+database);}}
        var a=signedIn("customer-a");var staff=signedIn("staff");reviewerGrants();var r=ready(a);json(write(staff,"POST",reviewPath(r)+"/decisions",JSONObjectUtils.toJSONString(decision(r,"VERIFIED"))));assertThatThrownBy(()->jdbc.sql("UPDATE cinenest_verification.verification_decision SET verdict='REJECTED'").update()).isInstanceOf(DataAccessException.class);assertThatThrownBy(()->jdbc.sql("UPDATE cinenest_verification.verification_request SET owner_id=:id,revision=revision+1").param("id",B).update()).isInstanceOf(DataAccessException.class);
    }
    @Test void interruptedProcessingLeaseAndScannerFailureAreRetryable()throws Exception{
        var a=signedIn("customer-a");var r=allocate(a,createCase(a));String eid=r.get("uploadId").toString();assertThat(upload(a,eid,IMAGE,"image/png").statusCode()).isEqualTo(200);json(write(a,"POST","/api/verification/uploads/"+eid+"/complete",JSONObjectUtils.toJSONString(cmd(((Number)r.get("revision")).longValue()))));
        jdbc.sql("UPDATE cinenest_verification.verification_work SET state='RUNNING',lease_until=clock_timestamp()-INTERVAL '1 second'").update();
        var service=context.getBean("verificationService");Object original=org.springframework.test.util.ReflectionTestUtils.getField(service,"processor");
        org.springframework.test.util.ReflectionTestUtils.setField(service,"processor",com.cinenest.verification.internal.VerificationFaults.scannerUnavailable());
        try{work();assertThat(jdbc.sql("SELECT state FROM cinenest_verification.verification_work").query(String.class).single()).isEqualTo("FAILED");assertThat(json(a.get(casePath(r))).get("state")).isEqualTo("PROCESSING");}finally{org.springframework.test.util.ReflectionTestUtils.setField(service,"processor",original);}
        work();assertThat(json(a.get(casePath(r))).get("state")).isEqualTo("REVIEW_REQUIRED");assertThat(jdbc.sql("SELECT attempts FROM cinenest_verification.verification_work").query(Integer.class).single()).isEqualTo(2);assertThat(original).isNotNull();
    }
    @Test void expiredUploadAndDecisionCannotApproveCurrentEvidence()throws Exception{
        var a=signedIn("customer-a");var staff=signedIn("staff");reviewerGrants();var r=ready(a);json(write(staff,"POST",reviewPath(r)+"/decisions",JSONObjectUtils.toJSONString(decision(r,"VERIFIED"))));jdbc.sql("UPDATE cinenest_verification.verification_request SET valid_until=clock_timestamp()-INTERVAL '1 second',revision=revision+1").update();assertThat(json(a.get(casePath(r))).get("state")).isEqualTo("EXPIRED");
        assertThat(staff.get(reviewPath(r)+"/evidence/"+UUID.randomUUID()).statusCode()).isEqualTo(404);
    }

    @Test void twoImagesRequireBothScansAndThirdImageIsRejected()throws Exception{
        var a=signedIn("customer-a");var first=ready(a);var second=allocate(a,first);assertThat(upload(a,second.get("uploadId").toString(),IMAGE,"image/png").statusCode()).isEqualTo(200);json(write(a,"POST","/api/verification/uploads/"+second.get("uploadId")+"/complete",JSONObjectUtils.toJSONString(cmd(((Number)second.get("revision")).longValue()))));assertThat(json(a.get(casePath(first))).get("state")).isEqualTo("PROCESSING");work();var current=json(a.get(casePath(first)));assertThat(current.get("state")).isEqualTo("REVIEW_REQUIRED");assertThat((java.util.List<?>)current.get("evidence")).hasSize(2);var body=cmd(((Number)current.get("revision")).longValue());body.put("documentType","NIC");assertThat(write(a,"POST",casePath(first)+"/uploads",JSONObjectUtils.toJSONString(body)).statusCode()).isEqualTo(409);
    }

    @Test void expiredWorkerCannotOverwriteNewerCompletedAttempt()throws Exception{
        var a=signedIn("customer-a");var r=allocate(a,createCase(a));String eid=r.get("uploadId").toString();assertThat(upload(a,eid,IMAGE,"image/png").statusCode()).isEqualTo(200);json(write(a,"POST","/api/verification/uploads/"+eid+"/complete",JSONObjectUtils.toJSONString(cmd(((Number)r.get("revision")).longValue()))));
        var service=context.getBean("verificationService");Object original=org.springframework.test.util.ReflectionTestUtils.getField(service,"processor");var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);org.springframework.test.util.ReflectionTestUtils.setField(service,"processor",com.cinenest.verification.internal.VerificationFaults.firstScannerDelayed(entered,release));
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
            var old=pool.submit(()->work());try{assertThat(entered.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();jdbc.sql("UPDATE cinenest_verification.verification_work SET lease_until=clock_timestamp()-INTERVAL '1 second'").update();work();assertThat(jdbc.sql("SELECT state FROM cinenest_verification.verification_work").query(String.class).single()).isEqualTo("DONE");}finally{release.countDown();}old.get();
        }finally{release.countDown();org.springframework.test.util.ReflectionTestUtils.setField(service,"processor",original);}
        assertThat(jdbc.sql("SELECT attempts FROM cinenest_verification.verification_work").query(Integer.class).single()).isEqualTo(2);assertThat(jdbc.sql("SELECT state FROM cinenest_verification.verification_work").query(String.class).single()).isEqualTo("DONE");assertThat(json(a.get(casePath(r))).get("state")).isEqualTo("REVIEW_REQUIRED");
        jdbc.sql("UPDATE cinenest_verification.verification_work SET lease_until=clock_timestamp()-INTERVAL '1 second'").update();var repository=context.getBean("jdbcVerificationRepository");Integer claim=org.springframework.test.util.ReflectionTestUtils.invokeMethod(repository,"claim",UUID.fromString(eid),java.time.Instant.now().plusSeconds(60));assertThat(claim).isZero();
    }

}
