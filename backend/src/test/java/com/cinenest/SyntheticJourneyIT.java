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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"cinenest.booking.extension-reservations.enabled=true","cinenest.booking.extension-reservations.worker-enabled=false","cinenest.booking.extension-reservations.rate-count=5","cinenest.booking.extension-reservations.rate-seconds=60","cinenest.booking.extension-reservations.approval-reference=CN-EXTENSION-ADMISSION-BUDGET-APPROVAL-001 synthetic test","cinenest.booking.amendments.enabled=true","cinenest.bank.refunds.enabled=true","cinenest.bank.refunds.maximum-minor=50000","spring.datasource.hikari.maximum-pool-size=4","spring.datasource.hikari.minimum-idle=0","cinenest.bank.enabled=true","cinenest.bank.ledger.enabled=true","cinenest.bank.ledger.resolution-seconds=60","cinenest.bank.worker-enabled=false","cinenest.bank.policy-version=SYNTHETIC-BANK1","cinenest.bank.lead-seconds=60","cinenest.bank.request-seconds=3000","cinenest.bank.initiation-seconds=900","cinenest.bank.submission-seconds=1200","cinenest.bank.review-seconds=1800","cinenest.bank.hold-seconds=2400","cinenest.bank.escalation-seconds=2500","cinenest.bank.upload-seconds=60","cinenest.bank.retention-seconds=3600","cinenest.bank.cleanup-seconds=1","cinenest.bank.review-opens=00:00","cinenest.bank.review-closes=23:59:59","cinenest.verification.enabled=true","cinenest.verification.worker-enabled=false","cinenest.verification.policy-version=SYNTHETIC-V1","cinenest.verification.upload-seconds=60","cinenest.verification.retention-seconds=3600","cinenest.verification.decision-seconds=600","cinenest.verification.cleanup-seconds=1","cinenest.pricing.synthetic-quotes.enabled=true","cinenest.pricing.synthetic-quotes.tax-profile=SYNTHETIC_FIXED_MINOR_V1","cinenest.pricing.synthetic-quotes.tax-minor=7","cinenest.pricing.synthetic-quotes.approval-reference=SYNTHETIC test fixture only","cinenest.pricing.synthetic-quotes.rate-count=100","cinenest.pricing.synthetic-quotes.rate-seconds=60","cinenest.pricing.publication-worker.enabled=false","cinenest.booking.holds.enabled=true","cinenest.booking.holds.rate-count=100","cinenest.booking.holds.rate-seconds=60","cinenest.booking.holds.approval-reference=SYNTHETIC owner-approved inventory test"})
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
        Process browser=null,extensionChild=null;
        UUID expiringHold=null,expiringQuote=null;
        Map<String,Object> expiryBaseline=null,invalidHoldBaseline=null;
        UUID invalidHold=null,rejectedRequest=null;
        Map<String,Object> rejectionBaseline=null,uploadBaseline=null;
        UUID uploadRequest=null;
        Map<String,Object> calendarCounts=null;
 Map<String,String> extensionBaseline=null;
 Map<String,Long> extensionAuditCounts=null;
 Object extensionClock=null;
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
                        if(op.equals("extension-cross-booking-fixture")){
 assertThat(extensionBaseline).isNull();var owner=fixture.signedIn("customer-a");var hold=fixture.held(owner,fixture.request(owner,java.time.LocalDateTime.parse(fixture.local()).plusDays(14).toString()));UUID other=UUID.randomUUID();
 assertThat(fixture.write(owner,"POST","/api/holds/"+hold.get("holdId")+"/release",JSONObjectUtils.toJSONString(Map.of("expectedRevision",hold.get("revision"),"idempotencyKey",UUID.randomUUID().toString()))).statusCode()).isEqualTo(200);
 // Existing recoverySameOwnerOtherPersistedBooking test pattern: read subject only,not a second accepted purchase.
 fixture.jdbc.sql("INSERT INTO cinenest_booking.booking(booking_id,request_id,hold_id,owner_id,venue_id,state,synthetic) VALUES(:b,:request,:hold,:a,:v,'SIMULATED_CONFIRMED',true)").param("b",other).param("request",UUID.randomUUID()).param("hold",UUID.fromString(hold.get("holdId").toString())).param("a",BankIntegrationTest.A).param("v",BankIntegrationTest.VA).update();response.put("bookingId",other.toString());response.put("fixtureOnly",true);
 }else if(op.equals("extension-baseline")){
 extensionAuditCounts=bankAuditCounts(fixture);extensionBaseline=extensionInvariants(fixture);extensionBaseline.remove("cinenest_booking.inventory_audit");response.put("captured",true);
 }else if(op.equals("extension-inspect")){
 assertThat(extensionBaseline).isNotNull();var unchanged=extensionInvariants(fixture);unchanged.remove("cinenest_booking.inventory_audit");var priorAudit=extensionAuditCounts;response.put("newAuditActions",bankAuditCounts(fixture).entrySet().stream().filter(e->!e.getValue().equals(priorAudit.getOrDefault(e.getKey(),0L))).map(Map.Entry::getKey).toList());var expected=extensionBaseline;response.put("changedTables",unchanged.keySet().stream().filter(k->!unchanged.get(k).equals(expected.get(k))).toList());assertThat(unchanged).isEqualTo(extensionBaseline);
 response.put("originalFinancialAccessNotificationStateUnchanged",true);
 var rows=fixture.jdbc.sql("SELECT e.state,e.revision,b.blocking,b.session_start=e.original_until AND b.blocked_until=e.proposed_until AND b.buffer_minutes=0 AS exact_tail,e.expires_at>e.created_at AND e.expires_at<=e.created_at+INTERVAL '600 seconds' AS bounded_deadline FROM cinenest_booking.booking_extension_reservation e JOIN cinenest_booking.inventory_block b ON b.block_id=e.tail_block ORDER BY e.created_at").query().listOfRows();
 for(var row:rows){assertThat(row.get("exact_tail")).isEqualTo(true);assertThat(row.get("bounded_deadline")).isEqualTo(true);assertThat(row.get("blocking")).isEqualTo(row.get("state").equals("ACTIVE"));}
 response.put("reservations",rows);response.put("active",rows.stream().filter(r->r.get("state").equals("ACTIVE")).count());
 response.put("creates",fixture.jdbc.sql("SELECT count(*) FROM cinenest_booking.booking_extension_command WHERE action='CREATE'").query(Long.class).single());
 response.put("withdrawals",fixture.jdbc.sql("SELECT count(*) FROM cinenest_booking.booking_extension_command WHERE action='WITHDRAW'").query(Long.class).single());
 response.put("auditCreates",fixture.jdbc.sql("SELECT count(*) FROM cinenest_booking.booking_extension_audit WHERE action='CREATE'").query(Long.class).single());
 }else if(op.equals("extension-restart")){
 assertThat(extensionChild).isNull();extensionChild=fixture.restartedWorker("recovery-read-e2e");fixture.awaitRefundRestart(extensionChild,"recovery-read-e2e");response.put("backend",fixture.extensionChildOrigin("recovery-read-e2e"));
 }else if(op.equals("extension-stop-restart")){
 assertThat(extensionChild).isNotNull();fixture.stopWorker(extensionChild);extensionChild=null;
 }else if(op.equals("extension-suspend")){
 fixture.jdbc.sql("UPDATE cinenest_identity.account SET eligibility=:e,revision=revision+1 WHERE account_id=:a").param("e",Boolean.TRUE.equals(body.get("suspended"))?"SUSPENDED":"ELIGIBLE").param("a",BankIntegrationTest.A).update();
 }else if(op.equals("extension-expire")){
 Object service=context.getBean("extensionReservationService");extensionClock=org.springframework.test.util.ReflectionTestUtils.getField(service,"clock");
 var future=fixture.jdbc.sql("SELECT max(expires_at)+INTERVAL '1 second' FROM cinenest_booking.booking_extension_reservation").query(java.time.OffsetDateTime.class).single().toInstant();
 org.springframework.test.util.ReflectionTestUtils.setField(service,"clock",(java.util.function.Supplier<java.time.Instant>)()->future);
 response.put("clockAdvancedBeyondDeadline",true);
 }else if(op.equals("extension-reconcile")){
 fixture.extensionMaintenance();org.springframework.test.util.ReflectionTestUtils.setField(context.getBean("extensionReservationService"),"clock",extensionClock);extensionClock=null;
 response.put("authoritativeMaintenanceCompleted",true);
 }else if(op.equals("calendar-hold-read")){
                            String phase=body.get("phase").toString();
                            if(phase.equals("before")){assertThat(calendarCounts).isNull();calendarCounts=calendarCounts(fixture);assertThat(calendarCounts.values()).allMatch(v->v.equals(0L));response.putAll(calendarCounts);}
                            else if(phase.equals("selected")){assertThat(calendarCounts).isNotNull();assertThat(calendarCounts(fixture)).isEqualTo(calendarCounts);response.putAll(calendarCounts);response.put("unchanged",true);}
                            else if(phase.equals("held")||phase.equals("accepted")){
                                assertThat(calendarCounts).isNotNull();response.putAll(calendarHoldRead(fixture,body));
                                var counts=calendarCounts(fixture);assertThat(counts.get("holds")).isEqualTo(1L);assertThat(counts.get("blocks")).isEqualTo(1L);assertThat(counts.get("createCommands")).isEqualTo(1L);
                                assertThat(counts.get("requests")).isEqualTo(0L);assertThat(counts.get("bookings")).isEqualTo(0L);
                                long quotes=phase.equals("accepted")?1L:0L;assertThat(counts.get("quotes")).isEqualTo(quotes);assertThat(counts.get("acceptances")).isEqualTo(quotes);assertThat(counts.get("acceptCommands")).isEqualTo(quotes);
                                if(phase.equals("accepted")){
                                    assertThat(fixture.jdbc.sql("SELECT count(*) FROM cinenest_pricing.quotation q JOIN cinenest_pricing.quotation_revision r ON r.quote_id=q.quote_id AND r.revision=q.current_revision JOIN cinenest_pricing.quotation_acceptance a ON a.quote_id=q.quote_id AND a.revision=r.revision JOIN cinenest_booking.reservation_hold h ON h.hold_id=q.hold_id JOIN cinenest_booking.inventory_block b ON b.block_id=h.block_id WHERE q.quote_id=:q AND q.hold_id=:h AND q.owner_id=:owner AND q.venue_id=:venue AND q.room_id=:room AND a.actor_id=q.owner_id AND a.content_hash=r.content_hash AND r.hold_revision=h.revision AND (r.snapshot->>'sessionStart')::timestamptz=b.session_start AND (r.snapshot->>'sessionEnd')::timestamptz=b.session_end").param("q",UUID.fromString(body.get("quoteId").toString())).param("h",UUID.fromString(body.get("holdId").toString())).param("owner",BankIntegrationTest.A).param("venue",BankIntegrationTest.VA).param("room",BankIntegrationTest.RID).query(Long.class).single()).isEqualTo(1L);
                                    response.put("acceptedQuoteBindingVerified",true);
                                }
                                response.putAll(counts);
                            }else throw new IllegalArgumentException("Unknown calendar observation phase");
                        }else if(op.equals("review")){
                            fixture.work();fixture.reviewerGrants();
                            var r=fixture.json(fixture.signedIn("customer-a").get("/api/verification/requests/"+body.get("id")));
                            response=fixture.json(fixture.write(staff,"POST",fixture.reviewPath(r)+"/decisions",JSONObjectUtils.toJSONString(fixture.decision(r,body.get("verdict").toString()))));
                        }else if(op.equals("prepare-upload-fault")){
                            if(uploadRequest!=null)throw new IllegalArgumentException("One upload fault fixture only");
                            uploadRequest=UUID.fromString(body.get("id").toString());
                            assertThat(fixture.jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_payment_request WHERE request_id=:r AND owner_id=:a AND venue_id=:v").param("r",uploadRequest).param("a",BankIntegrationTest.A).param("v",BankIntegrationTest.VA).query(Long.class).single()).isEqualTo(1);
                            fixture.bankWork();uploadBaseline=financialState(fixture);response.put("prepared",true);
                        }else if(op.equals("inspect-upload-fault")||op.equals("inspect-upload-uncertain")){
                            assertThat(uploadBaseline).isNotNull();assertThat(UUID.fromString(body.get("id").toString())).isEqualTo(uploadRequest);
                            assertThat(financialState(fixture)).isEqualTo(uploadBaseline);
                            UUID evidence=UUID.fromString(body.get("evidence").toString());
                            response.put("state",fixture.jdbc.sql("SELECT state FROM cinenest_payments.bank_receipt_evidence WHERE evidence_id=:e AND request_id=:r").param("e",evidence).param("r",uploadRequest).query(String.class).single());
                            response.put("scanQueued",fixture.jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_work WHERE evidence_id=:e AND kind='SCAN'").param("e",evidence).query(Long.class).single());
                            response.put("completed",fixture.jdbc.sql("SELECT completed_at IS NOT NULL FROM cinenest_payments.bank_receipt_evidence WHERE evidence_id=:e AND request_id=:r").param("e",evidence).param("r",uploadRequest).query(Boolean.class).single());
                            response.put("allocationCount",fixture.jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_receipt_evidence WHERE request_id=:r").param("r",uploadRequest).query(Long.class).single());
                            response.put("protectedStateUnchanged",true);
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
                        }else if(op.equals("prepare-rejection-review")){
                            if(rejectedRequest!=null)throw new IllegalArgumentException("One rejection fixture only");
                            UUID id=UUID.fromString(body.get("id").toString());fixture.work();fixture.reviewerGrants();
                            assertThat(fixture.jdbc.sql("SELECT count(*) FROM cinenest_verification.verification_request WHERE request_id=:r AND owner_id=:a AND venue_id=:v AND state='REVIEW_REQUIRED' AND NOT superseded").param("r",id).param("a",BankIntegrationTest.B).param("v",BankIntegrationTest.VA).query(Long.class).single()).isEqualTo(1);
                            rejectedRequest=id;rejectionBaseline=rejectionState(fixture,id);response.put("state","REVIEW_REQUIRED");
                        }else if(op.equals("inspect-rejection")){
                            assertThat(rejectionBaseline).isNotNull();var actual=rejectionState(fixture,rejectedRequest);assertThat(actual.equals(rejectionBaseline)).as("Rejection preserved protected financial/snapshot/evidence state").isTrue();
                            assertThat(fixture.jdbc.sql("SELECT count(*) FROM cinenest_verification.verification_request WHERE request_id=:r AND owner_id=:a AND venue_id=:v AND state='REJECTED' AND NOT superseded").param("r",rejectedRequest).param("a",BankIntegrationTest.B).param("v",BankIntegrationTest.VA).query(Long.class).single()).isEqualTo(1);
                            assertThat(fixture.jdbc.sql("SELECT count(*) FROM cinenest_verification.verification_decision WHERE request_id=:r AND actor_id=:a AND verdict='REJECTED' AND reason_code='CORRECTION_REQUIRED'").param("r",rejectedRequest).param("a",BankIntegrationTest.STAFF).query(Long.class).single()).isEqualTo(1);
                            assertThat(fixture.jdbc.sql("SELECT count(*) FROM cinenest_verification.verification_decision WHERE request_id=:r").param("r",rejectedRequest).query(Long.class).single()).isEqualTo(1);
                            var quote=fixture.jdbc.sql("SELECT quote_id FROM cinenest_verification.verification_request WHERE request_id=:r").param("r",rejectedRequest).query(UUID.class).single();
                            assertThat(context.getBean(com.cinenest.verification.BankEligibility.class).observe(BankIntegrationTest.B,quote,BankIntegrationTest.VA)).isFalse();
                            response.put("state","REJECTED");response.put("rejectedDecisions",1);response.put("verifiedDecisions",0);response.put("bankEligible",false);response.put("bankRequests",actual.get("requests"));response.put("bookings",actual.get("bookings"));response.put("protectedStateUnchanged",true);
                        }else if(op.equals("subject")){
                            String subject=body.get("subject").toString();
                            if(!List.of("customer-a","customer-b","staff").contains(subject))throw new IllegalArgumentException();
                            BankIntegrationTest.IDP.subject=subject;
                        }else if(op.equals("expire-session")){
                            fixture.jdbc.sql("DELETE FROM cinenest_identity.http_session WHERE principal_name=:p").param("p",BankIntegrationTest.A.toString()).update();
                        }else throw new IllegalArgumentException("Unsupported test control");
                        response.put("controlOk",true);
                    }catch(Exception|AssertionError failure){response=Map.of("controlOk",false,"changedTables",response.getOrDefault("changedTables",List.of()),"newAuditActions",response.getOrDefault("newAuditActions",List.of()));}
                    Path tmp=run.resolve("reply.tmp");Files.writeString(tmp,JSONObjectUtils.toJSONString(response));Files.move(tmp,run.resolve("reply.json"),StandardCopyOption.ATOMIC_MOVE);
                }
                Thread.sleep(25);
            }
            assertThat(browser.waitFor(1,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            if(browser.exitValue()!=0){
                Path diagnostic=Path.of("../.cache/e2e-booking-diagnosis",run.getFileName().toString(),"observations.json");
                if(!Files.exists(diagnostic)){
                    String raw=Files.readString(run.resolve("private-browser.log"));
                    var locations=new ArrayList<String>();var matcher=java.util.regex.Pattern.compile("synthetic-e2e\\.mjs:(\\d+):(\\d+)").matcher(raw);while(matcher.find())locations.add(matcher.group(1)+":"+matcher.group(2));
                    Map<String,Object> safe=Map.of("browserExitedBeforeResult",true,"sourceLocations",locations.stream().distinct().limit(8).toList(),"errorClasses",List.of("TimeoutError","ReferenceError","TypeError","SyntaxError","AssertionError","TargetClosedError").stream().filter(raw::contains).toList(),"operations",List.of("page.waitForResponse","locator.click","locator.fill","locator.selectOption","context.close").stream().filter(raw::contains).toList());
                    Files.writeString(diagnostic,JSONObjectUtils.toJSONString(safe),StandardOpenOption.CREATE_NEW);Files.setPosixFilePermissions(diagnostic,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
                }
            }
            assertThat(browser.exitValue()).isZero();
        }finally{
            if(browser!=null&&browser.isAlive()){browser.destroy();if(!browser.waitFor(3,java.util.concurrent.TimeUnit.SECONDS)){browser.destroyForcibly();browser.waitFor();}}
            if(extensionChild!=null)fixture.stopWorker(extensionChild);
            if(extensionClock!=null)org.springframework.test.util.ReflectionTestUtils.setField(context.getBean("extensionReservationService"),"clock",extensionClock);
            BankIntegrationTest.IDP.close();
        }
    }
    private static Map<String,Long> bankAuditCounts(BankIntegrationTest f){var result=new TreeMap<String,Long>();for(var row:f.jdbc.sql("SELECT action,count(*) AS n FROM cinenest_payments.bank_audit GROUP BY action").query().listOfRows())result.put(row.get("action").toString(),((Number)row.get("n")).longValue());return result;}
    private static Map<String,String> extensionInvariants(BankIntegrationTest f){
        var result=f.extensionUnchangedSources();
        result.put("originalConfirmedInventory",f.jdbc.sql("SELECT md5(COALESCE(string_agg(to_jsonb(t)::text,',' ORDER BY to_jsonb(t)::text),'')) FROM cinenest_booking.inventory_block t WHERE kind='CONFIRMED'").query(String.class).single());
        // Existing booking detail performs this mandatory protected refund-list read.
        // Preserve every other financial audit event as well as all financial domain rows.
        result.put("cinenest_payments.bank_audit",f.jdbc.sql("SELECT md5(COALESCE(string_agg(to_jsonb(t)::text,',' ORDER BY to_jsonb(t)::text),'')) FROM cinenest_payments.bank_audit t WHERE action NOT IN ('BOOKING_READ','CUSTOMER_REFUNDS_READ','STATUS')").query(String.class).single());
        return result;
    }
    // Test-only SELECT observer: no production endpoint, state transition, audit write or private evidence.
    private static Map<String,Object> calendarCounts(BankIntegrationTest f){
        var result=new HashMap<String,Object>();
        for(var entry:Map.of("holds","cinenest_booking.reservation_hold","blocks","cinenest_booking.inventory_block","quotes","cinenest_pricing.quotation","acceptances","cinenest_pricing.quotation_acceptance","requests","cinenest_payments.bank_payment_request","bookings","cinenest_booking.booking").entrySet())result.put(entry.getKey(),f.jdbc.sql("SELECT count(*) FROM "+entry.getValue()).query(Long.class).single());
        result.put("createCommands",f.jdbc.sql("SELECT count(*) FROM cinenest_booking.inventory_command WHERE action='CREATE'").query(Long.class).single());
        result.put("acceptCommands",f.jdbc.sql("SELECT count(*) FROM cinenest_pricing.quotation_command WHERE action='ACCEPT'").query(Long.class).single());return result;
    }
    private static Map<String,Object> calendarHoldRead(BankIntegrationTest f,Map<String,Object> body){
        UUID hold=UUID.fromString(body.get("holdId").toString());
        var row=f.jdbc.sql("SELECT h.state,h.revision,h.guests,h.timezone,h.created_at,h.expires_at,b.session_start,b.session_end,b.buffer_minutes,b.blocked_until,b.blocking,p.ttl_seconds,h.configuration=:configuration AS configuration_matches,clock_timestamp() AS observed_at FROM cinenest_booking.reservation_hold h JOIN cinenest_booking.inventory_block b ON b.block_id=h.block_id JOIN cinenest_booking.hold_policy p ON p.policy_id=h.policy_id WHERE h.hold_id=:h AND h.owner_id=:owner AND b.venue_id=:venue AND b.room_id=:room")
            .param("h",hold).param("owner",BankIntegrationTest.A).param("venue",BankIntegrationTest.VA).param("room",BankIntegrationTest.RID).param("configuration",body.get("configuration").toString()).query().singleRow();
        assertThat(row.get("state")).isEqualTo("ACTIVE");assertThat(row.get("blocking")).isEqualTo(true);assertThat(row.get("configuration_matches")).isEqualTo(true);assertThat(((Number)row.get("revision")).longValue()).isZero();
        var result=new HashMap<String,Object>();
        for(String field:List.of("created_at","expires_at","session_start","session_end","blocked_until","observed_at")){Object value=row.get(field);java.time.Instant instant=value instanceof java.sql.Timestamp t?t.toInstant():((java.time.OffsetDateTime)value).toInstant();result.put(field,instant.toString());}
        assertThat(java.time.Instant.parse(result.get("expires_at").toString())).isAfter(java.time.Instant.parse(result.get("observed_at").toString()));
        assertThat(java.time.Duration.between(java.time.Instant.parse(result.get("created_at").toString()),java.time.Instant.parse(result.get("expires_at").toString())).getSeconds()).isEqualTo(((Number)row.get("ttl_seconds")).longValue());
        for(String field:List.of("state","guests","timezone","buffer_minutes","ttl_seconds"))result.put(field,row.get(field));
        result.put("scopeVerified",true);result.put("configurationMatches",true);return result;
    }
    private static Map<String,Object> financialState(BankIntegrationTest fixture){
        Map<String,Object> state=new HashMap<>();
        for(String table:List.of("cinenest_payments.incoming_payment_credit","cinenest_payments.bank_credit_verification","cinenest_payments.payment_allocation","cinenest_booking.booking"))
            state.put(table,fixture.jdbc.sql("SELECT md5(COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text,'[]')) FROM "+table+" t").query(String.class).single());
        return state;
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

    private static Map<String,Object> rejectionState(BankIntegrationTest fixture,UUID request){
        Map<String,Object> state=new HashMap<>();
        state.put("requests",fixture.jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_payment_request b JOIN cinenest_verification.verification_request v ON v.quote_id=b.quote_id WHERE v.request_id=:r").param("r",request).query(Long.class).single());
        state.put("bookings",fixture.jdbc.sql("SELECT count(*) FROM cinenest_booking.booking_revision b JOIN cinenest_verification.verification_request v ON v.quote_id=b.quote_id WHERE v.request_id=:r").param("r",request).query(Long.class).single());
        assertThat(state.values()).allMatch(v->v.equals(0L));
        state.put("scope",fixture.jdbc.sql("SELECT jsonb_build_array(owner_id,venue_id,quote_id,policy_id,generation)::text FROM cinenest_verification.verification_request WHERE request_id=:r").param("r",request).query(String.class).single());
        state.put("evidence",fixture.jdbc.sql("SELECT md5(string_agg(to_jsonb(e)::text,'' ORDER BY e.evidence_id)) FROM cinenest_verification.identity_evidence e WHERE request_id=:r").param("r",request).query(String.class).single());
        assertThat(fixture.jdbc.sql("SELECT count(*)>0 AND bool_and(state='READY') FROM cinenest_verification.identity_evidence WHERE request_id=:r").param("r",request).query(Boolean.class).single()).isTrue();
        for(String table:List.of("cinenest_payments.incoming_payment_credit","cinenest_payments.bank_credit_verification","cinenest_payments.payment_allocation","cinenest_booking.booking","cinenest_pricing.quotation_acceptance","cinenest_pricing.quotation_revision"))
            state.put(table,fixture.jdbc.sql("SELECT md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY to_jsonb(t)::text),'')) FROM "+table+" t").query(String.class).single());
        return state;
    }

}
