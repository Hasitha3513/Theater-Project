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
class PricingIntegrationTest {
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
    @Test void editPreviewDuplicateHistoryAndOptionsHaveRealPersistence()throws Exception {
        grant("package.edit","VENUE",VA);var b=signedIn("staff");UUID id=create(b,"ONE");
        assertThat(b.get(collection()).body()).contains("ONE","DRAFT").doesNotContain("PUBLISHED");assertThat(b.get("/api/admin/venues/"+VA+"/package-options").body()).contains("180","HDMI equipment");
        var preview=write(b,"POST",collection()+"/"+id+"/preview","{\"expectedRevision\":0,\"selectedMinutes\":240,\"guests\":5}");assertThat(preview.statusCode()).as(preview.body()).isEqualTo(200);assertThat(preview.body()).contains("12470","UNRESOLVED","NOT A CHECKOUT QUOTATION","\"additionalChargeMinor\":\"0\"");
        var duplicate=write(b,"POST",collection()+"/"+id+"/duplicate","{\"expectedRevision\":0,\"code\":\"COPY\",\"reason\":\"Synthetic copy\"}");assertThat(duplicate.statusCode()).as(duplicate.body()).isEqualTo(201);assertThat(duplicate.body()).doesNotContain(id.toString());
        String update=draft("ONE").replaceFirst("\\{","{\"expectedRevision\":0,").replace("12345","12340");assertThat(write(b,"PUT",collection()+"/"+id+"/draft",update).statusCode()).isEqualTo(200);assertThat(b.get(collection()+"/"+id+"/history").body()).contains("12345","12340");
    }
    @Test void readEditAndWrongVenueAreDistinctAndNoGrantStitching()throws Exception {
        grant("package.edit","VENUE",VA);var b=signedIn("staff");UUID id=create(b,"ONE");jdbc.sql("UPDATE cinenest_identity.access_grant SET permission='package.read' WHERE account_id=:a").param("a",STAFF).update();
        assertThat(b.get(collection()+"/"+id).statusCode()).isEqualTo(200);assertThat(write(b,"POST",collection()+"/"+id+"/preview","{\"expectedRevision\":0}").statusCode()).isEqualTo(200);assertThat(write(b,"POST",collection(),draft("DENIED")).statusCode()).isEqualTo(403);
        grant("package.edit","VENUE",VB);assertThat(write(b,"PUT",collection()+"/"+id+"/draft",draft("ONE").replaceFirst("\\{","{\"expectedRevision\":0,")).statusCode()).isEqualTo(403);assertThat(b.get("/api/admin/venues/"+VB+"/packages/"+id).statusCode()).isEqualTo(404);
    }
    @ParameterizedTest @ValueSource(strings={"REVOKED","EXPIRED","SUSPENDED"}) void staleAuthorityFails(String mode)throws Exception {
        UUID g=grant("package.edit","VENUE",VA);var b=signedIn("staff");create(b,"BEFORE");
        if(mode.equals("REVOKED"))jdbc.sql("UPDATE cinenest_identity.access_grant SET state='REVOKED' WHERE grant_id=:g").param("g",g).update();
        if(mode.equals("EXPIRED"))jdbc.sql("UPDATE cinenest_identity.access_grant SET valid_from=clock_timestamp()-INTERVAL '2 hours',valid_until=clock_timestamp()-INTERVAL '1 hour' WHERE grant_id=:g").param("g",g).update();
        if(mode.equals("SUSPENDED"))jdbc.sql("UPDATE cinenest_identity.account SET eligibility='SUSPENDED' WHERE account_id=:a").param("a",STAFF).update();
        assertThat(write(b,"POST",collection(),draft("AFTER")).statusCode()).isEqualTo(403);
    }
    @Test void invalidMoneyDurationGuestsRoomsOptionsAndUnknownPropertiesFail()throws Exception {
        grant("package.edit","VENUE",VA);var b=signedIn("staff");
        for(String body:new String[]{draft("BAD").replace("12345","0"),draft("BAD").replace("12345","1.234"),draft("BAD").replace("12345","9223372036854775808"),draft("BAD").replace("12345","9223372036854775807"),draft("BAD").replace("\"includedMinutes\":180","\"includedMinutes\":120"),draft("BAD").replace("\"maximumGuests\":5","\"maximumGuests\":6"),draft("BAD").replace(RID.toString(),UUID.randomUUID().toString()),draft("BAD").replace("INCLUDED","BOTH"),draft("BAD").replace("Neutral equipment", "Netflix unlimited streaming"),draft("BAD").replaceFirst("\\{","{\"publish\":true,")}) {
          assertThat(write(b,"POST",collection(),body).statusCode()).as(body).isEqualTo(400);
        }
    }
    @Test void missingAndChangedMinimumPolicyRequiresRevalidation()throws Exception {
        grant("package.edit","VENUE",VA);var b=signedIn("staff");UUID id=create(b,"ONE");
        jdbc.sql("INSERT INTO cinenest_rooms.minimum_session_policy(policy_id,venue_id,revision,minimum_minutes,effective_from,approval_reference,approved_by) VALUES(:id,:v,2,240,clock_timestamp()-INTERVAL '1 second','SYNTHETIC separately approved policy revision',:actor)").param("id",UUID.randomUUID()).param("v",VA).param("actor",STAFF).update();
        assertThat(write(b,"POST",collection()+"/"+id+"/preview","{\"expectedRevision\":0}").statusCode()).isEqualTo(400);
        jdbc.sql("TRUNCATE cinenest_rooms.minimum_session_policy").update();assertThat(write(b,"POST",collection(),draft("MISSING")).statusCode()).isEqualTo(400);
    }
    @Test void staleSavesAndConcurrentWritersNeverOverwrite()throws Exception {
        grant("package.edit","VENUE",VA);var b=signedIn("staff");UUID id=create(b,"ONE");String body=draft("ONE").replaceFirst("\\{","{\"expectedRevision\":0,");
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){var gate=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.Callable<Integer> save=()->{gate.await();return write(b,"PUT",collection()+"/"+id+"/draft",body).statusCode();};var a=pool.submit(save);var c=pool.submit(save);gate.countDown();assertThat(java.util.List.of(a.get(),c.get())).containsExactlyInAnyOrder(200,409);}
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_pricing.package_draft_revision WHERE package_id=:id").param("id",id).query(Integer.class).single()).isEqualTo(2);
    }
    @Test void auditFailureRollsBackAndRetryRecovers()throws Exception {
        grant("package.edit","VENUE",VA);var b=signedIn("staff");jdbc.sql("CREATE FUNCTION cinenest_pricing.test_audit_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic unavailable'; END $$").update();jdbc.sql("CREATE TRIGGER test_fail BEFORE INSERT ON cinenest_pricing.configuration_audit FOR EACH ROW EXECUTE FUNCTION cinenest_pricing.test_audit_fail()").update();
        try{assertThat(write(b,"POST",collection(),draft("RECOVER")).statusCode()).isEqualTo(503);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_pricing.package").query(Integer.class).single()).isZero();}finally{jdbc.sql("DROP TRIGGER test_fail ON cinenest_pricing.configuration_audit").update();jdbc.sql("DROP FUNCTION cinenest_pricing.test_audit_fail()").update();}create(b,"RECOVER");
    }
    @Test void historyPolicyRatesAndDatabaseConstraintsAreImmutable()throws Exception {
        grant("package.edit","VENUE",VA);var b=signedIn("staff");UUID id=create(b,"ONE");
        assertThatThrownBy(()->jdbc.sql("UPDATE cinenest_pricing.package_draft_revision SET reason='overwrite'").update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.sql("UPDATE cinenest_rooms.minimum_session_policy SET minimum_minutes=1").update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.sql("UPDATE cinenest_pricing.service_rate_version SET unit_price_minor=1").update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.sql("UPDATE cinenest_pricing.package_draft SET base_price_minor=0 WHERE package_id=:id").param("id",id).update()).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void directUnauthenticatedCsrfAndPublicationAreDenied()throws Exception {
        assertThat(new Browser().get(collection()).statusCode()).isEqualTo(401);grant("package.edit","VENUE",VA);var b=signedIn("staff");
        var response=client.send(HttpRequest.newBuilder(URI.create(base()+collection())).header("Cookie",b.cookie).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(draft("BAD"))).build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(403);
        assertThat(write(b,"POST",collection()+"/"+UUID.randomUUID()+"/publish","{}").statusCode()).isEqualTo(403);
    }
    @Test void populatedV3UpgradeAddsV4OnlyAndRepeats()throws Exception {
        String name="cinenest_pkg_upgrade_"+UUID.randomUUID().toString().replace("-","");String url=environment.getRequiredProperty("spring.datasource.url"),user=environment.getRequiredProperty("spring.datasource.username"),password=environment.getRequiredProperty("spring.datasource.password");
        try(var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement()){
         assertThat(connection.getCatalog()).isEqualTo("cinenest_test");statement.execute("CREATE DATABASE "+name);String upgraded=url.substring(0,url.lastIndexOf('/')+1)+name;
         try{Flyway.configure().dataSource(upgraded,user,password).target("3").load().migrate();try(var db=DriverManager.getConnection(upgraded,user,password);var sql=db.createStatement()){sql.execute("INSERT INTO cinenest_rooms.venue(venue_id,code,display_name,timezone) VALUES('00000000-0000-4000-8000-000000000001','KEEP','Preserved','Asia/Colombo')");}var f=Flyway.configure().dataSource(upgraded,user,password).target("4").load();assertThat(f.migrate().migrationsExecuted).isEqualTo(1);f.validate();assertThat(f.migrate().migrationsExecuted).isZero();try(var db=DriverManager.getConnection(upgraded,user,password);var sql=db.createStatement();var row=sql.executeQuery("SELECT code FROM cinenest_rooms.venue")){assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("KEEP");}}
         finally{statement.execute("DROP DATABASE "+name);}
        }
    }

    @Test void concurrentPolicyChangeIsRevalidatedAfterVenueLockWait()throws Exception {
        grant("package.edit","VENUE",VA);var b=signedIn("staff");b.csrfToken=(String)JSONObjectUtils.parse(b.get("/api/session/csrf").body()).get("token");
        try(var connection=DriverManager.getConnection(environment.getRequiredProperty("spring.datasource.url"),environment.getRequiredProperty("spring.datasource.username"),environment.getRequiredProperty("spring.datasource.password"));var pool=java.util.concurrent.Executors.newSingleThreadExecutor()){
          connection.setAutoCommit(false);try(var lock=connection.prepareStatement("SELECT venue_id FROM cinenest_rooms.venue WHERE venue_id=? FOR UPDATE")){lock.setObject(1,VA);lock.executeQuery().close();}
          try(var sql=connection.prepareStatement("INSERT INTO cinenest_rooms.minimum_session_policy(policy_id,venue_id,revision,minimum_minutes,effective_from,approval_reference,approved_by) VALUES(?,?,2,240,clock_timestamp()-INTERVAL '1 second','SYNTHETIC approved change',?)")){sql.setObject(1,UUID.randomUUID());sql.setObject(2,VA);sql.setObject(3,STAFF);sql.executeUpdate();}
          var waiting=pool.submit(()->write(b,"POST",collection(),draft("POLICY-RACE")).statusCode());Thread.sleep(100);assertThat(waiting.isDone()).isFalse();connection.commit();assertThat(waiting.get()).isEqualTo(400);
        }
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_pricing.package").query(Integer.class).single()).isZero();
    }
    @Test void concurrentRoomCapacityChangeCannotCommitAnInvalidDraft()throws Exception {
        grant("package.edit","VENUE",VA);var b=signedIn("staff");b.csrfToken=(String)JSONObjectUtils.parse(b.get("/api/session/csrf").body()).get("token");
        try(var connection=DriverManager.getConnection(environment.getRequiredProperty("spring.datasource.url"),environment.getRequiredProperty("spring.datasource.username"),environment.getRequiredProperty("spring.datasource.password"));var pool=java.util.concurrent.Executors.newSingleThreadExecutor()){
          connection.setAutoCommit(false);try(var update=connection.prepareStatement("UPDATE cinenest_rooms.room SET capacity=3,version=version+1 WHERE room_id=?")){update.setObject(1,RID);update.executeUpdate();}
          var waiting=pool.submit(()->write(b,"POST",collection(),draft("ROOM-RACE")).statusCode());Thread.sleep(100);assertThat(waiting.isDone()).isFalse();connection.commit();assertThat(waiting.get()).isEqualTo(400);
        }
    }
    @Test void duplicateOptionsAndUnsupportedEquipmentCannotEnterAValidatedDraft()throws Exception {
        grant("package.edit","VENUE",VA);var b=signedIn("staff");String option="{\"serviceId\":\""+SERVICE+"\",\"rateVersionId\":\""+RATE+"\",\"mode\":\"INCLUDED\"}";
        assertThat(write(b,"POST",collection(),draft("DUP").replace(option,option+","+option)).statusCode()).isEqualTo(400);
        jdbc.sql("UPDATE cinenest_pricing.service_definition SET required_facility_code='UNSUPPORTED' WHERE service_id=:id").param("id",SERVICE).update();assertThat(write(b,"POST",collection(),draft("BAD-EQUIPMENT")).statusCode()).isEqualTo(400);
    }

    @Test void differentActorsEditingSameDraftHaveOneSuccessAndOneVersionConflict()throws Exception {
        grant("package.edit","VENUE",VA);var first=signedIn("staff");UUID id=create(first,"CROSS-ACTOR");
        jdbc.sql("INSERT INTO cinenest_identity.access_grant VALUES(:g,:actor,'package.edit','VENUE',:venue,'ACTIVE',clock_timestamp()-INTERVAL '1 minute',clock_timestamp()+INTERVAL '1 hour',0)").param("g",UUID.randomUUID()).param("actor",A).param("venue",VA).update();var second=signedIn("customer-a");
        String body=draft("CROSS-ACTOR").replaceFirst("\\{","{\"expectedRevision\":0,");
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){var gate=new java.util.concurrent.CountDownLatch(1);var one=pool.submit(()->{gate.await();return write(first,"PUT",collection()+"/"+id+"/draft",body).statusCode();});var two=pool.submit(()->{gate.await();return write(second,"PUT",collection()+"/"+id+"/draft",body).statusCode();});gate.countDown();assertThat(java.util.List.of(one.get(),two.get())).containsExactlyInAnyOrder(200,409);}
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_pricing.package_draft_revision WHERE package_id=:id").param("id",id).query(Integer.class).single()).isEqualTo(2);
    }
}
