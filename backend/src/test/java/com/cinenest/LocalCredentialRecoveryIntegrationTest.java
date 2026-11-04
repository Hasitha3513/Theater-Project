package com.cinenest;

import com.nimbusds.jose.util.JSONObjectUtils;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.session.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

/** Opt-in only: runner supplies ONE fresh run-owned DB, local OIDC and service runtime authority.
 * No Docker, database creation, migration editing, history reset or preserved DB access.
 * Direct loopback HTTP client follows existing integration transport; trusted-HTTPS browser
 * qualification remains separate. Passwords/tokens/cookies are never diagnostic output.
 */
@EnabledIfEnvironmentVariable(named="CINENEST_RECOVERY_INTEGRATION_TESTS",matches="AUTHORIZED_DISPOSABLE")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.hikari.maximum-pool-size=5","spring.datasource.hikari.minimum-idle=0"})
@ActiveProfiles({"local-synthetic","synthetic-quotation"})
@org.springframework.context.annotation.Import(LocalCredentialRecoveryIntegrationTest.RaceConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class LocalCredentialRecoveryIntegrationTest {
    static final String OLD="Synthetic initial credential 2026!",NEW="Synthetic rotated credential 2026!";
    static GoogleContractIssuer idp;static Path outbox;
    final UUID venue=UUID.randomUUID(),wrongVenue=UUID.randomUUID(),grant=UUID.randomUUID();
    UUID target,issuer;Browser operator;Map<String,Object> consumed;
    @Autowired JdbcClient jdbc;@Autowired Environment env;@Autowired ApplicationContext context;
    @DynamicPropertySource static void config(DynamicPropertyRegistry r)throws Exception {
        String run=System.getenv("CINENEST_RECOVERY_INTEGRATION_RUN_ID"),url=System.getenv("CINENEST_DB_URL");
        if(run==null||!run.matches("[a-f0-9]{32}")||url==null||!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/credential_integration_"+run))throw new IllegalStateException("Fresh authorized run-owned database required");
        idp=new GoogleContractIssuer();outbox=Files.createTempDirectory("cn-recovery-private-");
        r.add("cinenest.identity.enabled",()->"true");r.add("cinenest.identity.manual.enabled",()->"true");r.add("cinenest.identity.manual.recovery.enabled",()->"true");r.add("cinenest.identity.manual.attempt-limit",()->"100");
        r.add("cinenest.identity.manual.outbox",outbox::toString);r.add("cinenest.identity.session-timeout-seconds",()->"120");
        r.add("cinenest.identity.google.enabled",()->"true");r.add("cinenest.identity.google.mode",()->"CONTRACT_TEST");r.add("cinenest.identity.google.contract-issuer",idp::issuer);r.add("cinenest.identity.google.client-id",()->idp.clientId);r.add("cinenest.identity.google.client-secret",()->idp.clientSecret);r.add("cinenest.identity.google.frontend-origin",()->"http://127.0.0.1:42137");
    }
    String base(){return "http://127.0.0.1:"+env.getRequiredProperty("local.server.port");}
    class Browser {
        String cookie;
        HttpResponse<String> call(String method,String path,String body,String csrf)throws Exception {
            var request=HttpRequest.newBuilder(URI.create(path.startsWith("http")?path:base()+path)).method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
            if(cookie!=null&&!path.startsWith(idp.issuer()))request.header("Cookie",cookie);if(body!=null)request.header("Content-Type","application/json");if(csrf!=null)request.header("X-CSRF-TOKEN",csrf);
            var response=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build().send(request.build(),HttpResponse.BodyHandlers.ofString());
            for(var c:response.headers().allValues("Set-Cookie"))if(c.startsWith("CINENEST_SESSION="))cookie=c.split(";",2)[0];return response;
        }
        HttpResponse<String> get(String path)throws Exception{return call("GET",path,null,null);}
        String csrf()throws Exception{return JSONObjectUtils.parse(get("/api/auth/csrf").body()).get("token").toString();}
        HttpResponse<String> post(String path,Map<String,?> body)throws Exception{return call("POST",path,JSONObjectUtils.toJSONString(body),csrf());}
        int login(String email,String password)throws Exception{return post("/api/auth/login",Map.of("email",email,"password",password)).statusCode();}
        int oidc()throws Exception {
            var first=get("/oauth2/authorization/google");assertThat(first.statusCode()).isEqualTo(302);
            var authorization=first.headers().firstValue("Location").orElseThrow();String callback=get(authorization).headers().firstValue("Location").orElseThrow().replace("http://127.0.0.1:42137/api/auth/google/callback",base()+"/login/oauth2/code/google");return get(callback).statusCode();
        }
    }
    UUID register(String email)throws Exception {
        var b=new Browser();assertThat(b.post("/api/auth/registrations",Map.of("email",email,"password",OLD)).statusCode()).isEqualTo(202);
        UUID id=jdbc.sql("SELECT account_id FROM cinenest_identity.manual_customer WHERE email=:email").param("email",email).query(UUID.class).single();String token=Files.readAllLines(outbox.resolve(id+".txt")).get(2);
        assertThat(b.post("/api/auth/email-verifications",Map.of("token",token)).statusCode()).isEqualTo(204);return id;
    }
    @BeforeAll void fixture()throws Exception {
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.account").query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.manual_credential_version").query(Integer.class).single()).isZero();
        issuer=register("issuer@example.test");target=register("target@example.test");
        for(UUID id:List.of(issuer,target))jdbc.sql("INSERT INTO cinenest_identity.account_scope VALUES(:a,:v)").param("a",id).param("v",venue).update();
        jdbc.sql("INSERT INTO cinenest_identity.access_grant VALUES(:g,:a,'identity.credential.recovery.issue','VENUE',:v,'ACTIVE',clock_timestamp()-interval '1 minute',clock_timestamp()+interval '20 minutes',0)").param("g",grant).param("a",issuer).param("v",venue).update();
        jdbc.sql("INSERT INTO cinenest_identity.external_identity VALUES(:issuer,'customer-a',:account)").param("issuer",idp.issuer()).param("account",target).update();
        operator=new Browser();assertThat(operator.login("issuer@example.test",OLD)).isEqualTo(204);
    }
    String path(UUID v,String operation){return "/api/admin/venues/"+v+"/accounts/"+target+"/credential-recovery/"+operation;}
    long revision(){return jdbc.sql("SELECT coalesce(max(credential_revision),0) FROM cinenest_identity.manual_credential_version WHERE account_id=:a").param("a",target).query(Long.class).single();}
    Map<String,Object> issueBody(){return Map.of("commandId",UUID.randomUUID(),"issuerGrantId",grant,"expectedIssuerGrantRevision",0,"expectedAccountRevision",1,"expectedCredentialRevision",revision(),"lifetimeSeconds",120,"approvalReference","SYNTHETIC TEST ONLY");}
    Map<String,Object> issue()throws Exception {var r=operator.post(path(venue,"capabilities"),issueBody());assertThat(r.statusCode()).isEqualTo(201);return JSONObjectUtils.parse(r.body());}
    Map<String,Object> consumeBody(Map<String,Object> cap){return Map.of("commandId",UUID.randomUUID(),"capabilityId",cap.get("capabilityId"),"expectedAccountRevision",1,"expectedCredentialRevision",revision(),"token",cap.get("token"),"newPassword",NEW);}
    @Test @Order(1) void authenticatedAuthorityVenueAndCsrfBoundaries()throws Exception {
        assertThat(operator.call("POST",path(venue,"capabilities"),JSONObjectUtils.toJSONString(issueBody()),null).statusCode()).isEqualTo(403);
        assertThat(operator.post(path(wrongVenue,"capabilities"),issueBody()).statusCode()).isEqualTo(403);
        var reader=new Browser();assertThat(reader.login("target@example.test",OLD)).isEqualTo(204);assertThat(reader.post(path(venue,"capabilities"),issueBody()).statusCode()).isEqualTo(403);
        var anon=new Browser();assertThat(anon.post(path(venue,"capabilities"),issueBody()).statusCode()).isEqualTo(401);assertThat(revision()).isZero();
    }
    @Test @Order(2) void expiredCapabilityDeniedWithoutNewVersion()throws Exception {
        var body=new HashMap<>(issueBody());body.put("lifetimeSeconds",1);var response=operator.post(path(venue,"capabilities"),body);assertThat(response.statusCode()).isEqualTo(201);var cap=JSONObjectUtils.parse(response.body());Thread.sleep(1100);
        assertThat(operator.post(path(venue,"commands"),consumeBody(cap)).statusCode()).isEqualTo(409);assertThat(revision()).isZero();
    }
    @Test @Order(3) void rotationChangesLoginAndInvalidatesManualAndOidcSessions()throws Exception {
        var manual=new Browser();assertThat(manual.login("target@example.test",OLD)).isEqualTo(204);var oidc=new Browser();assertThat(oidc.oidc()).isEqualTo(204);
        consumed=issue();assertThat(operator.post(path(venue,"commands"),consumeBody(consumed)).statusCode()).isEqualTo(200);assertThat(revision()).isEqualTo(1);
        assertThat(manual.get("/api/accounts/me").statusCode()).isEqualTo(401);assertThat(oidc.get("/api/accounts/me").statusCode()).isEqualTo(401);
        assertThat(new Browser().login("target@example.test",OLD)).isEqualTo(401);assertThat(new Browser().login("target@example.test",NEW)).isEqualTo(204);
    }
    @Test @Order(4) void replayDoesNotAppendOrMutateHistory()throws Exception {assertThat(operator.post(path(venue,"commands"),consumeBody(consumed)).statusCode()).isEqualTo(409);assertThat(revision()).isEqualTo(1);}
    @Test @Order(5) void concurrentConsumptionHasOneWinner()throws Exception {
        var cap=issue();var payload=JSONObjectUtils.toJSONString(consumeBody(cap));String csrf=operator.csrf();String cookie=operator.cookie;
        var gate=new CountDownLatch(1);try(var workers=Executors.newFixedThreadPool(2)){
            var jobs=new ArrayList<Future<Integer>>();for(int i=0;i<2;i++)jobs.add(workers.submit(()->{gate.await(5,TimeUnit.SECONDS);var b=new Browser();b.cookie=cookie;return b.call("POST",path(venue,"commands"),payload,csrf).statusCode();}));gate.countDown();assertThat(List.of(jobs.get(0).get(20,TimeUnit.SECONDS),jobs.get(1).get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }assertThat(revision()).isEqualTo(2);
    }
    @Test @Order(6) void missingAuditPostgresTransactionRollsBackWithoutConsumingCapability()throws Exception {
        var cap=issue();long before=revision();UUID capability=UUID.fromString(cap.get("capabilityId").toString());
        try(var c=DriverManager.getConnection(env.getRequiredProperty("spring.datasource.url"),env.getRequiredProperty("spring.datasource.username"),env.getRequiredProperty("spring.datasource.password"))){c.setAutoCommit(false);
            try(var q=c.prepareStatement("INSERT INTO cinenest_identity.manual_credential_version(account_id,credential_revision,previous_revision,expected_account_revision,command_id,capability_id,actor_id,venue_id,password_hash,audit_id) SELECT ?,?,?,1,?,?,?,?,password_hash,? FROM cinenest_identity.manual_customer WHERE account_id=?")){
                Object[] values={target,before+1,before,UUID.randomUUID(),capability,issuer,venue,UUID.randomUUID(),target};for(int i=0;i<values.length;i++)q.setObject(i+1,values[i]);q.executeUpdate();
            }
            try(var q=c.createStatement()){var error=catchThrowableOfType(()->q.execute("SET CONSTRAINTS cinenest_identity.manual_credential_append_integrity IMMEDIATE"),SQLException.class);assertThat(error==null?null:error.getSQLState()).isEqualTo("23514");}finally{c.rollback();}
        }
        assertThat(revision()).isEqualTo(before);assertThat(operator.post(path(venue,"commands"),consumeBody(cap)).statusCode()).isEqualTo(200);
    }
    @SuppressWarnings({"unchecked","rawtypes"}) FindByIndexNameSessionRepository<Session> sessions(){return (FindByIndexNameSessionRepository)context.getBean("sessionRepository");}
    Session currentSession(Browser browser) {
        String value=browser.cookie.substring(browser.cookie.indexOf('=')+1);
        String id=new String(Base64.getDecoder().decode(value),java.nio.charset.StandardCharsets.UTF_8);
        Session session=sessions().findById(id);assertThat(session).isNotNull();return session;
    }
    @Test @Order(7) void loadedSessionCannotBeSavedAfterRotation()throws Exception {
        var b=new Browser();assertThat(b.login("target@example.test",NEW)).isEqualTo(204);var repo=sessions();var loaded=currentSession(b);
        var cap=issue();assertThat(operator.post(path(venue,"commands"),consumeBody(cap)).statusCode()).isEqualTo(200);assertThatThrownBy(()->repo.save(loaded)).isInstanceOf(RuntimeException.class);
    }
    @Test @Order(8) void deletedSessionCannotBeResurrected()throws Exception {
        var b=new Browser();assertThat(b.login("target@example.test",NEW)).isEqualTo(204);var repo=sessions();Session loaded=currentSession(b);String id=loaded.getId();repo.deleteById(id);assertThatThrownBy(()->repo.save(loaded)).isInstanceOf(RuntimeException.class);assertThat(repo.findById(id)).isNull();
    }
    static volatile CountDownLatch epochLocked,allowSave;
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class RaceConfiguration {
        @org.springframework.context.annotation.Bean static org.springframework.beans.factory.config.BeanPostProcessor observableRealDataSource() {
            return new org.springframework.beans.factory.config.BeanPostProcessor(){
                @Override public Object postProcessAfterInitialization(Object bean,String name) {
                    if(!(bean instanceof javax.sql.DataSource))return bean;
                    return java.lang.reflect.Proxy.newProxyInstance(javax.sql.DataSource.class.getClassLoader(),new Class<?>[]{javax.sql.DataSource.class},(proxy,method,args)->{
                        Object result=invokeReal(bean,method,args);
                        if(method.getName().equals("getConnection")&&result instanceof Connection c)return observe(c);
                        return result;
                    });
                }
            };
        }
    }
    static Object invokeReal(Object target,java.lang.reflect.Method method,Object[] args)throws Throwable {
        try{return method.invoke(target,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
    }
    static Connection observe(Connection target) {
        return (Connection)java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
            Object result=invokeReal(target,method,args);
            if(method.getName().equals("prepareStatement")&&args!=null&&args.length>0&&args[0] instanceof String sql&&result instanceof PreparedStatement statement)
                return java.lang.reflect.Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->{
                    Object value=invokeReal(statement,m,a);
                    if(m.getName().equals("executeQuery")&&Thread.currentThread().getName().equals("credential-epoch-save")&&sql.contains("FROM cinenest_identity.account WHERE account_id")&&sql.contains("FOR SHARE")&&epochLocked!=null){
                        epochLocked.countDown();if(!allowSave.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Bounded session-save barrier expired");
                    }
                    return value;
                });
            return result;
        });
    }
    @Test @Order(9) void actualSessionSaveLockSerializesWithRecoveryCommit()throws Exception {
        var browser=new Browser();assertThat(browser.login("target@example.test",NEW)).isEqualTo(204);Session loaded=currentSession(browser);loaded.setLastAccessedTime(java.time.Instant.now());
        var repo=sessions();var cap=issue();var body=consumeBody(cap);epochLocked=new CountDownLatch(1);allowSave=new CountDownLatch(1);
        try(var saver=Executors.newSingleThreadExecutor(r->new Thread(r,"credential-epoch-save"));var recovery=Executors.newSingleThreadExecutor()){
            var saved=saver.submit(()->repo.save(loaded));assertThat(epochLocked.await(5,TimeUnit.SECONDS)).isTrue();
            var rotated=recovery.submit(()->operator.post(path(venue,"commands"),body).statusCode());
            boolean observed=false;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<deadline){
                int waiting=jdbc.sql("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query LIKE '%FROM cinenest_identity.account%' AND query LIKE '%FOR NO KEY UPDATE%'").query(Integer.class).single();
                if(waiting>0){observed=true;break;}Thread.sleep(20);
            }
            assertThat(observed).as("Real recovery waits behind the real save's account lock").isTrue();assertThat(rotated.isDone()).isFalse();allowSave.countDown();saved.get(10,TimeUnit.SECONDS);assertThat(rotated.get(10,TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(browser.get("/api/accounts/me").statusCode()).isEqualTo(401);
        }finally{allowSave.countDown();epochLocked=null;}
    }
    @AfterAll void close(){if(idp!=null)idp.close();/* Private fixture files retained for authorized evidence closeout. */}
}
