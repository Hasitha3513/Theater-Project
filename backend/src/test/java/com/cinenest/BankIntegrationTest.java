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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"spring.datasource.hikari.maximum-pool-size=4","spring.datasource.hikari.minimum-idle=0","cinenest.bank.enabled=true","cinenest.bank.ledger.enabled=true","cinenest.bank.ledger.resolution-seconds=60","cinenest.bank.worker-enabled=false","cinenest.bank.policy-version=SYNTHETIC-BANK1","cinenest.bank.lead-seconds=60","cinenest.bank.request-seconds=3000","cinenest.bank.initiation-seconds=900","cinenest.bank.submission-seconds=1200","cinenest.bank.review-seconds=1800","cinenest.bank.hold-seconds=2400","cinenest.bank.escalation-seconds=2500","cinenest.bank.upload-seconds=60","cinenest.bank.retention-seconds=3600","cinenest.bank.cleanup-seconds=1","cinenest.bank.review-opens=00:00","cinenest.bank.review-closes=23:59:59","cinenest.verification.enabled=true","cinenest.verification.worker-enabled=false","cinenest.verification.policy-version=SYNTHETIC-V1","cinenest.verification.upload-seconds=60","cinenest.verification.retention-seconds=3600","cinenest.verification.decision-seconds=600","cinenest.verification.cleanup-seconds=1","cinenest.pricing.synthetic-quotes.enabled=true","cinenest.pricing.synthetic-quotes.tax-profile=SYNTHETIC_FIXED_MINOR_V1","cinenest.pricing.synthetic-quotes.tax-minor=7","cinenest.pricing.synthetic-quotes.approval-reference=SYNTHETIC test fixture only","cinenest.pricing.synthetic-quotes.rate-count=100","cinenest.pricing.synthetic-quotes.rate-seconds=60","cinenest.pricing.publication-worker.enabled=false","cinenest.booking.holds.enabled=true","cinenest.booking.holds.rate-count=100","cinenest.booking.holds.rate-seconds=60","cinenest.booking.holds.approval-reference=SYNTHETIC owner-approved inventory test"})
@ExtendWith(OutputCaptureExtension.class)
@org.springframework.test.context.ActiveProfiles("synthetic-quotation")
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class BankIntegrationTest {
    static final SyntheticOidcIssuer IDP = new SyntheticOidcIssuer();
    static final UUID A = id("customer-a"), B = id("customer-b"), STAFF = id("staff");
    static final UUID VA = id("venue-a"), VB = id("venue-b");
    @Autowired JdbcClient jdbc;
    @Autowired com.cinenest.identity.ScopedAuthorization scopedAuthorization;
    @Autowired com.cinenest.rooms.PackageConstraints packageConstraints;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired Environment environment;
    final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    static final String CREDIT_TIME=java.time.Instant.now().minusSeconds(60).toString();
    static final byte[] IMAGE=image(),RECEIPT=receipt();
    static byte[] receipt(){try{var img=new java.awt.image.BufferedImage(320,120,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=img.createGraphics();g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,320,120);g.setColor(java.awt.Color.BLACK);g.drawString("SYNTHETIC RECEIPT - NO REAL MONEY",10,40);g.drawString("TEST ONLY / NOT BANK CREDIT",10,70);g.dispose();var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(img,"png",out);return out.toByteArray();}catch(Exception e){throw new IllegalStateException(e);}}
    static final java.nio.file.Path STORE=store();
    static java.nio.file.Path store(){try{return java.nio.file.Files.createTempDirectory("cinenest-synthetic-evidence-");}catch(Exception e){throw new IllegalStateException(e);}}
    static byte[] image(){try{var img=new java.awt.image.BufferedImage(320,120,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=img.createGraphics();g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,320,120);g.setColor(java.awt.Color.BLACK);g.drawString("SYNTHETIC NIC - NOT A REAL PERSON",10,40);g.drawString("TEST ONLY / AGE 18+ / NO ADMISSION",10,70);g.dispose();var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(img,"png",out);return out.toByteArray();}catch(Exception e){throw new IllegalStateException(e);}}
    static String hash(byte[] bytes){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("cinenest.bank.ledger.venue-id",VA::toString);
        properties.add("cinenest.bank.ledger.fixture-credited-at",()->CREDIT_TIME);
        properties.add("cinenest.bank.fixture-sha256",()->hash(RECEIPT));
        properties.add("cinenest.bank.storage-root",()->STORE.resolve("bank").toString());
        properties.add("cinenest.bank.encryption-key",()->java.util.Base64.getEncoder().encodeToString(new byte[32]));
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
        jdbc.sql("TRUNCATE cinenest_payments.bank_credit_source_revision CASCADE").update();
        jdbc.sql("TRUNCATE cinenest_payments.bank_policy_revision CASCADE").update();
        jdbc.sql("TRUNCATE cinenest_payments.bank_command,cinenest_payments.bank_audit").update();
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

    Map<String,Object> bankBody(Browser a)throws Exception{
      var staff=signedIn("staff");reviewerGrants();var r=ready(a);json(write(staff,"POST",reviewPath(r)+"/decisions",JSONObjectUtils.toJSONString(decision(r,"VERIFIED"))));
      var q=json(a.get("/api/quotes/"+r.get("quoteId")));
      json(write(a,"POST","/api/quotes/"+r.get("quoteId")+"/accept",JSONObjectUtils.toJSONString(Map.of("expectedRevision",q.get("revision"),"contentHash",q.get("contentHash"),"disclosures",q.get("disclosures"),"idempotencyKey",UUID.randomUUID().toString()))));
      return new HashMap<>(Map.of("quoteId",q.get("quoteId"),"quoteRevision",q.get("revision"),"contentHash",q.get("contentHash"),"commandKey",UUID.randomUUID().toString()));
    }
    Map<String,Object> bank(Browser a)throws Exception{return json(write(a,"POST","/api/bank/requests",JSONObjectUtils.toJSONString(bankBody(a))));}
    String bankPath(Map<String,Object> r){return "/api/bank/requests/"+r.get("id");}
    String financePath(Map<String,Object> r){return "/api/admin/venues/"+VA+"/bank/requests/"+r.get("id");}
    void bankGrants(){grant("bank.claim.review","VENUE",VA);grant("bank.evidence.read","VENUE",VA);grant("bank.claim.escalate","VENUE",VA);}
    Map<String,Object> claimBody(Map<String,Object> r){var b=cmd(((Number)r.get("revision")).longValue());b.put("declaredPaidAt",java.time.Instant.now().toString());b.put("declaredAmountMinor","1");b.put("declaredReference","SYNTHETIC-NOT-A-BANK-REFERENCE");return b;}
    Map<String,Object> claim(Browser a,Map<String,Object> r)throws Exception{return json(write(a,"POST",bankPath(r)+"/claims",JSONObjectUtils.toJSONString(claimBody(r))));}
    Map<String,Object> bankReady(Browser a)throws Exception{var r=claim(a,bank(a));r=json(write(a,"POST","/api/bank/claims/"+r.get("claimId")+"/uploads",JSONObjectUtils.toJSONString(cmd(((Number)r.get("revision")).longValue()))));String upload=r.get("uploadId").toString();assertThat(bankUpload(a,upload,RECEIPT,"image/png").statusCode()).isEqualTo(200);r=json(write(a,"POST","/api/bank/uploads/"+upload+"/complete",JSONObjectUtils.toJSONString(cmd(((Number)r.get("revision")).longValue()))));bankWork();return json(a.get(bankPath(r)));}
    HttpResponse<String> bankUpload(Browser a,String id,byte[] bytes,String mime)throws Exception{if(a.csrfToken==null)a.csrfToken=(String)JSONObjectUtils.parse(a.get("/api/session/csrf").body()).get("token");return client.send(HttpRequest.newBuilder(URI.create(base()+"/api/bank/uploads/"+id+"/content")).header("Cookie",a.cookie).header("X-CSRF-TOKEN",a.csrfToken).header("Content-Type",mime).PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),HttpResponse.BodyHandlers.ofString());}
    void bankWork(){org.springframework.test.util.ReflectionTestUtils.invokeMethod(context.getBean("bankService"),"reconcile");}
    Map<String,Object> outcome(Map<String,Object> r,String action){var b=cmd(((Number)r.get("revision")).longValue());b.put("action",action);b.put("reason",action.equals("REVIEWED")?"SYNTHETIC_REVIEW":"CORRECTION_REQUIRED");return b;}
    @Test void realDatabaseRequestConvertsExactlyOneHoldAndPreservesSyntheticTerms()throws Exception{
      var a=signedIn("customer-a");var r=bank(a);assertThat(r.get("synthetic")).isEqualTo(true);assertThat(r.get("paymentVerified")).isEqualTo(false);assertThat(r.get("bookingConfirmed")).isEqualTo(false);assertThat(r.get("inventoryState")).isEqualTo("ACTIVE");assertThat(r.get("amountMinor")).isEqualTo("12352");
      assertThat(jdbc.sql("SELECT count(*) FROM cinenest_booking.booking_bank_request_hold").query(Integer.class).single()).isEqualTo(1);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_booking.inventory_block WHERE blocking").query(Integer.class).single()).isEqualTo(1);
      assertThatThrownBy(()->jdbc.sql("UPDATE cinenest_payments.bank_payment_request SET amount_minor=1,revision=revision+1").update()).isInstanceOf(DataAccessException.class);
      assertThat(a.get("/api/bank/requests/"+r.get("id")+"/allocate").statusCode()).isEqualTo(403);
    }
    @Test void claimIdempotencyRejectsChangedPayloadAndNeverChangesExpectedMoney()throws Exception{
      var a=signedIn("customer-a");var r=bank(a);var b=claimBody(r);var result=json(write(a,"POST",bankPath(r)+"/claims",JSONObjectUtils.toJSONString(b)));assertThat(result.get("amountMinor")).isEqualTo(r.get("amountMinor"));assertThat(json(write(a,"POST",bankPath(r)+"/claims",JSONObjectUtils.toJSONString(b))).get("claimId")).isEqualTo(result.get("claimId"));b.put("declaredAmountMinor","2");assertThat(write(a,"POST",bankPath(r)+"/claims",JSONObjectUtils.toJSONString(b)).statusCode()).isEqualTo(409);
    }
    @Test void privateEvidenceReviewedWithoutCreditAllocationOrConfirmation()throws Exception{
      var a=signedIn("customer-a");var staff=signedIn("staff");bankGrants();var r=bankReady(a);assertThat(r.get("evidence").toString()).contains("READY");var decided=json(write(staff,"POST",financePath(r)+"/decisions",JSONObjectUtils.toJSONString(outcome(r,"REVIEWED"))));assertThat(decided.get("reviewState")).isEqualTo("REVIEWED");assertThat(decided.get("paymentVerified")).isEqualTo(false);assertThat(decided.get("bookingConfirmed")).isEqualTo(false);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_work WHERE kind='NOTIFY'").query(Integer.class).single()).isGreaterThan(0);bankWork();bankWork();assertThat(jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_work WHERE kind='NOTIFY' AND state='DELIVERY_DISABLED'").query(Integer.class).single()).isGreaterThan(0);
    }
    @Test void ownerVenueSelfReviewStitchingAndRevocationAreDenied()throws Exception{
      var a=signedIn("customer-a");var b=signedIn("customer-b");var staff=signedIn("staff");var r=bank(a);assertThat(b.get(bankPath(r)).statusCode()).isEqualTo(404);assertThat(staff.get(financePath(r)).statusCode()).isEqualTo(403);grant("bank.claim.review","VENUE",VA);grant("bank.evidence.read","VENUE",VB);assertThat(staff.get(financePath(r)).statusCode()).isEqualTo(403);var g=grant("bank.evidence.read","VENUE",VA);assertThat(staff.get(financePath(r)).statusCode()).isEqualTo(200);jdbc.sql("UPDATE cinenest_identity.access_grant SET state='REVOKED' WHERE grant_id=:id").param("id",g).update();assertThat(staff.get(financePath(r)).statusCode()).isEqualTo(403);grantTo(A,VA,"bank.claim.review");grantTo(A,VA,"bank.evidence.read");assertThat(a.get(financePath(r)).statusCode()).isEqualTo(404);jdbc.sql("UPDATE cinenest_identity.account SET eligibility='SUSPENDED' WHERE account_id=:a").param("a",A).update();assertThat(a.get(bankPath(r)).statusCode()).isEqualTo(403);
    }
    @Test void csrfAndRealReceiptAndPdfAreRejected()throws Exception{
      var a=signedIn("customer-a");var r=claim(a,bank(a));assertThat(a.call("POST",base()+bankPath(r)+"/resubmissions",Map.of()).statusCode()).isEqualTo(403);r=json(write(a,"POST","/api/bank/claims/"+r.get("claimId")+"/uploads",JSONObjectUtils.toJSONString(cmd(((Number)r.get("revision")).longValue()))));assertThat(bankUpload(a,r.get("uploadId").toString(),IMAGE,"image/png").statusCode()).isEqualTo(415);
      var current=json(a.get(bankPath(r)));current=json(write(a,"POST","/api/bank/claims/"+current.get("claimId")+"/uploads",JSONObjectUtils.toJSONString(cmd(((Number)current.get("revision")).longValue()))));assertThat(bankUpload(a,current.get("uploadId").toString(),"%PDF synthetic".getBytes(),"application/pdf").statusCode()).isEqualTo(415);
    }
    @Test void concurrentReviewDecisionsAreGuarded()throws Exception{
      var a=signedIn("customer-a");var staff=signedIn("staff");bankGrants();var r=bankReady(a);var one=outcome(r,"REVIEWED");var two=outcome(r,"REJECTED");try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){var f=pool.submit(()->write(staff,"POST",financePath(r)+"/decisions",JSONObjectUtils.toJSONString(one)));var g=pool.submit(()->write(staff,"POST",financePath(r)+"/decisions",JSONObjectUtils.toJSONString(two)));assertThat(java.util.List.of(f.get().statusCode(),g.get().statusCode())).containsExactlyInAnyOrder(200,409);}assertThat(jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_review_decision").query(Integer.class).single()).isEqualTo(1);
    }
    @Test void populatedV8UpgradeAndRepeatPreserveExistingRows()throws Exception{
      String url=System.getenv("CINENEST_DB_URL"),user=System.getenv("CINENEST_DB_USER"),password=System.getenv("CINENEST_DB_PASSWORD"),database="bank_upgrade_"+UUID.randomUUID().toString().replace("-","");String target=url.substring(0,url.lastIndexOf('/')+1)+database;
      try(var c=DriverManager.getConnection(url,user,password);var sql=c.createStatement()){sql.execute("CREATE DATABASE "+database);}try{Flyway.configure().dataSource(target,user,password).target("8").load().migrate();try(var c=DriverManager.getConnection(target,user,password);var sql=c.createStatement()){sql.execute("INSERT INTO cinenest_rooms.venue(venue_id,code,display_name,timezone) VALUES('00000000-0000-4000-8000-000000000001','KEEP','Retained V8','Asia/Colombo')");}var f=Flyway.configure().dataSource(target,user,password).target("9").load();assertThat(f.migrate().migrationsExecuted).isEqualTo(1);assertThat(f.migrate().migrationsExecuted).isZero();f.validate();try(var c=DriverManager.getConnection(target,user,password);var sql=c.createStatement();var rs=sql.executeQuery("SELECT display_name FROM cinenest_rooms.venue WHERE code='KEEP'")){assertThat(rs.next()).isTrue();assertThat(rs.getString(1)).isEqualTo("Retained V8");}}finally{try(var c=DriverManager.getConnection(url,user,password);var sql=c.createStatement()){sql.execute("DROP DATABASE "+database);}}
    }
    @Test void mandatoryAuditFailureRollsBackDecisionAndIdempotency()throws Exception{
      var a=signedIn("customer-a");var staff=signedIn("staff");bankGrants();var r=bankReady(a);var body=outcome(r,"REVIEWED");
      jdbc.sql("CREATE FUNCTION cinenest_payments.test_audit_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'SYNTHETIC audit unavailable'; END $$").update();
      jdbc.sql("CREATE TRIGGER test_audit_failure BEFORE INSERT ON cinenest_payments.bank_audit FOR EACH ROW EXECUTE FUNCTION cinenest_payments.test_audit_failure()").update();
      try{assertThat(write(staff,"POST",financePath(r)+"/decisions",JSONObjectUtils.toJSONString(body)).statusCode()).isEqualTo(503);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_review_decision").query(Integer.class).single()).isZero();assertThat(jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_command WHERE command_key=:key").param("key",UUID.fromString(body.get("commandKey").toString())).query(Integer.class).single()).isZero();}
      finally{jdbc.sql("DROP TRIGGER test_audit_failure ON cinenest_payments.bank_audit").update();jdbc.sql("DROP FUNCTION cinenest_payments.test_audit_failure()").update();}
      assertThat(write(staff,"POST",financePath(r)+"/decisions",JSONObjectUtils.toJSONString(body)).statusCode()).isEqualTo(200);
    }
    @Test void duplicateHintsAndRestrictedAuditedPreviewsDoNotProveFunds()throws Exception{
      var a=signedIn("customer-a");var staff=signedIn("staff");var r=bankReady(a);var evidence=((java.util.List<Map<String,Object>>)r.get("evidence")).getFirst();String path=financePath(r)+"/evidence/"+evidence.get("id");assertThat(staff.get(path).statusCode()).isEqualTo(403);bankGrants();assertThat(staff.get(path).statusCode()).isEqualTo(200);assertThat(a.get(path).statusCode()).isEqualTo(403);
      var more=json(write(a,"POST","/api/bank/claims/"+r.get("claimId")+"/uploads",JSONObjectUtils.toJSONString(cmd(((Number)r.get("revision")).longValue()))));assertThat(bankUpload(a,more.get("uploadId").toString(),RECEIPT,"image/png").statusCode()).isEqualTo(200);more=json(write(a,"POST","/api/bank/uploads/"+more.get("uploadId")+"/complete",JSONObjectUtils.toJSONString(cmd(((Number)more.get("revision")).longValue()))));bankWork();r=json(a.get(bankPath(more)));assertThat(r.get("evidence").toString()).contains("possibleDuplicate=true");assertThat(r.get("paymentVerified")).isEqualTo(false);assertThat(write(a,"POST","/api/bank/claims/"+r.get("claimId")+"/uploads",JSONObjectUtils.toJSONString(cmd(((Number)r.get("revision")).longValue()))).statusCode()).isEqualTo(409);
    }
    @Test void evidenceCompletionAloneDefinesTimelinessAndExpiryDoesNotRenewInventory()throws Exception{
      var env=(org.springframework.core.env.ConfigurableEnvironment)environment;var overrides=new org.springframework.core.env.MapPropertySource("bank-short-test",Map.of("cinenest.bank.request-seconds","1","cinenest.bank.initiation-seconds","1","cinenest.bank.submission-seconds","1","cinenest.bank.review-seconds","1","cinenest.bank.hold-seconds","1","cinenest.bank.escalation-seconds","2"));env.getPropertySources().addFirst(overrides);
      try{var a=signedIn("customer-a");var r=claim(a,bank(a));assertThat(r.get("submissionTimely")).isEqualTo(false);String until=r.get("inventoryExpiresAt").toString();Thread.sleep(1200);bankWork();r=json(a.get(bankPath(r)));assertThat(r.get("requestState")).isEqualTo("EXPIRED");assertThat(r.get("inventoryState")).isEqualTo("EXPIRED");var late=json(write(a,"POST",bankPath(r)+"/resubmissions",JSONObjectUtils.toJSONString(claimBody(r))));assertThat(late.get("inventoryExpiresAt")).isEqualTo(until);assertThat(late.get("inventoryState")).isEqualTo("EXPIRED");assertThat(late.get("paymentVerified")).isEqualTo(false);}
      finally{env.getPropertySources().remove("bank-short-test");}
    }
    @Test void disabledAndFailedNotificationIntentSurvivesRetryAndWorkerReentry()throws Exception{
      var a=signedIn("customer-a");var r=claim(a,bank(a));Object service=context.getBean("bankService"),original=org.springframework.test.util.ReflectionTestUtils.getField(service,"notice");org.springframework.test.util.ReflectionTestUtils.setField(service,"notice",com.cinenest.payments.internal.BankFaults.noticeFailure());
      try{bankWork();assertThat(jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_work WHERE kind='NOTIFY' AND state='FAILED'").query(Integer.class).single()).isEqualTo(1);assertThat(json(a.get(bankPath(r))).get("reviewState")).isEqualTo("PENDING");}finally{org.springframework.test.util.ReflectionTestUtils.setField(service,"notice",original);}
      Thread.sleep(1100);bankWork();bankWork();assertThat(jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_work WHERE kind='NOTIFY' AND state='DELIVERY_DISABLED'").query(Integer.class).single()).isEqualTo(1);
    }
    @Test void cleanupReconcilesOnlyBankOwnedEncryptedObjects()throws Exception{
      var env=(org.springframework.core.env.ConfigurableEnvironment)environment;env.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("bank-retention-test",Map.of("cinenest.bank.upload-seconds","1","cinenest.bank.retention-seconds","2")));
      try{var a=signedIn("customer-a");var r=bankReady(a);var dir=STORE.resolve("bank");var unrelated=dir.resolve("unrelated-"+UUID.randomUUID()+".enc");java.nio.file.Files.writeString(unrelated,"SYNTHETIC unrelated content");Thread.sleep(2200);bankWork();assertThat(jdbc.sql("SELECT state FROM cinenest_payments.bank_receipt_evidence").query(String.class).single()).isEqualTo("DELETED");assertThat(java.nio.file.Files.exists(unrelated)).isTrue();assertThat(json(a.get(bankPath(r))).get("paymentVerified")).isEqualTo(false);}
      finally{env.getPropertySources().remove("bank-retention-test");}
    }

    @Test void expiredCheckoutHoldCannotConvertAndMissingTimingFailsClosed()throws Exception{
      var a=signedIn("customer-a");var body=bankBody(a);var env=(org.springframework.core.env.ConfigurableEnvironment)environment;env.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("bank-missing-policy",Map.of("cinenest.bank.request-seconds","0")));try{assertThat(write(a,"POST","/api/bank/requests",JSONObjectUtils.toJSONString(body)).statusCode()).isEqualTo(503);}finally{env.getPropertySources().remove("bank-missing-policy");}
      var quote=json(a.get("/api/quotes/"+body.get("quoteId")));expire(Map.of("holdId",quote.get("holdId")));assertThat(write(a,"POST","/api/bank/requests",JSONObjectUtils.toJSONString(body)).statusCode()).isEqualTo(409);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_booking.booking_bank_request_hold").query(Integer.class).single()).isZero();assertThat(jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_payment_request").query(Integer.class).single()).isZero();
    }
    @Test void handoverRequiresAnotherCurrentlyScopedReviewerAndAppendsAttribution()throws Exception{
      var a=signedIn("customer-a");var staff=signedIn("staff");bankGrants();var r=bankReady(a);var b=outcome(r,"REVIEWED");b.put("action","HANDOVER");b.put("reason","STAFF_HANDOVER");b.put("handoverTo",B.toString());assertThat(write(staff,"POST",financePath(r)+"/escalations",JSONObjectUtils.toJSONString(b)).statusCode()).isEqualTo(403);grantTo(B,VA,"bank.claim.review");grantTo(B,VA,"bank.evidence.read");var result=json(write(staff,"POST",financePath(r)+"/escalations",JSONObjectUtils.toJSONString(b)));assertThat(result.get("history").toString()).contains("HANDOVER",B.toString());assertThat(result.get("paymentVerified")).isEqualTo(false);
    }

    @Test void expiredProcessingLeaseRecoversAndStaleWorkerCannotOverwriteNewAttempt()throws Exception{
      var a=signedIn("customer-a");var r=claim(a,bank(a));r=json(write(a,"POST","/api/bank/claims/"+r.get("claimId")+"/uploads",JSONObjectUtils.toJSONString(cmd(((Number)r.get("revision")).longValue()))));String upload=r.get("uploadId").toString();assertThat(bankUpload(a,upload,RECEIPT,"image/png").statusCode()).isEqualTo(200);json(write(a,"POST","/api/bank/uploads/"+upload+"/complete",JSONObjectUtils.toJSONString(cmd(((Number)r.get("revision")).longValue()))));
      Object service=context.getBean("bankService"),original=org.springframework.test.util.ReflectionTestUtils.getField(service,"scanner");var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);org.springframework.test.util.ReflectionTestUtils.setField(service,"scanner",com.cinenest.payments.internal.BankFaults.delayedScanner(entered,release));
      try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){var stale=pool.submit(()->bankWork());try{assertThat(entered.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();Thread.sleep(1100);org.springframework.test.util.ReflectionTestUtils.setField(service,"scanner",original);bankWork();}finally{release.countDown();org.springframework.test.util.ReflectionTestUtils.setField(service,"scanner",original);}stale.get(10,java.util.concurrent.TimeUnit.SECONDS);}
      assertThat(jdbc.sql("SELECT state FROM cinenest_payments.bank_receipt_evidence WHERE evidence_id=:id").param("id",UUID.fromString(upload)).query(String.class).single()).isEqualTo("READY");assertThat(jdbc.sql("SELECT attempts FROM cinenest_payments.bank_work WHERE evidence_id=:id AND kind='SCAN'").param("id",UUID.fromString(upload)).query(Integer.class).single()).isEqualTo(2);
    }

    @Test void eligibilityDeadlineIsRecheckedAfterWaitingForDecisionLock()throws Exception{
      var a=signedIn("customer-a");var staff=signedIn("staff");reviewerGrants();var r=ready(a);json(write(staff,"POST",reviewPath(r)+"/decisions",JSONObjectUtils.toJSONString(decision(r,"VERIFIED"))));UUID request=UUID.fromString(r.get("id").toString()),quote=UUID.fromString(r.get("quoteId").toString());
      jdbc.sql("UPDATE cinenest_verification.verification_request SET valid_until=clock_timestamp()+INTERVAL '2 seconds',revision=revision+1 WHERE request_id=:id").param("id",request).update();
      try(var connection=DriverManager.getConnection(System.getenv("CINENEST_DB_URL"),System.getenv("CINENEST_DB_USER"),System.getenv("CINENEST_DB_PASSWORD"));var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
        connection.setAutoCommit(false);try(var statement=connection.prepareStatement("SELECT request_id FROM cinenest_verification.verification_request WHERE request_id=? FOR UPDATE")){statement.setObject(1,request);statement.executeQuery().close();}
        var pid=new java.util.concurrent.atomic.AtomicInteger();var waiting=pool.submit(()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(t->{pid.set(jdbc.sql("SELECT pg_backend_pid()").query(Integer.class).single());return context.getBean(com.cinenest.verification.BankEligibility.class).eligible(A,quote,VA);}));
        boolean blocked=false;for(int n=0;n<30;n++){if(pid.get()!=0&&jdbc.sql("SELECT count(*) FROM pg_stat_activity WHERE pid=:pid AND wait_event_type='Lock'").param("pid",pid.get()).query(Integer.class).single()==1){blocked=true;break;}Thread.sleep(20);}assertThat(blocked).isTrue();Thread.sleep(2100);connection.commit();assertThat(waiting.get(5,java.util.concurrent.TimeUnit.SECONDS)).isFalse();
      }
    }

    @Test void failedRequestAuditAtomicallyRollsBackBookingConversionAndCanRetry()throws Exception{
      var a=signedIn("customer-a");var body=bankBody(a);var quote=json(a.get("/api/quotes/"+body.get("quoteId")));UUID hold=UUID.fromString(quote.get("holdId").toString());var original=jdbc.sql("SELECT expires_at FROM cinenest_booking.reservation_hold WHERE hold_id=:id").param("id",hold).query(java.time.OffsetDateTime.class).single();
      jdbc.sql("CREATE FUNCTION cinenest_payments.test_create_audit_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'SYNTHETIC audit unavailable'; END $$").update();jdbc.sql("CREATE TRIGGER test_create_audit_failure BEFORE INSERT ON cinenest_payments.bank_audit FOR EACH ROW EXECUTE FUNCTION cinenest_payments.test_create_audit_failure()").update();
      try{assertThat(write(a,"POST","/api/bank/requests",JSONObjectUtils.toJSONString(body)).statusCode()).isEqualTo(503);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_booking.booking_bank_request_hold").query(Integer.class).single()).isZero();assertThat(jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_payment_request").query(Integer.class).single()).isZero();assertThat(jdbc.sql("SELECT expires_at FROM cinenest_booking.reservation_hold WHERE hold_id=:id").param("id",hold).query(java.time.OffsetDateTime.class).single()).isEqualTo(original);}
      finally{jdbc.sql("DROP TRIGGER test_create_audit_failure ON cinenest_payments.bank_audit").update();jdbc.sql("DROP FUNCTION cinenest_payments.test_create_audit_failure()").update();}
      assertThat(write(a,"POST","/api/bank/requests",JSONObjectUtils.toJSONString(body)).statusCode()).isEqualTo(200);
    }

    Process restartedWorker(String name)throws Exception{
      var config=new HashMap<String,Object>();for(String key:java.util.List.of("policy-version","lead-seconds","request-seconds","initiation-seconds","submission-seconds","review-seconds","hold-seconds","escalation-seconds","upload-seconds","retention-seconds","cleanup-seconds","review-opens","review-closes","fixture-sha256","storage-root","encryption-key"))config.put("cinenest.bank."+key,environment.getRequiredProperty("cinenest.bank."+key));
      config.put("cinenest.bank.ledger.enabled",false);config.put("cinenest.bank.ledger.worker-enabled",true);config.put("cinenest.bank.ledger.worker-delay-ms",100);for(String k:java.util.List.of("venue-id","fixture-credited-at","resolution-seconds"))config.put("cinenest.bank.ledger."+k,environment.getRequiredProperty("cinenest.bank.ledger."+k));config.put("cinenest.bank.enabled",false);config.put("cinenest.bank.worker-enabled",true);config.put("cinenest.bank.worker-delay-ms",100);config.put("spring.datasource.hikari.maximum-pool-size",2);config.put("spring.datasource.hikari.minimum-idle",0);config.put("cinenest.pricing.publication-worker.enabled",false);
      var builder=new ProcessBuilder(PathJava(),"-Djava.awt.headless=true","-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),"com.cinenest.CineNestApplication","--spring.profiles.active=synthetic-quotation","--server.address=127.0.0.1","--server.port=0");builder.environment().put("SPRING_APPLICATION_JSON",JSONObjectUtils.toJSONString(config));builder.redirectErrorStream(true);builder.redirectOutput(STORE.resolve(name+".log").toFile());return builder.start();
    }
    String PathJava(){return java.nio.file.Path.of(System.getProperty("java.home"),"bin","java").toString();}
    void stopWorker(Process child)throws Exception{child.destroy();if(!child.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)){child.destroyForcibly();assertThat(child.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}}
    void awaitDisabled(Process child,int count)throws Exception{for(int n=0;n<300;n++){assertThat(child.isAlive()).as("Isolated restarted worker must remain available; inspect synthetic test log").isTrue();if(jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_work WHERE kind='NOTIFY' AND state='DELIVERY_DISABLED'").query(Integer.class).single()==count)return;Thread.sleep(50);}throw new AssertionError("Restarted worker did not recover durable notification intent");}
    @Test void actualWorkerProcessRestartRecoversDurableIntentWithoutLiveDelivery()throws Exception{
      var a=signedIn("customer-a");var r=claim(a,bank(a));Process first=restartedWorker("worker-before-restart");try{awaitDisabled(first,1);}finally{stopWorker(first);}
      r=json(a.get(bankPath(r)));r=json(write(a,"POST",bankPath(r)+"/resubmissions",JSONObjectUtils.toJSONString(claimBody(r))));Process second=restartedWorker("worker-after-restart");try{awaitDisabled(second,2);}finally{stopWorker(second);}
      var current=json(a.get(bankPath(r)));assertThat(current.get("reviewState")).isEqualTo("PENDING");assertThat(current.get("paymentVerified")).isEqualTo(false);assertThat(current.get("bookingConfirmed")).isEqualTo(false);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_receipt_claim").query(Integer.class).single()).isEqualTo(2);
    }


    String ledgerRoot(){return "/api/admin/venues/"+VA+"/bank";}
    Map<String,Object> financial(long revision){return new HashMap<>(Map.of("expectedRevision",revision,"commandKey",UUID.randomUUID().toString(),"reason","SYNTHETIC controlled financial test"));}
    void financeGrants(){jdbc.sql("INSERT INTO cinenest_identity.account_scope VALUES(:a,:v) ON CONFLICT DO NOTHING").param("a",B).param("v",VA).update();for(UUID actor:java.util.List.of(STAFF,B))for(String permission:java.util.List.of("bank.synthetic.credit.verify","bank.synthetic.credit.read","bank.synthetic.allocate","bank.synthetic.resolve","bank.synthetic.correct.request","bank.synthetic.correct.approve"))jdbc.sql("INSERT INTO cinenest_identity.access_grant VALUES(:id,:a,:p,'VENUE',:v,'ACTIVE',clock_timestamp()-INTERVAL '1 minute',clock_timestamp()+INTERVAL '1 hour',0)").param("id",UUID.randomUUID()).param("a",actor).param("p",permission).param("v",VA).update();}
    Map<String,Object> credit(Browser staff,String fixture)throws Exception{var b=financial(0);b.put("fixtureKey",fixture);return json(write(staff,"POST",ledgerRoot()+"/credits",JSONObjectUtils.toJSONString(b)));}
    Map<String,Object> match(Browser allocator,Map<String,Object> r,Map<String,Object> c)throws Exception{var b=financial(((Number)r.get("revision")).longValue());b.put("requestId",r.get("id"));json(write(allocator,"POST",ledgerRoot()+"/credits/"+c.get("id")+"/matches",JSONObjectUtils.toJSONString(b)));return json(signedIn("customer-a").get(bankPath(r)));}
    HttpResponse<String> allocateCredit(Browser allocator,Map<String,Object> r,Map<String,Object> c,long amount)throws Exception{var b=financial(((Number)r.get("revision")).longValue());b.put("creditId",c.get("id"));b.put("amountMinor",amount);return write(allocator,"POST",ledgerRoot()+"/requests/"+r.get("id")+"/allocations",JSONObjectUtils.toJSONString(b));}
    @Test void syntheticCreditAdmissionRequiresIndependentSourceAndDeduplicatesOverlappingStatements()throws Exception{
      financeGrants();var staff=signedIn("staff");var body=financial(0);body.put("fixtureKey","REAL-BANK-UNKNOWN");assertThat(write(staff,"POST",ledgerRoot()+"/credits",JSONObjectUtils.toJSONString(body)).statusCode()).isEqualTo(400);
      var c=credit(staff,"SYNTHETIC-BANK-100");assertThat(c.get("paymentVerified")).isEqualTo(false);assertThat(java.time.Instant.parse(c.get("creditedAt").toString())).isNotNull();assertThat(c.toString()).doesNotContain("owner_id","account_identity","movement_identity");body.put("fixtureKey","SYNTHETIC-OVERLAP-100");assertThat(write(staff,"POST",ledgerRoot()+"/credits",JSONObjectUtils.toJSONString(body)).statusCode()).isEqualTo(409);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_payments.incoming_payment_credit").query(Integer.class).single()).isEqualTo(1);
      assertThat(staff.get(ledgerRoot()+"/credits").statusCode()).isEqualTo(200);
    }
    @Test void partialTopupsAndFinalFundingCreateOneSyntheticProtectedBooking()throws Exception{
      financeGrants();var customer=signedIn("customer-a");var staff=signedIn("staff");var allocator=signedIn("customer-b");var r=bank(customer);var first=credit(staff,"SYNTHETIC-BANK-100");r=match(allocator,r,first);json(allocateCredit(allocator,r,first,100));r=json(customer.get(bankPath(r)));var second=credit(staff,"SYNTHETIC-BANK-50000");r=match(allocator,r,second);long due=Long.parseLong(r.get("amountMinor").toString());var result=json(allocateCredit(allocator,r,second,due-100));assertThat(result.get("syntheticBookingStatus")).isEqualTo("SIMULATED_CONFIRMED");assertThat(result.get("bookingConfirmed")).isEqualTo(false);assertThat(customer.get("/api/bookings/"+result.get("bookingId")).statusCode()).isEqualTo(200);assertThat(allocator.get("/api/bookings/"+result.get("bookingId")).statusCode()).isEqualTo(200);
      assertThat(jdbc.sql("SELECT actor_id FROM cinenest_booking.inventory_audit WHERE action='SYNTHETIC_CONFIRM'").query(UUID.class).single()).isEqualTo(B);assertThat(jdbc.sql("SELECT state FROM cinenest_booking.reservation_hold").query(String.class).single()).isEqualTo("CONSUMED");bankWork();assertThat(jdbc.sql("SELECT blocking FROM cinenest_booking.inventory_block").query(Boolean.class).single()).isTrue();assertThat(jdbc.sql("SELECT count(*) FROM cinenest_booking.booking").query(Integer.class).single()).isEqualTo(1);
      assertThat(staff.get(ledgerRoot()+"/credits/"+second.get("id")).body()).contains("availableMinor");
    }
    @Test void expiredInventoryRollsBackFinalAllocationButRetainsCreditAndDurableException()throws Exception{
      financeGrants();var customer=signedIn("customer-a");var staff=signedIn("staff");var allocator=signedIn("customer-b");var r=bank(customer);var c=credit(staff,"SYNTHETIC-BANK-50000");r=match(allocator,r,c);jdbc.sql("UPDATE cinenest_booking.reservation_hold SET expires_at=clock_timestamp()-INTERVAL '1 second',created_at=clock_timestamp()-INTERVAL '1 hour'").update();assertThat(allocateCredit(allocator,r,c,Long.parseLong(r.get("amountMinor").toString())).statusCode()).isEqualTo(409);
      assertThat(jdbc.sql("SELECT count(*) FROM cinenest_payments.incoming_payment_credit").query(Integer.class).single()).isEqualTo(1);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_payments.payment_allocation").query(Integer.class).single()).isZero();assertThat(jdbc.sql("SELECT count(*) FROM cinenest_booking.booking").query(Integer.class).single()).isZero();assertThat(allocator.get(ledgerRoot()+"/exceptions").body()).contains("BOOKING_RESOLUTION");
    }
    @Test void financialPrivilegesCannotBeBorrowedFromReceiptReviewOrOtherVenue()throws Exception{
      var staff=signedIn("staff");bankGrants();var b=financial(0);b.put("fixtureKey","SYNTHETIC-BANK-100");assertThat(write(staff,"POST",ledgerRoot()+"/credits",JSONObjectUtils.toJSONString(b)).statusCode()).isEqualTo(403);grant("bank.synthetic.credit.verify","VENUE",VB);assertThat(write(staff,"POST",ledgerRoot()+"/credits",JSONObjectUtils.toJSONString(b)).statusCode()).isEqualTo(403);financeGrants();assertThat(write(staff,"POST",ledgerRoot()+"/credits",JSONObjectUtils.toJSONString(b)).statusCode()).isEqualTo(200);jdbc.sql("UPDATE cinenest_identity.access_grant SET state='REVOKED' WHERE permission='bank.synthetic.credit.verify'").update();b.put("commandKey",UUID.randomUUID().toString());assertThat(write(staff,"POST",ledgerRoot()+"/credits",JSONObjectUtils.toJSONString(b)).statusCode()).isEqualTo(403);
    }
    @Test void selfVerificationAndSameVerifierAllocatorAreRejected()throws Exception{
      financeGrants();var a=signedIn("customer-a");var staff=signedIn("staff");var r=bank(a);var c=credit(staff,"SYNTHETIC-BANK-100");var b=financial(((Number)r.get("revision")).longValue());b.put("requestId",r.get("id"));assertThat(write(staff,"POST",ledgerRoot()+"/credits/"+c.get("id")+"/matches",JSONObjectUtils.toJSONString(b)).statusCode()).isEqualTo(403);assertThat(write(a,"POST",ledgerRoot()+"/credits/"+c.get("id")+"/matches",JSONObjectUtils.toJSONString(b)).statusCode()).isEqualTo(403);
    }
    @Test void creditCommandsAreIdempotentAndRejectChangedPayloadOrForgedAmounts()throws Exception{
      financeGrants();var staff=signedIn("staff");var b=financial(0);b.put("fixtureKey","SYNTHETIC-BANK-100");var c=json(write(staff,"POST",ledgerRoot()+"/credits",JSONObjectUtils.toJSONString(b)));assertThat(json(write(staff,"POST",ledgerRoot()+"/credits",JSONObjectUtils.toJSONString(b))).get("id")).isEqualTo(c.get("id"));b.put("fixtureKey","SYNTHETIC-BANK-200");assertThat(write(staff,"POST",ledgerRoot()+"/credits",JSONObjectUtils.toJSONString(b)).statusCode()).isEqualTo(409);b.put("amountMinor",-1);assertThat(write(staff,"POST",ledgerRoot()+"/credits",JSONObjectUtils.toJSONString(b)).statusCode()).isEqualTo(400);
    }
    @Test void concurrentFinalAllocatorsCommitOnlyOneBookingAndAllocation()throws Exception{
      financeGrants();var customer=signedIn("customer-a");var staff=signedIn("staff");var allocator=signedIn("customer-b");var r=bank(customer);var c=credit(staff,"SYNTHETIC-BANK-50000");var current=match(allocator,r,c);long due=Long.parseLong(r.get("amountMinor").toString());try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){var one=pool.submit(()->allocateCredit(allocator,current,c,due));var two=pool.submit(()->allocateCredit(allocator,current,c,due));assertThat(java.util.List.of(one.get().statusCode(),two.get().statusCode())).containsExactlyInAnyOrder(200,409);}assertThat(jdbc.sql("SELECT count(*) FROM cinenest_booking.booking").query(Integer.class).single()).isEqualTo(1);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_payments.payment_allocation").query(Integer.class).single()).isEqualTo(1);
    }
    @Test void independentSourceReversalPreservesConfirmedBookingAndFinancialHistory()throws Exception{
      financeGrants();var customer=signedIn("customer-a");var staff=signedIn("staff");var allocator=signedIn("customer-b");var r=bank(customer);var c=credit(staff,"SYNTHETIC-BANK-50000");r=match(allocator,r,c);json(allocateCredit(allocator,r,c,Long.parseLong(r.get("amountMinor").toString())));var b=financial(0);b.put("sourceKey","SYNTHETIC-REVERSAL-"+c.get("id"));var correction=json(write(allocator,"POST",ledgerRoot()+"/credits/"+c.get("id")+"/corrections",JSONObjectUtils.toJSONString(b)));String path=ledgerRoot()+"/corrections/"+correction.get("adjustmentId")+"/decisions";assertThat(write(allocator,"POST",path,JSONObjectUtils.toJSONString(financial(0))).statusCode()).isEqualTo(403);json(write(staff,"POST",path,JSONObjectUtils.toJSONString(financial(0))));assertThat(jdbc.sql("SELECT state FROM cinenest_booking.booking").query(String.class).single()).isEqualTo("SIMULATED_CONFIRMED");assertThat(customer.get(bankPath(r)).body()).contains("DISPUTED");var financialStatus=(Map<String,Object>)json(customer.get(bankPath(r))).get("financial");assertThat(((Number)financialStatus.get("fundingShortfallMinor")).longValue()).isEqualTo(Long.parseLong(r.get("amountMinor").toString()));assertThat(jdbc.sql("SELECT count(*) FROM cinenest_payments.payment_allocation").query(Integer.class).single()).isEqualTo(1);
    }

    @Test void mixedSyntheticSourcesRespectOneObligationAndRetainExcess()throws Exception{
      financeGrants();var customer=signedIn("customer-a");var staff=signedIn("staff");var allocator=signedIn("customer-b");var r=bank(customer);var bankCredit=credit(staff,"SYNTHETIC-BANK-100");r=match(allocator,r,bankCredit);json(allocateCredit(allocator,r,bankCredit,100));r=json(customer.get(bankPath(r)));var gateway=credit(staff,"SYNTHETIC-GATEWAY-50000");r=match(allocator,r,gateway);json(allocateCredit(allocator,r,gateway,Long.parseLong(r.get("amountMinor").toString())-100));assertThat(jdbc.sql("SELECT count(*) FROM cinenest_payments.incoming_payment_credit").query(Integer.class).single()).isEqualTo(2);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_booking.booking").query(Integer.class).single()).isEqualTo(1);assertThat(staff.get(ledgerRoot()+"/credits/"+gateway.get("id")).body()).contains("SYNTHETIC_GATEWAY","availableMinor");
    }
    @Test void auditFailureRollsBackFinalFundingAndBookingButNotEarlierCredit()throws Exception{
      financeGrants();var customer=signedIn("customer-a");var staff=signedIn("staff");var allocator=signedIn("customer-b");var r=bank(customer);var c=credit(staff,"SYNTHETIC-BANK-50000");r=match(allocator,r,c);jdbc.sql("CREATE FUNCTION cinenest_payments.test_financial_audit_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action LIKE 'LEDGER_%' THEN RAISE EXCEPTION 'SYNTHETIC financial audit failure'; END IF; RETURN NEW; END $$").update();jdbc.sql("CREATE TRIGGER test_financial_audit_failure BEFORE INSERT ON cinenest_payments.bank_audit FOR EACH ROW EXECUTE FUNCTION cinenest_payments.test_financial_audit_failure()").update();try{assertThat(allocateCredit(allocator,r,c,Long.parseLong(r.get("amountMinor").toString())).statusCode()).isEqualTo(503);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_payments.payment_allocation").query(Integer.class).single()).isZero();assertThat(jdbc.sql("SELECT count(*) FROM cinenest_booking.booking").query(Integer.class).single()).isZero();assertThat(jdbc.sql("SELECT count(*) FROM cinenest_payments.incoming_payment_credit").query(Integer.class).single()).isEqualTo(1);assertThat(allocator.get(ledgerRoot()+"/exceptions").body()).contains("BOOKING_RESOLUTION");}finally{jdbc.sql("DROP TRIGGER test_financial_audit_failure ON cinenest_payments.bank_audit").update();jdbc.sql("DROP FUNCTION cinenest_payments.test_financial_audit_failure()").update();}
    }
    @Test void actualRestartEscalatesDurableFinancialExceptionsWithCommandsDisabled()throws Exception{
      financeGrants();var staff=signedIn("staff");credit(staff,"SYNTHETIC-BANK-100");jdbc.sql("UPDATE cinenest_payments.bank_financial_exception SET due_at=clock_timestamp()-INTERVAL '1 second'").update();Process one=restartedWorker("financial-worker-first");try{awaitFinancial(one,1);}finally{stopWorker(one);}credit(staff,"SYNTHETIC-BANK-200");jdbc.sql("UPDATE cinenest_payments.bank_financial_exception SET due_at=clock_timestamp()-INTERVAL '1 second' WHERE state='OPEN'").update();Process two=restartedWorker("financial-worker-restarted");try{awaitFinancial(two,2);}finally{stopWorker(two);}
    }
    void awaitFinancial(Process child,int expected)throws Exception{for(int n=0;n<300;n++){assertThat(child.isAlive()).isTrue();if(jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_financial_exception WHERE state='ESCALATED'").query(Integer.class).single()==expected)return;Thread.sleep(50);}throw new AssertionError("Durable financial exceptions not recovered");}
    @Test void populatedV9LedgerUpgradePreservesExistingVenueAndRepeats()throws Exception{
      String url=environment.getRequiredProperty("spring.datasource.url"),user=environment.getRequiredProperty("spring.datasource.username"),password=environment.getRequiredProperty("spring.datasource.password"),database="ledger_upgrade_"+UUID.randomUUID().toString().replace("-","");String target=url.substring(0,url.lastIndexOf('/')+1)+database;
      try(var c=DriverManager.getConnection(url,user,password);var sql=c.createStatement()){sql.execute("CREATE DATABASE "+database);}try{Flyway.configure().dataSource(target,user,password).target("9").load().migrate();try(var c=DriverManager.getConnection(target,user,password);var sql=c.createStatement()){sql.execute("INSERT INTO cinenest_rooms.venue(venue_id,code,display_name,timezone) VALUES('00000000-0000-4000-8000-000000000001','KEEP','Retained V9','Asia/Colombo')");}var f=Flyway.configure().dataSource(target,user,password).target("10").load();assertThat(f.migrate().migrationsExecuted).isEqualTo(1);assertThat(f.migrate().migrationsExecuted).isZero();f.validate();try(var c=DriverManager.getConnection(target,user,password);var sql=c.createStatement();var rs=sql.executeQuery("SELECT display_name FROM cinenest_rooms.venue WHERE code='KEEP'")){assertThat(rs.next()).isTrue();assertThat(rs.getString(1)).isEqualTo("Retained V9");}}finally{try(var c=DriverManager.getConnection(url,user,password);var sql=c.createStatement()){sql.execute("DROP DATABASE "+database);}}
    }

    @Test void financialApisRejectCsrfCrossVenueNegativeOverflowAndForgedCurrency()throws Exception{
      financeGrants();var customer=signedIn("customer-a");var staff=signedIn("staff");var allocator=signedIn("customer-b");var body=financial(0);body.put("fixtureKey","SYNTHETIC-BANK-100");assertThat(client.send(HttpRequest.newBuilder(URI.create(base()+ledgerRoot()+"/credits")).header("Cookie",staff.cookie).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(JSONObjectUtils.toJSONString(body))).build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);assertThat(staff.get("/api/admin/venues/"+VB+"/bank/credits").statusCode()).isEqualTo(403);
      var r=bank(customer);var c=credit(staff,"SYNTHETIC-BANK-50000");r=match(allocator,r,c);for(String amount:java.util.List.of("-1","0","9223372036854775808")){var b=financial(((Number)r.get("revision")).longValue());b.put("creditId",c.get("id"));b.put("amountMinor",amount);assertThat(write(allocator,"POST",financePath(r)+"/allocations",JSONObjectUtils.toJSONString(b)).statusCode()).isIn(400,409);}var b=financial(((Number)r.get("revision")).longValue());b.put("creditId",c.get("id"));b.put("amountMinor",1);b.put("currency","USD");assertThat(write(allocator,"POST",financePath(r)+"/allocations",JSONObjectUtils.toJSONString(b)).statusCode()).isEqualTo(400);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_payments.payment_allocation").query(Integer.class).single()).isZero();assertThat(customer.get(ledgerRoot()+"/credits/"+c.get("id")).statusCode()).isEqualTo(403);
    }
    @Test void postgresRejectsOverAllocationAndImmutableCreditHistory()throws Exception{
      financeGrants();var customer=signedIn("customer-a");var staff=signedIn("staff");var allocator=signedIn("customer-b");var r=bank(customer);var c=credit(staff,"SYNTHETIC-BANK-100");r=match(allocator,r,c);UUID credit=UUID.fromString(c.get("id").toString()),request=UUID.fromString(r.get("id").toString());assertThatThrownBy(()->jdbc.sql("UPDATE cinenest_payments.incoming_payment_credit SET amount_minor=200 WHERE credit_id=:c").param("c",credit).update()).isInstanceOf(DataAccessException.class);assertThatThrownBy(()->jdbc.sql("INSERT INTO cinenest_payments.payment_allocation VALUES(:id,:c,:r,101,'LKR',:a,:key,clock_timestamp())").param("id",UUID.randomUUID()).param("c",credit).param("r",request).param("a",B).param("key",UUID.randomUUID()).update()).isInstanceOf(DataAccessException.class);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_payments.payment_allocation").query(Integer.class).single()).isZero();
    }
}
