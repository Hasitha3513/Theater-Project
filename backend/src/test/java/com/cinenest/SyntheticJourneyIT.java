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
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.cinenest.booking.SyntheticGuestAdmission operationalEligibility;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean(name="syntheticCreditSource")
    Object demoCreditSource;
    private Map<String,Object> demoConfiguration;
    private static boolean demoJourney(){return System.getenv("CINENEST_DEMO_FIXTURE")!=null;}
    private static UUID demoActor(String role){return switch(role){case "customer"->BankIntegrationTest.A;case "operations"->BankIntegrationTest.STAFF;case "booking-inventory"->BankIntegrationTest.B;default->UUID.nameUUIDFromBytes(("SYNTHETIC_DEMO_"+role).getBytes(java.nio.charset.StandardCharsets.UTF_8));};}
    private static String demoSubject(String role){return "synthetic-demo-"+role;}
    private static UUID operationalCleaner(){return demoJourney()?demoActor("cleaner"):BankIntegrationTest.STAFF;}
    private static UUID operationalReviewer(){return demoJourney()?demoActor("independent-turnover-reviewer"):BankIntegrationTest.B;}
    private Map<String,Object> prepareDemo(BankIntegrationTest f)throws Exception {
        Path input=Path.of(System.getenv("CINENEST_DEMO_FIXTURE"));
        assertThat(input.getParent()).isEqualTo(Path.of(System.getenv("CINENEST_E2E_RUN")));
        Map<String,Object> data=JSONObjectUtils.parse(Files.readString(input));
        assertThat(data.get("classification")).isEqualTo("SYNTHETIC_TEST_ONLY");
        var venue=(Map<?,?>)data.get("venue");var room=(Map<?,?>)data.get("room");var pack=(Map<?,?>)data.get("package");
        assertThat(venue.get("label")).isEqualTo("CineNest Main Branch (DEMO)");assertThat(venue.get("timezone")).isEqualTo("Asia/Colombo");
        assertThat(((Number)room.get("plannedCapacity")).intValue()).isEqualTo(5);assertThat(((Number)room.get("cleaningBufferMinutes")).intValue()).isEqualTo(15);
        assertThat(((Number)pack.get("basePriceMinor")).longValue()).isEqualTo(250000);assertThat(((Number)pack.get("fifthGuestPriceMinor")).longValue()).isEqualTo(80000);assertThat(((Number)pack.get("additionalHourPriceMinor")).longValue()).isEqualTo(100000);
        assertThat(((Number)pack.get("minimumMinutes")).intValue()).isEqualTo(180);
        var roles=List.of("customer","operations","booking-inventory","bank-verifier","bank-allocator","admission","cleaner","independent-turnover-reviewer");
        assertThat(((List<?>)data.get("principals")).stream().map(x->((Map<?,?>)x).get("role").toString()).toList()).containsExactlyInAnyOrderElementsOf(roles);
        f.jdbc.sql("DELETE FROM cinenest_identity.external_identity").update();f.jdbc.sql("DELETE FROM cinenest_identity.account_scope").update();
        var bindings=new LinkedHashMap<String,Object>();
        for(String role:roles){UUID id=demoActor(role);f.jdbc.sql("INSERT INTO cinenest_identity.account VALUES(:id,TRUE,'ELIGIBLE',0) ON CONFLICT DO NOTHING").param("id",id).update();f.jdbc.sql("INSERT INTO cinenest_identity.external_identity VALUES(:issuer,:subject,:id)").param("issuer",BankIntegrationTest.IDP.issuer()).param("subject",demoSubject(role)).param("id",id).update();f.scope(id,BankIntegrationTest.VA);bindings.put(role,Map.of("subject",demoSubject(role),"id",id.toString(),"classification","SYNTHETIC_TEST_ONLY"));}
        assertThat(f.jdbc.sql("SELECT count(*) FROM cinenest_identity.account").query(Integer.class).single()).isEqualTo(8);
        f.jdbc.sql("UPDATE cinenest_rooms.venue SET display_name='CineNest Main Branch (DEMO)',cleaning_buffer_minutes=15 WHERE venue_id=:v").param("v",BankIntegrationTest.VA).update();
        f.jdbc.sql("UPDATE cinenest_rooms.room SET name='Private Cinema Room 01',capacity=5 WHERE room_id=:r").param("r",BankIntegrationTest.RID).update();
        var permissions=new LinkedHashMap<String,List<String>>();
        permissions.put("operations",List.of("package.edit","booking.synthetic.read","guest.synthetic.read","guest.admission.synthetic.read","booking.session.synthetic.read","booking.session.synthetic.start","booking.session.synthetic.checkout","operations.synthetic.read","operations.synthetic.record","operations.synthetic.assign","operations.synthetic.handoff.retry"));
        permissions.put("booking-inventory",List.of("package.publish","inventory.read","booking.synthetic.read"));
        permissions.put("admission",List.of("verification.review","verification.evidence.read","booking.synthetic.read","guest.synthetic.read","guest.admission.synthetic.read","guest.check-in.synthetic.record"));
        permissions.put("bank-verifier",List.of("bank.claim.review","bank.evidence.read","bank.synthetic.credit.verify","bank.synthetic.credit.read"));
        permissions.put("bank-allocator",List.of("bank.synthetic.credit.read","bank.synthetic.allocate","bank.synthetic.resolve"));
        permissions.put("cleaner",List.of("operations.synthetic.read","operations.synthetic.record"));
        permissions.put("independent-turnover-reviewer",List.of("operations.synthetic.read","operations.synthetic.review"));
        for(var entry:permissions.entrySet())for(String permission:entry.getValue())f.grantTo(demoActor(entry.getKey()),BankIntegrationTest.VA,permission);
        // Controlled test-only statement fact. Every production policy/authority/ledger guard remains real.
        var method=demoCreditSource.getClass().getDeclaredMethod("fixture",String.class);method.setAccessible(true);
        org.mockito.Mockito.doAnswer(invocation->{
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(demoCreditSource,"configured");
            Class<?> fact=Class.forName("com.cinenest.payments.internal.SyntheticCreditSource$Fact");var ctor=fact.getDeclaredConstructors()[0];ctor.setAccessible(true);
            String key="SYNTHETIC-DEMO-BANK-330000";String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((key+":OWNER_APPROVED_DEMO_FIXTURE").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return ctor.newInstance(key,"SYNTHETIC-DEMO-TEST-ACCOUNT",key,"SYNTHETIC_BANK",330000L,java.time.Instant.parse(f.environment.getRequiredProperty("cinenest.bank.ledger.fixture-credited-at")),BankIntegrationTest.VA,hash);
        }).when(demoCreditSource);
        method.invoke(demoCreditSource,"SYNTHETIC-DEMO-BANK-330000");
        return Map.of("classification","SYNTHETIC_TEST_ONLY","principals",bindings,"fixtureLoaded",true,"bankStatementSource","EXPLICIT_TEST_ONLY_STATEMENT_FACT_NOT_REAL_BANK_CREDIT","configuredValues",data);
    }
    private static boolean operationalJourney(){return "customer-turnover".equals(System.getenv("CINENEST_E2E_MODE"));}

    private static boolean cancellationJourney() {
        return "cancellation".equals(System.getenv("CINENEST_E2E_MODE"));
    }

    private static boolean fundingJourney() {
        return "extension-funding".equals(System.getenv("CINENEST_E2E_MODE"));
    }

    private static boolean twoJourneys() {
        return "reschedule-refund".equals(System.getenv("CINENEST_E2E_MODE"));
    }

    private static boolean packageBookingJourney() {
        return "package-booking".equals(System.getenv("CINENEST_E2E_MODE"));
    }

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        BankIntegrationTest.properties(p);
        if ("supervised-h1".equals(System.getenv("CINENEST_E2E_MODE"))) {
            p.add("server.address", () -> "127.0.0.1");
            p.add("cinenest.booking.staff-read.enabled", () -> "true");
            p.add("cinenest.booking.room-blocks.enabled", () -> "true");
            p.add("cinenest.booking.room-blocks.mode", () -> "LOCAL_SYNTHETIC_ONLY");
            p.add("cinenest.identity.session-timeout-seconds", () -> "1800");
            for (String flag : List.of("enabled", "issuance-enabled", "bundle-enabled")) {
                p.add("cinenest.commercial.operational." + flag, () -> "false");
            }
        }
        if (fundingJourney()) {
            p.add("server.address", () -> "127.0.0.1");
            p.add("cinenest.payments.extension-funding.enabled", () -> "true");
        }
        if (cancellationJourney() || twoJourneys() || packageBookingJourney()) {
            p.add("server.address", () -> "127.0.0.1");
            p.add("cinenest.booking.staff-read.enabled", () -> "true");
        }
        for(String flag:List.of("cinenest.iot.enabled","cinenest.access.enabled","cinenest.notifications.enabled","cinenest.reporting.synthetic-bank.enabled","cinenest.rooms.turnover.enabled"))p.add(flag,()->"false");
        if(operationalJourney()) {
            if(demoJourney())p.add("cinenest.pricing.synthetic-quotes.tax-minor",()->"0"); // Explicit synthetic zero-tax fixture, not commercial tax approval.
            // Short, positive synthetic deadlines fit this near-term real-time journey.
            // Preserve every production rule and ordering constraint; no clock override.
            for(var timing:Map.of("lead",15,"request",75,"initiation",20,"submission",30,
                "review",40,"hold",60,"escalation",50,"upload",5).entrySet())
                p.add("cinenest.bank."+timing.getKey()+"-seconds",()->timing.getValue().toString());
            p.add("server.address",()->"127.0.0.1");
            for(String flag:List.of("cinenest.booking.staff-read.enabled","cinenest.booking.guests.enabled",
                "cinenest.booking.guest-admission.enabled","cinenest.booking.staff-check-in.enabled",
                "cinenest.booking.staff-session.enabled","cinenest.rooms.turnover.enabled"))p.add(flag,()->"true");
        }
    }
    private static Map<String,String> operationalFinancialState(BankIntegrationTest fixture){
        var state=fixture.extensionDomainFingerprint();
        state.keySet().removeIf(name->!(name.startsWith("cinenest_payments.")||name.startsWith("cinenest_pricing."))
            ||name.endsWith("_audit")||name.endsWith("_work"));
        return state;
    }
    private Map<String,String> operationalFinancial;
    private String operationalPurchase,operationalInventory;
    private Map<String,Object> operationalControl(BankIntegrationTest f,String op,Map<String,Object> body)throws Exception {
        if(op.equals("operational-scan")){f.work();f.bankWork();return Map.of("scannedSyntheticFixtures",true);}
        if(demoJourney() && op.equals("operational-demo-unfunded")){assertThat(f.jdbc.sql("SELECT count(*) FROM cinenest_booking.booking").query(Integer.class).single()).isZero();assertThat(f.jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_credit_verification").query(Integer.class).single()).isZero();assertThat(f.jdbc.sql("SELECT count(*) FROM cinenest_payments.payment_allocation").query(Integer.class).single()).isZero();return Map.of("bookings",0,"verifiedCredits",0,"allocations",0);}
        if(demoJourney() && op.equals("operational-demo-revoke")){f.jdbc.sql("UPDATE cinenest_identity.access_grant SET state='REVOKED' WHERE account_id=:a AND permission='guest.check-in.synthetic.record'").param("a",demoActor("admission")).update();return Map.of("revoked",true);}
        if(demoJourney() && op.equals("operational-demo-restore")){f.grantTo(demoActor("admission"),BankIntegrationTest.VA,"guest.check-in.synthetic.record");return Map.of("restored",true);}
        UUID booking=UUID.fromString(body.get("bookingId").toString());
        if(op.equals("operational-baseline")){
            operationalFinancial=operationalFinancialState(f);
            operationalPurchase=f.jdbc.sql("SELECT to_jsonb(r)::text FROM cinenest_booking.booking_revision r WHERE booking_id=:b").param("b",booking).query(String.class).single();
            operationalInventory=f.jdbc.sql("SELECT md5(string_agg(to_jsonb(b)::text,',' ORDER BY block_id)) FROM cinenest_booking.inventory_block b WHERE blocking").query(String.class).single();
            return Map.of("capturedImmutablePurchaseAndFinancialState",true);
        }
        if(op.equals("operational-eligibility")){
            org.mockito.Mockito.when(operationalEligibility.currentDecisions(org.mockito.ArgumentMatchers.any())).thenAnswer(call->{
                com.cinenest.booking.SyntheticGuestAdmission.Scope scope=call.getArgument(0);
                var decisions=new HashMap<UUID,com.cinenest.booking.SyntheticGuestAdmission.Decision>();
                if(scope.booking().equals(booking)) for(UUID entry:f.jdbc.sql("SELECT entry_id FROM cinenest_booking.guest_entry WHERE booking_id=:b").param("b",booking).query(UUID.class).list())
                    decisions.put(entry,new com.cinenest.booking.SyntheticGuestAdmission.Decision(scope,com.cinenest.booking.SyntheticGuestAdmission.Outcome.ELIGIBLE,false));
                return decisions;
            });
            return Map.of("source","SYNTHETIC_ELIGIBILITY_PORT_FIXTURE","originalDocumentInspected",false);
        }
        if(!op.equals("operational-persistence"))throw new IllegalArgumentException("Unsupported operational fixture command");
        if(demoJourney()){
            assertThat(f.jdbc.sql("SELECT count(*) FROM cinenest_identity.account").query(Integer.class).single()).isEqualTo(8);
            assertThat(f.jdbc.sql("SELECT count(*) FROM cinenest_pricing.package_version WHERE snapshot->>'basePriceMinor'='250000' AND snapshot->>'extensionPriceMinor'='100000' AND snapshot->>'extraGuestPriceMinor'='80000' AND snapshot->>'includedMinutes'='180'").query(Integer.class).single()).isEqualTo(1);
            assertThat(f.jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_credit_verification WHERE verifier_id=:a AND status='SIMULATED_VERIFIED'").param("a",demoActor("bank-verifier")).query(Integer.class).single()).isEqualTo(1);
            assertThat(f.jdbc.sql("SELECT count(*) FROM cinenest_payments.payment_allocation WHERE actor_id=:a").param("a",demoActor("bank-allocator")).query(Integer.class).single()).isEqualTo(1);
            assertThat(f.jdbc.sql("SELECT count(*) FROM cinenest_booking.synthetic_staff_check_in WHERE actor_id=:a").param("a",demoActor("admission")).query(Integer.class).single()).isEqualTo(1);

            assertThat(f.jdbc.sql("SELECT cleaning_buffer_minutes FROM cinenest_rooms.venue WHERE venue_id=:v").param("v",BankIntegrationTest.VA).query(Integer.class).single()).isEqualTo(15);
            assertThat(f.jdbc.sql("SELECT count(*) FROM cinenest_identity.access_grant WHERE state='ACTIVE' AND (scope_kind<>'VENUE' OR venue_id<>:v)").param("v",BankIntegrationTest.VA).query(Integer.class).single()).isZero();
        }
        assertThat(operationalFinancialState(f)).as("Immutable financial and pricing state").isEqualTo(operationalFinancial);
        assertThat(f.jdbc.sql("SELECT to_jsonb(r)::text FROM cinenest_booking.booking_revision r WHERE booking_id=:b").param("b",booking).query(String.class).single()).isEqualTo(operationalPurchase);
        assertThat(f.jdbc.sql("SELECT md5(string_agg(to_jsonb(b)::text,',' ORDER BY block_id)) FROM cinenest_booking.inventory_block b WHERE blocking").query(String.class).single()).isEqualTo(operationalInventory);
        var row=f.jdbc.sql("SELECT b.state,b.synthetic FROM cinenest_booking.booking b JOIN cinenest_booking.booking_revision r USING(booking_id) WHERE b.booking_id=:b AND b.owner_id=:a AND b.venue_id=:v AND r.revision=(SELECT max(revision) FROM cinenest_booking.booking_revision WHERE booking_id=b.booking_id) AND (r.snapshot->>'roomId')::uuid=:r")
            .param("b",booking).param("a",BankIntegrationTest.A).param("v",BankIntegrationTest.VA).param("r",BankIntegrationTest.RID).query().singleRow();
        assertThat(row.get("state")).isEqualTo("SIMULATED_CONFIRMED");assertThat(row.get("synthetic")).isEqualTo(true);
        var events=f.jdbc.sql("SELECT event_type FROM cinenest_booking.synthetic_staff_session_event WHERE booking_id=:b ORDER BY recorded_at,event_id").param("b",booking).query(String.class).list();
        assertThat(events).containsExactly("START","CHECKOUT");
        assertThat(f.jdbc.sql("SELECT count(*) FROM cinenest_booking.synthetic_staff_check_in WHERE booking_id=:b").param("b",booking).query(Integer.class).single()).isEqualTo(1);
        UUID checkout=f.jdbc.sql("SELECT event_id FROM cinenest_booking.synthetic_staff_session_event WHERE booking_id=:b AND event_type='CHECKOUT'").param("b",booking).query(UUID.class).single();
        assertThat(f.jdbc.sql("SELECT state FROM cinenest_rooms.turnover_case WHERE case_id=:c AND booking_id=:b AND assignee_id=:a").param("c",checkout).param("b",booking).param("a",operationalCleaner()).query(String.class).single()).isEqualTo("RESOLVED");
        assertThat(f.jdbc.sql("SELECT count(*) FROM cinenest_rooms.turnover_audit WHERE case_id=:c AND action='REVIEW' AND actor_id=:a").param("c",checkout).param("a",operationalReviewer()).query(Integer.class).single()).isEqualTo(1);
        assertThat(f.jdbc.sql("SELECT count(*) FROM cinenest_payments.payment_allocation").query(Integer.class).single()).isEqualTo(1);
        assertThat(f.jdbc.sql("SELECT count(*) FROM cinenest_access.access_credential").query(Integer.class).single()).isZero();
        assertThat(f.jdbc.sql("SELECT count(*) FROM cinenest_booking.inventory_block a JOIN cinenest_booking.inventory_block b ON a.room_id=b.room_id AND a.block_id<b.block_id WHERE a.blocking AND b.blocking AND tstzrange(a.session_start,a.blocked_until,'[)') && tstzrange(b.session_start,b.blocked_until,'[)')").query(Integer.class).single()).isZero();
        return Map.of("booking","SIMULATED_CONFIRMED","sessionEvents",events,"checkInCount",1,"allocationCount",1,"turnover","RESOLVED","reviewerIndependent",true,"physicalOccupancy","UNKNOWN","physicalReadiness","UNVERIFIED","physicalCredentials",0);
    }

    private static final List<String> ADMIN_ROLES =
        List.of("administrator", "wrong-venue", "ungranted", "revoked", "publisher");

    private static UUID adminId(String role) {
        return BankIntegrationTest.id("qa-admin-" + role);
    }

    private static Map<String, Object> adminResponse(String stage, BankIntegrationTest fixture,
            java.net.http.HttpResponse<String> response) throws Exception {
        System.out.printf("ADMIN_SETUP %s expected=200 actual=%d%n", stage, response.statusCode());
        assertThat(response.statusCode()).as("Admin setup %s", stage).isEqualTo(200);
        return fixture.json(response);
    }

    private static Map<String, Object> prepareAdminQa(BankIntegrationTest fixture, UUID pkg, Object version) throws Exception {
        for (String role : ADMIN_ROLES) {
            UUID account = adminId(role);
            UUID venue = role.equals("wrong-venue") ? BankIntegrationTest.VB : BankIntegrationTest.VA;
            fixture.jdbc.sql("INSERT INTO cinenest_identity.account VALUES (:id,TRUE,'ELIGIBLE',0)")
                .param("id", account).update();
            fixture.jdbc.sql("INSERT INTO cinenest_identity.external_identity VALUES (:issuer,:subject,:id)")
                .param("issuer", BankIntegrationTest.IDP.issuer()).param("subject", "qa-admin-" + role)
                .param("id", account).update();
            fixture.scope(account, venue);
        }
        for (String permission : List.of("venue.read", "venue.write", "room.read", "room.write",
                "package.read", "package.edit")) {
            fixture.grantTo(adminId("administrator"), BankIntegrationTest.VA, permission);
        }
        for (String permission : List.of("venue.read", "room.read", "package.read", "booking.synthetic.read")) {
            // Insert directly for the scoped subject. grantTo first inserts for Staff A.
            fixture.jdbc.sql("""
                INSERT INTO cinenest_identity.access_grant
                VALUES (:id,:actor,:permission,'VENUE',:venue,'ACTIVE',
                    CURRENT_TIMESTAMP - INTERVAL '1 minute',CURRENT_TIMESTAMP + INTERVAL '1 hour',0)
                """).param("id", adminId("wrong-venue:" + permission))
                .param("actor", adminId("wrong-venue")).param("permission", permission)
                .param("venue", BankIntegrationTest.VB).update();
        }
        var venueBMembers = fixture.jdbc.sql("""
            SELECT account_id FROM cinenest_identity.account_scope WHERE venue_id=:venue
            """).param("venue", BankIntegrationTest.VB).query(UUID.class).list();
        assertThat(venueBMembers).contains(adminId("wrong-venue")).doesNotContain(BankIntegrationTest.STAFF);
        fixture.grantTo(adminId("revoked"), BankIntegrationTest.VA, "booking.synthetic.read");
        fixture.grantTo(adminId("publisher"), BankIntegrationTest.VA, "package.read");
        fixture.grantTo(adminId("publisher"), BankIntegrationTest.VA, "package.publish");
        fixture.grantTo(adminId("publisher"), BankIntegrationTest.VA, "package.deactivate");
        fixture.bankGrants();
        UUID roomB = adminId("room-b");
        fixture.jdbc.sql("""
            INSERT INTO cinenest_rooms.room(room_id,venue_id,code,name,capacity,enabled)
            VALUES(:id,:venue,'SYNTHETIC-B-ROOM','Synthetic room B',5,TRUE)
            """).param("id", roomB).param("venue", BankIntegrationTest.VB).update();
        UUID booking = adminId("booking");
        UUID block = adminId("booking-block");
        UUID hold = adminId("booking-hold");
        UUID policy = adminId("booking-policy");
        fixture.jdbc.sql("""
            INSERT INTO cinenest_booking.hold_policy VALUES
                (:policy,600,10,60,'SYNTHETIC Admin QA','2020-01-01T00:00:00Z')
            """).param("policy", policy).update();
        fixture.jdbc.sql("""
            INSERT INTO cinenest_booking.inventory_block VALUES
                (:block,:venue,:room,'2020-01-02T00:00:00Z','2020-01-02T03:00:00Z',0,
                 '2020-01-02T03:00:00Z',false,'CONFIRMED',0)
            """).param("block", block).param("venue", BankIntegrationTest.VA)
            .param("room", BankIntegrationTest.RID).update();
        fixture.jdbc.sql("""
            INSERT INTO cinenest_booking.reservation_hold VALUES
                (:hold,:owner,:block,:policy,4,'SYNTHETIC','Asia/Colombo','SYNTHETIC',
                 '2020-01-01T00:00:00Z','2020-01-01T00:10:00Z','CONSUMED',1)
            """).param("hold", hold).param("owner", BankIntegrationTest.A)
            .param("block", block).param("policy", policy).update();
        fixture.jdbc.sql("""
            INSERT INTO cinenest_booking.booking
                (booking_id,request_id,hold_id,owner_id,venue_id,state,synthetic,confirmed_at)
            VALUES (:booking,:request,:hold,:owner,:venue,'SIMULATED_CONFIRMED',true,'2020-01-01T00:00:00Z')
            """).param("booking", booking).param("request", adminId("booking-request"))
            .param("hold", hold).param("owner", BankIntegrationTest.A).param("venue", BankIntegrationTest.VA).update();
        var snapshot = Map.of("synthetic", true, "commercialIssuance", false,
            "roomId", BankIntegrationTest.RID.toString(), "sessionStart", "2020-01-02T00:00:00Z",
            "sessionEnd", "2020-01-02T03:00:00Z", "timezone", "Asia/Colombo",
            "package", Map.of("name", "SYNTHETIC package"), "guests", 4);
        fixture.jdbc.sql("""
            INSERT INTO cinenest_booking.booking_revision
                (booking_id,revision,quote_id,quote_revision,quote_hash,amount_minor,currency,snapshot)
            VALUES (:booking,1,:quote,1,repeat('a',64),12345,'LKR',CAST(:snapshot AS jsonb))
            """).param("booking", booking).param("quote", adminId("booking-quote"))
            .param("snapshot", JSONObjectUtils.toJSONString(snapshot)).update();
        var customer = fixture.signedIn("customer-a");
        var held = adminResponse("hold-create", fixture, fixture.create(customer, fixture.request(customer, fixture.local())));
        var quote = adminResponse("quote-create", fixture, fixture.write(customer, "POST", "/api/quotes",
            JSONObjectUtils.toJSONString(Map.of(
            "holdId", held.get("holdId"), "packageId", pkg.toString(), "packageVersionId", version,
            "expectedHoldRevision", held.get("revision"), "optionalServiceIds", List.of(),
            "paymentMethod", "BANK", "idempotencyKey", adminId("payment-quote-command")))));
        var quoteId = quote.get("quoteId");
        adminResponse("quote-accept", fixture, fixture.write(customer, "POST", fixture.quotePath(quote) + "/accept",
            JSONObjectUtils.toJSONString(Map.of("expectedRevision", quote.get("revision"),
                "contentHash", quote.get("contentHash"), "disclosures", quote.get("disclosures"),
                "idempotencyKey", adminId("payment-accept-command")))));
        var verification = adminResponse("verification-create", fixture, fixture.write(customer, "POST", "/api/verification/requests",
            JSONObjectUtils.toJSONString(Map.of("commandKey", adminId("payment-verification-command"),
                "quoteId", quoteId, "policyVersion", "SYNTHETIC-V1", "consent", true))));
        var allocated = fixture.allocate(customer, verification);
        String upload = allocated.get("uploadId").toString();
        var uploaded = fixture.upload(customer, upload, BankIntegrationTest.IMAGE, "image/png");
        System.out.printf("ADMIN_SETUP verification-upload expected=200 actual=%d%n", uploaded.statusCode());
        assertThat(uploaded.statusCode()).isEqualTo(200);
        adminResponse("verification-complete", fixture, fixture.write(customer, "POST", "/api/verification/uploads/" + upload + "/complete",
            JSONObjectUtils.toJSONString(fixture.cmd(((Number) allocated.get("revision")).longValue()))));
        fixture.work();
        var ready = adminResponse("verification-read", fixture, customer.get(fixture.casePath(verification)));
        adminResponse("verification-review", fixture, fixture.write(fixture.signedIn("staff"), "POST", fixture.reviewPath(ready) + "/decisions",
            JSONObjectUtils.toJSONString(fixture.decision(ready, "VERIFIED"))));
        var accepted = adminResponse("accepted-quote-read", fixture, customer.get(fixture.quotePath(quote)));
        assertThat(accepted).containsEntry("state", "ACCEPTED");
        var pending = adminResponse("pending-bank-create", fixture, fixture.write(customer, "POST", "/api/bank/requests",
            JSONObjectUtils.toJSONString(Map.of("quoteId", quoteId, "quoteRevision", accepted.get("revision"),
                "contentHash", accepted.get("contentHash"), "commandKey", adminId("payment-bank-command")))));

        assertThat(pending).containsEntry("requestState", "OPEN").containsEntry("reviewState", "PENDING")
            .containsEntry("paymentVerified", false).containsEntry("bookingConfirmed", false);
        return Map.of("packageId", pkg, "bookingId", booking, "requestId", pending.get("id"),
            "draft", JSONObjectUtils.parse(fixture.draft("PUBLIC")),
            "publication", fixture.request(false), "roomB", roomB);
    }

    private record CancellationFixture(UUID booking, UUID block, long revision, Map<String, String> protectedState) {
        static CancellationFixture prepare(BankIntegrationTest fixture) throws Exception {
            var booking = fixture.accessCase().booking();
            fixture.grantTo(BankIntegrationTest.STAFF, BankIntegrationTest.VA, "booking.synthetic.read");
            fixture.grantTo(BankIntegrationTest.STAFF, BankIntegrationTest.VA, "booking.synthetic.cancel");
            fixture.grantTo(BankIntegrationTest.STAFF, BankIntegrationTest.VB, "booking.synthetic.cancel");
            var purchasedBlock = fixture.jdbc.sql("""
                SELECT i.block_id, i.revision, i.kind, i.blocking
                FROM cinenest_booking.booking b
                JOIN cinenest_booking.reservation_hold h ON h.hold_id=b.hold_id
                JOIN cinenest_booking.inventory_block i USING (block_id)
                WHERE b.booking_id=:booking AND b.owner_id=:owner AND b.venue_id=:venue
                  AND b.synthetic AND b.state='SIMULATED_CONFIRMED'
                """).param("booking", booking).param("owner", BankIntegrationTest.A)
                .param("venue", BankIntegrationTest.VA).query().singleRow();
            assertThat(purchasedBlock).containsEntry("kind", "CONFIRMED").containsEntry("blocking", true);
            return new CancellationFixture(booking, (UUID)purchasedBlock.get("block_id"),
                ((Number)purchasedBlock.get("revision")).longValue(), protectedState(fixture));
        }

        private static Map<String,String> protectedState(BankIntegrationTest fixture) {
            var state = fixture.extensionDomainFingerprint();
            state.keySet().removeIf(key -> !(key.equals("cinenest_booking.booking_revision")
                || key.startsWith("cinenest_access.")
                || key.startsWith("cinenest_payments.refund_")
                || key.equals("cinenest_payments.payment_allocation")
                || key.equals("cinenest_payments.incoming_payment_credit")));
            return state;
        }

        Map<String,Object> inspect(BankIntegrationTest fixture, String operation, Object rawKey) {
            assertThat(protectedState(fixture)).isEqualTo(protectedState);
            boolean cancelled = operation.equals("cancellation-committed");
            var bookingStatus = fixture.jdbc.sql("""
                SELECT state,lifecycle_version FROM cinenest_booking.booking WHERE booking_id=:booking
                """).param("booking", booking).query().singleRow();
            assertThat(bookingStatus).containsEntry("state", cancelled ? "SIMULATED_CANCELLED" : "SIMULATED_CONFIRMED")
                .containsEntry("lifecycle_version", cancelled ? 1L : 0L);
            var blockStatus = fixture.jdbc.sql("""
                SELECT blocking,revision,kind FROM cinenest_booking.inventory_block WHERE block_id=:block
                """).param("block", block).query().singleRow();
            assertThat(blockStatus).containsEntry("kind", "CONFIRMED").containsEntry("blocking", !cancelled)
                .containsEntry("revision", revision + (cancelled ? 1 : 0));
            var counts = fixture.jdbc.sql("""
                SELECT
                  (SELECT count(*) FROM cinenest_booking.booking_cancellation WHERE booking_id=:booking) AS cancellations,
                  (SELECT count(*) FROM cinenest_booking.cancellation_audit WHERE booking_id=:booking) AS audits,
                  (SELECT count(*) FROM cinenest_payments.cancellation_refund_obligation WHERE booking_id=:booking) AS obligations
                """).param("booking", booking).query().singleRow();
            assertThat(counts).containsEntry("cancellations", cancelled ? 1L : 0L)
                .containsEntry("audits", cancelled ? 1L : 0L).containsEntry("obligations", cancelled ? 1L : 0L);
            if (cancelled) {
                var committed = fixture.jdbc.sql("""
                    SELECT c.actor_id, c.block_id, a.actor_id AS audit_actor, a.venue_id,
                           cmd.actor_id AS command_actor, cmd.command_key, r.state AS obligation
                    FROM cinenest_booking.booking_cancellation c
                    JOIN cinenest_booking.cancellation_audit a USING (cancellation_id,booking_id)
                    JOIN cinenest_booking.cancellation_command cmd USING (cancellation_id)
                    JOIN cinenest_payments.cancellation_refund_obligation r USING (cancellation_id,booking_id)
                    WHERE c.booking_id=:booking
                    """).param("booking", booking).query().singleRow();
                assertThat(committed).containsEntry("actor_id", BankIntegrationTest.A)
                    .containsEntry("audit_actor", BankIntegrationTest.A).containsEntry("command_actor", BankIntegrationTest.A)
                    .containsEntry("command_key", UUID.fromString(rawKey.toString()))
                    .containsEntry("block_id", block).containsEntry("venue_id", BankIntegrationTest.VA)
                    .containsEntry("obligation", "RESERVED_REVIEW_REQUIRED");
            }
            return Map.of("state", bookingStatus.get("state"), "blocking", blockStatus.get("blocking"),
                "audits", counts.get("audits"), "obligations", counts.get("obligations"),
                "protectedStateUnchanged", true);
        }
    }

    private final class FundingFixture {
        private final BankIntegrationTest.AccessCase purchase;
        private Map<String, Object> reservation;
        private Map<String, String> protectedDomain;
        private Map<String, Object> committed;

        FundingFixture(BankIntegrationTest fixture) throws Exception {
            purchase = fixture.accessCase();
        }

        Map<String, Object> prepare(BankIntegrationTest fixture) throws Exception {
            assertThat(reservation).isNull();
            var command = fixture.reservationBody(purchase);
            var service = context.getBean("extensionReservationService");
            var clock = org.springframework.test.util.ReflectionTestUtils.getField(service, "clock");
            var now = fixture.jdbc.sql("SELECT clock_timestamp() - INTERVAL '555 seconds'")
                .query(java.time.OffsetDateTime.class).single().toInstant();
            try {
                org.springframework.test.util.ReflectionTestUtils.setField(service, "clock",
                    (java.util.function.Supplier<java.time.Instant>) () -> now);
                var result = fixture.reserve(purchase, command);
                assertThat(result.statusCode()).isEqualTo(200);
                reservation = fixture.json(result);
            } finally {
                org.springframework.test.util.ReflectionTestUtils.setField(service, "clock", clock);
            }
            protectedDomain = protectedState(fixture);
            return Map.of("reservation", reservation, "reservationKey", command.get("commandKey"));
        }

        private static Map<String, String> protectedState(BankIntegrationTest fixture) {
            var state = fixture.recoveryDomain();
            state.remove("cinenest_payments.extension_funding_request");
            state.remove("cinenest_payments.extension_funding_audit");
            return state;
        }

        Map<String, Object> inspect(BankIntegrationTest fixture, String op, Object commandKey) {
            assertThat(protectedState(fixture)).isEqualTo(protectedDomain);
            var requests = fixture.jdbc.sql("""
                SELECT request_id, owner_id, venue_id, booking_id, reservation_id, command_key,
                       purchased_revision, lifecycle_version, reservation_revision, package_version_id,
                       terms_version, amount_minor, currency, expires_at,
                       to_jsonb(r)::text AS row_snapshot
                FROM cinenest_payments.extension_funding_request r
                """).query().listOfRows();
            var audits = fixture.jdbc.sql("""
                SELECT request_id, actor_id, venue_id, to_jsonb(a)::text AS row_snapshot
                FROM cinenest_payments.extension_funding_audit a
                """).query().listOfRows();
            if (op.equals("funding-before")) {
                assertThat(requests).isEmpty();
                assertThat(audits).isEmpty();
            } else {
                assertThat(requests).hasSize(1);
                assertThat(audits).hasSize(1);
                var request = requests.getFirst();
                var audit = audits.getFirst();
                assertThat(request).containsEntry("owner_id", BankIntegrationTest.A)
                    .containsEntry("venue_id", BankIntegrationTest.VA)
                    .containsEntry("booking_id", purchase.booking())
                    .containsEntry("reservation_id", UUID.fromString(reservation.get("reservationId").toString()))
                    .containsEntry("command_key", UUID.fromString(commandKey.toString()))
                    .containsEntry("purchased_revision", ((Number) reservation.get("purchasedRevision")).longValue())
                    .containsEntry("reservation_revision", ((Number) reservation.get("proposalRevision")).longValue())
                    .containsEntry("lifecycle_version", 0L)
                    .containsEntry("package_version_id", UUID.fromString(reservation.get("packageVersionId").toString()))
                    .containsEntry("terms_version", reservation.get("termsVersion"))
                    .containsEntry("amount_minor", Long.parseLong(reservation.get("additionalMinor").toString()))
                    .containsEntry("currency", "LKR");
                assertThat(audit).containsEntry("request_id", request.get("request_id"))
                    .containsEntry("actor_id", BankIntegrationTest.A).containsEntry("venue_id", BankIntegrationTest.VA);
                var snapshot = Map.of("request", request.get("row_snapshot"), "audit", audit.get("row_snapshot"));
                if (committed == null) committed = snapshot;
                assertThat(snapshot).isEqualTo(committed);
                if (op.equals("funding-expired")) {
                    assertThat(fixture.jdbc.sql("SELECT clock_timestamp() >= expires_at FROM cinenest_payments.extension_funding_request")
                        .query(Boolean.class).single()).isTrue();
                    org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.jdbc.sql(
                        "UPDATE cinenest_payments.extension_funding_request SET amount_minor=amount_minor").update())
                        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
                    org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.jdbc.sql(
                        "DELETE FROM cinenest_payments.extension_funding_audit").update())
                        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
                }
            }
            return Map.of("requests", requests.size(), "audits", audits.size(), "protectedStateUnchanged", true);
        }
    }

    private static final class TwoJourneyFixture {
        private Map<String, String> protectedState;
        private UUID booking;
        private UUID originalBlock;
        private String originalRevision;
        private Map<String, Object> refundCredit;

        private static Map<String, String> protectedState(BankIntegrationTest fixture, boolean refund) {
            var snapshot = fixture.extensionDomainFingerprint();
            snapshot.keySet().removeIf(name -> refund
                ? name.startsWith("cinenest_payments.refund_") || name.endsWith(".bank_audit")
                    || name.endsWith(".financial_audit")
                : name.equals("cinenest_payments.bank_audit")
                    || (!name.startsWith("cinenest_payments.") && !name.startsWith("cinenest_access.")));
            return snapshot;
        }

        private void verifyProtectedState(BankIntegrationTest fixture, boolean refund, Map<String, Object> evidence) {
            var current = protectedState(fixture, refund);
            var changed = new java.util.TreeSet<>(protectedState.keySet());
            changed.addAll(current.keySet());
            changed.removeIf(table -> java.util.Objects.equals(current.get(table), protectedState.get(table)));
            evidence.put("changedTables", changed);
            assertThat(changed).as("Protected synthetic domain table fingerprints").isEmpty();
        }

        Map<String, Object> run(BankIntegrationTest fixture, String operation, Map<String, Object> evidence) throws Exception {
            return switch (operation) {
                case "two-reschedule-prepare" -> {
                    var purchase = fixture.accessCase();
                    booking = purchase.booking();
                    fixture.grantTo(BankIntegrationTest.STAFF, BankIntegrationTest.VA, "booking.synthetic.read");
                    fixture.grantTo(BankIntegrationTest.STAFF, BankIntegrationTest.VB, "booking.synthetic.read");
                    originalBlock = fixture.jdbc.sql("""
                        SELECT block_id FROM cinenest_booking.inventory_block WHERE blocking AND kind='CONFIRMED'
                        """).query(UUID.class).single();
                    originalRevision = originalRevision(fixture);
                    protectedState = protectedState(fixture, false);
                    yield Map.of("booking", booking.toString(), "targetLocal",
                        java.time.LocalDateTime.parse(fixture.local()).plusHours(4).toString());
                }
                case "two-reschedule-inspect" -> {
                    verifyProtectedState(fixture, false, evidence);
                    assertThat(originalRevision(fixture)).isEqualTo(originalRevision);
                    assertThat(fixture.jdbc.sql("SELECT revision FROM cinenest_booking.booking_revision WHERE booking_id=:b ORDER BY revision")
                        .param("b", booking).query(Long.class).list()).containsExactly(1L, 2L);
                    assertThat(fixture.jdbc.sql("SELECT blocking FROM cinenest_booking.inventory_block WHERE block_id=:old")
                        .param("old", originalBlock).query(Boolean.class).single()).isFalse();
                    var blocks = fixture.jdbc.sql("""
                        SELECT i.block_id FROM cinenest_booking.booking_amendment a
                        JOIN cinenest_booking.reservation_hold h ON h.hold_id=a.target_hold
                        JOIN cinenest_booking.inventory_block i ON i.block_id=h.block_id
                        WHERE a.booking_id=:b AND a.state='CONFIRMED' AND a.committed_revision=2
                          AND i.blocking AND i.kind='CONFIRMED' AND h.state='CONSUMED'
                        """).param("b", booking).query(UUID.class).list();
                    assertThat(blocks).hasSize(1).doesNotContain(originalBlock);
                    assertThat(fixture.jdbc.sql("SELECT count(*) FROM cinenest_booking.inventory_block WHERE blocking")
                        .query(Long.class).single()).isEqualTo(1L);
                    assertThat(fixture.jdbc.sql("""
                        SELECT actor_id FROM cinenest_booking.booking_amendment_audit
                        WHERE booking_id=:b AND action='CONFIRMED' AND outcome='APPLIED'
                        """).param("b", booking).query(UUID.class).list()).containsExactly(BankIntegrationTest.A);
                    org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.jdbc.sql(
                        "UPDATE cinenest_booking.booking_revision SET amount_minor=amount_minor WHERE booking_id=:b")
                        .param("b", booking).update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
                    yield Map.of("revisions", 2, "blocking", 1, "confirmationAudits", 1, "paymentUnchanged", true);
                }
                case "two-refund-prepare" -> {
                    // Prior scenario contexts are closed before resetting this disposable database.
                    fixture.fixtures();
                    fixture.financeGrants();
                    var owner = fixture.signedIn("customer-a");
                    var reviewer = fixture.signedIn("staff");
                    var executor = fixture.signedIn("customer-b");
                    var request = fixture.bank(owner);
                    refundCredit = fixture.credit(reviewer, "SYNTHETIC-BANK-50000");
                    request = fixture.match(executor, request, refundCredit);
                    for (String p : List.of("read", "review", "destination.verify", "approve", "reconcile"))
                        fixture.grantTo(BankIntegrationTest.STAFF, BankIntegrationTest.VA, "refund.synthetic." + p);
                    for (String p : List.of("read", "execute"))
                        fixture.grantTo(BankIntegrationTest.B, BankIntegrationTest.VA, "refund.synthetic." + p);
                    protectedState = protectedState(fixture, true);
                    yield Map.of("request", request.get("id"), "revision", request.get("revision"), "credit", refundCredit.get("id"));
                }
                case "two-refund-inspect" -> {
                    verifyProtectedState(fixture, true, evidence);
                    var state = fixture.jdbc.sql("""
                        SELECT r.state, r.amount_minor, r.approver_id, a.executor_id, a.attempt_id
                        FROM cinenest_payments.refund_obligation r
                        JOIN cinenest_payments.refund_attempt a USING(refund_id)
                        """).query().singleRow();
                    assertThat(state).containsEntry("state", "SIMULATED_CONFIRMED").containsEntry("amount_minor", 100L)
                        .containsEntry("approver_id", BankIntegrationTest.STAFF).containsEntry("executor_id", BankIntegrationTest.B);
                    var attempt = state.get("attempt_id");
                    var rows = fixture.jdbc.sql("""
                        SELECT fixture_key, phase, recorder_id, verifier_id
                        FROM cinenest_payments.refund_outgoing_evidence WHERE attempt_id=:id
                        """).param("id", attempt).query().listOfRows();
                    assertThat(rows).hasSize(4);
                    for (String outcome : List.of("SYNTHETIC-UNKNOWN", "SYNTHETIC-CONFIRMED")) {
                        assertThat(rows.stream().filter(row -> outcome.equals(row.get("fixture_key"))).toList())
                            .hasSize(2).allSatisfy(row -> {
                                assertThat(row.get("recorder_id")).isEqualTo(BankIntegrationTest.B);
                                if ("VERIFIED".equals(row.get("phase"))) assertThat(row.get("verifier_id")).isEqualTo(BankIntegrationTest.STAFF);
                            });
                    }
                    // Destination verification appends its own REVIEW decision.
                    var decisions = fixture.jdbc.sql("""
                        SELECT action, actor_id FROM cinenest_payments.refund_decision ORDER BY recorded_at, decision_id
                        """).query().listOfRows();
                    assertThat(decisions.stream().map(row -> row.get("action")).toList())
                        .as("Review, destination verification and approval decisions")
                        .containsExactly("REVIEW", "REVIEW", "APPROVE");
                    assertThat(decisions.stream().map(row -> row.get("actor_id")).toList())
                        .as("Decision attribution")
                        .containsExactly(BankIntegrationTest.STAFF, BankIntegrationTest.STAFF, BankIntegrationTest.STAFF);
                    assertThat(fixture.jdbc.sql("""
                        SELECT actor_id FROM cinenest_payments.bank_audit
                        WHERE action='REFUND_DESTINATION' AND venue_id=:venue
                        """).param("venue", BankIntegrationTest.VA).query(UUID.class).list()).containsExactly(BankIntegrationTest.STAFF);
                    assertThat(fixture.jdbc.sql("""
                        SELECT actor_id FROM cinenest_payments.bank_audit
                        WHERE action='REFUND_ATTEMPT' AND venue_id=:venue
                        """).param("venue", BankIntegrationTest.VA).query(UUID.class).list()).containsExactly(BankIntegrationTest.B);
                    assertThat(fixture.jdbc.sql("""
                        SELECT actor_id FROM cinenest_payments.bank_audit
                        WHERE action='REFUND_DECISION' AND venue_id=:venue
                        """).param("venue", BankIntegrationTest.VA).query(UUID.class).list())
                        .containsExactly(BankIntegrationTest.STAFF, BankIntegrationTest.STAFF);
                    assertThat(fixture.jdbc.sql("SELECT cinenest_payments.refund_committed(:credit)")
                        .param("credit", UUID.fromString(refundCredit.get("id").toString())).query(Long.class).single()).isEqualTo(100L);
                    org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.jdbc.sql(
                        "DELETE FROM cinenest_payments.refund_outgoing_evidence WHERE attempt_id=:id")
                        .param("id", attempt).update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
                    yield Map.of("attempts", 1, "immutableEvidence", 4, "simulatedMinor", 100, "protectedStateUnchanged", true);
                }
                default -> throw new IllegalArgumentException("Unsupported two-journey fixture command");
            };
        }

        private String originalRevision(BankIntegrationTest fixture) {
            return fixture.jdbc.sql("""
                SELECT to_jsonb(r)::text FROM cinenest_booking.booking_revision r WHERE booking_id=:b AND revision=1
                """).param("b", booking).query(String.class).single();
        }
    }

    private static final class PackageBookingFixture {
        private final UUID booking;
        private final UUID pkg;
        private final Map<String, Object> original;
        private final String purchased;
        private final Map<String, Object> unrelated;
        private final String originalVersionSnapshot;
        private String revisedVersionSnapshot;
        private Map<String, String> payments;
        private UUID newBooking;
        private UUID bankRequestId;
        private String newPurchase;
        private String revisedQuote;

        PackageBookingFixture(BankIntegrationTest fixture) throws Exception {
            booking = fixture.accessCase().booking();
            UUID observer = adminId("history-observer");
            fixture.jdbc.sql("INSERT INTO cinenest_identity.account VALUES (:id,TRUE,'ELIGIBLE',0)")
                .param("id", observer).update();
            fixture.jdbc.sql("INSERT INTO cinenest_identity.external_identity VALUES (:issuer,:subject,:id)")
                .param("issuer", BankIntegrationTest.IDP.issuer()).param("subject", "qa-admin-history-observer")
                .param("id", observer).update();
            fixture.grantTo(BankIntegrationTest.STAFF, BankIntegrationTest.VA, "package.read");
            fixture.grantTo(BankIntegrationTest.STAFF, BankIntegrationTest.VA, "booking.synthetic.read");
            fixture.grantTo(BankIntegrationTest.B, BankIntegrationTest.VA, "package.read");
            fixture.grantTo(BankIntegrationTest.B, BankIntegrationTest.VA, "package.deactivate");
            original = fixture.jdbc.sql("""
                SELECT package_id, publication_id, version_id, revision
                FROM cinenest_pricing.package_publication WHERE state='ACTIVE'
                """).query().singleRow();
            pkg = (UUID) original.get("package_id");
            purchased = purchase(fixture, booking, original.get("version_id"));
            originalVersionSnapshot = versionSnapshot(fixture, original.get("version_id"));
            var editor = fixture.signedIn("staff");
            var request = new HashMap<>(JSONObjectUtils.parse(fixture.draft("UNRELATED")));
            request.put("name", "SYNTHETIC unrelated customer offer");
            var created = fixture.write(editor, "POST", fixture.collection(), JSONObjectUtils.toJSONString(request));
            assertThat(created.statusCode()).isEqualTo(201);
            var unrelatedId = UUID.fromString(JSONObjectUtils.parse(created.body()).get("packageId").toString());
            unrelated = fixture.published(fixture.signedIn("customer-b"), unrelatedId, fixture.request(false));
            payments = paymentState(fixture);
        }

        private static String versionSnapshot(BankIntegrationTest fixture, Object version) {
            return fixture.jdbc.sql("""
                SELECT to_jsonb(v)::text FROM cinenest_pricing.package_version v WHERE version_id=:version
                """).param("version", version).query(String.class).single();
        }

        Map<String, Object> configuration(BankIntegrationTest fixture) throws Exception {
            return Map.of("booking", booking, "packageId", pkg, "original", original,
                "publication", fixture.request(false), "unrelated", unrelated);
        }

        private String purchase(BankIntegrationTest fixture, UUID bookingId, Object version) {
            return fixture.jdbc.sql("""
                SELECT to_jsonb(r)::text FROM cinenest_booking.booking_revision r
                JOIN cinenest_booking.booking b USING (booking_id)
                JOIN cinenest_pricing.quotation_revision q
                    ON q.quote_id=r.quote_id AND q.revision=r.quote_revision
                WHERE b.booking_id=:booking AND b.owner_id=:owner AND b.venue_id=:venue
                  AND b.synthetic AND b.state='SIMULATED_CONFIRMED' AND r.revision=1
                  AND q.version_id=:version AND r.quote_hash=q.content_hash AND r.amount_minor=q.total_minor
                """).param("booking", bookingId).param("owner", BankIntegrationTest.A)
                .param("venue", BankIntegrationTest.VA).param("version", version)
                .query(String.class).single();
        }

        private Map<String, String> paymentState(BankIntegrationTest fixture) {
            var fingerprints = fixture.extensionDomainFingerprint();
            fingerprints.keySet().removeIf(key -> !key.startsWith("cinenest_payments."));
            assertThat(fingerprints).containsKeys("cinenest_payments.payment_allocation",
                "cinenest_payments.incoming_payment_credit");
            if (bankRequestId != null) {
                fingerprints.put("cinenest_payments.bank_audit", fixture.jdbc.sql("""
                    SELECT md5(coalesce(string_agg(to_jsonb(a)::text, ',' ORDER BY to_jsonb(a)::text), ''))
                    FROM cinenest_payments.bank_audit a
                    WHERE (
                        (request_id=:request AND outcome='PERMIT'
                          AND ((action='STATUS' AND actor_id IN (:owner,:staff))
                            OR (action='PREVIEW' AND actor_id=:staff)))
                        OR (actor_id=:owner
                            AND ((action='CUSTOMER_REFUNDS_READ' AND outcome='OWNED_SYNTHETIC_ONLY')
                                OR (action='STATUS' AND outcome='PERMIT'))
                            AND request_id IN (SELECT request_id FROM cinenest_booking.booking
                                WHERE booking_id IN (:originalBooking,:newBooking)))
                        OR (request_id IS NULL AND (
                            (action='BOOKING_READ' AND actor_id=:owner AND outcome='SYNTHETIC_ONLY')
                            OR (outcome='DENIED' AND (
                                (action='LEDGER_DENIED' AND actor_id=:observer)
                                OR (action='HTTP_DENIAL' AND actor_id IN (:owner,:staff,:observer))))))
                    ) IS NOT TRUE
                    """).param("request", bankRequestId).param("owner", BankIntegrationTest.A)
                    .param("staff", BankIntegrationTest.STAFF).param("observer", adminId("history-observer"))
                    .param("originalBooking", booking).param("newBooking", newBooking).query(String.class).single());
            }
            return fingerprints;
        }

        Map<String, Object> confirm(BankIntegrationTest fixture, Map<String, Object> body) throws Exception {
            assertThat(newBooking).isNull();
            assertThat(paymentState(fixture)).isEqualTo(payments);
            var customer = fixture.signedIn("customer-a");
            var staff = fixture.signedIn("staff");
            var allocator = fixture.signedIn("customer-b");
            String quoteId = UUID.fromString(body.get("quoteId").toString()).toString();
            String version = UUID.fromString(body.get("versionId").toString()).toString();
            var quote = fixture.json(customer.get("/api/quotes/" + quoteId));
            assertThat(quote).containsEntry("state", "ACCEPTED").containsEntry("packageVersionId", version);
            var request = fixture.json(fixture.write(customer, "POST", "/api/verification/requests",
                JSONObjectUtils.toJSONString(Map.of("commandKey", adminId("package-verify"),
                    "quoteId", quoteId, "policyVersion", "SYNTHETIC-V1", "consent", true))));
            var upload = fixture.allocate(customer, request);
            String uploadId = upload.get("uploadId").toString();
            assertThat(fixture.upload(customer, uploadId, BankIntegrationTest.IMAGE, "image/png").statusCode()).isEqualTo(200);
            fixture.json(fixture.write(customer, "POST", "/api/verification/uploads/" + uploadId + "/complete",
                JSONObjectUtils.toJSONString(fixture.cmd(((Number) upload.get("revision")).longValue()))));
            fixture.work();
            var ready = fixture.json(customer.get(fixture.casePath(request)));
            fixture.json(fixture.write(staff, "POST", fixture.reviewPath(ready) + "/decisions",
                JSONObjectUtils.toJSONString(fixture.decision(ready, "VERIFIED"))));
            var pending = fixture.json(fixture.write(customer, "POST", "/api/bank/requests",
                JSONObjectUtils.toJSONString(Map.of("quoteId", quoteId, "quoteRevision", quote.get("revision"),
                    "contentHash", quote.get("contentHash"), "commandKey", adminId("package-bank")))));
            bankRequestId = UUID.fromString(pending.get("id").toString());
            fixture.bankGrants();
            var claim = fixture.claim(customer, pending);
            var receipt = fixture.json(fixture.write(customer, "POST",
                "/api/bank/claims/" + claim.get("claimId") + "/uploads",
                JSONObjectUtils.toJSONString(fixture.cmd(((Number) claim.get("revision")).longValue()))));
            String receiptId = receipt.get("uploadId").toString();
            assertThat(fixture.bankUpload(customer, receiptId, BankIntegrationTest.RECEIPT, "image/png")
                .statusCode()).isEqualTo(200);
            fixture.json(fixture.write(customer, "POST", "/api/bank/uploads/" + receiptId + "/complete",
                JSONObjectUtils.toJSONString(fixture.cmd(((Number) receipt.get("revision")).longValue()))));
            fixture.bankWork();
            pending = fixture.json(customer.get(fixture.bankPath(pending)));
            assertThat(pending.get("evidence").toString()).contains(receiptId, "READY");
            assertThat(pending).containsEntry("paymentVerified", false).containsEntry("bookingConfirmed", false);
            long amount = Long.parseLong(pending.get("amountMinor").toString());
            assertThat(amount).isPositive().isLessThanOrEqualTo(10000L);
            var credit = fixture.credit(staff, "SYNTHETIC-BANK-10000");
            var matched = fixture.match(allocator, pending, credit);
            var confirmed = fixture.json(fixture.allocateCredit(allocator, matched, credit, amount));
            newBooking = UUID.fromString(confirmed.get("bookingId").toString());
            assertThat(newBooking).isNotEqualTo(booking);
            newPurchase = purchase(fixture, newBooking, UUID.fromString(version));
            assertThat(fixture.jdbc.sql("SELECT count(*) FROM cinenest_payments.payment_allocation")
                .query(Long.class).single()).isEqualTo(2L);
            payments = paymentState(fixture);
            return Map.of("booking", newBooking, "syntheticPurchasedVersionVerified", true,
                "bankRequest", pending.get("id"), "receiptId", receiptId);
        }

        Map<String, Object> inspect(BankIntegrationTest fixture, Map<String, Object> body) {
            var version = UUID.fromString(body.get("versionId").toString());
            var quote = UUID.fromString(body.get("quoteId").toString());
            boolean retired = Boolean.TRUE.equals(body.get("retired"));
            var persisted = fixture.jdbc.sql("""
                SELECT to_jsonb(r)::text FROM cinenest_pricing.quotation_revision r
                JOIN cinenest_pricing.quotation q USING (quote_id)
                WHERE q.quote_id=:quote AND q.owner_id=:owner AND q.venue_id=:venue
                  AND r.version_id=:version AND r.revision=1
                """).param("quote", quote).param("owner", BankIntegrationTest.A)
                .param("venue", BankIntegrationTest.VA).param("version", version).query(String.class).single();
            var versionState = versionSnapshot(fixture, version);
            assertThat(versionSnapshot(fixture, original.get("version_id"))).isEqualTo(originalVersionSnapshot);
            if (retired) {
                assertThat(versionState).isEqualTo(revisedVersionSnapshot);
                assertThat(persisted).isEqualTo(revisedQuote);
            } else {
                revisedQuote = persisted;
                revisedVersionSnapshot = versionState;
            }
            assertThat(purchase(fixture, booking, original.get("version_id"))).isEqualTo(purchased);
            assertThat(purchase(fixture, newBooking, version)).isEqualTo(newPurchase);
            assertThat(paymentState(fixture)).isEqualTo(payments);
            if (Boolean.TRUE.equals(body.get("previewed"))) {
                assertThat(fixture.jdbc.sql("""
                    SELECT count(*) FROM cinenest_payments.bank_audit
                    WHERE request_id=:id AND actor_id=:staff AND action='PREVIEW' AND outcome='PERMIT'
                    """).param("id", bankRequestId).param("staff", BankIntegrationTest.STAFF)
                    .query(Long.class).single()).isEqualTo(1L);
            }
            assertThat(fixture.jdbc.sql("""
                SELECT state FROM cinenest_pricing.package_publication
                WHERE package_id=:pkg AND version_id=:version
                """).param("pkg", pkg).param("version", version).query(String.class).single())
                .isEqualTo(retired ? "INACTIVE" : "ACTIVE");
            assertThat(fixture.jdbc.sql("""
                SELECT count(*) FROM cinenest_pricing.configuration_audit
                WHERE actor_id=:publisher AND package_id=:pkg AND venue_id=:venue
                  AND publication_id IN (SELECT publication_id FROM cinenest_pricing.package_publication WHERE version_id=:version)
                  AND outcome='ALLOW'
                  AND action IN ('PUBLISH','DEACTIVATE')
                """).param("publisher", BankIntegrationTest.B).param("pkg", pkg)
                .param("venue", BankIntegrationTest.VA).param("version", version)
                .query(Long.class).single()).isEqualTo(retired ? 2L : 1L);
            assertThat(fixture.jdbc.sql("""
                SELECT count(*) FROM cinenest_booking.inventory_block a JOIN cinenest_booking.inventory_block b
                  ON a.room_id=b.room_id AND a.block_id<b.block_id
                WHERE a.blocking AND b.blocking AND a.kind='CONFIRMED' AND b.kind='CONFIRMED'
                  AND a.session_start<b.blocked_until AND b.session_start<a.blocked_until
                """).query(Long.class).single()).isZero();
            assertThat(fixture.jdbc.sql("""
                SELECT count(*) FROM cinenest_pricing.package_publication
                WHERE package_id=:pkg AND state='ACTIVE'
                """).param("pkg", pkg).query(Long.class).single()).isEqualTo(retired ? 0L : 1L);
            assertThat(fixture.jdbc.sql("""
                SELECT state FROM cinenest_pricing.package_publication WHERE version_id=:version
                """).param("version", UUID.fromString(unrelated.get("versionId").toString()))
                .query(String.class).single()).isEqualTo("ACTIVE");
            return Map.of("purchaseAndPaymentsUnchanged", true, "quoteVersionPersisted", true,
                "attributablePublicationAudit", true);
        }
    }

    @Test void realBrowserJourney() throws Exception {
        Path run=Path.of(System.getenv("CINENEST_E2E_RUN"));
        var fixture=new BankIntegrationTest();
        context.getAutowireCapableBeanFactory().autowireBean(fixture);
        fixture.fixtures();
        if(demoJourney())demoConfiguration=prepareDemo(fixture);
        boolean supervised = "supervised-h1".equals(System.getenv("CINENEST_E2E_MODE"));
        boolean adminQa = supervised && Boolean.getBoolean("cinenest.test.admin-qa");
        BankIntegrationTest.Browser staff = null;
        UUID pkg = null;
        Map<String,Object> publication = null;
        if (!cancellationJourney() && !fundingJourney() && !twoJourneys() && !packageBookingJourney()) {
            staff = fixture.signedIn(demoJourney()?demoSubject("operations"):"staff");
            if(demoJourney()){
                var draft=JSONObjectUtils.parse(fixture.draft("SYNTHETIC-DEMO"));draft.put("name","SYNTHETIC_TEST_ONLY CineNest 180-minute package");draft.put("basePriceMinor","250000");draft.put("extensionPriceMinor","100000");draft.put("extraGuestPriceMinor","80000");
                var created=fixture.write(staff,"POST",fixture.collection(),JSONObjectUtils.toJSONString(draft));assertThat(created.statusCode()).as(created.body()).isEqualTo(201);pkg=UUID.fromString(JSONObjectUtils.parse(created.body()).get("packageId").toString());
            }else pkg = fixture.setup(staff);
            fixture.grantTo(BankIntegrationTest.B, BankIntegrationTest.VA, "package.publish");
            var publicationRequest=fixture.request(false);
            if(operationalJourney()) publicationRequest.put("sessionFromLocal",java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Colombo")).minusMinutes(5).withNano(0).toString());
            publication = fixture.published(fixture.signedIn(demoJourney()?demoSubject("booking-inventory"):"customer-b"), pkg, publicationRequest);
            if(demoJourney())fixture.jdbc.sql("UPDATE cinenest_identity.access_grant SET state='REVOKED' WHERE account_id=:a AND permission='package.edit'").param("a",BankIntegrationTest.STAFF).update();
        }
        if (supervised) {
            fixture.jdbc.sql("DELETE FROM cinenest_identity.access_grant").update();
            fixture.jdbc.sql("DELETE FROM cinenest_identity.account_scope WHERE account_id=:staff AND venue_id=:venue")
                .param("staff", BankIntegrationTest.STAFF).param("venue", BankIntegrationTest.VB).update();
            var activeHolds = fixture.jdbc.sql("""
                SELECT count(*) FROM cinenest_booking.reservation_hold
                WHERE owner_id=:actor AND state='ACTIVE' AND expires_at>clock_timestamp()
                """).param("actor", BankIntegrationTest.A).query(Long.class).single();
            assertThat(activeHolds).as("supervised customer starts without an active hold").isZero();
            fixture.reviewerGrants();
            fixture.grant("booking.synthetic.read", "VENUE", BankIntegrationTest.VA);
            fixture.grant("inventory.read", "VENUE", BankIntegrationTest.VA);
            fixture.grant("inventory.block.create", "VENUE", BankIntegrationTest.VA);
        }
        var supervisedCustomer = supervised ? fixture.signedIn("customer-a") : null;
        var cancellation = cancellationJourney() ? CancellationFixture.prepare(fixture) : null;
        var pair = twoJourneys() ? new TwoJourneyFixture() : null;
        var funding = fundingJourney() ? new FundingFixture(fixture) : null;
        BankIntegrationTest.IDP.subject="customer-a";
        Files.write(run.resolve("identity.png"),BankIntegrationTest.IMAGE);
        Files.write(run.resolve("receipt.png"),BankIntegrationTest.RECEIPT);
        Map<String,Object> config=new HashMap<>();
        config.put("backend",fixture.base());config.put("room",BankIntegrationTest.RID.toString());
        config.put("ports",List.of(java.net.URI.create(fixture.base()).getPort(),java.net.URI.create(BankIntegrationTest.IDP.issuer()).getPort()));config.put("backendPid",ProcessHandle.current().pid());
        if(operationalJourney()) {
            if(!demoJourney()){fixture.reviewerGrants();fixture.bankGrants();fixture.financeGrants();
            for(String permission:List.of("booking.synthetic.read","guest.synthetic.read","guest.admission.synthetic.read",
                "guest.check-in.synthetic.record","booking.session.synthetic.read","booking.session.synthetic.start",
                "booking.session.synthetic.checkout","operations.synthetic.read","operations.synthetic.record",
                "operations.synthetic.assign","operations.synthetic.review","operations.synthetic.handoff.retry"))fixture.grantTo(BankIntegrationTest.STAFF,BankIntegrationTest.VA,permission);
            for(String permission:List.of("operations.synthetic.read","operations.synthetic.review"))fixture.grantTo(BankIntegrationTest.B,BankIntegrationTest.VA,permission);
            }else config.put("demo",demoConfiguration);
            // PostgreSQL supplies the schedule. No application clock or purchased interval is rewritten.
            String local=fixture.jdbc.sql("SELECT to_char((clock_timestamp()+interval '3 minutes') AT TIME ZONE 'Asia/Colombo','YYYY-MM-DD\"T\"HH24:MI')").query(String.class).single();
            config.put("operationalStartLocal",local);
            config.put("staffId",BankIntegrationTest.STAFF.toString());
        }
        config.put("otherVenue",BankIntegrationTest.VB.toString());config.put("venue",BankIntegrationTest.VA.toString());config.put("local",fixture.local());
        if (cancellation != null) config.put("cancellationBooking", cancellation.booking().toString());
        if (funding != null) config.put("fundingBooking", funding.purchase.booking().toString());
        var packageJourney = packageBookingJourney() ? new PackageBookingFixture(fixture) : null;
        if (packageJourney != null) config.put("packageBooking", packageJourney.configuration(fixture));
        if (adminQa) config.put("adminQa", prepareAdminQa(fixture, pkg, publication.get("versionId")));
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
            long until=System.nanoTime()+java.time.Duration.ofMinutes(supervised ? 28 : 7).toNanos();
            boolean supervisedAuthSealed = false;
            while(browser.isAlive()&&System.nanoTime()<until){
                Path command=run.resolve("command.json");
                if(Files.exists(command)){
                    var body=JSONObjectUtils.parse(Files.readString(command));Files.delete(command);
                    if (supervised && !(adminQa && List.of("admin-revoke", "admin-audit", "admin-financial").contains(body.get("op"))) && !List.of("subject", "seal-supervised-auth", "expire-session", "supervised-expire-hold", "supervised-booking-audit", "supervised-conflict-inspect", "supervised-block-inspect", "supervised-booking-count", "review", "r1-pending-inspect").contains(body.get("op"))) {
                        throw new IllegalStateException("Unsupported supervised fixture command");
                    }
                    Map<String,Object> response=new HashMap<>();
                    try {
                        String op=body.get("op").toString();
                        if(operationalJourney() && op.startsWith("operational-")) {
                            response.putAll(operationalControl(fixture,op,body));
                        } else if (packageJourney != null && op.equals("package-confirm")) {
                            response.putAll(packageJourney.confirm(fixture, body));
                        } else if (packageJourney != null && op.equals("package-inspect")) {
                            response.putAll(packageJourney.inspect(fixture, body));
                        } else if (packageJourney != null && !List.of("subject", "prepare-expiring-quote",
                                "await-quote-expiry", "inspect-expired-quote").contains(op)) {
                            throw new IllegalArgumentException("Unsupported package-to-booking fixture command");
                        } else if (pair != null && !op.equals("subject")) {
                            response.putAll(pair.run(fixture, op, response));
                            response.remove("changedTables");
                        } else if (funding != null && op.equals("funding-prepare")) {
                            response.putAll(funding.prepare(fixture));
                        } else if (funding != null && List.of("funding-before", "funding-committed", "funding-expired").contains(op)) {
                            response.putAll(funding.inspect(fixture, op, body.get("commandKey")));
                        } else if (funding != null && !op.equals("subject")) {
                            throw new IllegalArgumentException("Unsupported funding fixture command");
                        } else if (cancellation != null && List.of("cancellation-before", "cancellation-committed").contains(op)) {
                            response.putAll(cancellation.inspect(fixture, op, body.get("commandKey")));
                        } else if (cancellation != null && !op.equals("subject")) {
                            throw new IllegalArgumentException("Unsupported cancellation fixture command");
                        } else if (adminQa && op.equals("admin-revoke")) {
                            int changed = fixture.jdbc.sql("""
                                DELETE FROM cinenest_identity.http_session WHERE principal_name=:actor
                                """).param("actor", adminId("revoked").toString()).update();
                            assertThat(changed).isPositive();
                        } else if (adminQa && op.equals("admin-audit")) {
                            response.put("writes", fixture.jdbc.sql("""
                                SELECT count(*) FROM cinenest_rooms.configuration_audit
                                WHERE actor_id=:actor AND venue_id=:venue AND resource_id=:venue
                                  AND action='UPDATE' AND outcome='ALLOW'
                                """).param("actor", adminId("administrator"))
                                .param("venue", BankIntegrationTest.VA).query(Long.class).single());
                        } else if (adminQa && op.equals("admin-financial")) {
                            response.put("effects", fixture.jdbc.sql("""
                                SELECT (SELECT count(*) FROM cinenest_payments.payment_allocation) AS allocations,
                                       (SELECT count(*) FROM cinenest_payments.incoming_payment_credit) AS credits,
                                       (SELECT count(*) FROM cinenest_payments.bank_credit_verification) AS verified,
                                       (SELECT count(*) FROM cinenest_access.access_credential) AS credentials,
                                       (SELECT count(*) FROM cinenest_booking.booking) AS bookings
                                """).query().singleRow());
                        } else if(supervised && op.equals("supervised-expire-hold")){
                            UUID holdId = UUID.fromString(body.get("holdId").toString());
                            int changed = fixture.jdbc.sql("""
                                UPDATE cinenest_booking.reservation_hold
                                SET created_at=clock_timestamp()-interval '20 minutes',
                                    expires_at=clock_timestamp()-interval '1 minute'
                                WHERE hold_id=:hold AND owner_id=:actor AND state='ACTIVE'
                                """).param("hold", holdId).param("actor", BankIntegrationTest.A).update();
                            assertThat(changed).isEqualTo(1);
                        }else if(supervised && op.equals("supervised-conflict-inspect")){
                            UUID hold = UUID.fromString(body.get("holdId").toString());
                            var holds = fixture.jdbc.sql("SELECT count(*) FROM cinenest_booking.reservation_hold").query(Long.class).single();
                            var original = fixture.jdbc.sql("""
                                SELECT count(*) FROM cinenest_booking.reservation_hold h
                                JOIN cinenest_booking.inventory_block b ON b.block_id=h.block_id
                                WHERE h.hold_id=:hold AND h.owner_id=:actor AND h.state='ACTIVE' AND b.blocking
                                """).param("hold", hold).param("actor", BankIntegrationTest.A).query(Long.class).single();
                            assertThat(holds).isEqualTo(1L);
                            assertThat(original).isEqualTo(1L);
                        }else if(supervised && op.equals("supervised-block-inspect")){
                            UUID block = UUID.fromString(body.get("blockId").toString());
                            var saved = fixture.jdbc.sql("""
                                SELECT count(*) FROM cinenest_booking.room_block_audit a
                                JOIN cinenest_booking.inventory_block b ON b.block_id=a.block_id
                                WHERE a.block_id=:block AND a.actor_id=:actor AND a.venue_id=:venue
                                  AND a.room_id=:room AND a.action='CREATE' AND b.blocking AND b.kind='MAINTENANCE'
                                """).param("block", block).param("actor", BankIntegrationTest.STAFF)
                                .param("venue", BankIntegrationTest.VA).param("room", BankIntegrationTest.RID)
                                .query(Long.class).single();
                            assertThat(saved).isEqualTo(1L);
                        }else if(supervised && op.equals("supervised-booking-count")){
                            response.put("bookings", fixture.jdbc.sql("SELECT count(*) FROM cinenest_booking.booking")
                                .query(Long.class).single());
                        }else if(supervised && op.equals("supervised-booking-audit")){
                            var correlation = UUID.fromString(body.get("correlation").toString());
                            var count = fixture.jdbc.sql("""
                                SELECT count(*) FROM cinenest_booking.staff_booking_read_audit
                                WHERE correlation_id=:correlation AND actor_id=:actor AND venue_id=:venue
                                  AND action='LIST' AND outcome='ALLOW'
                                """).param("correlation", correlation).param("actor", BankIntegrationTest.STAFF)
                                .param("venue", BankIntegrationTest.VA).query(Long.class).single();
                            assertThat(count).isEqualTo(1);
                        }else if(op.equals("r1-no-request")){
 UUID q=UUID.fromString(body.get("quoteId").toString());assertThat(fixture.jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_payment_request WHERE quote_id=:q").param("q",q).query(Long.class).single()).isZero();
 assertThat(fixture.jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_command").query(Long.class).single()).isZero();response.put("noRequestOrReceipt",true);
 }else if(op.equals("r1-pending-inspect")){
 response.putAll(release1Pending(fixture,body));
 }else if(op.equals("r1-race-inspect")){
 @SuppressWarnings("unchecked") var keys=(List<String>)body.get("keys");assertThat(keys).hasSize(2);
 var receipts=fixture.jdbc.sql("SELECT count(*) FROM cinenest_booking.inventory_command WHERE command_key IN (:a,:b) AND action='CREATE'").param("a",UUID.fromString(keys.get(0))).param("b",UUID.fromString(keys.get(1))).query(Long.class).single();assertThat(receipts).isEqualTo(1L);
 var held=fixture.jdbc.sql("SELECT h.state,b.blocking FROM cinenest_booking.reservation_hold h JOIN cinenest_booking.inventory_block b ON b.block_id=h.block_id WHERE h.hold_id=:h").param("h",UUID.fromString(body.get("holdId").toString())).query().singleRow();
 boolean released=Boolean.TRUE.equals(body.get("released"));assertThat(held.get("state")).isEqualTo(released?"RELEASED":"ACTIVE");assertThat(held.get("blocking")).isEqualTo(!released);
 response.put("createReceipts",receipts);response.put("blocking",held.get("blocking"));response.put("overlapPairs",release1OverlapPairs(fixture));
 }else if(op.equals("extension-cross-booking-fixture")){
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
                            if (supervised && !"VERIFIED".equals(body.get("verdict"))) throw new IllegalArgumentException("Supervised review only verifies its fixture");
                            fixture.work();
                            if (!supervised) fixture.reviewerGrants();
                            var customer = supervised ? supervisedCustomer : fixture.signedIn("customer-a");
                            var r=fixture.json(customer.get("/api/verification/requests/"+body.get("id")));
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
                        }else if(supervised && op.equals("seal-supervised-auth")){
                            supervisedAuthSealed = true;
                            BankIntegrationTest.IDP.subject = "unassociated-supervised-subject";
                        }else if(op.equals("subject")){
                            String subject=body.get("subject").toString();
                            if(supervisedAuthSealed || !(List.of("customer-a","customer-b","staff").contains(subject)
                                    || (demoJourney() && List.of("customer","operations","booking-inventory","bank-verifier","bank-allocator","admission","cleaner","independent-turnover-reviewer").stream().anyMatch(role->demoSubject(role).equals(subject)))
                                    || (packageJourney != null && subject.equals("qa-admin-history-observer"))
                                    || (adminQa && ADMIN_ROLES.stream().map(role -> "qa-admin-" + role).anyMatch(subject::equals))))throw new IllegalArgumentException();
                            BankIntegrationTest.IDP.subject=subject;
                        }else if(op.equals("expire-session")){
                            int expired=fixture.jdbc.sql("UPDATE cinenest_identity.http_session SET last_access_time=0,expiry_time=0 WHERE principal_name=:p AND max_inactive_interval>0").param("p",BankIntegrationTest.A.toString()).update();
                            assertThat(expired).isPositive();response.put("sessionDeadlineExpired",true);response.put("expiredSessions",expired);
                        }else throw new IllegalArgumentException("Unsupported test control");
                        response.put("controlOk",true);
                    }catch(Exception|AssertionError failure){
                        var diagnostic = new HashMap<String,Object>();
                        diagnostic.put("controlOk", false);
                        diagnostic.put("changedTables", response.getOrDefault("changedTables", List.of()));
                        diagnostic.put("newAuditActions", response.getOrDefault("newAuditActions", List.of()));
                        if (pair != null || packageJourney != null) {
                            diagnostic.put("fixtureError", failure.getClass().getSimpleName());
                            String fixtureClass = pair != null ? TwoJourneyFixture.class.getName() : PackageBookingFixture.class.getName();
                            java.util.Arrays.stream(failure.getStackTrace())
                                .filter(frame -> frame.getClassName().startsWith(fixtureClass))
                                .findFirst().ifPresent(frame -> diagnostic.put("fixtureLine", frame.getLineNumber()));
                        }
                        if(operationalJourney()){
                            diagnostic.put("fixtureError",failure.getClass().getSimpleName());
                            java.util.Arrays.stream(failure.getStackTrace()).filter(frame->frame.getClassName().equals(SyntheticJourneyIT.class.getName())).findFirst().ifPresent(frame->diagnostic.put("fixtureLine",frame.getLineNumber()));
                        }
                        response = diagnostic;
                    }
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
    private static long release1OverlapPairs(BankIntegrationTest f){
 assertThat(f.jdbc.sql("SELECT count(*) FROM pg_constraint WHERE conrelid='cinenest_booking.inventory_block'::regclass AND contype='x' AND convalidated").query(Long.class).single()).isEqualTo(1L);
 long result=f.jdbc.sql("SELECT count(*) FROM cinenest_booking.inventory_block a JOIN cinenest_booking.inventory_block b ON a.block_id<b.block_id AND a.room_id=b.room_id AND a.blocking AND b.blocking AND tstzrange(a.session_start,a.blocked_until,'[)') && tstzrange(b.session_start,b.blocked_until,'[)')").query(Long.class).single();assertThat(result).isZero();return result;
    }
    private static Map<String,Object> release1Pending(BankIntegrationTest f,Map<String,Object> body){
 UUID quote=UUID.fromString(body.get("quoteId").toString()),request=UUID.fromString(body.get("requestId").toString()),key=UUID.fromString(body.get("commandKey").toString());
 var bound=f.jdbc.sql("SELECT r.state,r.review_state,r.revision,r.amount_minor,r.currency,h.state AS hold_state,b.blocking,r.snapshot=qr.snapshot AND r.quote_hash=qr.content_hash AND r.amount_minor=(qr.snapshot->>'totalMinor')::bigint AND r.currency=qr.snapshot->>'currency' AND r.owner_id=q.owner_id AND r.venue_id=q.venue_id AND r.hold_id=q.hold_id AND a.actor_id=r.owner_id AND a.content_hash=r.quote_hash AND bh.owner_id=r.owner_id AND bh.hold_id=h.hold_id AND bh.expires_at=h.expires_at AND bh.expires_at=r.hold_until AND b.venue_id=r.venue_id AND b.room_id=q.room_id AS binding_verified FROM cinenest_payments.bank_payment_request r JOIN cinenest_pricing.quotation q ON q.quote_id=r.quote_id JOIN cinenest_pricing.quotation_revision qr ON qr.quote_id=q.quote_id AND qr.revision=r.quote_revision JOIN cinenest_pricing.quotation_acceptance a ON a.quote_id=q.quote_id AND a.revision=qr.revision JOIN cinenest_booking.reservation_hold h ON h.hold_id=r.hold_id JOIN cinenest_booking.inventory_block b ON b.block_id=h.block_id JOIN cinenest_booking.booking_bank_request_hold bh ON bh.request_id=r.request_id WHERE r.request_id=:r AND r.quote_id=:q AND r.owner_id=:owner AND r.venue_id=:venue")
 .param("r",request).param("q",quote).param("owner",BankIntegrationTest.A).param("venue",BankIntegrationTest.VA).query().singleRow();
 assertThat(bound.get("state")).isEqualTo("OPEN");assertThat(bound.get("review_state")).isEqualTo("PENDING");assertThat(bound.get("revision")).isEqualTo(0L);assertThat(bound.get("hold_state")).isEqualTo("ACTIVE");assertThat(bound.get("blocking")).isEqualTo(true);assertThat(bound.get("binding_verified")).isEqualTo(true);
 Map<String,Object> out=new HashMap<>();out.put("requestState",bound.get("state"));out.put("reviewState",bound.get("review_state"));out.put("bindingVerified",true);
 long requests=f.jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_payment_request").query(Long.class).single();assertThat(requests).isEqualTo(1L);out.put("requests",requests);
 long receipts=f.jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_command WHERE actor_id=:a AND command_key=:k AND response->>'id'=:r").param("a",BankIntegrationTest.A).param("k",key).param("r",request.toString()).query(Long.class).single();assertThat(receipts).isEqualTo(1L);out.put("commandReceipts",receipts);
 for(var pair:Map.of("credits","cinenest_payments.incoming_payment_credit","verifications","cinenest_payments.bank_credit_verification","allocations","cinenest_payments.payment_allocation","bookings","cinenest_booking.booking","credentials","cinenest_access.access_credential","provisioningWork","cinenest_access.access_work").entrySet()){
  long count=f.jdbc.sql("SELECT count(*) FROM "+pair.getValue()).query(Long.class).single();assertThat(count).isZero();out.put(pair.getKey(),count);
 }
 out.put("overlapPairs",release1OverlapPairs(f));out.put("databaseExclusionVerified",true);return out;
    }
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
