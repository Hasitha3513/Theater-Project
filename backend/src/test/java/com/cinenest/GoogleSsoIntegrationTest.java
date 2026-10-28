package com.cinenest;

import com.nimbusds.jose.util.JSONObjectUtils;
import com.cinenest.identity.internal.GoogleAuditCorrelationSupport;
import com.cinenest.identity.internal.GoogleAuditCorrelationSupport.*;
import java.net.*;import java.net.http.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;import org.junit.jupiter.params.ParameterizedTest;import org.junit.jupiter.params.provider.ValueSource;import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.core.env.Environment;import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.test.context.ActiveProfiles;import org.springframework.test.context.DynamicPropertyRegistry;import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"cinenest.identity.profile.enabled=true","spring.datasource.hikari.maximum-pool-size=2","spring.datasource.hikari.minimum-idle=0"})
@ActiveProfiles("local-synthetic")
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.context.annotation.Import(GoogleAuditCorrelationSupport.class)
class GoogleSsoIntegrationTest {
 static final GoogleContractIssuer IDP=new GoogleContractIssuer();static final UUID A=UUID.randomUUID(),B=UUID.randomUUID();static final Path OUTBOX;static final int FRONTEND;
 static {try{OUTBOX=Files.createTempDirectory("cinenest-google-mail-");try(var s=new java.net.ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){FRONTEND=s.getLocalPort();}}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 static String frontendOrigin(){return "true".equals(System.getenv("CINENEST_HTTPS_E2E"))?"https://localhost:4443":"http://127.0.0.1:"+FRONTEND;}
 @Autowired JdbcClient jdbc;@Autowired Environment env;@Autowired Recorder correlation;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
  r.add("cinenest.identity.enabled",()->"true");r.add("cinenest.identity.manual.enabled",()->"true");r.add("cinenest.identity.session-timeout-seconds",()->"120");r.add("cinenest.identity.manual.outbox",OUTBOX::toString);
  r.add("cinenest.identity.google.enabled",()->"true");r.add("cinenest.identity.google.mode",()->"CONTRACT_TEST");r.add("cinenest.identity.google.contract-issuer",IDP::issuer);r.add("cinenest.identity.google.client-id",()->IDP.clientId);r.add("cinenest.identity.google.client-secret",()->IDP.clientSecret);r.add("cinenest.identity.google.frontend-origin",()->frontendOrigin());
 }
 @BeforeEach void fixtures() throws Exception {correlation.reset();jdbc.sql("TRUNCATE cinenest_identity.account CASCADE").update();jdbc.sql("TRUNCATE cinenest_identity.http_session CASCADE").update();jdbc.sql("TRUNCATE cinenest_identity.security_audit").update();jdbc.sql("TRUNCATE cinenest_identity.manual_throttle").update();IDP.mode="VALID";IDP.subject="customer-a";IDP.codes.clear();
  for(UUID id:List.of(A,B))jdbc.sql("INSERT INTO cinenest_identity.account VALUES(:id,TRUE,'ELIGIBLE',0)").param("id",id).update();
  jdbc.sql("INSERT INTO cinenest_identity.external_identity VALUES(:issuer,'customer-a',:id)").param("issuer",IDP.issuer()).param("id",A).update();
  try(var files=Files.list(OUTBOX)){for(var p:files.toList())Files.delete(p);}
 }
 @AfterAll static void cleanup() throws Exception {IDP.close();try(var files=Files.list(OUTBOX)){for(var p:files.toList())Files.delete(p);}Files.delete(OUTBOX);}
 String base(){return "http://127.0.0.1:"+env.getRequiredProperty("local.server.port");}
 class Browser {
  final UUID attempt=UUID.randomUUID();
  final HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();String cookie;
  HttpResponse<String> get(String url)throws Exception{return send("GET",url,null,null);}
  HttpResponse<String> send(String method,String url,String body,String csrf)throws Exception {
   var request=HttpRequest.newBuilder(URI.create(url.startsWith("http")?url:base()+url)).method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));if(url.startsWith(base()) || !url.startsWith("http"))request.header(GoogleAuditCorrelationSupport.ATTEMPT_HEADER,attempt.toString());if(cookie!=null)request.header("Cookie",cookie);if(body!=null)request.header("Content-Type","application/json");if(csrf!=null)request.header("X-CSRF-TOKEN",csrf);
   var response=client.send(request.build(),HttpResponse.BodyHandlers.ofString());for(String c:response.headers().allValues("Set-Cookie"))if(c.startsWith("CINENEST_SESSION="))cookie=c.split(";",2)[0];return response;
  }
  String callback()throws Exception {var first=get("/oauth2/authorization/google");assertThat(first.statusCode()).isEqualTo(302);String authorization=first.headers().firstValue("Location").orElseThrow();assertThat(authorization).contains("code_challenge_method=S256").contains("nonce=").contains("state=");return get(authorization).headers().firstValue("Location").orElseThrow().replace(frontendOrigin()+"/api/auth/google/callback",base()+"/login/oauth2/code/google");}
  int login()throws Exception{return get(callback()).statusCode();}
  HttpResponse<String> post(String path,Map<String,?> value)throws Exception {var csrf=JSONObjectUtils.parse(get("/api/auth/csrf").body()).get("token").toString();return send("POST",path,JSONObjectUtils.toJSONString(value),csrf);}
 }
 @Test void associatedSubjectUsesExistingUuidRotatesSessionAndNeverStoresProviderTokens(org.springframework.boot.test.system.CapturedOutput output)throws Exception {
  var b=new Browser();String callback=b.callback();String before=b.cookie;assertThat(b.get(callback).statusCode()).isEqualTo(204);assertThat(b.cookie).isNotEqualTo(before);
  var own=b.get("/api/accounts/me");assertThat(own.body()).contains(A.toString()).doesNotContain(IDP.lastIdToken,IDP.lastAccessToken,IDP.clientSecret);
  assertThat(b.get("/api/accounts/"+B).statusCode()).isEqualTo(403);
  assertThat(output.getAll()).doesNotContain(IDP.lastIdToken,IDP.lastAccessToken,IDP.clientSecret);
  String serialized=jdbc.sql("SELECT coalesce(string_agg(encode(attribute_bytes,'escape'),''),'') FROM cinenest_identity.http_session_attributes").query(String.class).single();assertThat(serialized).doesNotContain(IDP.lastIdToken,IDP.lastAccessToken,IDP.clientSecret);
  assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.account").query(Integer.class).single()).isEqualTo(2);
 }
 @ParameterizedTest @ValueSource(strings={"NONCE","EXPIRED","AUDIENCE","ISSUER","SIGNATURE","CANCEL","TIMEOUT"})
 void badProviderOutcomesFailClosed(String mode)throws Exception {IDP.mode=mode;var b=new Browser();assertThat(b.login()).isEqualTo(403);assertThat(b.get("/api/accounts/me").statusCode()).isEqualTo(401);}
 @ParameterizedTest @ValueSource(strings={"", "wrong-verifier"})
 void missingOrInvalidPkceCannotExchangeCommittedAuthorizationCode(String verifier)throws Exception {
  var b=new Browser();String callback=b.callback();String code=java.util.Arrays.stream(URI.create(callback).getRawQuery().split("&")).filter(x->x.startsWith("code=")).findFirst().orElseThrow().substring(5);
  String body="grant_type=authorization_code&code="+code+"&redirect_uri="+java.net.URLEncoder.encode(frontendOrigin()+"/api/auth/google/callback",java.nio.charset.StandardCharsets.UTF_8)+(verifier.isEmpty()?"":"&code_verifier="+verifier);
  var request=HttpRequest.newBuilder(URI.create(IDP.issuer()+"/token")).header("Authorization","Basic "+Base64.getEncoder().encodeToString((IDP.clientId+":"+IDP.clientSecret).getBytes(java.nio.charset.StandardCharsets.UTF_8))).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build();
  assertThat(b.client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(400);assertThat(b.get("/api/accounts/me").statusCode()).isEqualTo(401);
 }
 @Test void invalidStateAndReplayedCallbackAreDenied()throws Exception {var b=new Browser();String callback=b.callback();assertThat(b.get(callback+"wrong").statusCode()).isEqualTo(403);b=new Browser();callback=b.callback();assertThat(b.get(callback).statusCode()).isEqualTo(204);assertThat(b.get(callback).statusCode()).isEqualTo(403);assertThat(b.get("/api/accounts/me").statusCode()).isEqualTo(401);}
 @ParameterizedTest @ValueSource(strings={"MISSING_EMAIL","UNVERIFIED_EMAIL"})
 void emailIsNotIdentityOrAdmissionAuthorityForAlreadyAssociatedSubject(String mode)throws Exception {IDP.mode=mode;assertThat(new Browser().login()).isEqualTo(204);}
 @Test void unknownSubjectEvenMatchingEmailCannotCreateOrLinkAnAccount()throws Exception {
  var encoder=new org.springframework.security.crypto.password.Pbkdf2PasswordEncoder("",16,600000,256);encoder.setAlgorithm(org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
  jdbc.sql("INSERT INTO cinenest_identity.manual_customer(account_id,email,password_hash,verification_digest,verification_expires_at,verified_at) VALUES(:id,'same-address@example.test',:hash,repeat('b',64),clock_timestamp()+INTERVAL '1 hour',clock_timestamp())").param("id",B).param("hash","{pbkdf2-v1}"+encoder.encode("Synthetic fixture phrase!")).update();
  IDP.subject="unknown";assertThat(new Browser().login()).isEqualTo(403);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.account").query(Integer.class).single()).isEqualTo(2);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.external_identity").query(Integer.class).single()).isEqualTo(1);}
 @Test void suspensionAndMandatoryAuditFailureDenySession()throws Exception {jdbc.sql("UPDATE cinenest_identity.account SET eligibility='SUSPENDED',revision=1 WHERE account_id=:id").param("id",A).update();assertThat(new Browser().login()).isEqualTo(403);
  jdbc.sql("UPDATE cinenest_identity.account SET eligibility='ELIGIBLE' WHERE account_id=:id").param("id",A).update();jdbc.sql("ALTER TABLE cinenest_identity.security_audit ADD CONSTRAINT google_audit_fault CHECK(false) NOT VALID").update();try{var b=new Browser();assertThat(b.login()).isEqualTo(500);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.http_session WHERE principal_name=:id").param("id",A.toString()).query(Integer.class).single()).isZero();}finally{jdbc.sql("ALTER TABLE cinenest_identity.security_audit DROP CONSTRAINT google_audit_fault").update();}}
 @Test void manualPasswordCredentialsAndAccountIdentitySurviveGoogleLogin()throws Exception {
  var b=new Browser();String email="manual@example.test",password="Local cinema testing 2026!";assertThat(b.post("/api/auth/registrations",Map.of("email",email,"password",password)).statusCode()).isEqualTo(202);
  String token;try(var paths=Files.list(OUTBOX)){token=Files.readAllLines(paths.findFirst().orElseThrow()).get(2);}assertThat(b.post("/api/auth/email-verifications",Map.of("token",token)).statusCode()).isEqualTo(204);
  UUID id=jdbc.sql("SELECT account_id FROM cinenest_identity.manual_customer WHERE email=:email").param("email",email).query(UUID.class).single();String hash=jdbc.sql("SELECT password_hash FROM cinenest_identity.manual_customer").query(String.class).single();
  // Trusted isolated fixture association; no runtime linking command exists.
  jdbc.sql("INSERT INTO cinenest_identity.external_identity VALUES(:issuer,'manual',:id)").param("issuer",IDP.issuer()).param("id",id).update();IDP.subject="manual";
  assertThat(b.login()).isEqualTo(204);assertThat(b.get("/api/accounts/me").body()).contains(id.toString());assertThat(b.post("/api/auth/login",Map.of("email",email,"password",password)).statusCode()).isEqualTo(204);assertThat(b.get("/api/accounts/me").body()).contains(id.toString());assertThat(jdbc.sql("SELECT password_hash FROM cinenest_identity.manual_customer").query(String.class).single()).isEqualTo(hash);
 }
 @Test @EnabledIfEnvironmentVariable(named="CINENEST_GOOGLE_E2E",matches="true")
 void realBrowserContractJourney()throws Exception {
  Path run=Files.createTempDirectory("cinenest-google-e2e-");Files.writeString(run.resolve("config.json"),JSONObjectUtils.toJSONString(Map.of("backend",base(),"issuer",IDP.issuer(),"port",FRONTEND,"account",A.toString())));
  Map<String,Long> before=new HashMap<>();for(String table:List.of("cinenest_payments.bank_payment_request","cinenest_payments.incoming_payment_credit","cinenest_payments.payment_allocation","cinenest_booking.booking","cinenest_access.access_credential","cinenest_iot.device"))before.put(table,jdbc.sql("SELECT count(*) FROM "+table).query(Long.class).single());
  var process=new ProcessBuilder("node",Path.of("../frontend/tests/google-sso-e2e.mjs").toAbsolutePath().toString(),run.toString()).redirectErrorStream(true).redirectOutput(run.resolve("browser.log").toFile()).start();try{assertThat(process.waitFor(2,TimeUnit.MINUTES)).isTrue();assertThat(process.exitValue()).as(Files.readString(run.resolve("browser.log"))).isZero();for(var e:before.entrySet())assertThat(jdbc.sql("SELECT count(*) FROM "+e.getKey()).query(Long.class).single()).isEqualTo(e.getValue());assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.account").query(Integer.class).single()).isEqualTo(2);assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.access_grant").query(Integer.class).single()).isZero();}finally{if(process.isAlive())process.destroy();}
 }

 // Correlation captures only test-generated opaque IDs, fixed enums, status and committed audit identity.
 Map<String,String> identitySnapshot() {
  Map<String,String> snapshot=new HashMap<>();
  for(String table:List.of("account","manual_customer","external_identity","access_grant"))
   snapshot.put(table,jdbc.sql("SELECT md5(coalesce(string_agg(row_to_json(t)::text,E'\\n' ORDER BY row_to_json(t)::text),'')) FROM cinenest_identity."+table+" t").query(String.class).single());
  return snapshot;
 }
 void assertNoAdmissionOrMutation(Map<String,String> before) {
  assertThat(identitySnapshot()).isEqualTo(before);
  assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.http_session WHERE principal_name IS NOT NULL").query(Integer.class).single()).isZero();
  for(String table:List.of("cinenest_payments.bank_payment_request","cinenest_payments.incoming_payment_credit","cinenest_payments.payment_allocation","cinenest_booking.booking","cinenest_access.access_credential","cinenest_iot.device"))
   assertThat(jdbc.sql("SELECT count(*) FROM "+table).query(Integer.class).single()).isZero();
 }
 void assertCorrelated(Request request,Outcome expected) {
  assertThat(request.route()).isEqualTo(Route.CALLBACK);
  assertThat(request.audits()).hasSize(1);
  Audit audit=request.audits().getFirst();assertThat(audit.kind()).isEqualTo(Kind.AUTHENTICATION);assertThat(audit.outcome()).isEqualTo(expected);assertThat(audit.committed()).isTrue();
  assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.security_audit WHERE event_id=:id AND event_kind='AUTHENTICATION' AND outcome=:outcome").param("id",audit.eventId()).param("outcome",expected.name()).query(Integer.class).single()).isEqualTo(1);
  assertThat(request.toString()).doesNotContain(IDP.lastIdToken,IDP.lastAccessToken,IDP.clientSecret,IDP.subject);
 }
 @Test void existingProviderSessionCanEditOnlyItsOwnProfile()throws Exception {
  var b=new Browser();assertThat(b.login()).isEqualTo(204);
  var initial=b.get("/api/accounts/me/profile");assertThat(initial.statusCode()).isEqualTo(200);
  var data=JSONObjectUtils.parse(initial.body());assertThat(data.get("customerId")).isEqualTo(A.toString());
  assertThat(data.get("linkedProviderTypes").toString()).contains("GOOGLE");
  String csrf=JSONObjectUtils.parse(b.get("/api/session/csrf").body()).get("token").toString();
  assertThat(b.send("PUT","/api/accounts/me/profile","{\"fullName\":\"Synthetic Profile\",\"phone\":null,\"expectedRevision\":0}",csrf).statusCode()).isEqualTo(200);
  assertThat(jdbc.sql("SELECT revision FROM cinenest_identity.customer_profile WHERE account_id=:id").param("id",A).query(Long.class).single()).isEqualTo(1);
  assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.customer_profile WHERE account_id=:id").param("id",B).query(Integer.class).single()).isZero();
 }
 @Test void correlatedSingleDeniedCallback()throws Exception {
  var before=identitySnapshot();IDP.subject="unassociated-correlation-fixture";var browser=new Browser();assertThat(browser.login()).isEqualTo(403);
  var requests=correlation.callbacks(browser.attempt);assertThat(requests).hasSize(1);assertCorrelated(requests.getFirst(),Outcome.DENY);assertThat(requests.getFirst().status()).isEqualTo(403);assertNoAdmissionOrMutation(before);
 }
 @Test void correlatedReplayHasSeparateRequestAndCommittedDenial()throws Exception {
  var before=identitySnapshot();IDP.subject="unassociated-correlation-fixture";var browser=new Browser();String callback=browser.callback();assertThat(browser.get(callback).statusCode()).isEqualTo(403);assertThat(browser.get(callback).statusCode()).isEqualTo(403);
  var requests=correlation.callbacks(browser.attempt);assertThat(requests).hasSize(2);for(var request:requests)assertCorrelated(request,Outcome.DENY);
  assertThat(requests).extracting(Request::requestId).doesNotHaveDuplicates();assertThat(requests).flatExtracting(Request::audits).extracting(Audit::eventId).doesNotHaveDuplicates();assertNoAdmissionOrMutation(before);
 }
 @Test void correlatedIndependentAttemptsRemainDistinct()throws Exception {
  var before=identitySnapshot();IDP.subject="unassociated-correlation-fixture";var first=new Browser();var second=new Browser();assertThat(first.login()).isEqualTo(403);assertThat(second.login()).isEqualTo(403);
  assertThat(first.attempt).isNotEqualTo(second.attempt);for(var browser:List.of(first,second)){var requests=correlation.callbacks(browser.attempt);assertThat(requests).hasSize(1);assertCorrelated(requests.getFirst(),Outcome.DENY);}assertNoAdmissionOrMutation(before);
 }
 @Test void correlatedAssociatedLoginPreservesExistingAccountAndJdbcSession()throws Exception {
  var before=identitySnapshot();var browser=new Browser();String callback=browser.callback();assertThat(browser.get(callback).statusCode()).isEqualTo(204);
  var requests=correlation.callbacks(browser.attempt);assertThat(requests).hasSize(1);assertCorrelated(requests.getFirst(),Outcome.ALLOW);assertThat(requests.getFirst().status()).isEqualTo(204);
  assertThat(jdbc.sql("SELECT count(*) FROM cinenest_identity.http_session WHERE principal_name=:account").param("account",A.toString()).query(Integer.class).single()).isEqualTo(1);assertThat(browser.get("/api/accounts/me").statusCode()).isEqualTo(200);assertThat(identitySnapshot()).isEqualTo(before);
 }
}
