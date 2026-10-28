package com.cinenest.identity.internal;

import java.net.*;import java.net.http.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes={com.cinenest.CineNestApplication.class},webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.main.allow-bean-definition-overriding=true","spring.datasource.hikari.maximum-pool-size=2","spring.datasource.hikari.minimum-idle=0","cinenest.identity.enabled=true","cinenest.identity.manual.enabled=true","cinenest.identity.session-timeout-seconds=120","cinenest.identity.facebook.enabled=true","cinenest.identity.facebook.mode=LIVE_QUALIFICATION"})
@ActiveProfiles("local-synthetic")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class FacebookLiveBoundaryIntegrationTest {
 static final Path OUTBOX;static final Path SECRET;
 static{try{OUTBOX=Files.createTempDirectory("cinenest-facebook-live-contract-");SECRET=OUTBOX.resolve("credential");Files.writeString(SECRET,UUID.randomUUID().toString());Files.setPosixFilePermissions(SECRET,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("cinenest.identity.manual.outbox",OUTBOX::toString);r.add("cinenest.identity.facebook.client-id",()->"123456789");r.add("cinenest.identity.facebook.client-secret-file",SECRET::toString);r.add("cinenest.identity.facebook.frontend-origin",()->"http://127.0.0.1:42137");}
 @Autowired Environment env;@Autowired JdbcClient jdbc;
 String base(){return "http://127.0.0.1:"+env.getRequiredProperty("local.server.port");}
 @Test void actualHttpLiveBoundaryDefaultsDeniedAndNeverCreatesASession()throws Exception{
  var client=HttpClient.newHttpClient();
  var status=client.send(HttpRequest.newBuilder(URI.create(base()+"/api/auth/facebook/status")).build(),HttpResponse.BodyHandlers.ofString());assertThat(status.statusCode()).isEqualTo(200);assertThat(status.body()).contains("LIVE_QUALIFICATION","\"executionEnabled\":false","\"liveProviderQualified\":false").doesNotContain(Files.readString(SECRET));
  for(String path:List.of("/oauth2/authorization/facebook","/login/oauth2/code/facebook?code=fixture&state=fixture")){
   var response=client.send(HttpRequest.newBuilder(URI.create(base()+path)).build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(503);assertThat(response.headers().firstValue("location")).isEmpty();assertThat(response.body()).isEmpty();
  }
  assertThat(client.send(HttpRequest.newBuilder(URI.create(base()+"/api/accounts/me")).build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
 }
 @Test @EnabledIfEnvironmentVariable(named="CINENEST_FACEBOOK_E2E",matches="true")
 void realBrowserShowsConfiguredButBlockedAdapterWithNoDomainMutation()throws Exception{
  Map<String,Long> before=new HashMap<>();for(String table:List.of("cinenest_identity.account","cinenest_identity.external_identity","cinenest_identity.access_grant","cinenest_payments.bank_payment_request","cinenest_payments.incoming_payment_credit","cinenest_payments.payment_allocation","cinenest_booking.booking","cinenest_access.access_credential","cinenest_iot.device"))before.put(table,jdbc.sql("SELECT count(*) FROM "+table).query(Long.class).single());
  Path run=Files.createTempDirectory("cinenest-facebook-live-boundary-");Files.writeString(run.resolve("config.json"),com.nimbusds.jose.util.JSONObjectUtils.toJSONString(Map.of("backend",base())));
  var process=new ProcessBuilder("node",Path.of("../frontend/tests/facebook-live-boundary-e2e.mjs").toAbsolutePath().toString(),run.toString()).redirectErrorStream(true).redirectOutput(run.resolve("browser.log").toFile()).start();try{assertThat(process.waitFor(2,TimeUnit.MINUTES)).isTrue();assertThat(process.exitValue()).as(Files.readString(run.resolve("browser.log"))).isZero();for(var e:before.entrySet())assertThat(jdbc.sql("SELECT count(*) FROM "+e.getKey()).query(Long.class).single()).isEqualTo(e.getValue());}finally{if(process.isAlive())process.destroy();}
 }
 @AfterAll static void cleanup()throws Exception{try(var files=Files.list(OUTBOX)){for(var p:files.toList())Files.delete(p);}Files.delete(OUTBOX);}
}
