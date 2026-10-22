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
class ManagedGrantReadIntegrationTest {
    static final SyntheticOidcIssuer IDP = new SyntheticOidcIssuer();
    static final UUID A = id("customer-a"), B = id("customer-b"), STAFF = id("staff");
    static final UUID VA = id("venue-a"), VB = id("venue-b");
    @Autowired JdbcClient jdbc;
    @Autowired Environment environment;
    final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("cinenest.identity.grant-read.enabled", () -> "true");
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
        jdbc.sql("TRUNCATE cinenest_identity.grant_read_audit").update();
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


    // All grants below are isolated synthetic test fixtures, never production/bootstrap assignments.
    String endpoint(){return "/api/admin/venues/"+VA+"/accounts/"+A+"/grants";}
    UUID targetGrant(String permission,String state,String from,String until){
        UUID id=UUID.randomUUID();jdbc.sql("INSERT INTO cinenest_identity.access_grant VALUES(:id,:a,:p,'VENUE',:v,:s,CAST(:f AS timestamptz),CAST(:u AS timestamptz),7)")
            .param("id",id).param("a",A).param("p",permission).param("v",VA).param("s",state).param("f",from).param("u",until).update();return id;
    }
    UUID active(String permission){return targetGrant(permission,"ACTIVE","2000-01-01T00:00:00Z","2100-01-01T00:00:00Z");}
    @SuppressWarnings("unchecked") Map<String,Object> json(HttpResponse<String> response)throws Exception{assertThat(response.statusCode()).isEqualTo(200);return JSONObjectUtils.parse(response.body());}
    @SuppressWarnings("unchecked") java.util.List<Map<String,Object>> items(Map<String,Object> page){return (java.util.List<Map<String,Object>>)page.get("items");}
    void permit(){grant("identity.grant.read","VENUE",VA);}
    @ParameterizedTest @ValueSource(strings={"account.read","booking.synthetic.read","guest.synthetic.read","ADMIN","identity.grant.approve","bank.synthetic.credit.read"})
    void substitutePermissionsCannotInspect(String permission)throws Exception{grant(permission,"VENUE",VA);var response=signedIn("staff").get(endpoint());assertThat(response.statusCode()).isEqualTo(403);assertThat(response.body()).isEmpty();}
    @Test void missingSelfOtherVenueAndStitchedGrantsDeny()throws Exception{
        var b=signedIn("staff");assertThat(b.get(endpoint()).statusCode()).isEqualTo(403);
        grant("identity.grant.read","SELF",null);assertThat(b.get(endpoint()).statusCode()).isEqualTo(403);
        grant("identity.grant.read","VENUE",VB);grant("account.read","VENUE",VA);assertThat(b.get(endpoint()).statusCode()).isEqualTo(403);
    }
    @ParameterizedTest @ValueSource(strings={"REVOKED","EXPIRED","FUTURE","SUSPENDED","INELIGIBLE","MEMBERSHIP"})
    void changedCallerAuthorityDenies(String change)throws Exception{
        permit();var b=signedIn("staff");assertThat(b.get(endpoint()).statusCode()).isEqualTo(200);
        switch(change){
            case "REVOKED"->jdbc.sql("UPDATE cinenest_identity.access_grant SET state='REVOKED' WHERE account_id=:a").param("a",STAFF).update();
            case "EXPIRED"->jdbc.sql("UPDATE cinenest_identity.access_grant SET valid_from='2000-01-01',valid_until='2001-01-01' WHERE account_id=:a").param("a",STAFF).update();
            case "FUTURE"->jdbc.sql("UPDATE cinenest_identity.access_grant SET valid_from='2090-01-01',valid_until='2100-01-01' WHERE account_id=:a").param("a",STAFF).update();
            case "MEMBERSHIP"->jdbc.sql("DELETE FROM cinenest_identity.account_scope WHERE account_id=:a AND venue_id=:v").param("a",STAFF).param("v",VA).update();
            default->jdbc.sql("UPDATE cinenest_identity.account SET eligibility=:e WHERE account_id=:a").param("a",STAFF).param("e",change).update();
        }
        assertThat(b.get(endpoint()).statusCode()).isEqualTo(403);
    }
    @Test void missingAndExpiredSessionsDeny()throws Exception{permit();assertThat(new Browser().get(endpoint()).statusCode()).isEqualTo(401);var b=signedIn("staff");jdbc.sql("UPDATE cinenest_identity.http_session SET last_access_time=0, expiry_time=0").update();assertThat(b.get(endpoint()).statusCode()).isEqualTo(401);}
    @Test void missingNonMemberAndNonSyntheticTargetsAreOpaque()throws Exception{
        permit();var b=signedIn("staff");for(UUID target:java.util.List.of(B,UUID.randomUUID()))assertThat(b.get(endpoint().replace(A.toString(),target.toString())).statusCode()).isEqualTo(404);
        jdbc.sql("UPDATE cinenest_identity.account SET synthetic=FALSE WHERE account_id=:a").param("a",A).update();assertThat(b.get(endpoint()).statusCode()).isEqualTo(404);
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.grant_read_audit WHERE target_id IS NOT NULL").query(Integer.class).single()).isZero();
    }
    @Test void emptyAndExactAllowlistedDtoHideUnrelatedGrants()throws Exception{
        permit();var b=signedIn("staff");var empty=json(b.get(endpoint()));assertThat(items(empty)).isEmpty();assertThat(empty.get("kind")).isEqualTo("MANAGED_GRANT_SUBSET");
        active("booking.synthetic.read");active("guest.synthetic.read");active("bank.synthetic.allocate");active("identity.grant.read");
        var response=b.get(endpoint());var page=json(response);assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(page.keySet()).containsExactlyInAnyOrder("kind","venueId","accountId","targetEligibility","observedAt","limit","hasNext","nextCursor","snapshotPolicy","overlapBasis","items");
        assertThat(items(page)).hasSize(2);for(var item:items(page)){assertThat(item.keySet()).containsExactlyInAnyOrder("grantId","venueId","permission","storedState","validFrom","validUntil","revision","effectiveAtObservation","overlapsAnotherActiveGrant","anotherEffectiveGrant");assertThat(item.get("effectiveAtObservation")).isEqualTo(true);}
        assertThat(response.body()).doesNotContain("bank.synthetic.allocate","identity.grant.read","issuer","session_id","actor_id");
    }
    @Test void storedAndEffectiveStatesAndLegacyOverlapsAreTruthful()throws Exception{
        permit();UUID first=active("booking.synthetic.read");active("booking.synthetic.read");UUID expired=targetGrant("guest.synthetic.read","ACTIVE","2000-01-01T00:00:00Z","2001-01-01T00:00:00Z");UUID revoked=targetGrant("guest.synthetic.read","REVOKED","2000-01-01T00:00:00Z","2100-01-01T00:00:00Z");
        var rows=items(json(signedIn("staff").get(endpoint())));for(var r:rows){if(r.get("permission").equals("booking.synthetic.read")){assertThat(r.get("effectiveAtObservation")).isEqualTo(true);assertThat(r.get("overlapsAnotherActiveGrant")).isEqualTo(true);assertThat(r.get("anotherEffectiveGrant")).isEqualTo(true);}else assertThat(r.get("effectiveAtObservation")).isEqualTo(false);}
        assertThat(rows.stream().filter(x->x.get("grantId").equals(expired.toString())).findFirst().orElseThrow().get("storedState")).isEqualTo("ACTIVE");
        assertThat(rows.stream().filter(x->x.get("grantId").equals(revoked.toString())).findFirst().orElseThrow().get("storedState")).isEqualTo("REVOKED");
        jdbc.sql("UPDATE cinenest_identity.access_grant SET state='REVOKED' WHERE grant_id=:g").param("g",first).update();
        var partial=items(json(signedIn("staff").get(endpoint())));var revokedPath=partial.stream().filter(x->x.get("grantId").equals(first.toString())).findFirst().orElseThrow();
        assertThat(revokedPath.get("effectiveAtObservation")).isEqualTo(false);assertThat(revokedPath.get("anotherEffectiveGrant")).isEqualTo(true);
        jdbc.sql("UPDATE cinenest_identity.account SET eligibility='SUSPENDED' WHERE account_id=:a").param("a",A).update();rows=items(json(signedIn("staff").get(endpoint())));assertThat(rows).allSatisfy(r->{assertThat(r.get("effectiveAtObservation")).isEqualTo(false);assertThat(r.get("anotherEffectiveGrant")).isEqualTo(false);});
    }
    @Test void paginationIsCompleteBoundAndOverlapUsesWholeSubset()throws Exception{
        permit();for(int i=0;i<27;i++)active(i%2==0?"booking.synthetic.read":"guest.synthetic.read");var b=signedIn("staff");var seen=new java.util.HashSet<String>();String path=endpoint()+"?limit=3";
        for(int n=0;n<9;n++){var page=json(b.get(path));assertThat(items(page)).hasSize(3);for(var item:items(page)){assertThat(seen.add(item.get("grantId").toString())).isTrue();assertThat(item.get("overlapsAnotherActiveGrant")).isEqualTo(true);}assertThat(page.get("hasNext")).isEqualTo(n<8);path=endpoint()+"?limit=3&cursor="+page.get("nextCursor");}assertThat(seen).hasSize(27);
        var first=json(b.get(endpoint()+"?limit=1"));String cursor=first.get("nextCursor").toString();assertThat(b.get(endpoint()+"?limit=2&cursor="+cursor).statusCode()).isEqualTo(400);assertThat(b.get(endpoint()+"?limit=1&cursor="+cursor+"x").statusCode()).isEqualTo(400);
        active("guest.synthetic.read");assertThat(b.get(endpoint()+"?limit=1&cursor="+cursor).statusCode()).isEqualTo(409);
    }
    @ParameterizedTest @ValueSource(strings={"limit=0","limit=-1","limit=101","limit=1.0","limit=abc","limit=01","limit=1&limit=2","cursor=bad","cursor=","page=0","permission=bank.synthetic.allocate","cursor=x&cursor=y"})
    void invalidQueryRejected(String query)throws Exception{permit();assertThat(signedIn("staff").get(endpoint()+"?"+query).statusCode()).isEqualTo(400);}
    String snapshot(String table){return jdbc.sql("SELECT COALESCE(jsonb_agg(to_jsonb(x) ORDER BY to_jsonb(x)::text),'[]'::jsonb)::text FROM "+table+" x").query(String.class).single();}
    @Test void mandatoryAuditAppendOnlyRollbackAndZeroBusinessMutations()throws Exception{
        permit();active("booking.synthetic.read");var b=signedIn("staff");var before=new java.util.HashMap<String,String>();for(String table:java.util.List.of("account","account_scope","access_grant","external_identity"))before.put(table,snapshot("cinenest_identity."+table));
        json(b.get(endpoint()));assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.grant_read_audit WHERE actor_id=:a AND venue_id=:v AND target_id=:t AND outcome='ALLOW' AND observed_at IS NOT NULL AND correlation_id IS NOT NULL").param("a",STAFF).param("v",VA).param("t",A).query(Integer.class).single()).isEqualTo(1);
        assertThatThrownBy(()->jdbc.sql("UPDATE cinenest_identity.grant_read_audit SET reason_code='INVALID_QUERY'").update()).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.sql("DELETE FROM cinenest_identity.grant_read_audit").update()).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.sql("ALTER TABLE cinenest_identity.grant_read_audit ADD CONSTRAINT synthetic_failure CHECK(outcome<>'ALLOW') NOT VALID").update();try{var response=b.get(endpoint());assertThat(response.statusCode()).isEqualTo(503);assertThat(response.body()).isEmpty();assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.grant_read_audit").query(Integer.class).single()).isEqualTo(1);}finally{jdbc.sql("ALTER TABLE cinenest_identity.grant_read_audit DROP CONSTRAINT synthetic_failure").update();}
        for(var entry:before.entrySet())assertThat(snapshot("cinenest_identity."+entry.getKey())).isEqualTo(entry.getValue());
        var token=JSONObjectUtils.parse(b.get("/api/session/csrf").body());assertThat(b.call("POST",endpoint(),Map.of(token.get("headerName").toString(),token.get("token").toString())).statusCode()).isIn(403,404,405);
        for(String suffix:java.util.List.of("/assignments","/approvals","/revocations","/bootstrap"))assertThat(b.call("POST",endpoint()+suffix,Map.of()).statusCode()).isEqualTo(403);
    }
    @Test void populatedV19UpgradeCreatesOnlyAuditAndPreservesGrants()throws Exception{
        String db="grant_upgrade_"+UUID.randomUUID().toString().replace("-","");String url=System.getenv("CINENEST_DB_URL"),user=System.getenv("CINENEST_DB_USER"),password=System.getenv("CINENEST_DB_PASSWORD");
        try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){s.execute("CREATE DATABASE "+db);}String target=url.substring(0,url.lastIndexOf('/')+1)+db;
        try{Flyway.configure().dataSource(target,user,password).target("19").load().migrate();
            try(var c=DriverManager.getConnection(target,user,password);var s=c.createStatement()){s.execute("INSERT INTO cinenest_identity.account VALUES ('"+A+"',TRUE,'ELIGIBLE',9)");s.execute("INSERT INTO cinenest_identity.account_scope VALUES ('"+A+"','"+VA+"')");s.execute("INSERT INTO cinenest_identity.access_grant VALUES(gen_random_uuid(),'"+A+"','booking.synthetic.read','VENUE','"+VA+"','ACTIVE','2000-01-01','2100-01-01',7)");}
            var m=Flyway.configure().dataSource(target,user,password).load();assertThat(m.migrate().migrationsExecuted).isEqualTo(1);assertThat(m.migrate().migrationsExecuted).isZero();
            try(var c=DriverManager.getConnection(target,user,password);var s=c.createStatement();var rs=s.executeQuery("SELECT count(*),min(revision) FROM cinenest_identity.access_grant")){assertThat(rs.next()).isTrue();assertThat(rs.getInt(1)).isEqualTo(1);assertThat(rs.getLong(2)).isEqualTo(7);}
        }finally{try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){s.execute("DROP DATABASE "+db+" WITH (FORCE)");}}
    }

    @ParameterizedTest @ValueSource(strings={"CALLER_REVOKE","TARGET_REVOKE","TARGET_SUSPEND","TARGET_MEMBERSHIP"})
    void concurrentChangesAreObservedAfterRealLockWait(String change)throws Exception {
        permit();UUID managed=active("booking.synthetic.read");var b=signedIn("staff");
        String url=environment.getRequiredProperty("spring.datasource.url"),user=environment.getRequiredProperty("spring.datasource.username"),password=environment.getRequiredProperty("spring.datasource.password");
        try(var c=DriverManager.getConnection(url,user,password)){
            c.setAutoCommit(false);
            String sql=switch(change){
                case "CALLER_REVOKE"->"UPDATE cinenest_identity.access_grant SET state='REVOKED' WHERE account_id=? AND permission='identity.grant.read'";
                case "TARGET_REVOKE"->"UPDATE cinenest_identity.access_grant SET state='REVOKED' WHERE grant_id=?";
                case "TARGET_SUSPEND"->"UPDATE cinenest_identity.account SET eligibility='SUSPENDED' WHERE account_id=?";
                default->"DELETE FROM cinenest_identity.account_scope WHERE account_id=? AND venue_id='"+VA+"'";
            };
            try(var statement=c.prepareStatement(sql)){statement.setObject(1,change.equals("CALLER_REVOKE")?STAFF:change.equals("TARGET_REVOKE")?managed:A);statement.executeUpdate();}
            var read=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return b.get(endpoint());}catch(Exception e){throw new RuntimeException(e);}});
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
            while(System.nanoTime()<deadline){
                waiting=jdbc.sql("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND (query LIKE 'SELECT account_id FROM cinenest_identity.%' OR query LIKE 'SELECT grant_id FROM cinenest_identity.%')").query(Integer.class).single()>0;
                if(waiting)break;java.util.concurrent.locks.LockSupport.parkNanos(10000000);
            }
            assertThat(waiting).as("actual PostgreSQL lock wait before committing concurrent change").isTrue();assertThat(read.isDone()).isFalse();c.commit();
            var response=read.get(10,java.util.concurrent.TimeUnit.SECONDS);
            if(change.equals("CALLER_REVOKE"))assertThat(response.statusCode()).isEqualTo(403);
            else if(change.equals("TARGET_MEMBERSHIP"))assertThat(response.statusCode()).isEqualTo(404);
            else assertThat(items(json(response))).allSatisfy(item->assertThat(item.get("effectiveAtObservation")).isEqualTo(false));
        }
    }
}
