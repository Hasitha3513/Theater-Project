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
class RoomsIntegrationTest {
    static final SyntheticOidcIssuer IDP = new SyntheticOidcIssuer();
    static final UUID A = id("customer-a"), B = id("customer-b"), STAFF = id("staff");
    static final UUID VA = id("venue-a"), VB = id("venue-b");
    @Autowired JdbcClient jdbc;
    @Autowired com.cinenest.identity.ScopedAuthorization scopedAuthorization;
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
    String base() { return "http://127.0.0.1:" + environment.getRequiredProperty("local.server.port"); }

    @BeforeEach void fixtures() {
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


    String collection(){return "/api/admin/venues/"+VA+"/rooms";}
    String roomJson(String code,int capacity){return "{\"code\":\""+code+"\",\"name\":\"Synthetic Room\",\"capacity\":"+capacity+",\"enabled\":true,\"facilities\":[{\"code\":\"HDMI\",\"label\":\"HDMI input\",\"sortOrder\":0}]}";}
    HttpResponse<String> write(Browser browser,String method,String path,String json) throws Exception {
        synchronized(browser) { if(browser.csrfToken==null)browser.csrfToken=(String)JSONObjectUtils.parse(browser.get("/api/session/csrf").body()).get("token"); }
        return client.send(HttpRequest.newBuilder(URI.create(base()+path)).header("Cookie",browser.cookie).header("Content-Type","application/json")
            .header("X-CSRF-TOKEN",browser.csrfToken).method(method,HttpRequest.BodyPublishers.ofString(json)).build(),HttpResponse.BodyHandlers.ofString());
    }
    UUID create(Browser b,String code)throws Exception {var response=write(b,"POST",collection(),roomJson(code,4));assertThat(response.statusCode()).as(response.body()).isEqualTo(201);return UUID.fromString((String)JSONObjectUtils.parse(response.body()).get("roomId"));}
    @Test void publicCatalogIsAnonymousAndPrivateFieldsNeverLeak() throws Exception {
        grant("room.write","VENUE",VA);var b=signedIn("staff");UUID id=create(b,"ONE");
        var response=new Browser().get("/api/catalog/rooms");assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).contains(id.toString(),"HDMI input").doesNotContain("operationalApprovalReference","permittedActions","cleaningBufferMinutes","eligibility","price","availability");
        assertThat(new Browser().get("/api/catalog/rooms/"+id).statusCode()).isEqualTo(200);
        assertThat(new Browser().get("/api/catalog/venues").body()).contains("Synthetic Venue A").doesNotContain("Synthetic Venue B");
    }
    @Test void missingPermissionAndWrongVenueAreDeniedAndAudited()throws Exception {
        var b=signedIn("staff");assertThat(write(b,"POST",collection(),roomJson("DENY",4)).statusCode()).isEqualTo(403);
        grant("room.write","VENUE",VB);grant("finance.read","VENUE",VA);
        assertThat(write(b,"POST",collection(),roomJson("DENY",4)).statusCode()).isEqualTo(403);
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_rooms.configuration_audit WHERE outcome='DENY'").query(Integer.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_rooms.room").query(Integer.class).single()).isZero();
    }
    @Test void readGrantCannotMutateAndPathCannotTransferOwnership()throws Exception {
        var b=signedIn("staff");UUID g=grant("room.write","VENUE",VA);UUID id=create(b,"ONE");
        assertThat(b.get("/api/admin/venues/"+VB+"/rooms/"+id).statusCode()).isEqualTo(403);
        jdbc.sql("UPDATE cinenest_identity.access_grant SET permission='room.read' WHERE grant_id=:id").param("id",g).update();
        assertThat(b.get(collection()+"/"+id).statusCode()).isEqualTo(200);
        assertThat(write(b,"PUT",collection()+"/"+id,roomJson("ONE",5).replaceFirst("\\{","{\"expectedVersion\":0,")).statusCode()).isEqualTo(403);
    }
    @ParameterizedTest @ValueSource(strings={"REVOKED","EXPIRED","SUSPENDED"})
    void staleAuthorityCannotAuthorizeNextRequest(String state)throws Exception {
        UUID g=grant("room.write","VENUE",VA);var b=signedIn("staff");create(b,"BEFORE");
        if(state.equals("REVOKED"))jdbc.sql("UPDATE cinenest_identity.access_grant SET state='REVOKED' WHERE grant_id=:id").param("id",g).update();
        if(state.equals("EXPIRED"))jdbc.sql("UPDATE cinenest_identity.access_grant SET valid_from=clock_timestamp()-INTERVAL '2 hours',valid_until=clock_timestamp()-INTERVAL '1 hour' WHERE grant_id=:id").param("id",g).update();
        if(state.equals("SUSPENDED"))jdbc.sql("UPDATE cinenest_identity.account SET eligibility='SUSPENDED' WHERE account_id=:id").param("id",STAFF).update();
        assertThat(write(b,"POST",collection(),roomJson("AFTER",4)).statusCode()).isEqualTo(403);
    }
    @Test void validationUnknownPropertiesUnsafeImagesAndMediaClaimsRejected()throws Exception {
        grant("room.write","VENUE",VA);var b=signedIn("staff");
        for(String json:new String[]{roomJson("BAD",0),roomJson("BAD",4).replace("Synthetic Room","Netflix unlimited streaming"),roomJson("BAD",4).replace("{\"code\"","{\"tenantId\":1,\"code\""),roomJson("BAD",4).replace("\"enabled\":true","\"images\":[{\"assetPath\":\"../secret\",\"altText\":\"no\",\"sortOrder\":0}],\"enabled\":true")})
            assertThat(write(b,"POST",collection(),json).statusCode()).as(json).isEqualTo(400);
    }
    @Test void versionConflictPreservesSavedAggregateAndAudit()throws Exception {
        grant("room.write","VENUE",VA);var b=signedIn("staff");UUID id=create(b,"ONE");String update=roomJson("ONE",5).replaceFirst("\\{","{\"expectedVersion\":0,");
        assertThat(write(b,"PUT",collection()+"/"+id,update).statusCode()).isEqualTo(200);
        assertThat(write(b,"PUT",collection()+"/"+id,update).statusCode()).isEqualTo(409);
        assertThat(jdbc.sql("SELECT version FROM cinenest_rooms.room WHERE room_id=:id").param("id",id).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_rooms.configuration_audit WHERE outcome='ALLOW'").query(Integer.class).single()).isEqualTo(2);
    }
    @Test void filtersPaginationAndUnknownResourcesAreDeterministic()throws Exception {
        grant("room.write","VENUE",VA);var b=signedIn("staff");create(b,"ALPHA");create(b,"BETA");
        assertThat(new Browser().get("/api/catalog/rooms?q=alpha&minimumCapacity=4&facility=HDMI&size=1").body()).contains("ALPHA").doesNotContain("BETA");
        assertThat(new Browser().get("/api/catalog/rooms?page=1&size=1").body()).contains("BETA").doesNotContain("ALPHA");
        assertThat(new Browser().get("/api/catalog/rooms/"+UUID.randomUUID()).statusCode()).isEqualTo(404);
        assertThat(new Browser().get("/api/catalog/rooms?size=101").statusCode()).isEqualTo(400);
        assertThat(b.get(collection()+"/not-a-uuid").statusCode()).isEqualTo(400);
    }
    @Test void venueConfigurationAndWindowsAreVersionedWithoutDefaults()throws Exception {
        grant("venue.write","VENUE",VB);var b=signedIn("staff");String path="/api/admin/venues/"+VB;
        String body="{\"expectedVersion\":0,\"code\":\"SYNTHETIC-B\",\"displayName\":\"Synthetic B\",\"timezone\":\"Asia/Colombo\",\"cleaningBufferMinutes\":9,\"enabled\":true,\"windows\":[{\"weekday\":2,\"openingMinute\":610,\"closingMinute\":1440}]}";
        assertThat(write(b,"PUT",path,body).statusCode()).isEqualTo(200);
        assertThat(b.get(path).body()).contains("1440","cleaningBufferMinutes");
        assertThat(write(b,"PUT",path,body.replace("\"expectedVersion\":0","\"expectedVersion\":1").replace("\"enabled\":true","\"postCloseCleaningApproved\":true,\"operationalApprovalReference\":\"invented\",\"enabled\":true")).statusCode()).isEqualTo(400);
        assertThat(new Browser().get("/api/catalog/venues").body()).doesNotContain("operationalApprovalReference");
    }
    @Test void directHttpAndCsrfCannotBypassBoundary()throws Exception {
        assertThat(new Browser().get(collection()).statusCode()).isEqualTo(401);grant("room.write","VENUE",VA);var b=signedIn("staff");
        var response=client.send(HttpRequest.newBuilder(URI.create(base()+collection())).header("Cookie",b.cookie).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(roomJson("NO-CSRF",4))).build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(403);
    }
    @Test void databaseRejectsInvalidForeignKeysCapacityAndDuplicateCodes()throws Exception {
        assertThatThrownBy(()->jdbc.sql("INSERT INTO cinenest_rooms.room(room_id,venue_id,code,name,capacity) VALUES(:id,:venue,'BAD','Bad',0)").param("id",UUID.randomUUID()).param("venue",VA).update()).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.sql("INSERT INTO cinenest_rooms.venue_operating_window VALUES(:id,8,100,90)").param("id",VA).update()).isInstanceOf(DataIntegrityViolationException.class);
        grant("room.write","VENUE",VA);var b=signedIn("staff");create(b,"DUP");assertThat(write(b,"POST",collection(),roomJson("DUP",4)).statusCode()).isEqualTo(409);
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_rooms.room").query(Integer.class).single()).isEqualTo(1);
    }

    @Test void concurrentSameCodeAndVersionHaveExactlyOneWinner()throws Exception {
        grant("room.write","VENUE",VA);var b=signedIn("staff");
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> create=()->{start.await();return write(b,"POST",collection(),roomJson("RACE",4)).statusCode();};
            var one=pool.submit(create);var two=pool.submit(create);start.countDown();
            assertThat(java.util.List.of(one.get(),two.get())).containsExactlyInAnyOrder(201,409);
            UUID room=jdbc.sql("SELECT room_id FROM cinenest_rooms.room WHERE code='RACE'").query(UUID.class).single();
            String body=roomJson("RACE",6).replaceFirst("\\{","{\"expectedVersion\":0,");
            var gate=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> update=()->{gate.await();return write(b,"PUT",collection()+"/"+room,body).statusCode();};
            var u1=pool.submit(update);var u2=pool.submit(update);gate.countDown();assertThat(java.util.List.of(u1.get(),u2.get())).containsExactlyInAnyOrder(200,409);
        }
    }
    @Test void auditFailureRollsBackRoomAndRetryRecovers()throws Exception {
        grant("room.write","VENUE",VA);var b=signedIn("staff");
        jdbc.sql("CREATE FUNCTION cinenest_rooms.test_fail_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic audit unavailable'; END $$").update();
        jdbc.sql("CREATE TRIGGER test_fail BEFORE INSERT ON cinenest_rooms.configuration_audit FOR EACH ROW EXECUTE FUNCTION cinenest_rooms.test_fail_audit()").update();
        try {assertThat(write(b,"POST",collection(),roomJson("RECOVER",4)).statusCode()).isEqualTo(503);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_rooms.room").query(Integer.class).single()).isZero();}
        finally{jdbc.sql("DROP TRIGGER test_fail ON cinenest_rooms.configuration_audit").update();jdbc.sql("DROP FUNCTION cinenest_rooms.test_fail_audit()").update();}
        create(b,"RECOVER");
    }
    @Test void committedSuspensionWinsBeforeWaitingConfigurationAuthorization()throws Exception {
        grant("room.write","VENUE",VA);var b=signedIn("staff");
        try(var connection=DriverManager.getConnection(environment.getRequiredProperty("spring.datasource.url"),environment.getRequiredProperty("spring.datasource.username"),environment.getRequiredProperty("spring.datasource.password"));var pool=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);try(var statement=connection.prepareStatement("UPDATE cinenest_identity.account SET eligibility='SUSPENDED' WHERE account_id=?")){statement.setObject(1,STAFF);statement.executeUpdate();}
            var waiting=pool.submit(()->write(b,"POST",collection(),roomJson("AFTER-SUSPEND",4)).statusCode());Thread.sleep(100);connection.commit();assertThat(waiting.get()).isEqualTo(403);
        }
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_rooms.room").query(Integer.class).single()).isZero();
    }
    @Test void populatedV2UpgradesToV3WithoutChangingIdentity()throws Exception {
        String name="cinenest_room_upgrade_"+UUID.randomUUID().toString().replace("-","");String url=environment.getRequiredProperty("spring.datasource.url"),user=environment.getRequiredProperty("spring.datasource.username"),password=environment.getRequiredProperty("spring.datasource.password");
        try(var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement()) {
            assertThat(connection.getCatalog()).isEqualTo("cinenest_test");statement.execute("CREATE DATABASE "+name);String upgraded=url.substring(0,url.lastIndexOf('/')+1)+name;
            try {Flyway.configure().dataSource(upgraded,user,password).target("2").load().migrate();
                try(var db=DriverManager.getConnection(upgraded,user,password);var sql=db.createStatement()){sql.execute("INSERT INTO cinenest_identity.account VALUES ('00000000-0000-4000-8000-000000000001',TRUE,'ELIGIBLE',9)");}
                var v3=Flyway.configure().dataSource(upgraded,user,password).load();assertThat(v3.migrate().migrationsExecuted).isEqualTo(1);v3.validate();assertThat(v3.migrate().migrationsExecuted).isZero();
                try(var db=DriverManager.getConnection(upgraded,user,password);var sql=db.createStatement();var rows=sql.executeQuery("SELECT revision FROM cinenest_identity.account")){assertThat(rows.next()).isTrue();assertThat(rows.getLong(1)).isEqualTo(9);}
            }finally{statement.execute("DROP DATABASE "+name);}
        }
    }
    @Test void createVenueRequiresExistingExactVenueGrantAndNoPublicDefaults()throws Exception {
        UUID assigned=UUID.randomUUID();scope(STAFF,assigned);grant("venue.write","VENUE",assigned);var b=signedIn("staff");
        String body="{\"venueId\":\""+assigned+"\",\"code\":\"ASSIGNED\",\"displayName\":\"Synthetic assigned venue\",\"timezone\":\"Asia/Colombo\"}";
        assertThat(write(b,"POST","/api/admin/venues",body).statusCode()).isEqualTo(201);assertThat(new Browser().get("/api/catalog/venues").body()).doesNotContain("ASSIGNED");
        assertThat(write(b,"POST","/api/admin/venues",body.replace(assigned.toString(),UUID.randomUUID().toString())).statusCode()).isEqualTo(403);
    }

    @Test void writeAuthorizationLocksSerializeAgainstSubsequentRevocation()throws Exception {
        UUID grant=grant("room.write","VENUE",VA);var locked=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var writer=pool.submit(()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{
                assertThat(scopedAuthorization.permits(STAFF,VA,"room.write",true)).isTrue();locked.countDown();
                try{if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("test coordination timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return true;
            }));
            assertThat(locked.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var revoker=pool.submit(()->jdbc.sql("UPDATE cinenest_identity.access_grant SET state='REVOKED' WHERE grant_id=:id").param("id",grant).update());
            try{Thread.sleep(100);assertThat(revoker.isDone()).isFalse();}finally{release.countDown();}
            assertThat(writer.get()).isTrue();assertThat(revoker.get()).isEqualTo(1);assertThat(scopedAuthorization.permits(STAFF,VA,"room.write",false)).isFalse();
        }finally{release.countDown();}
    }
    @Test void earlierUncommittedRevocationIsRecheckedAfterGrantLockWait()throws Exception {
        UUID g=grant("room.write","VENUE",VA);var b=signedIn("staff");b.csrfToken=(String)JSONObjectUtils.parse(b.get("/api/session/csrf").body()).get("token");
        try(var connection=DriverManager.getConnection(environment.getRequiredProperty("spring.datasource.url"),environment.getRequiredProperty("spring.datasource.username"),environment.getRequiredProperty("spring.datasource.password"));var pool=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);try(var statement=connection.prepareStatement("UPDATE cinenest_identity.access_grant SET state='REVOKED' WHERE grant_id=?")){statement.setObject(1,g);statement.executeUpdate();}
            var waiting=pool.submit(()->write(b,"POST",collection(),roomJson("AFTER-REVOKE",4)).statusCode());Thread.sleep(100);connection.commit();assertThat(waiting.get()).isEqualTo(403);
        }
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_rooms.room").query(Integer.class).single()).isZero();
    }
}
