package com.cinenest;

import com.nimbusds.jose.util.JSONObjectUtils;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.hikari.maximum-pool-size=2","spring.datasource.hikari.minimum-idle=0"})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("local-synthetic")
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class LocalCustomerIdentityTest {
 static final Path OUTBOX;
 static {try{OUTBOX=Files.createTempDirectory("cinenest-test-mail-");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 static final String PASSWORD="Local cinema testing 2026!";
 @Autowired JdbcClient jdbc;
 @Autowired Environment env;
 @DynamicPropertySource static void config(DynamicPropertyRegistry r){
  r.add("cinenest.identity.enabled",()->"true");r.add("cinenest.identity.manual.enabled",()->"true");
  r.add("cinenest.identity.session-timeout-seconds",()->"120");r.add("cinenest.identity.manual.outbox",OUTBOX::toString);
 }
 @BeforeEach void reset() throws Exception {
  jdbc.sql("TRUNCATE cinenest_identity.account CASCADE").update();jdbc.sql("TRUNCATE cinenest_identity.http_session CASCADE").update();jdbc.sql("TRUNCATE cinenest_identity.manual_throttle").update();
  try(var paths=Files.list(OUTBOX)){for(var p:paths.toList())Files.delete(p);}
 }
 @AfterAll static void cleanupPrivateTestMail() throws Exception {
  try(var files=Files.list(OUTBOX)){for(var p:files.toList())Files.delete(p);}Files.delete(OUTBOX);
 }
 String base(){return "http://127.0.0.1:"+env.getRequiredProperty("local.server.port");}
 final class Browser {
  String cookie,csrf;
  final HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
  HttpResponse<String> call(String method,String path,String body,boolean token) throws Exception {
   var request=HttpRequest.newBuilder(URI.create(base()+path)).method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
   if(cookie!=null)request.header("Cookie",cookie);if(body!=null)request.header("Content-Type","application/json");if(token&&csrf!=null)request.header("X-CSRF-TOKEN",csrf);
   var result=client.send(request.build(),HttpResponse.BodyHandlers.ofString());
   for(var value:result.headers().allValues("Set-Cookie"))if(value.startsWith("CINENEST_SESSION="))cookie=value.split(";",2)[0];
   return result;
  }
  void csrf() throws Exception {var r=call("GET","/api/auth/csrf",null,false);assertThat(r.statusCode()).isEqualTo(200);csrf=JSONObjectUtils.parse(r.body()).get("token").toString();}
  HttpResponse<String> post(String path,Map<String,?> body) throws Exception{csrf();return call("POST","/api/auth/"+path,JSONObjectUtils.toJSONString(body),true);}
  int register(String email) throws Exception {csrf();return post("registrations",Map.of("email",email,"password",PASSWORD)).statusCode();}
  int login(String email,String password) throws Exception {csrf();return post("login",Map.of("email",email,"password",password)).statusCode();}
 }
 String token(String email) throws Exception {
  try(var entries=Files.list(OUTBOX)){for(var p:entries.toList()){var lines=Files.readAllLines(p);if(lines.get(1).equals(email))return lines.get(2);}}throw new AssertionError("Private test delivery absent");
 }
 UUID account(String email){return jdbc.sql("SELECT account_id FROM cinenest_identity.manual_customer WHERE email=:email").param("email",email).query(UUID.class).single();}
 void verified(Browser b,String email) throws Exception {assertThat(b.register(email)).isEqualTo(202);assertThat(b.post("email-verifications",Map.of("token",token(email))).statusCode()).isEqualTo(204);}
 @Test void registrationVerificationLoginRotatesExistingJdbcSessionAndLogout() throws Exception {
  var b=new Browser();assertThat(b.register(" Alice@EXAMPLE.TEST ")).isEqualTo(202);assertThat(b.login("alice@example.test",PASSWORD)).isEqualTo(401);
  String token=token("alice@example.test");assertThat(b.post("email-verifications",Map.of("token",token)).statusCode()).isEqualTo(204);
  assertThat(b.post("email-verifications",Map.of("token",token)).statusCode()).isEqualTo(400);
  String previous=b.cookie;assertThat(b.login("alice@example.test",PASSWORD)).isEqualTo(204);assertThat(b.cookie).isNotEqualTo(previous);
  assertThat(b.call("GET","/api/accounts/me",null,false).statusCode()).isEqualTo(200);
  assertThat(new Browser().call("GET","/api/accounts/me",null,false).statusCode()).isEqualTo(401);
  var csrf=JSONObjectUtils.parse(b.call("GET","/api/session/csrf",null,false).body());b.csrf=csrf.get("token").toString();String old=b.cookie;
  assertThat(b.call("POST","/api/session/logout",null,true).statusCode()).isEqualTo(204);b.cookie=old;
  assertThat(b.call("GET","/api/accounts/me",null,false).statusCode()).isEqualTo(401);
  String hash=jdbc.sql("SELECT password_hash FROM cinenest_identity.manual_customer").query(String.class).single();assertThat(hash).startsWith("{pbkdf2-v1}").doesNotContain(PASSWORD);
  assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.account_scope").query(Integer.class).single()).isZero();
  assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.access_grant").query(Integer.class).single()).isZero();
 }
 @Test void duplicateNormalizationDoesNotReplaceCredentialsOrCreateAnotherAccount() throws Exception {
  var a=new Browser();assertThat(a.register("duplicate@example.test")).isEqualTo(202);UUID id=account("duplicate@example.test");
  assertThat(a.post("registrations",Map.of("email"," DUPLICATE@example.test ","password","Different secret phrase 2026!")).statusCode()).isEqualTo(202);
  assertThat(account("duplicate@example.test")).isEqualTo(id);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.account").query(Integer.class).single()).isEqualTo(1);
  assertThat(a.post("email-verifications",Map.of("token",token("duplicate@example.test"))).statusCode()).isEqualTo(204);
  assertThat(a.login("duplicate@example.test",PASSWORD)).isEqualTo(204);
 }
 @Test void invalidWeakOverlongAndRealEmailAreRejected() throws Exception {
  var b=new Browser();b.csrf();for(String p:List.of("short","passwordpassword","a".repeat(129)))assertThat(b.post("registrations",Map.of("email","weak@example.test","password",p)).statusCode()).isEqualTo(400);
  assertThat(b.post("registrations",Map.of("email","customer@gmail.com","password",PASSWORD)).statusCode()).isEqualTo(400);
  assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.account").query(Integer.class).single()).isZero();
 }
 @Test void expiredVerificationAndSuspendedAccountCannotActivate() throws Exception {
  var b=new Browser();b.register("expired@example.test");String token=token("expired@example.test");
  UUID expired=UUID.randomUUID();
  jdbc.sql("INSERT INTO cinenest_identity.account VALUES (:id,TRUE,'INELIGIBLE',0)").param("id",expired).update();
  jdbc.sql("INSERT INTO cinenest_identity.manual_customer SELECT :id,'past@example.test',password_hash,repeat('a',64),clock_timestamp()-INTERVAL '1 hour',NULL,clock_timestamp()-INTERVAL '2 hours' FROM cinenest_identity.manual_customer LIMIT 1").param("id",expired).update();
  assertThat(b.post("email-verifications",Map.of("token","0123456789abcdef".repeat(4))).statusCode()).isEqualTo(400);
  // A valid-format but expired digest is exercised separately from unknown-token rejection.
  String expiredToken="f".repeat(64);
  String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(expiredToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  UUID past=UUID.randomUUID();jdbc.sql("INSERT INTO cinenest_identity.account VALUES (:id,TRUE,'INELIGIBLE',0)").param("id",past).update();
  jdbc.sql("INSERT INTO cinenest_identity.manual_customer SELECT :id,'past2@example.test',password_hash,:digest,clock_timestamp()-INTERVAL '1 hour',NULL,clock_timestamp()-INTERVAL '2 hours' FROM cinenest_identity.manual_customer LIMIT 1").param("id",past).param("digest",digest).update();
  assertThat(b.post("email-verifications",Map.of("token",expiredToken)).statusCode()).isEqualTo(400);
  jdbc.sql("UPDATE cinenest_identity.account SET eligibility='SUSPENDED',revision=revision+1").update();
  assertThat(b.post("email-verifications",Map.of("token",token)).statusCode()).isEqualTo(400);
  assertThat(b.login("expired@example.test",PASSWORD)).isEqualTo(401);
 }
 @Test void loginThrottleAndUnknownAccountAreEnumerationResistant() throws Exception {
  var b=new Browser();verified(b,"throttle@example.test");
  for(int i=0;i<5;i++)assertThat(b.login("throttle@example.test","Incorrect phrase 2026!")).isEqualTo(401);
  assertThat(b.login("throttle@example.test",PASSWORD)).isEqualTo(429);
  assertThat(b.login("absent@example.test",PASSWORD)).isEqualTo(401);
 }
 @Test void csrfUnknownFieldsDuplicateMembersAndQueriesAreRejected() throws Exception {
  var b=new Browser();assertThat(b.call("POST","/api/auth/registrations","{}",false).statusCode()).isEqualTo(403);b.csrf();
  assertThat(b.call("POST","/api/auth/registrations","{\"email\":\"a@example.test\",\"email\":\"b@example.test\",\"password\":\"Local cinema testing 2026!\"}",true).statusCode()).isEqualTo(400);
  assertThat(b.post("registrations",Map.of("email","a@example.test","password",PASSWORD,"role","ADMIN")).statusCode()).isEqualTo(400);
  assertThat(b.call("GET","/api/auth/csrf?unexpected=1",null,false).statusCode()).isEqualTo(400);
 }
 @Test void currentEligibilityAndSessionExpiryDenyProtectedData() throws Exception {
  var b=new Browser();verified(b,"eligibility@example.test");assertThat(b.login("eligibility@example.test",PASSWORD)).isEqualTo(204);
  assertThat(b.call("GET","/api/accounts/"+UUID.randomUUID(),null,false).statusCode()).isEqualTo(403);
  jdbc.sql("UPDATE cinenest_identity.account SET eligibility='SUSPENDED',revision=revision+1").update();assertThat(b.call("GET","/api/accounts/me",null,false).statusCode()).isEqualTo(403);
  jdbc.sql("UPDATE cinenest_identity.http_session SET last_access_time=0,expiry_time=0").update();assertThat(b.call("GET","/api/accounts/me",null,false).statusCode()).isEqualTo(401);
 }
 @Test void mandatoryAuditFailureRollsBackRegistrationAndDeniesLogin() throws Exception {
  var b=new Browser();verified(b,"audit@example.test");
  jdbc.sql("ALTER TABLE cinenest_identity.manual_audit ADD CONSTRAINT manual_test_fault CHECK(false) NOT VALID").update();
  try {assertThat(b.register("rollback@example.test")).isEqualTo(503);assertThat(b.login("audit@example.test",PASSWORD)).isEqualTo(503);assertThat(b.call("GET","/api/accounts/me",null,false).statusCode()).isEqualTo(401);
   assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.manual_customer WHERE email='rollback@example.test'").query(Integer.class).single()).isZero();
  }finally{jdbc.sql("ALTER TABLE cinenest_identity.manual_audit DROP CONSTRAINT manual_test_fault").update();}
 }
 @Test void concurrentDuplicateRegistrationHasOneIdentity() throws Exception {
  var a=new Browser();var b=new Browser();a.csrf();b.csrf();
  var x=CompletableFuture.supplyAsync(()->{try{return a.post("registrations",Map.of("email","race@example.test","password",PASSWORD)).statusCode();}catch(Exception e){throw new RuntimeException(e);}});
  var y=CompletableFuture.supplyAsync(()->{try{return b.post("registrations",Map.of("email"," RACE@example.test ","password",PASSWORD)).statusCode();}catch(Exception e){throw new RuntimeException(e);}});
  assertThat(x.get(10,TimeUnit.SECONDS)).isEqualTo(202);assertThat(y.get(10,TimeUnit.SECONDS)).isEqualTo(202);
  assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.account").query(Integer.class).single()).isEqualTo(1);
 }
 @Test void v27UpgradePreservesOidcAccountsAndSessionsAndV28IsIdempotent() throws Exception {
  String database="manual_upgrade_"+UUID.randomUUID().toString().replace("-","");
  String url=env.getRequiredProperty("spring.datasource.url"),user=env.getRequiredProperty("spring.datasource.username"),password=env.getRequiredProperty("spring.datasource.password");
  String target=url.substring(0,url.lastIndexOf('/')+1)+database;
  try(var c=java.sql.DriverManager.getConnection(url,user,password);var sql=c.createStatement()){sql.execute("CREATE DATABASE "+database);}
  try {
   org.flywaydb.core.Flyway.configure().dataSource(target,user,password).target("27").load().migrate();
   UUID id=UUID.randomUUID();
   try(var c=java.sql.DriverManager.getConnection(target,user,password);var sql=c.createStatement()){
    sql.execute("INSERT INTO cinenest_identity.account VALUES ('"+id+"',TRUE,'ELIGIBLE',7)");
    sql.execute("INSERT INTO cinenest_identity.external_identity VALUES ('https://synthetic.invalid','retained-subject','"+id+"')");
    sql.execute("INSERT INTO cinenest_identity.http_session VALUES ('"+UUID.randomUUID()+"','"+UUID.randomUUID()+"',1,1,120,9999999999999,'"+id+"')");
   }
   var flyway=org.flywaydb.core.Flyway.configure().dataSource(target,user,password).load();assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);assertThat(flyway.migrate().migrationsExecuted).isZero();flyway.validate();
   try(var c=java.sql.DriverManager.getConnection(target,user,password);var sql=c.createStatement()){
    try(var rows=sql.executeQuery("SELECT revision FROM cinenest_identity.account")){assertThat(rows.next()).isTrue();assertThat(rows.getLong(1)).isEqualTo(7);}
    try(var rows=sql.executeQuery("SELECT subject FROM cinenest_identity.external_identity")){assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("retained-subject");}
    try(var rows=sql.executeQuery("SELECT count(*) FROM cinenest_identity.http_session")){rows.next();assertThat(rows.getInt(1)).isEqualTo(1);}
   }
  }finally{try(var c=java.sql.DriverManager.getConnection(url,user,password);var sql=c.createStatement()){sql.execute("DROP DATABASE "+database+" WITH (FORCE)");}}
 }
 @Test void metadataAndAuditAreImmutableAndVerificationDoesNotGrantAuthority() throws Exception {
  var b=new Browser();verified(b,"immutable@example.test");
  assertThatThrownBy(()->jdbc.sql("UPDATE cinenest_identity.manual_customer SET email='changed@example.test'").update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->jdbc.sql("DELETE FROM cinenest_identity.manual_audit").update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->jdbc.sql("TRUNCATE cinenest_identity.manual_audit").update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.external_identity").query(Integer.class).single()).isZero();
  assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.access_grant").query(Integer.class).single()).isZero();
 }
 @Test void credentialsAndTestTokensNeverAppearInLogs(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
  var b=new Browser();b.register("secrecy@example.test");String secret=token("secrecy@example.test");
  assertThat(b.post("email-verifications",Map.of("token",secret)).statusCode()).isEqualTo(204);
  assertThat(b.login("secrecy@example.test",PASSWORD)).isEqualTo(204);
  assertThat(output.getAll()).doesNotContain(PASSWORD,secret);
 }
 @Test @EnabledIfEnvironmentVariable(named="CINENEST_MANUAL_E2E",matches="true")
 void realBrowserJourney() throws Exception {
  Path run=Files.createTempDirectory("cinenest-manual-e2e-");Files.writeString(run.resolve("config.json"),JSONObjectUtils.toJSONString(Map.of("backend",base(),"outbox",OUTBOX.toString())));
  var business=new HashMap<String,Long>();
  for(String table:List.of("cinenest_payments.bank_payment_request","cinenest_payments.incoming_payment_credit","cinenest_payments.payment_allocation","cinenest_booking.booking","cinenest_access.access_credential","cinenest_iot.device"))business.put(table,jdbc.sql("SELECT count(*) FROM "+table).query(Long.class).single());
  var process=new ProcessBuilder("node",Path.of("../frontend/tests/manual-auth-e2e.mjs").toAbsolutePath().toString(),run.toString()).redirectErrorStream(true).redirectOutput(run.resolve("browser.log").toFile()).start();
  try{
   long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(3);
   while(process.isAlive() && System.nanoTime()<deadline){
    Path command=run.resolve("command.json");
    if(Files.exists(command)) {
     var request=JSONObjectUtils.parse(Files.readString(command));Files.delete(command);
     String reply="{\"ok\":true}";
     if("expire-session".equals(request.get("op")))jdbc.sql("UPDATE cinenest_identity.http_session SET last_access_time=0,expiry_time=0").update();
     else if("expired-token".equals(request.get("op"))) {
      UUID expired=UUID.randomUUID();String raw=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");
      String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      jdbc.sql("INSERT INTO cinenest_identity.account VALUES (:id,TRUE,'INELIGIBLE',0)").param("id",expired).update();
      jdbc.sql("INSERT INTO cinenest_identity.manual_customer SELECT :id,'expired-browser@example.test',password_hash,:digest,clock_timestamp()-INTERVAL '1 hour',NULL,clock_timestamp()-INTERVAL '2 hours' FROM cinenest_identity.manual_customer WHERE email='browser@example.test'").param("id",expired).param("digest",digest).update();
      reply=JSONObjectUtils.toJSONString(Map.of("ok",true,"token",raw));
     }else throw new AssertionError("Unknown test control");
     Files.writeString(run.resolve("reply.tmp"),reply);Files.move(run.resolve("reply.tmp"),run.resolve("reply.json"),StandardCopyOption.ATOMIC_MOVE);
    }Thread.sleep(20);
   }
   assertThat(process.waitFor(1,TimeUnit.SECONDS)).as(run.toString()).isTrue();assertThat(process.exitValue()).as(Files.readString(run.resolve("browser.log"))).isZero();
   assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.manual_customer WHERE verified_at IS NOT NULL").query(Integer.class).single()).isEqualTo(1);
   assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.manual_audit WHERE action='LOGIN' AND outcome='ALLOW'").query(Integer.class).single()).isPositive();
   for(var entry:business.entrySet())assertThat(jdbc.sql("SELECT count(*) FROM "+entry.getKey()).query(Long.class).single()).isEqualTo(entry.getValue());
   assertThat(jdbc.sql("SELECT max(version::int) FROM flyway_schema_history WHERE success AND version IS NOT NULL").query(Integer.class).single()).isEqualTo(28);
  }finally{if(process.isAlive())process.destroy();}
 }
}
