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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"cinenest.pricing.publication-worker.enabled=false","cinenest.booking.holds.enabled=true","cinenest.booking.holds.rate-count=100","cinenest.booking.holds.rate-seconds=60","cinenest.booking.holds.approval-reference=SYNTHETIC owner-approved inventory test"})
@ExtendWith(OutputCaptureExtension.class)
class InventoryIntegrationTest {
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
    String calendarPath(String date,int duration,int guests){return "/api/rooms/"+RID+"/availability/calendar?date="+date+"&durationMinutes="+duration+"&guests="+guests;}
    Map<String,Object> calendar(Browser b,String date,int duration,int guests)throws Exception{var r=b.get(calendarPath(date,duration,guests));assertThat(r.statusCode()).as(r.body()).isEqualTo(200);assertThat(r.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(512*1024);return JSONObjectUtils.parse(r.body());}
    @SuppressWarnings("unchecked") Map<String,Object> calendarItem(Map<String,Object> d,int minute){return (Map<String,Object>)((java.util.List<?>)d.get("items")).get(minute);}
    String calendarDate(){return java.time.LocalDateTime.parse(local()).toLocalDate().toString();}
    java.util.List<String> inventoryRows(){return jdbc.sql("SELECT row_to_json(x)::text FROM (SELECT * FROM cinenest_booking.inventory_block ORDER BY block_id) x UNION ALL SELECT row_to_json(x)::text FROM (SELECT * FROM cinenest_booking.reservation_hold ORDER BY hold_id) x UNION ALL SELECT row_to_json(x)::text FROM (SELECT * FROM cinenest_booking.inventory_audit ORDER BY event_id) x").query(String.class).list();}
    @Test void calendarCompletePrivateReadAndFullyReconciledParity()throws Exception{
        var a=signedIn("customer-a");var before=inventoryRows();var d=calendar(a,calendarDate(),180,4);
        assertThat(d.keySet()).containsExactlyInAnyOrder("roomId","venueId","date","timezone","durationMinutes","guests","configuration","observedAt","evidenceLevel","complete","items");
        assertThat((java.util.List<?>)d.get("items")).hasSize(1440);assertThat(d.get("complete")).isEqualTo(true);
        for(int minute:new int[]{0,600,1200}){var item=calendarItem(d,minute);assertThat(item.keySet()).containsExactlyInAnyOrder("startLocal","sessionStart","sessionEnd","blockedUntil","available","reasons");assertThat(item.get("available")).isEqualTo(inquiry(a,item.get("startLocal").toString(),180,4).get("available"));}
        assertThat(inventoryRows()).isEqualTo(before);var h=held(a,request(a,local()));var blocked=calendar(a,calendarDate(),180,4);assertThat(calendarItem(blocked,600).get("available")).isEqualTo(false);assertThat(calendarItem(blocked,600).get("reasons").toString()).contains("TEMPORARILY_HELD");
        assertThat(JSONObjectUtils.toJSONString(blocked)).doesNotContain(h.get("holdId").toString(),A.toString(),B.toString(),"owner_id","expiresAt","block_id");
    }
    @Test void calendarAuditFailureLeavesPendingUnknownUntilCommittedReconciliation()throws Exception{
        var a=signedIn("customer-a");var h=held(a,request(a,local()));
        jdbc.sql("CREATE FUNCTION public.calendar_audit_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'SYNTHETIC audit fault'; END $$").update();jdbc.sql("CREATE TRIGGER calendar_audit_fault BEFORE INSERT ON cinenest_booking.inventory_audit FOR EACH ROW EXECUTE FUNCTION public.calendar_audit_fault()").update();
        try{expire(h);assertThatThrownBy(this::reconcile).isInstanceOf(DataAccessException.class);var before=inventoryRows();var d=calendar(a,calendarDate(),180,4);var item=calendarItem(d,600);assertThat(item.get("available")).isNull();assertThat(item.get("reasons").toString()).contains("PENDING_RECONCILIATION");assertThat(inventoryRows()).isEqualTo(before);assertThat(jdbc.sql("SELECT state FROM cinenest_booking.reservation_hold").query(String.class).single()).isEqualTo("ACTIVE");assertThat(jdbc.sql("SELECT blocking FROM cinenest_booking.inventory_block").query(Boolean.class).single()).isTrue();}
        finally{jdbc.sql("DROP TRIGGER calendar_audit_fault ON cinenest_booking.inventory_audit").update();jdbc.sql("DROP FUNCTION public.calendar_audit_fault()").update();}
        reconcile();assertThat(jdbc.sql("SELECT blocking FROM cinenest_booking.inventory_block").query(Boolean.class).single()).isFalse();assertThat(calendarItem(calendar(a,calendarDate(),180,4),600).get("available")).isEqualTo(inquiry(a,local(),180,4).get("available"));
    }
    @Test void calendarInvalidatableAndReleasedHoldsDoNotBecomeFalseAvailability()throws Exception{
        var a=signedIn("customer-a");held(a,request(a,local()));jdbc.sql("UPDATE cinenest_rooms.venue SET cleaning_buffer_minutes=8,version=version+1 WHERE venue_id=:v").param("v",VA).update();var before=inventoryRows();assertThat(calendarItem(calendar(a,calendarDate(),180,4),600).get("reasons").toString()).contains("PENDING_RECONCILIATION");assertThat(inventoryRows()).isEqualTo(before);reconcile();assertThat(jdbc.sql("SELECT state FROM cinenest_booking.reservation_hold").query(String.class).single()).isEqualTo("INVALIDATED");assertThat(calendarItem(calendar(a,calendarDate(),180,4),600).get("available")).isEqualTo(true);
    }
    @Test void calendarConfirmedAndMaintenanceAndBufferAdjacencyPreserved()throws Exception{
        var a=signedIn("customer-a");held(a,request(a,local()));jdbc.sql("UPDATE cinenest_booking.inventory_block SET kind='CONFIRMED'").update();expire(Map.of("holdId",jdbc.sql("SELECT hold_id FROM cinenest_booking.reservation_hold").query(UUID.class).single()));var before=inventoryRows();var d=calendar(a,calendarDate(),180,4);assertThat(calendarItem(d,600).get("available")).isEqualTo(false);assertThat(calendarItem(d,780).get("available")).isEqualTo(false);assertThat(calendarItem(d,787).get("available")).isEqualTo(true);assertThat(inventoryRows()).isEqualTo(before);
        jdbc.sql("UPDATE cinenest_booking.inventory_block SET kind='MAINTENANCE'").update();assertThat(calendarItem(calendar(a,calendarDate(),180,4),600).get("reasons").toString()).contains("MAINTENANCE_BLOCK");
    }
    @Test void calendarParameterEligibilityAndDiscoveryFailuresAreSanitized()throws Exception{
        var a=signedIn("customer-a");String path=calendarPath(calendarDate(),180,4);assertThat(new Browser().get(path).statusCode()).isEqualTo(401);
        for(String q:new String[]{path+"&date="+calendarDate(),path+"&ownerId=secret",path.replace("durationMinutes=180","durationMinutes=0"),path.replace("guests=4","guests=6"),path.replace(calendarDate(),"2027-02-30"),path.replace("&guests=4",""),path.replace(RID.toString(),"bad")})assertThat(a.get(q).statusCode()).as(q).isEqualTo(400);
        jdbc.sql("UPDATE cinenest_rooms.room SET enabled=false").update();assertThat(a.get(path).statusCode()).isEqualTo(404);jdbc.sql("UPDATE cinenest_rooms.room SET enabled=true").update();jdbc.sql("UPDATE cinenest_identity.account SET eligibility='SUSPENDED' WHERE account_id=:a").param("a",A).update();assertThat(a.get(path).statusCode()).isEqualTo(403);
    }
    @Test void calendarMinimumClosingMidnightAndKnownFuturePolicyApply()throws Exception{
        var a=signedIn("customer-a");var d=calendar(a,calendarDate(),180,4);assertThat(calendarItem(d,1261).get("available")).isEqualTo(false);assertThat(calendarItem(d,1260).get("reasons").toString()).contains("OUTSIDE_OPERATING_HOURS");assertThat(calendarItem(calendar(a,calendarDate(),179,4),600).get("reasons").toString()).contains("MINIMUM_DURATION");
        jdbc.sql("UPDATE cinenest_rooms.venue SET post_close_cleaning_approved=true,operational_approval_reference='SYNTHETIC approval'").update();assertThat(calendarItem(calendar(a,calendarDate(),180,4),1260).get("available")).isEqualTo(true);
        jdbc.sql("INSERT INTO cinenest_rooms.minimum_session_policy VALUES(:id,:v,2,240,CAST(:time AS timestamptz),'SYNTHETIC future',:a,clock_timestamp())").param("id",UUID.randomUUID()).param("v",VA).param("time",java.time.LocalDateTime.parse(local()).atZone(java.time.ZoneId.of("Asia/Colombo")).toInstant().plusSeconds(3600).toString()).param("a",STAFF).update();assertThat(calendarItem(calendar(a,calendarDate(),180,4),600).get("available")).isEqualTo(false);
    }
    @Test void calendarDstGapFoldAndTimezoneVersionRemainAuthoritative()throws Exception{
        var a=signedIn("customer-a");jdbc.sql("UPDATE cinenest_rooms.venue SET timezone='America/New_York',version=version+1").update();
        var gap=calendar(a,"2027-03-14",180,4);assertThat(calendarItem(gap,150).get("available")).isNull();assertThat(calendarItem(gap,150).get("sessionStart")).isNull();assertThat(calendarItem(gap,150).get("reasons").toString()).contains("INVALID_LOCAL_TIME");
        var fold=calendar(a,"2027-11-07",180,4);assertThat(calendarItem(fold,90).get("available")).isNull();assertThat(fold.get("timezone")).isEqualTo("America/New_York");assertThat((java.util.List<?>)fold.get("items")).hasSize(1440);
    }
    @Test void calendarResourceLimitAndQueryPlanFailClosedWithoutTruncation()throws Exception{
        var a=signedIn("customer-a");String day=calendarDate();jdbc.sql("INSERT INTO cinenest_booking.inventory_block SELECT gen_random_uuid(),:v,:r,CAST(:start AS timestamptz)+n*INTERVAL '1 second',CAST(:start AS timestamptz)+(n+1)*INTERVAL '1 second',0,CAST(:start AS timestamptz)+(n+1)*INTERVAL '1 second',true,'MAINTENANCE',0 FROM generate_series(0,1000)n").param("v",VA).param("r",RID).param("start",java.time.LocalDate.parse(day).atStartOfDay(java.time.ZoneId.of("Asia/Colombo")).toInstant().toString()).update();
        assertThat(a.get(calendarPath(day,180,4)).statusCode()).isEqualTo(503);
        var plan=jdbc.sql("EXPLAIN SELECT block_id FROM cinenest_booking.inventory_block WHERE room_id=:r AND blocking AND tstzrange(session_start,blocked_until,'[)') && tstzrange(CAST(:s AS timestamptz),CAST(:e AS timestamptz),'[)') LIMIT 1001").param("r",RID).param("s",day+"T00:00:00Z").param("e",day+"T23:59:00Z").query(String.class).list();assertThat(String.join(" ",plan)).contains("Limit");
        assertThat(jdbc.sql("SELECT count(*) FROM pg_indexes WHERE schemaname='cinenest_booking' AND indexdef LIKE '%USING gist%'").query(Integer.class).single()).isPositive();
    }
    @Test void calendarSnapshotConcurrentCreateAndReleaseAndConstantQueryBudget()throws Exception{
        var a=signedIn("customer-a");var req=request(a,local());var created=new java.util.concurrent.atomic.AtomicReference<Map<String,Object>>();var spy=org.mockito.Mockito.spy(jdbc);
        byte[] old=CalendarSnapshotSupport.read(context,spy,transactions,A,RID,java.time.LocalDate.parse(calendarDate()),()->{try{created.set(held(a,req));}catch(Exception e){throw new RuntimeException(e);}});
        assertThat(calendarItem(JSONObjectUtils.parse(new String(old,java.nio.charset.StandardCharsets.UTF_8)),600).get("available")).isEqualTo(true);assertThat(calendarItem(calendar(a,calendarDate(),180,4),600).get("available")).isEqualTo(false);org.mockito.Mockito.verify(spy,org.mockito.Mockito.times(2)).sql(org.mockito.ArgumentMatchers.anyString());
        var h=created.get();byte[] beforeRelease=CalendarSnapshotSupport.read(context,jdbc,transactions,A,RID,java.time.LocalDate.parse(calendarDate()),()->{try{var body=Map.of("expectedRevision",h.get("revision"),"idempotencyKey",UUID.randomUUID().toString());assertThat(write(a,"POST",holdPath(h)+"/release",JSONObjectUtils.toJSONString(body)).statusCode()).isEqualTo(200);}catch(Exception e){throw new RuntimeException(e);}});
        assertThat(calendarItem(JSONObjectUtils.parse(new String(beforeRelease,java.nio.charset.StandardCharsets.UTF_8)),600).get("available")).isEqualTo(false);assertThat(calendarItem(calendar(a,calendarDate(),180,4),600).get("available")).isEqualTo(true);
    }
    @Test void calendarRechecksEligibilityOutsideItsReadSnapshot()throws Exception{
        signedIn("customer-a");var fresh=new org.springframework.transaction.support.TransactionTemplate(transactions);fresh.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThatThrownBy(()->CalendarSnapshotSupport.read(context,jdbc,transactions,A,RID,java.time.LocalDate.parse(calendarDate()),()->fresh.executeWithoutResult(t->jdbc.sql("UPDATE cinenest_identity.account SET eligibility='SUSPENDED' WHERE account_id=:a").param("a",A).update()))).hasMessageContaining("Access denied");
    }
    @Test void intervalExpiryPolicyAndReadPrivacyAreAuthoritative()throws Exception {
        var b=signedIn("customer-a");var req=request(b,local());var h=held(b,req);
        assertThat(h).containsEntry("state","ACTIVE").containsEntry("bufferMinutes",7L).containsEntry("evidenceLevel","INVENTORY_ONLY");
        assertThat(java.time.Duration.between(java.time.Instant.parse(h.get("createdAt").toString()),java.time.Instant.parse(h.get("expiresAt").toString())).getSeconds()).isEqualTo(600);
        assertThat(b.get(holdPath(h)).body()).doesNotContain("ownerId","policyIds","approval_reference","jdbc:");
        assertThat(create(b,req).statusCode()).isEqualTo(200);
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_booking.reservation_hold").query(Long.class).single()).isEqualTo(1);
        assertThatThrownBy(()->jdbc.sql("UPDATE cinenest_booking.hold_policy SET ttl_seconds=1").update()).isInstanceOf(DataAccessException.class);
    }
    @Test void competingCustomersCannotBothAcquireOverlappingInventory()throws Exception {
        var a=signedIn("customer-a");var b=signedIn("customer-b");var ra=request(a,local());var rb=request(b,local());
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){var go=new java.util.concurrent.CountDownLatch(1);var f1=pool.submit(()->{go.await();return create(a,ra).statusCode();});var f2=pool.submit(()->{go.await();return create(b,rb).statusCode();});go.countDown();assertThat(java.util.List.of(f1.get(),f2.get())).containsExactlyInAnyOrder(200,409);}
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_booking.inventory_block WHERE blocking").query(Long.class).single()).isEqualTo(1);
    }
    @Test void concurrentSameKeyAndOwnerCapPreserveOneAllocation()throws Exception {
        var a=signedIn("customer-a");var req=request(a,local());
        // Fetch CSRF before parallel HTTP commands to isolate allocation races from login machinery.
        a.csrfToken=(String)JSONObjectUtils.parse(a.get("/api/session/csrf").body()).get("token");
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){var f1=pool.submit(()->create(a,req));var f2=pool.submit(()->create(a,req));assertThat(f1.get().statusCode()).isEqualTo(200);assertThat(f2.get().statusCode()).isEqualTo(200);}
        var other=request(a,java.time.LocalDateTime.parse(local()).plusHours(5).toString());assertThat(create(a,other).statusCode()).isEqualTo(409);
        req.put("guests",3);assertThat(create(a,req).statusCode()).isEqualTo(409);
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_booking.inventory_command").query(Long.class).single()).isEqualTo(1);
    }
    @Test void bufferAndConfirmedIntervalsAreProtectedButAdjacencyIsAllowed()throws Exception {
        var a=signedIn("customer-a");var b=signedIn("customer-b");var h=held(a,request(a,local()));
        var overlap=request(b,java.time.LocalDateTime.parse(local()).plusMinutes(180).toString());assertThat(create(b,overlap).statusCode()).isEqualTo(409);
        var adjacent=request(b,java.time.LocalDateTime.parse(local()).plusMinutes(187).toString());held(b,adjacent);
        assertThatThrownBy(()->jdbc.sql("INSERT INTO cinenest_booking.inventory_block VALUES(:id,:v,:r,:s,:e,0,:e,TRUE,'CONFIRMED',0)").param("id",UUID.randomUUID()).param("v",VA).param("r",RID).param("s",java.time.Instant.parse(h.get("sessionStart").toString()).atOffset(java.time.ZoneOffset.UTC)).param("e",java.time.Instant.parse(h.get("sessionEnd").toString()).atOffset(java.time.ZoneOffset.UTC)).update()).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void confirmedAndMaintenanceFixturesCannotOverlapEachOtherOrHolds()throws Exception {
        var a=signedIn("customer-a");var req=request(a,local());var fact=inquiry(a,local(),180,4);
        jdbc.sql("INSERT INTO cinenest_booking.inventory_block VALUES(:id,:v,:r,:s,:e,0,:e,TRUE,'CONFIRMED',0)").param("id",UUID.randomUUID()).param("v",VA).param("r",RID).param("s",java.time.Instant.parse(fact.get("sessionStart").toString()).atOffset(java.time.ZoneOffset.UTC)).param("e",java.time.Instant.parse(fact.get("sessionEnd").toString()).atOffset(java.time.ZoneOffset.UTC)).update();
        assertThat(create(a,req).statusCode()).isEqualTo(409);reconcile();assertThat(jdbc.sql("SELECT count(*) FROM cinenest_booking.inventory_block WHERE blocking AND kind='CONFIRMED'").query(Long.class).single()).isEqualTo(1);
        assertThatThrownBy(()->jdbc.sql("INSERT INTO cinenest_booking.inventory_block SELECT :id,venue_id,room_id,session_start,session_end,buffer_minutes,blocked_until,TRUE,'MAINTENANCE',0 FROM cinenest_booking.inventory_block LIMIT 1").param("id",UUID.randomUUID()).update()).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void expiredHoldReplayCannotReviveAndNewAllocationReconcilesWithoutWorker()throws Exception {
        var a=signedIn("customer-a");var req=request(a,local());var h=held(a,req);expire(h);
        var r=create(a,req);assertThat(r.statusCode()).isEqualTo(200);assertThat(JSONObjectUtils.parse(r.body())).containsEntry("state","EXPIRED");
        held(signedIn("customer-b"),request(signedIn("staff"),local()));reconcile();reconcile();
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_booking.inventory_audit WHERE action='EXPIRED'").query(Long.class).single()).isEqualTo(1);
    }
    @Test void releaseIsVersionedIdempotentAndDoesNotExtendExpiry()throws Exception {
        var a=signedIn("customer-a");var req=request(a,local());var h=held(a,req);String release="{\"expectedRevision\":0,\"idempotencyKey\":\""+UUID.randomUUID()+"\"}";
        assertThat(write(a,"POST",holdPath(h)+"/release",release).statusCode()).isEqualTo(200);assertThat(write(a,"POST",holdPath(h)+"/release",release).statusCode()).isEqualTo(200);
        var replay=JSONObjectUtils.parse(create(a,req).body());assertThat(replay).containsEntry("state","RELEASED").containsEntry("expiresAt",h.get("expiresAt"));
        assertThat(write(a,"POST",holdPath(h)+"/release","{\"expectedRevision\":0,\"idempotencyKey\":\""+UUID.randomUUID()+"\"}").statusCode()).isEqualTo(409);
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_booking.inventory_audit WHERE action='RELEASED'").query(Long.class).single()).isEqualTo(1);
    }
    @Test void policyChangeInvalidatesWithoutRewritingAndKnownFutureMinimumRejects()throws Exception {
        var a=signedIn("customer-a");var h=held(a,request(a,local()));
        jdbc.sql("INSERT INTO cinenest_rooms.minimum_session_policy VALUES(:id,:v,2,240,clock_timestamp(),'SYNTHETIC future approved policy',:actor,clock_timestamp())").param("id",UUID.randomUUID()).param("v",VA).param("actor",STAFF).update();
        var r=a.get(holdPath(h));assertThat(JSONObjectUtils.parse(r.body())).containsEntry("state","INVALIDATED").containsEntry("sessionEnd",h.get("sessionEnd"));
        assertThat(a.get("/api/rooms/"+RID+"/availability?sessionStartLocal="+local()+"&durationMinutes=180&guests=4").statusCode()).isEqualTo(409);
    }
    @Test void missingBufferDisabledRoomAndStaleConfigurationFailClosed()throws Exception {
        var a=signedIn("customer-a");var req=request(a,local());jdbc.sql("UPDATE cinenest_rooms.room SET version=version+1 WHERE room_id=:r").param("r",RID).update();assertThat(create(a,req).statusCode()).isEqualTo(409);
        jdbc.sql("UPDATE cinenest_rooms.venue SET cleaning_buffer_minutes=NULL WHERE venue_id=:v").param("v",VA).update();assertThat(a.get("/api/rooms/"+RID+"/availability?sessionStartLocal="+local()+"&durationMinutes=180&guests=4").statusCode()).isEqualTo(409);
    }
    @Test void deniedOwnerVenueStitchingRevocationAndSuspensionAreEnforced()throws Exception {
        var a=signedIn("customer-a");var b=signedIn("customer-b");var h=held(a,request(a,local()));
        assertThat(b.get(holdPath(h)).statusCode()).isEqualTo(403);assertThat(write(b,"POST",holdPath(h)+"/release","{\"expectedRevision\":0,\"idempotencyKey\":\""+UUID.randomUUID()+"\"}").statusCode()).isEqualTo(403);
        var staff=signedIn("staff");String inventory="/api/admin/venues/"+VA+"/inventory";assertThat(staff.get(inventory).statusCode()).isEqualTo(403);
        grant("inventory.read","VENUE",VB);grant("room.write","VENUE",VA);assertThat(staff.get(inventory).statusCode()).isEqualTo(403);
        UUID g=grant("inventory.read","VENUE",VA);var response=staff.get(inventory);assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).doesNotContain("owner_id",A.toString(),"expiresAt");
        jdbc.sql("UPDATE cinenest_identity.access_grant SET state='REVOKED' WHERE grant_id=:g").param("g",g).update();assertThat(staff.get(inventory).statusCode()).isEqualTo(403);
        jdbc.sql("UPDATE cinenest_identity.account SET eligibility='SUSPENDED' WHERE account_id=:a").param("a",A).update();assertThat(a.get(holdPath(h)).statusCode()).isEqualTo(403);
    }
    @Test void unauthenticatedCsrfMalformedAndClientAuthorityFailClosed()throws Exception {
        var a=signedIn("customer-a");var req=request(a,local());assertThat(new Browser().get("/api/rooms/"+RID+"/availability?sessionStartLocal="+local()+"&durationMinutes=180&guests=4").statusCode()).isEqualTo(401);
        var noCsrf=client.send(HttpRequest.newBuilder(URI.create(base()+"/api/holds")).header("Cookie",a.cookie).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(JSONObjectUtils.toJSONString(req))).build(),HttpResponse.BodyHandlers.ofString());assertThat(noCsrf.statusCode()).isEqualTo(403);
        req.put("ownerId",B.toString());assertThat(create(a,req).statusCode()).isEqualTo(400);req.remove("ownerId");req.put("durationMinutes",1.5);assertThat(create(a,req).statusCode()).isEqualTo(400);
        assertThat(a.logout().statusCode()).isEqualTo(204);assertThat(a.get("/api/holds/"+UUID.randomUUID()).statusCode()).isEqualTo(401);
    }
    @Test void admissionIncludesConflictsAndMissingConfigurationDisablesCreation()throws Exception {
        var a=signedIn("customer-a");var req=request(a,local());
        for(int n=0;n<100;n++)jdbc.sql("INSERT INTO cinenest_booking.inventory_audit VALUES(:id,:a,NULL,'CREATE_ATTEMPT',NULL,NULL,clock_timestamp(),:c)").param("id",UUID.randomUUID()).param("a",A).param("c",UUID.randomUUID()).update();
        assertThat(create(a,req).statusCode()).isEqualTo(429);
        Object service=context.getBean("inventoryService");org.springframework.test.util.ReflectionTestUtils.setField(service,"rate",0);try {assertThat(create(a,req).statusCode()).isEqualTo(503);}finally{org.springframework.test.util.ReflectionTestUtils.setField(service,"rate",100);}
    }
    @Test void allocationAndExpiryAuditFailureRollbackAndRetryRecovers()throws Exception {
        var a=signedIn("customer-a");var req=request(a,local());
        jdbc.sql("CREATE FUNCTION public.synthetic_fail_inventory() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action<>'CREATE_ATTEMPT' THEN RAISE EXCEPTION 'SYNTHETIC audit fault'; END IF; RETURN NEW; END $$").update();
        jdbc.sql("CREATE TRIGGER synthetic_fail_inventory BEFORE INSERT ON cinenest_booking.inventory_audit FOR EACH ROW EXECUTE FUNCTION public.synthetic_fail_inventory()").update();
        try {assertThat(create(a,req).statusCode()).isEqualTo(503);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_booking.inventory_block").query(Long.class).single()).isZero();}
        finally {jdbc.sql("DROP TRIGGER synthetic_fail_inventory ON cinenest_booking.inventory_audit").update();jdbc.sql("DROP FUNCTION public.synthetic_fail_inventory()").update();}
        var h=held(a,req);expire(h);
        jdbc.sql("CREATE FUNCTION public.synthetic_fail_inventory() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'SYNTHETIC interrupted worker'; END $$").update();jdbc.sql("CREATE TRIGGER synthetic_fail_inventory BEFORE INSERT ON cinenest_booking.inventory_audit FOR EACH ROW EXECUTE FUNCTION public.synthetic_fail_inventory()").update();
        try {assertThatThrownBy(this::reconcile).isInstanceOf(DataAccessException.class);assertThat(jdbc.sql("SELECT state FROM cinenest_booking.reservation_hold").query(String.class).single()).isEqualTo("ACTIVE");assertThat(jdbc.sql("SELECT blocking FROM cinenest_booking.inventory_block").query(Boolean.class).single()).isTrue();}
        finally {jdbc.sql("DROP TRIGGER synthetic_fail_inventory ON cinenest_booking.inventory_audit").update();jdbc.sql("DROP FUNCTION public.synthetic_fail_inventory()").update();}
        reconcile();assertThat(JSONObjectUtils.parse(a.get(holdPath(h)).body())).containsEntry("state","EXPIRED");
    }
    @Test void populatedV5ToV6AndRepeatPreserveExistingData()throws Exception {
        String url=environment.getRequiredProperty("spring.datasource.url"),user=environment.getRequiredProperty("spring.datasource.username"),password=environment.getRequiredProperty("spring.datasource.password"),name="inventory_upgrade_"+UUID.randomUUID().toString().replace("-","");
        try(var db=DriverManager.getConnection(url,user,password);var sql=db.createStatement()){sql.execute("CREATE DATABASE "+name);}String target=url.substring(0,url.lastIndexOf('/')+1)+name;
        try {Flyway.configure().dataSource(target,user,password).target("5").load().migrate();try(var db=DriverManager.getConnection(target,user,password);var sql=db.createStatement()){sql.execute("INSERT INTO cinenest_rooms.venue(venue_id,code,display_name,timezone) VALUES('00000000-0000-4000-8000-000000000001','KEEP','Retained','Asia/Colombo')");}
            var f=Flyway.configure().dataSource(target,user,password).target("6").load();assertThat(f.migrate().migrationsExecuted).isEqualTo(1);assertThat(f.migrate().migrationsExecuted).isZero();f.validate();try(var db=DriverManager.getConnection(target,user,password);var sql=db.createStatement();var row=sql.executeQuery("SELECT code FROM cinenest_rooms.venue")){assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("KEEP");}
        } finally {try(var db=DriverManager.getConnection(url,user,password);var sql=db.createStatement()){sql.execute("DROP DATABASE "+name);}}
    }
    @Test void expiredGrantAndExpiredDatabaseSessionDenyProtectedReads()throws Exception {
        var staff=signedIn("staff");UUID g=grant("inventory.read","VENUE",VA);
        jdbc.sql("UPDATE cinenest_identity.access_grant SET valid_from=clock_timestamp()-INTERVAL '2 hours',valid_until=clock_timestamp()-INTERVAL '1 hour' WHERE grant_id=:g").param("g",g).update();
        assertThat(staff.get("/api/admin/venues/"+VA+"/inventory").statusCode()).isEqualTo(403);
        var a=signedIn("customer-a");var h=held(a,request(a,local()));jdbc.sql("UPDATE cinenest_identity.http_session SET last_access_time=0,expiry_time=0 WHERE principal_name=:a").param("a",A.toString()).update();
        assertThat(a.get(holdPath(h)).statusCode()).isEqualTo(401);
    }
    @Test void databaseClockStartsTtlAfterWaitingForVenueLock()throws Exception {
        var a=signedIn("customer-a");var req=request(a,local());a.csrfToken=(String)JSONObjectUtils.parse(a.get("/api/session/csrf").body()).get("token");
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var response=new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<HttpResponse<String>>>();
            var beforeUnlock=new java.util.concurrent.atomic.AtomicReference<java.time.Instant>();
            new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status->{
                jdbc.sql("SELECT venue_id FROM cinenest_rooms.venue WHERE venue_id=:v FOR UPDATE").param("v",VA).query(UUID.class).single();
                response.set(pool.submit(()->create(a,req)));
                try {Thread.sleep(150);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
                assertThat(response.get().isDone()).isFalse();beforeUnlock.set(jdbc.sql("SELECT clock_timestamp()").query(java.time.OffsetDateTime.class).single().toInstant());
            });
            var result=response.get().get();assertThat(result.statusCode()).isEqualTo(200);var h=JSONObjectUtils.parse(result.body());
            assertThat(java.time.Instant.parse(h.get("createdAt").toString())).isAfterOrEqualTo(beforeUnlock.get());
        }
    }
    @Test void recreatedApplicationServiceRecoversDurableExpiryAndReplay()throws Exception {
        var a=signedIn("customer-a");var req=request(a,local());var h=held(a,req);expire(h);
        // Reconstruct the application service without retaining its former instance or any in-memory hold state.
        var type=Class.forName("com.cinenest.booking.internal.InventoryService");
        Object fresh=context.getAutowireCapableBeanFactory().createBean(type);
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(fresh,"reconcile",VA);
        var recovered=org.springframework.test.util.ReflectionTestUtils.<Map<String,Object>>invokeMethod(fresh,"read",A,UUID.fromString(h.get("holdId").toString()));
        assertThat(recovered).containsEntry("state","EXPIRED");assertThat(JSONObjectUtils.parse(create(a,req).body())).containsEntry("state","EXPIRED");
    }

}
