package com.cinenest;

import com.nimbusds.jose.util.JSONObjectUtils;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in only: never selected by the existing Surefire *Test convention. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"cinenest.booking.amendments.enabled=true","cinenest.bank.refunds.enabled=true","cinenest.bank.refunds.maximum-minor=50000","spring.datasource.hikari.maximum-pool-size=4","spring.datasource.hikari.minimum-idle=0","cinenest.bank.enabled=true","cinenest.bank.ledger.enabled=true","cinenest.bank.ledger.resolution-seconds=60","cinenest.bank.worker-enabled=false","cinenest.bank.policy-version=SYNTHETIC-BANK1","cinenest.bank.lead-seconds=60","cinenest.bank.request-seconds=3000","cinenest.bank.initiation-seconds=900","cinenest.bank.submission-seconds=1200","cinenest.bank.review-seconds=1800","cinenest.bank.hold-seconds=2400","cinenest.bank.escalation-seconds=2500","cinenest.bank.upload-seconds=60","cinenest.bank.retention-seconds=3600","cinenest.bank.cleanup-seconds=1","cinenest.bank.review-opens=00:00","cinenest.bank.review-closes=23:59:59","cinenest.verification.enabled=true","cinenest.verification.worker-enabled=false","cinenest.verification.policy-version=SYNTHETIC-V1","cinenest.verification.upload-seconds=60","cinenest.verification.retention-seconds=3600","cinenest.verification.decision-seconds=600","cinenest.verification.cleanup-seconds=1","cinenest.pricing.synthetic-quotes.enabled=true","cinenest.pricing.synthetic-quotes.tax-profile=SYNTHETIC_FIXED_MINOR_V1","cinenest.pricing.synthetic-quotes.tax-minor=7","cinenest.pricing.synthetic-quotes.approval-reference=SYNTHETIC test fixture only","cinenest.pricing.synthetic-quotes.rate-count=100","cinenest.pricing.synthetic-quotes.rate-seconds=60","cinenest.pricing.publication-worker.enabled=false","cinenest.booking.holds.enabled=true","cinenest.booking.holds.rate-count=100","cinenest.booking.holds.rate-seconds=60","cinenest.booking.holds.approval-reference=SYNTHETIC owner-approved inventory test"})
@org.springframework.test.context.ActiveProfiles("synthetic-quotation")
@EnabledIfEnvironmentVariable(named="CINENEST_E2E_RUN", matches="/tmp/cinenest-e2e-[^/]+")
class SyntheticJourneyIT {
    @Autowired ConfigurableApplicationContext context;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        BankIntegrationTest.properties(p);
        for(String flag:List.of("cinenest.iot.enabled","cinenest.access.enabled","cinenest.notifications.enabled","cinenest.reporting.synthetic-bank.enabled","cinenest.rooms.turnover.enabled"))p.add(flag,()->"false");
    }
    @Test void realBrowserJourney() throws Exception {
        Path run=Path.of(System.getenv("CINENEST_E2E_RUN"));
        var fixture=new BankIntegrationTest();
        context.getAutowireCapableBeanFactory().autowireBean(fixture);
        fixture.fixtures();
        var staff=fixture.signedIn("staff");
        var pkg=fixture.setup(staff);
        fixture.grantTo(BankIntegrationTest.B,BankIntegrationTest.VA,"package.publish");
        fixture.published(fixture.signedIn("customer-b"),pkg,fixture.request(false));
        BankIntegrationTest.IDP.subject="customer-a";
        Files.write(run.resolve("identity.png"),BankIntegrationTest.IMAGE);
        Files.write(run.resolve("receipt.png"),BankIntegrationTest.RECEIPT);
        Map<String,Object> config=new HashMap<>();
        config.put("backend",fixture.base());config.put("room",BankIntegrationTest.RID.toString());
        config.put("ports",List.of(java.net.URI.create(fixture.base()).getPort(),java.net.URI.create(BankIntegrationTest.IDP.issuer()).getPort()));config.put("backendPid",ProcessHandle.current().pid());
        config.put("otherVenue",BankIntegrationTest.VB.toString());config.put("venue",BankIntegrationTest.VA.toString());config.put("local",fixture.local());
        Files.writeString(run.resolve("config.json"),JSONObjectUtils.toJSONString(config));
        Process browser=null;
        try {
            var builder=new ProcessBuilder("node",Path.of("../frontend/tests/synthetic-e2e.mjs").toAbsolutePath().toString());
            builder.redirectErrorStream(true);builder.redirectOutput(run.resolve("private-browser.log").toFile());
            browser=builder.start();
            long until=System.nanoTime()+java.time.Duration.ofMinutes(7).toNanos();
            while(browser.isAlive()&&System.nanoTime()<until){
                Path command=run.resolve("command.json");
                if(Files.exists(command)){
                    var body=JSONObjectUtils.parse(Files.readString(command));Files.delete(command);
                    Map<String,Object> response=new HashMap<>();
                    try {
                        String op=body.get("op").toString();
                        if(op.equals("review")){
                            fixture.work();fixture.reviewerGrants();
                            var r=fixture.json(fixture.signedIn("customer-a").get("/api/verification/requests/"+body.get("id")));
                            response=fixture.json(fixture.write(staff,"POST",fixture.reviewPath(r)+"/decisions",JSONObjectUtils.toJSONString(fixture.decision(r,body.get("verdict").toString()))));
                        }else if(op.equals("finance")){
                            fixture.financeGrants();
                            var customer=fixture.signedIn("customer-a");var allocator=fixture.signedIn("customer-b");
                            var r=fixture.json(customer.get("/api/bank/requests/"+body.get("id")));
                            var c=fixture.credit(staff,"SYNTHETIC-BANK-50000");r=fixture.match(allocator,r,c);
                            response=fixture.json(fixture.allocateCredit(allocator,r,c,Long.parseLong(r.get("amountMinor").toString())));
                            assertThat(response.get("syntheticBookingStatus")).isEqualTo("SIMULATED_CONFIRMED");
                            response.put("diagnosticBooking",fixture.jdbc.sql("SELECT state,synthetic FROM cinenest_booking.booking WHERE request_id=:id").param("id",UUID.fromString(body.get("id").toString())).query().singleRow());
                        }else if(op.equals("subject")){
                            String subject=body.get("subject").toString();
                            if(!List.of("customer-a","customer-b","staff").contains(subject))throw new IllegalArgumentException();
                            BankIntegrationTest.IDP.subject=subject;
                        }else if(op.equals("expire-session")){
                            fixture.jdbc.sql("DELETE FROM cinenest_identity.http_session WHERE principal_name=:p").param("p",BankIntegrationTest.A.toString()).update();
                        }else throw new IllegalArgumentException("Unsupported test control");
                        response.put("controlOk",true);
                    }catch(Exception|AssertionError failure){response=Map.of("controlOk",false);}
                    Path tmp=run.resolve("reply.tmp");Files.writeString(tmp,JSONObjectUtils.toJSONString(response));Files.move(tmp,run.resolve("reply.json"),StandardCopyOption.ATOMIC_MOVE);
                }
                Thread.sleep(25);
            }
            assertThat(browser.waitFor(1,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(browser.exitValue()).isZero();
        }finally{
            if(browser!=null&&browser.isAlive()){browser.destroy();if(!browser.waitFor(3,java.util.concurrent.TimeUnit.SECONDS)){browser.destroyForcibly();browser.waitFor();}}
            BankIntegrationTest.IDP.close();
        }
    }
}
