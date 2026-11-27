package com.cinenest;

import com.cinenest.identity.internal.CustomerCredentialConfiguration;
import com.cinenest.identity.internal.CustomerCredentialPostgresTest;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;

/** Genuine HTTPS backend + restricted PostgreSQL role. Delivery is explicitly test-owned, not email. */
@EnabledIfEnvironmentVariable(named="CINENEST_CUSTOMER_RUN",matches="/tmp/cinenest-customer-credential-[^/]+")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.hikari.maximum-pool-size=5","spring.datasource.hikari.minimum-idle=0","server.ssl.enabled=true"})
@ActiveProfiles("customer-qualification")
@Import(CustomerCredentialJourneyIT.ProtectedTestConfiguration.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerCredentialJourneyIT {
    static CustomerCredentialPostgresTest.Fixture fixture;
    @Autowired ConfigurableApplicationContext context;@Autowired Environment env;
    HttpServer control;ExecutorService controlExecutor;String controlKey;Path run;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p)throws Exception{
        CustomerCredentialPostgresTest.sqlAdmission();fixture=new CustomerCredentialPostgresTest.Fixture();
        p.add("spring.flyway.enabled",()->false);p.add("spring.sql.init.mode",()->"never");p.add("spring.datasource.url",()->fixture.url);p.add("spring.datasource.username",()->fixture.user);p.add("spring.datasource.password",()->fixture.password);
        p.add("cinenest.identity.enabled",()->true);p.add("cinenest.identity.customer.enabled",()->true);p.add("cinenest.identity.session-timeout-seconds",()->120);
        p.add("cinenest.identity.customer.policy-revision",()->"TEST_POLICY_V1");p.add("cinenest.identity.customer.privacy-revision",()->"TEST_PRIVACY_V1");p.add("cinenest.identity.customer.delivery-revision",()->"TEST_DELIVERY_V1");p.add("cinenest.identity.customer.address-policy",()->"ASCII_CASEFOLD_V1");
        p.add("cinenest.identity.customer.capability-seconds",()->900);p.add("cinenest.identity.customer.attempt-limit",()->100);p.add("cinenest.identity.customer.window-seconds",()->60);p.add("cinenest.identity.customer.hash-concurrency",()->2);p.add("cinenest.identity.customer.registration-seconds",()->3600);p.add("cinenest.identity.customer.resend-cooldown-seconds",()->1);p.add("cinenest.identity.customer.delivery-worker.enabled",()->false);
        p.add("server.ssl.key-store",()->Path.of(System.getenv("CINENEST_CUSTOMER_TLS_P12")).toUri().toString());p.add("server.ssl.key-store-password",()->System.getenv("CINENEST_CUSTOMER_TLS_PASSWORD"));p.add("server.ssl.key-store-type",()->"PKCS12");
    }
    @TestConfiguration(proxyBeanMethods=false) static class ProtectedTestConfiguration {
        @Bean CustomerCredentialConfiguration.KeySource testKeys(){return fixture::keys;}
        @Bean CustomerCredentialConfiguration.DeliveryGateway privateMailbox(){return new CustomerCredentialConfiguration.DeliveryGateway(){
            public boolean qualified(){return true;}public boolean qualificationOnly(){return true;}
            public CustomerCredentialConfiguration.DeliveryStatus send(CustomerCredentialConfiguration.DeliveryMessage message){fixture.mailbox().put(message.address()+":"+message.purpose(),message);return CustomerCredentialConfiguration.DeliveryStatus.ACCEPTED;}
        };}
    }
    SSLContext trust()throws Exception{var ca=CertificateFactory.getInstance("X.509").generateCertificate(Files.newInputStream(Path.of(System.getenv("CINENEST_CUSTOMER_CA"))));var store=KeyStore.getInstance(KeyStore.getDefaultType());store.load(null,null);store.setCertificateEntry("owned-test-ca",ca);var managers=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());managers.init(store);var ssl=SSLContext.getInstance("TLS");ssl.init(null,managers.getTrustManagers(),null);return ssl;}
    @BeforeAll void startControl()throws Exception{
        run=Path.of(System.getenv("CINENEST_CUSTOMER_RUN"));controlKey=System.getenv("CINENEST_CUSTOMER_CONTROL_KEY");if(controlKey==null||!controlKey.matches("[A-Za-z0-9_-]{32,128}"))throw new IllegalStateException("Protected test control credential required");
        control=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);controlExecutor=Executors.newFixedThreadPool(2);control.setExecutor(controlExecutor);
        control.createContext("/control",exchange->{
            int status=400;Map<String,Object> result=Map.of("ok",false);
            try{
                if(!exchange.getRequestMethod().equals("POST")||!MessageDigest.isEqual(controlKey.getBytes(StandardCharsets.US_ASCII),String.valueOf(exchange.getRequestHeaders().getFirst("X-Cinenest-Test-Control")).getBytes(StandardCharsets.US_ASCII)))status=403;
                else{
                    byte[] body=exchange.getRequestBody().readNBytes(2049);if(body.length>2048)throw new IllegalArgumentException();var command=JSONObjectUtils.parse(new String(body,StandardCharsets.UTF_8));String op=String.valueOf(command.get("op")),address=String.valueOf(command.get("email"));
                    if(!Set.of("credential-browser@qualification.invalid","duplicate-browser@qualification.invalid").contains(address))throw new IllegalArgumentException();
                    if(op.equals("mail")){
                        if(!command.keySet().equals(Set.of("op","email","purpose")))throw new IllegalArgumentException();String purpose=String.valueOf(command.get("purpose"));if(!Set.of("VERIFY_EMAIL","RECOVER_ACCOUNT").contains(purpose))throw new IllegalArgumentException();
                        ReflectionTestUtils.invokeMethod(context.getBean("customerCredentialDispatcher"),"dispatchPending",4);
                        var message=fixture.mailbox().get(address+":"+purpose);if(message==null)throw new IllegalStateException();result=Map.of("token",message.token());status=200;
                    }else if(op.equals("assert-state")){
                        if(!command.keySet().equals(Set.of("op","email","phase")))throw new IllegalArgumentException();String phase=String.valueOf(command.get("phase"));
                        var rows=fixture.jdbc.sql("SELECT c.state,c.credential_epoch,a.synthetic,a.eligibility FROM cinenest_identity.customer_credential c JOIN cinenest_identity.account a USING(account_id) WHERE c.address_lookup=:lookup").param("lookup",fixture.lookup(address)).query((r,n)->new Object[]{r.getString(1),r.getLong(2),r.getBoolean(3),r.getString(4)}).list();
                        if(rows.size()!=1||!Boolean.FALSE.equals(rows.getFirst()[2]))throw new IllegalStateException();var row=rows.getFirst();
                        boolean expected=switch(phase){case "REGISTERED"->row[0].equals("PENDING")&&row[3].equals("INELIGIBLE");case "VERIFIED"->row[0].equals("VERIFIED")&&row[3].equals("ELIGIBLE")&&((Long)row[1])==0;case "RESET"->row[0].equals("VERIFIED")&&((Long)row[1])==1;default->false;};
                        if(!expected)throw new IllegalStateException();result=Map.of("ok",true);status=200;
                    }
                }
            }catch(Exception failure){status=400;result=Map.of("ok",false);}
            byte[] payload=JSONObjectUtils.toJSONString(result).getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.getResponseHeaders().set("Cache-Control","no-store");exchange.sendResponseHeaders(status,payload.length);try(var out=exchange.getResponseBody()){out.write(payload);}exchange.close();
        });control.start();
        var config=Map.of("backendOrigin","https://127.0.0.1:"+env.getRequiredProperty("local.server.port"),"controlOrigin","http://127.0.0.1:"+control.getAddress().getPort(),"caPath",System.getenv("CINENEST_CUSTOMER_CA"),"tlsCertPath",System.getenv("CINENEST_CUSTOMER_TLS_CERT"),"tlsKeyPath",System.getenv("CINENEST_CUSTOMER_TLS_KEY"));
        Path target=run.resolve("config.json");Files.createFile(target,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));Files.writeString(target,JSONObjectUtils.toJSONString(config));
    }
    @Test void realHttpsDeniesAnonymousAccountAndMissingCsrf()throws Exception{
        var client=HttpClient.newBuilder().sslContext(trust()).connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();String base="https://127.0.0.1:"+env.getRequiredProperty("local.server.port");
        assertThat(client.send(HttpRequest.newBuilder(URI.create(base+"/api/accounts/me")).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
        assertThat(client.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/registrations")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
    }
    final class BrowserClient {
        final HttpClient client;
        BrowserClient()throws Exception{client=HttpClient.newBuilder().sslContext(trust()).cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();}
        HttpResponse<String> request(String method,String path,Map<String,?> body,String csrf)throws Exception{var builder=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+env.getRequiredProperty("local.server.port")+path)).timeout(Duration.ofSeconds(15));if(csrf!=null)builder.header("X-CSRF-TOKEN",csrf);if(body==null)builder.method(method,HttpRequest.BodyPublishers.noBody());else builder.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(JSONObjectUtils.toJSONString(body)));return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());}
        HttpResponse<String> get(String path)throws Exception{return request("GET",path,null,null);}
        HttpResponse<String> post(String path,Map<String,?> body)throws Exception{var csrf=get("/api/auth/csrf");assertThat(csrf.statusCode()).isEqualTo(200);return request("POST",path,body,String.valueOf(JSONObjectUtils.parse(csrf.body()).get("token")));}
    }
    UUID verifiedCustomer(String address,BrowserClient browser)throws Exception{
        String password="Synthetic HTTP qualification password 2026!";
        assertThat(browser.post("/api/auth/registrations",Map.of("commandId",UUID.randomUUID().toString(),"email",address,"password",password,"privacyRevision","TEST_PRIVACY_V1")).statusCode()).isEqualTo(202);
        ReflectionTestUtils.invokeMethod(context.getBean("customerCredentialDispatcher"),"dispatchPending",4);var message=fixture.mailbox().get(address+":VERIFY_EMAIL");assertThat(message!=null).isTrue();
        assertThat(browser.post("/api/auth/email-verifications",Map.of("commandId",UUID.randomUUID().toString(),"token",message.token(),"password",password)).statusCode()).isEqualTo(204);
        assertThat(browser.post("/api/auth/login",Map.of("commandId",UUID.randomUUID().toString(),"email",address,"password",password)).statusCode()).isEqualTo(204);
        return fixture.jdbc.sql("SELECT account_id FROM cinenest_identity.customer_credential WHERE address_lookup=:lookup AND abandoned_at IS NULL").param("lookup",fixture.lookup(address)).query(UUID.class).single();
    }
    @Test void actualCustomerHttpCannotReadOthersOrUseStaffFinancialAdmissionEvenWithErroneousGrants()throws Exception{
        var browser=new BrowserClient();UUID account=verifiedCustomer("authority-http@qualification.invalid",browser),venue=UUID.randomUUID();
        fixture.owner.sql("INSERT INTO cinenest_identity.account_scope(account_id,venue_id) VALUES(:account,:venue)").param("account",account).param("venue",venue).update();
        for(String permission:List.of("account.read","bank.synthetic.credit.verify","guest.check-in.synthetic.record"))fixture.owner.sql("INSERT INTO cinenest_identity.access_grant(grant_id,account_id,permission,scope_kind,venue_id,state,valid_from,valid_until,revision) VALUES(:grant,:account,:permission,'VENUE',:venue,'ACTIVE',clock_timestamp()-interval '1 second',clock_timestamp()+interval '5 minutes',0)").param("grant",UUID.randomUUID()).param("account",account).param("permission",permission).param("venue",venue).update();
        assertThat(browser.get("/api/accounts/me").statusCode()).isEqualTo(200);assertThat(browser.get("/api/accounts/"+UUID.randomUUID()).statusCode()).isEqualTo(403);assertThat(browser.get("/api/admin/venues/"+venue+"/accounts").statusCode()).isEqualTo(403);assertThat(browser.get("/api/admin/venues/"+venue+"/bank/credits").statusCode()).isEqualTo(403);assertThat(browser.post("/api/admin/venues/"+venue+"/bookings/"+UUID.randomUUID()+"/guest-check-in",Map.of()).statusCode()).isEqualTo(403);
    }
    @Test void actualCustomerHttpRejectsSuspendedAccountAndStaleCredentialEpoch()throws Exception{
        var suspended=new BrowserClient();UUID account=verifiedCustomer("suspended-http@qualification.invalid",suspended);fixture.owner.sql("UPDATE cinenest_identity.account SET eligibility='SUSPENDED',revision=revision+1 WHERE account_id=:id").param("id",account).update();assertThat(suspended.get("/api/accounts/me").statusCode()).isEqualTo(401);
        var original=new BrowserClient();verifiedCustomer("epoch-http@qualification.invalid",original);var reset=new BrowserClient();assertThat(reset.post("/api/auth/recovery-requests",Map.of("commandId",UUID.randomUUID().toString(),"email","epoch-http@qualification.invalid")).statusCode()).isEqualTo(202);ReflectionTestUtils.invokeMethod(context.getBean("customerCredentialDispatcher"),"dispatchPending",4);var message=fixture.mailbox().get("epoch-http@qualification.invalid:RECOVER_ACCOUNT");assertThat(message!=null).isTrue();assertThat(reset.post("/api/auth/recoveries",Map.of("commandId",UUID.randomUUID().toString(),"token",message.token(),"newPassword","Changed HTTP qualification password 2026!")).statusCode()).isEqualTo(204);assertThat(original.get("/api/accounts/me").statusCode()).isEqualTo(401);assertThat(reset.get("/api/accounts/me").statusCode()).isEqualTo(401);
    }
    @Test void actualHttpSessionRejectsChangedTrustedCurrentPolicy()throws Exception{
        var browser=new BrowserClient();verifiedCustomer("policy-http@qualification.invalid",browser);Object store=context.getBean("identityStore");Object previous=ReflectionTestUtils.getField(store,"customerPolicy");try{ReflectionTestUtils.setField(store,"customerPolicy","TEST_CHANGED_CURRENT_POLICY");assertThat(browser.get("/api/accounts/me").statusCode()).isEqualTo(401);}finally{ReflectionTestUtils.setField(store,"customerPolicy",previous);}
    }
    @Test void actualChromiumCustomerCredentialJourney()throws Exception{
        Path root=Path.of("..").toRealPath();var builder=new ProcessBuilder(System.getenv("CINENEST_NODE"),root.resolve("frontend/tests/customer-credential-e2e.mjs").toString());builder.directory(root.toFile());var inherited=new HashMap<>(builder.environment());builder.environment().clear();for(String key:List.of("PATH","HOME","PLAYWRIGHT_MODULE_PATH","NODE_EXTRA_CA_CERTS"))if(inherited.containsKey(key))builder.environment().put(key,inherited.get(key));builder.environment().put("CINENEST_CUSTOMER_RUN",run.toString());builder.environment().put("CINENEST_CUSTOMER_CONTROL_KEY",controlKey);
        Path log=run.resolve("browser-process.log");builder.redirectOutput(log.toFile()).redirectErrorStream(true);var process=builder.start();boolean done=process.waitFor(360,TimeUnit.SECONDS);if(!done){process.destroy();if(!process.waitFor(3,TimeUnit.SECONDS))process.destroyForcibly();}assertThat(done).as("Owned browser journey completed within bound; sanitized evidence is local only").isTrue();assertThat(process.exitValue()).as("Real backend browser qualification result; private output is not copied to assertions").isZero();
        var result=JSONObjectUtils.parse(Files.readString(run.resolve("browser-results.json")));assertThat(result.get("status")).isEqualTo("PASS");assertThat(result.get("interceptedRequests")).isEqualTo(0L);var groups=(List<?>)result.get("groups");assertThat(groups).hasSize(7);for(Object group:groups)assertThat(((Map<?,?>)group).get("status")).isEqualTo("PASS");
    }
    @Test @SuppressWarnings("unchecked") void retainedLegacySessionCannotEnterCustomerMode()throws Exception{
        UUID id=UUID.randomUUID();fixture.owner.sql("INSERT INTO cinenest_identity.account(account_id,synthetic,eligibility,revision) VALUES(:id,true,'ELIGIBLE',0)").param("id",id).update();
        String tables="cinenest_identity.manual_customer,cinenest_identity.manual_credential_version,cinenest_identity.manual_credential_recovery_audit,cinenest_identity.access_grant";
        fixture.owner.sql("GRANT SELECT ON "+tables+" TO "+fixture.user).update();
        org.springframework.session.SessionRepository<org.springframework.session.Session> raw=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(context.getBean(org.springframework.session.SessionRepository.class));
        String sessionId=null;
        try{
            assertThat((Long)ReflectionTestUtils.invokeMethod(context.getBean("identityStore"),"credentialEpoch",id,false)).isZero();
            var legacy=(org.springframework.security.core.Authentication)ReflectionTestUtils.invokeMethod(Class.forName("com.cinenest.identity.internal.CredentialEpochSessions"),"authentication",id,0L);
            var security=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();security.setAuthentication(legacy);
            var session=raw.createSession();session.setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,security);raw.save(session);sessionId=session.getId();
            var cookieRequest=new org.springframework.mock.web.MockHttpServletRequest();cookieRequest.setSecure(true);var cookieResponse=new org.springframework.mock.web.MockHttpServletResponse();
            context.getBean(org.springframework.session.web.http.CookieSerializer.class).writeCookieValue(new org.springframework.session.web.http.CookieSerializer.CookieValue(cookieRequest,cookieResponse,sessionId));
            String cookie=Objects.requireNonNull(cookieResponse.getHeader("Set-Cookie")).split(";",2)[0];
            var client=HttpClient.newBuilder().sslContext(trust()).followRedirects(HttpClient.Redirect.NEVER).build();
            var request=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+env.getRequiredProperty("local.server.port")+"/api/accounts/me")).header("Cookie",cookie).GET().build();
            assertThat(client.send(request,HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);assertThat(raw.findById(sessionId)).isNull();
        }finally{if(sessionId!=null)raw.deleteById(sessionId);fixture.owner.sql("REVOKE SELECT ON "+tables+" FROM "+fixture.user).update();}
    }
    @AfterAll void cleanup()throws Exception{if(control!=null)control.stop(0);if(controlExecutor!=null){controlExecutor.shutdownNow();controlExecutor.awaitTermination(3,TimeUnit.SECONDS);}/* @DirtiesContext owns Spring shutdown; closing it here breaks afterTestClass callbacks. */if(fixture!=null)fixture.close();}
}
