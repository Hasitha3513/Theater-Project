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
        UUID expiringHold=null,expiringQuote=null;
        Map<String,Object> expiryBaseline=null,invalidHoldBaseline=null;
        UUID invalidHold=null;
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
                        }else if(op.equals("prepare-expiring-quote")){
                            if(expiringHold!=null)throw new IllegalArgumentException("One expiry fixture only");
                            UUID id=UUID.fromString(body.get("holdId").toString());
                            int changed=fixture.jdbc.sql("UPDATE cinenest_booking.reservation_hold h SET expires_at=clock_timestamp()+INTERVAL '8 seconds' WHERE hold_id=:h AND owner_id=:a AND EXISTS(SELECT 1 FROM cinenest_booking.inventory_block b WHERE b.block_id=h.block_id AND b.venue_id=:v) AND state='ACTIVE' AND expires_at>clock_timestamp() AND NOT EXISTS(SELECT 1 FROM cinenest_pricing.quotation q WHERE q.hold_id=h.hold_id)").param("h",id).param("a",BankIntegrationTest.A).param("v",BankIntegrationTest.VA).update();
                            assertThat(changed).isEqualTo(1);expiringHold=id;response.put("prepared",true);
                        }else if(op.equals("await-quote-expiry")){
                            UUID id=UUID.fromString(body.get("quoteId").toString());
                            assertThat(expiringHold).isNotNull();
                            assertThat(fixture.jdbc.sql("SELECT count(*) FROM cinenest_pricing.quotation q JOIN cinenest_pricing.quotation_revision r ON r.quote_id=q.quote_id AND r.revision=q.current_revision JOIN cinenest_booking.reservation_hold h ON h.hold_id=q.hold_id WHERE q.quote_id=:q AND q.owner_id=:a AND q.hold_id=:h AND r.expires_at=h.expires_at AND r.expires_at>clock_timestamp()").param("q",id).param("a",BankIntegrationTest.A).param("h",expiringHold).query(Long.class).single()).isEqualTo(1);
                            expiringQuote=id;expiryBaseline=expiryState(fixture,id,expiringHold);
                            long deadline=System.nanoTime()+java.time.Duration.ofSeconds(12).toNanos();
                            while(!quoteExpired(fixture,id)&&System.nanoTime()<deadline)Thread.sleep(25);
                            assertThat(quoteExpired(fixture,id)).isTrue();response.put("serverExpired",true);
                        }else if(op.equals("inspect-expired-quote")){
                            assertThat(expiryBaseline).isNotNull();assertThat(quoteExpired(fixture,expiringQuote)).isTrue();
                            var actual=expiryState(fixture,expiringQuote,expiringHold);assertThat(actual.equals(expiryBaseline)).as("Expired quote snapshot and financial state unchanged").isTrue();
                            response.put("serverExpired",true);response.put("snapshotAndFinancialStateUnchanged",true);
                            response.put("quoteAcceptances",actual.get("acceptances"));response.put("quoteAcceptCommands",actual.get("commands"));response.put("quoteBankRequests",actual.get("requests"));response.put("holdBookings",actual.get("bookings"));
                        }else if(op.equals("prepare-invalid-hold")){
                            if(invalidHold!=null)throw new IllegalArgumentException("One invalid hold fixture only");
                            UUID id=UUID.fromString(body.get("holdId").toString());
                            int changed=fixture.jdbc.sql("UPDATE cinenest_booking.reservation_hold h SET created_at=clock_timestamp()-INTERVAL '20 minutes',expires_at=clock_timestamp()-INTERVAL '1 minute' WHERE hold_id=:h AND owner_id=:a AND state='ACTIVE' AND expires_at>clock_timestamp() AND EXISTS(SELECT 1 FROM cinenest_booking.inventory_block b WHERE b.block_id=h.block_id AND b.venue_id=:v AND b.room_id=:r) AND NOT EXISTS(SELECT 1 FROM cinenest_pricing.quotation q WHERE q.hold_id=h.hold_id)").param("h",id).param("a",BankIntegrationTest.B).param("v",BankIntegrationTest.VA).param("r",BankIntegrationTest.RID).update();
                            assertThat(changed).isEqualTo(1);invalidHold=id;invalidHoldBaseline=invalidHoldState(fixture,id);response.put("serverExpired",true);
                        }else if(op.equals("inspect-invalid-hold")){
                            assertThat(invalidHoldBaseline).isNotNull();var actual=invalidHoldState(fixture,invalidHold);
                            assertThat(actual.equals(invalidHoldBaseline)).as("Invalid hold left protected state unchanged").isTrue();
                            assertThat(fixture.jdbc.sql("SELECT h.state='EXPIRED' AND h.expires_at<=clock_timestamp() AND NOT b.blocking AND h.owner_id=:a AND b.venue_id=:v AND b.room_id=:r FROM cinenest_booking.reservation_hold h JOIN cinenest_booking.inventory_block b ON b.block_id=h.block_id WHERE h.hold_id=:h").param("h",invalidHold).param("a",BankIntegrationTest.B).param("v",BankIntegrationTest.VA).param("r",BankIntegrationTest.RID).query(Boolean.class).single()).isTrue();
                            response.put("serverExpired",true);response.put("holdState","EXPIRED");response.put("scopeAndFinancialStateUnchanged",true);
                            for(String key:List.of("quotes","acceptances","requests","bookings"))response.put(key,actual.get(key));
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
    private static boolean quoteExpired(BankIntegrationTest fixture,UUID quote){
        return fixture.jdbc.sql("SELECT expires_at<=clock_timestamp() FROM cinenest_pricing.quotation_revision WHERE quote_id=:q AND revision=1").param("q",quote).query(Boolean.class).single();
    }
    private static Map<String,Object> expiryState(BankIntegrationTest fixture,UUID quote,UUID hold){
        Map<String,Object> state=new HashMap<>();
        state.put("acceptances",fixture.jdbc.sql("SELECT count(*) FROM cinenest_pricing.quotation_acceptance WHERE quote_id=:q").param("q",quote).query(Long.class).single());
        state.put("commands",fixture.jdbc.sql("SELECT count(*) FROM cinenest_pricing.quotation_command WHERE quote_id=:q AND action='ACCEPT'").param("q",quote).query(Long.class).single());
        state.put("requests",fixture.jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_payment_request WHERE quote_id=:q").param("q",quote).query(Long.class).single());
        state.put("bookings",fixture.jdbc.sql("SELECT count(*) FROM cinenest_booking.booking WHERE hold_id=:h").param("h",hold).query(Long.class).single());
        assertThat(state.values()).allMatch(v->v.equals(0L));
        state.put("revision",fixture.jdbc.sql("SELECT to_jsonb(r)::text FROM cinenest_pricing.quotation_revision r WHERE quote_id=:q AND revision=1").param("q",quote).query(String.class).single());
        // Test-only equality fingerprints stay in memory; never put ledger rows in evidence/logs.
        for(String table:List.of("cinenest_payments.incoming_payment_credit","cinenest_payments.bank_credit_verification","cinenest_payments.payment_allocation","cinenest_booking.booking"))
            state.put(table,fixture.jdbc.sql("SELECT md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY to_jsonb(t)::text),'')) FROM "+table+" t").query(String.class).single());
        return state;
    }

    private static Map<String,Object> invalidHoldState(BankIntegrationTest fixture,UUID hold){
        Map<String,Object> state=new HashMap<>();
        state.put("quotes",fixture.jdbc.sql("SELECT count(*) FROM cinenest_pricing.quotation WHERE hold_id=:h").param("h",hold).query(Long.class).single());
        state.put("acceptances",fixture.jdbc.sql("SELECT count(*) FROM cinenest_pricing.quotation_acceptance a JOIN cinenest_pricing.quotation q ON q.quote_id=a.quote_id WHERE q.hold_id=:h").param("h",hold).query(Long.class).single());
        state.put("requests",fixture.jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_payment_request WHERE hold_id=:h").param("h",hold).query(Long.class).single());
        state.put("bookings",fixture.jdbc.sql("SELECT count(*) FROM cinenest_booking.booking WHERE hold_id=:h").param("h",hold).query(Long.class).single());
        assertThat(state.values()).allMatch(v->v.equals(0L));
        state.put("scope",fixture.jdbc.sql("SELECT jsonb_build_array(h.owner_id,h.block_id,h.configuration,h.guests,h.created_at,h.expires_at,b.venue_id,b.room_id,b.session_start,b.session_end,b.blocked_until)::text FROM cinenest_booking.reservation_hold h JOIN cinenest_booking.inventory_block b ON b.block_id=h.block_id WHERE h.hold_id=:h").param("h",hold).query(String.class).single());
        for(String table:List.of("cinenest_payments.incoming_payment_credit","cinenest_payments.bank_credit_verification","cinenest_payments.payment_allocation","cinenest_booking.booking","cinenest_pricing.quotation_acceptance","cinenest_pricing.quotation_revision"))
            state.put(table,fixture.jdbc.sql("SELECT md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY to_jsonb(t)::text),'')) FROM "+table+" t").query(String.class).single());
        return state;
    }

}
