package com.cinenest;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.net.*;import java.net.http.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;import org.junit.jupiter.params.ParameterizedTest;import org.junit.jupiter.params.provider.ValueSource;import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.core.env.Environment;import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.test.context.ActiveProfiles;import org.springframework.test.context.DynamicPropertyRegistry;import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.hikari.maximum-pool-size=2","spring.datasource.hikari.minimum-idle=0"})
@ActiveProfiles("local-synthetic")
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class FacebookSsoIntegrationTest {
 static final FacebookContractIssuer FB=new FacebookContractIssuer();static final GoogleContractIssuer GOOGLE=new GoogleContractIssuer();
 static final UUID A=UUID.randomUUID(),B=UUID.randomUUID();static final String EMAIL=UUID.randomUUID()+"@example.test",PASSWORD=UUID.randomUUID()+"-"+UUID.randomUUID();static final Path OUTBOX;static final int FRONTEND;
 static {try{OUTBOX=Files.createTempDirectory("cinenest-facebook-mail-");try(var s=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){FRONTEND=s.getLocalPort();}FB.redirect="http://127.0.0.1:"+FRONTEND+"/api/auth/facebook/callback";}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 @Autowired JdbcClient jdbc;@Autowired Environment env;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
  r.add("cinenest.identity.enabled",()->"true");r.add("cinenest.identity.manual.enabled",()->"true");r.add("cinenest.identity.session-timeout-seconds",()->"120");r.add("cinenest.identity.manual.outbox",OUTBOX::toString);
  r.add("cinenest.identity.facebook.enabled",()->"true");r.add("cinenest.identity.facebook.mode",()->"CONTRACT_TEST");r.add("cinenest.identity.facebook.contract-origin",FB::origin);r.add("cinenest.identity.facebook.client-id",()->FB.appId);r.add("cinenest.identity.facebook.client-secret-file",()->FB.secretFile.toString());r.add("cinenest.identity.facebook.frontend-origin",()->"http://127.0.0.1:"+FRONTEND);
  r.add("cinenest.identity.google.enabled",()->"true");r.add("cinenest.identity.google.mode",()->"CONTRACT_TEST");r.add("cinenest.identity.google.contract-issuer",GOOGLE::issuer);r.add("cinenest.identity.google.client-id",()->GOOGLE.clientId);r.add("cinenest.identity.google.client-secret",()->GOOGLE.clientSecret);r.add("cinenest.identity.google.frontend-origin",()->"http://127.0.0.1:"+FRONTEND);
 }
 @BeforeEach void fixture()throws Exception {
  jdbc.sql("TRUNCATE cinenest_identity.account CASCADE").update();jdbc.sql("TRUNCATE cinenest_identity.http_session CASCADE").update();jdbc.sql("TRUNCATE cinenest_identity.security_audit").update();jdbc.sql("TRUNCATE cinenest_identity.manual_throttle").update();FB.reset();GOOGLE.mode="VALID";GOOGLE.subject="customer-a";GOOGLE.codes.clear();
  for(UUID id:List.of(A,B))jdbc.sql("INSERT INTO cinenest_identity.account VALUES(:id,TRUE,'ELIGIBLE',0)").param("id",id).update();
  jdbc.sql("INSERT INTO cinenest_identity.external_identity VALUES(:issuer,:subject,:id)").param("issuer",FB.namespace()).param("subject",FB.knownSubject).param("id",A).update();
  jdbc.sql("INSERT INTO cinenest_identity.external_identity VALUES(:issuer,'customer-a',:id)").param("issuer",GOOGLE.issuer()).param("id",A).update();
  var encoder=new org.springframework.security.crypto.password.Pbkdf2PasswordEncoder("",16,600000,256);encoder.setAlgorithm(org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
  jdbc.sql("INSERT INTO cinenest_identity.manual_customer(account_id,email,password_hash,verification_digest,verification_expires_at,verified_at) VALUES(:id,:email,:hash,repeat('a',64),clock_timestamp()+INTERVAL '1 hour',clock_timestamp())").param("id",A).param("email",EMAIL).param("hash","{pbkdf2-v1}"+encoder.encode(PASSWORD)).update();
 }
 @AfterAll static void close()throws Exception{FB.close();GOOGLE.close();try(var files=Files.list(OUTBOX)){for(var p:files.toList())Files.delete(p);}Files.delete(OUTBOX);}
 String base(){return "http://127.0.0.1:"+env.getRequiredProperty("local.server.port");}
 class Browser {
  final HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();String cookie;
  HttpResponse<String> send(String method,String url,String body,String csrf)throws Exception{
   var q=HttpRequest.newBuilder(URI.create(url.startsWith("http")?url:base()+url)).method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));if(cookie!=null)q.header("Cookie",cookie);if(body!=null)q.header("Content-Type","application/json");if(csrf!=null)q.header("X-CSRF-TOKEN",csrf);
   var r=client.send(q.build(),HttpResponse.BodyHandlers.ofString());for(String c:r.headers().allValues("Set-Cookie"))if(c.startsWith("CINENEST_SESSION="))cookie=c.split(";",2)[0];return r;
  }
  HttpResponse<String> get(String path)throws Exception{return send("GET",path,null,null);}
  String callback(String provider)throws Exception{var first=get("/oauth2/authorization/"+provider);assertThat(first.statusCode()).isEqualTo(302);var target=first.headers().firstValue("Location").orElseThrow();var reply=get(target);assertThat(reply.statusCode()).isEqualTo(302);return reply.headers().firstValue("Location").orElseThrow().replace("http://127.0.0.1:"+FRONTEND+"/api/auth/"+provider+"/callback",base()+"/login/oauth2/code/"+provider);}
  int login()throws Exception{return get(callback("facebook")).statusCode();}
  HttpResponse<String> post(String path,Map<String,?> body)throws Exception{String csrf=JSONObjectUtils.parse(get("/api/auth/csrf").body()).get("token").toString();return send("POST",path,JSONObjectUtils.toJSONString(body),csrf);}
 }
 void noAdmission(){assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.http_session WHERE principal_name IS NOT NULL").query(Integer.class).single()).isZero();assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.account").query(Integer.class).single()).isEqualTo(2);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.external_identity").query(Integer.class).single()).isEqualTo(2);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.access_grant").query(Integer.class).single()).isZero();}
 @Test void authorizationUsesOnlyTheSelectedMetaWebContract()throws Exception {
  var response=new Browser().get("/oauth2/authorization/facebook");assertThat(response.statusCode()).isEqualTo(302);
  URI location=URI.create(response.headers().firstValue("Location").orElseThrow());var keys=java.util.Arrays.stream(location.getRawQuery().split("&")).map(x->x.split("=",2)[0]).toList();
  assertThat(keys).containsOnly("client_id","redirect_uri","response_type","scope","state");
 }
 @Test void associatedFacebookUsesExistingSessionAndAudit()throws Exception{
  var b=new Browser();String callback=b.callback("facebook");String before=b.cookie;assertThat(b.get(callback).statusCode()).isEqualTo(204);assertThat(b.cookie.equals(before)).isFalse();assertThat(b.get("/api/accounts/me").statusCode()).isEqualTo(200);assertThat(b.get("/api/accounts/"+B).statusCode()).isEqualTo(403);
  assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.security_audit WHERE event_kind='AUTHENTICATION' AND outcome='ALLOW'").query(Integer.class).single()).isEqualTo(1);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.http_session WHERE principal_name=:id").param("id",A.toString()).query(Integer.class).single()).isEqualTo(1);
  assertThat(b.send("POST","/api/session/logout","{}",null).statusCode()).isEqualTo(403);String csrf=JSONObjectUtils.parse(b.get("/api/session/csrf").body()).get("token").toString();assertThat(b.send("POST","/api/session/logout","{}",csrf).statusCode()).isEqualTo(204);assertThat(b.get("/api/accounts/me").statusCode()).isEqualTo(401);noAdmission();
 }
 @ParameterizedTest @ValueSource(strings={"WRONG_APP","INVALID","EXPIRED","TOKEN_EXPIRED","DATA_EXPIRED","BAD_TYPE","MISSING_ID","API_ERROR","MALFORMED","TIMEOUT","CANCEL"})
 void providerFailuresNeverAdmit(String mode)throws Exception{FB.mode=mode;assertThat(new Browser().login()).isEqualTo(403);noAdmission();}
 @Test void unassociatedAndGoogleNamespaceCannotBeStitched()throws Exception{FB.subject="customer-a";assertThat(new Browser().login()).isEqualTo(403);noAdmission();}
 @Test void invalidStateAndReplayAreDeniedBeforeTokenReuse()throws Exception{
  var b=new Browser();String callback=b.callback("facebook");assertThat(b.get(callback.replaceFirst("state=[^&]+","state=invalid")).statusCode()).isEqualTo(403);assertThat(FB.exchanges.get()).isZero();noAdmission();
  b=new Browser();callback=b.callback("facebook");assertThat(b.get(callback).statusCode()).isEqualTo(204);assertThat(b.get(callback).statusCode()).isEqualTo(403);assertThat(FB.exchanges.get()).isEqualTo(1);noAdmission();
 }
 @Test void suspendedAndAuditFailureStayClosed()throws Exception{
  jdbc.sql("UPDATE cinenest_identity.account SET eligibility='SUSPENDED' WHERE account_id=:id").param("id",A).update();assertThat(new Browser().login()).isEqualTo(403);noAdmission();
  jdbc.sql("UPDATE cinenest_identity.account SET eligibility='ELIGIBLE' WHERE account_id=:id").param("id",A).update();jdbc.sql("ALTER TABLE cinenest_identity.security_audit ADD CONSTRAINT facebook_audit_fault CHECK(false) NOT VALID").update();try{assertThat(new Browser().login()).isEqualTo(500);noAdmission();}finally{jdbc.sql("ALTER TABLE cinenest_identity.security_audit DROP CONSTRAINT facebook_audit_fault").update();}
 }
 @Test void manualAndGoogleCoexistWithFacebook()throws Exception{
  var b=new Browser();assertThat(b.login()).isEqualTo(204);assertThat(b.post("/api/auth/login",Map.of("email",EMAIL,"password",PASSWORD)).statusCode()).isEqualTo(204);assertThat(b.get(b.callback("google")).statusCode()).isEqualTo(204);assertThat(b.get("/api/accounts/me").body().contains(A.toString())).isTrue();assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.account").query(Integer.class).single()).isEqualTo(2);
 }
 @Test void providerCredentialsNeverReachSessionOrLogs(org.springframework.boot.test.system.CapturedOutput out)throws Exception{
  assertThat(new Browser().login()).isEqualTo(204);String serialized=jdbc.sql("SELECT coalesce(string_agg(encode(attribute_bytes,'escape'),''),'') FROM cinenest_identity.http_session_attributes").query(String.class).single();assertThat(serialized.contains(FB.secret)).isFalse();assertThat(serialized.contains(FB.knownSubject)).isFalse();assertThat(out.getAll().contains(FB.secret)).isFalse();assertThat(out.getAll().contains(FB.knownSubject)).isFalse();
 }
 @Test @EnabledIfEnvironmentVariable(named="CINENEST_FACEBOOK_E2E",matches="true")
 void realBrowserSyntheticFacebookJourney()throws Exception {
  for(String phase:List.of("disabled","associated","unassociated")){
   if(phase.equals("unassociated"))FB.subject=UUID.randomUUID().toString();
   Path run=Files.createTempDirectory("cinenest-facebook-e2e-");Files.writeString(run.resolve("config.json"),JSONObjectUtils.toJSONString(Map.of("backend",base(),"origin",FB.origin(),"googleIssuer",GOOGLE.issuer(),"port",FRONTEND,"phase",phase)));
   var process=new ProcessBuilder("node",Path.of("../frontend/tests/facebook-sso-e2e.mjs").toAbsolutePath().toString(),run.toString()).redirectErrorStream(true).redirectOutput(run.resolve("browser.log").toFile()).start();try{assertThat(process.waitFor(3,TimeUnit.MINUTES)).isTrue();assertThat(process.exitValue()).as("Sanitized Facebook browser qualification failed;inspect local harness log").isZero();noAdmission();for(String table:List.of("cinenest_payments.bank_payment_request","cinenest_payments.payment_allocation","cinenest_booking.booking","cinenest_access.access_credential"))assertThat(jdbc.sql("SELECT count(*) FROM "+table).query(Integer.class).single()).isZero();}finally{if(process.isAlive())process.destroy();}
  }
 }
}
