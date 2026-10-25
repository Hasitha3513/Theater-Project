package com.cinenest;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;import org.springframework.test.context.DynamicPropertyRegistry;import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"cinenest.booking.extension-reservations.enabled=true","cinenest.booking.extension-reservations.worker-enabled=false","cinenest.booking.extension-reservations.rate-count=5","cinenest.booking.extension-reservations.rate-seconds=60","cinenest.booking.extension-reservations.approval-reference=CN-EXTENSION-ADMISSION-BUDGET-APPROVAL-001 synthetic test","cinenest.booking.amendments.enabled=true","cinenest.bank.refunds.enabled=true","cinenest.bank.refunds.maximum-minor=50000","spring.datasource.hikari.maximum-pool-size=4","spring.datasource.hikari.minimum-idle=0","cinenest.bank.enabled=true","cinenest.bank.ledger.enabled=true","cinenest.bank.ledger.resolution-seconds=60","cinenest.bank.worker-enabled=false","cinenest.bank.policy-version=SYNTHETIC-BANK1","cinenest.bank.lead-seconds=60","cinenest.bank.request-seconds=3000","cinenest.bank.initiation-seconds=900","cinenest.bank.submission-seconds=1200","cinenest.bank.review-seconds=1800","cinenest.bank.hold-seconds=2400","cinenest.bank.escalation-seconds=2500","cinenest.bank.upload-seconds=60","cinenest.bank.retention-seconds=3600","cinenest.bank.cleanup-seconds=1","cinenest.bank.review-opens=00:00","cinenest.bank.review-closes=23:59:59","cinenest.verification.enabled=true","cinenest.verification.worker-enabled=false","cinenest.verification.policy-version=SYNTHETIC-V1","cinenest.verification.upload-seconds=60","cinenest.verification.retention-seconds=3600","cinenest.verification.decision-seconds=600","cinenest.verification.cleanup-seconds=1","cinenest.pricing.synthetic-quotes.enabled=true","cinenest.pricing.synthetic-quotes.tax-profile=SYNTHETIC_FIXED_MINOR_V1","cinenest.pricing.synthetic-quotes.tax-minor=7","cinenest.pricing.synthetic-quotes.approval-reference=SYNTHETIC test fixture only","cinenest.pricing.synthetic-quotes.rate-count=100","cinenest.pricing.synthetic-quotes.rate-seconds=60","cinenest.pricing.publication-worker.enabled=false","cinenest.booking.holds.enabled=true","cinenest.booking.holds.rate-count=100","cinenest.booking.holds.rate-seconds=60","cinenest.booking.holds.approval-reference=SYNTHETIC owner-approved inventory test"})
@org.springframework.test.context.ActiveProfiles("synthetic-quotation")
@EnabledIfEnvironmentVariable(named="CINENEST_E2E_RUN", matches="/tmp/cinenest-e2e-[^/]+")
class BankInstructionsJourneyIT {
 @Autowired ConfigurableApplicationContext context;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry p){BankIntegrationTest.properties(p);for(String f:List.of("cinenest.iot.enabled","cinenest.access.enabled","cinenest.notifications.enabled","cinenest.reporting.synthetic-bank.enabled","cinenest.rooms.turnover.enabled"))p.add(f,()->"false");}
 @Test void realBrowserInstructions()throws Exception{
  var f=new BankIntegrationTest(){int day,pack;@Override Map<String,Object> held(Browser b,Map<String,Object> req)throws Exception{var r=create(b,req);System.out.println("BI_SAFE_HOLD_STATUS="+r.statusCode());assertThat(r.statusCode()).isEqualTo(200);return JSONObjectUtils.parse(r.body());}@Override String local(){return java.time.LocalDateTime.parse(super.local()).plusDays(day++).toString();}@Override UUID setup(Browser editor)throws Exception{grant("package.edit","VENUE",VA);return create(editor,"BI-PUBLIC-"+(pack++));}};context.getAutowireCapableBeanFactory().autowireBean(f);f.fixtures();
  var a=f.signedIn("customer-a");var legacy=f.bank(f.signedIn("customer-b"));var body=f.bankBody(a);
  UUID v1=f.instructionFixture("SYNTHETIC-BANK1","CN-R1-BI-V1"),grant=f.grant("bank.instructions.read","VENUE",BankIntegrationTest.VA);f.bankGrants();
  var bad=new ArrayList<UUID>();
  // Isolated insert fixtures distinguish missing reference,wrong version and digest mismatch.
  UUID ref=BankIntegrationTest.id("CN-R1-BI-NONPAYABLE"),rv=BankIntegrationTest.id("CN-R1-BI-V1");
  for(int i=0;i<3;i++){UUID id=UUID.randomUUID();f.jdbc.sql("INSERT INTO cinenest_payments.bank_instruction_revision SELECT :id,venue_id,:version,mode,:ref,:rv,:digest,effective_from,effective_until,proposer_id,approver_id,approval_reference,approval_sha256,approved_at,registered_at FROM cinenest_payments.bank_instruction_revision WHERE instruction_revision_id=:source").param("id",id).param("version","SYNTHETIC-FAULT-"+i).param("ref",i==0?UUID.randomUUID():ref).param("rv",i==1?UUID.randomUUID():rv).param("digest",i==2?"0".repeat(64):BankIntegrationTest.hash(("NON-PAYABLE STAGING FIXTURE|NO REAL RECEIVING ACCOUNT|DO NOT TRANSFER MONEY|"+rv).getBytes(java.nio.charset.StandardCharsets.UTF_8))).param("source",v1).update();bad.add(id);}
  Path run=Path.of(System.getenv("CINENEST_E2E_RUN"));Map<String,Object> config=new HashMap<>();config.put("backend",f.base());config.put("venue",BankIntegrationTest.VA);config.put("otherVenue",BankIntegrationTest.VB);config.put("revision",v1);config.put("bad",bad);config.put("legacy",legacy.get("id"));config.put("body",body);config.put("ports",List.of(java.net.URI.create(f.base()).getPort(),java.net.URI.create(BankIntegrationTest.IDP.issuer()).getPort()));
  Files.writeString(run.resolve("config.json"),JSONObjectUtils.toJSONString(config));Process browser=null;CountDownLatch release=new CountDownLatch(1);CompletableFuture<Void> lock=null;
  try(var enabled=f.instructionsEnabled()){
   browser=new ProcessBuilder("node",Path.of("../frontend/tests/bank-instructions-e2e.mjs").toAbsolutePath().toString()).redirectErrorStream(true).redirectOutput(run.resolve("private-browser.log").toFile()).start();long until=System.nanoTime()+java.time.Duration.ofMinutes(5).toNanos();
   while(browser.isAlive()&&System.nanoTime()<until){Path command=run.resolve("command.json");if(Files.exists(command)){
    var request=JSONObjectUtils.parse(Files.readString(command));Files.delete(command);Map<String,Object> reply=new HashMap<>();
    try{switch(request.get("op").toString()){
     case "subject"-> {String subject=request.get("subject").toString();assertThat(List.of("staff","customer-a","customer-b")).contains(subject);BankIntegrationTest.IDP.subject=subject;}
     case "revision2"->reply.put("revision",f.instructionFixture("SYNTHETIC-BANK2","CN-R1-BI-V2"));
     case "other-read"->f.grant("bank.instructions.read","VENUE",BankIntegrationTest.VB);
     case "revoke"->f.jdbc.sql("UPDATE cinenest_identity.access_grant SET state='REVOKED' WHERE grant_id=:g").param("g",grant).update();
     case "restore"->f.jdbc.sql("UPDATE cinenest_identity.access_grant SET state='ACTIVE' WHERE grant_id=:g").param("g",grant).update();
     case "request-deny"->f.jdbc.sql("UPDATE cinenest_identity.access_grant SET state='REVOKED' WHERE permission='bank.evidence.read'").update();
     case "request-restore"->f.grant("bank.evidence.read","VENUE",BankIntegrationTest.VA);
     case "expire"-> {int n=f.jdbc.sql("UPDATE cinenest_identity.http_session SET last_access_time=0,expiry_time=0 WHERE principal_name=:a").param("a",BankIntegrationTest.STAFF.toString()).update();assertThat(n).isPositive();}
     case "audit-fail"->f.jdbc.sql("ALTER TABLE cinenest_payments.bank_instruction_audit ADD CONSTRAINT instruction_e2e_fault CHECK(false) NOT VALID").update();
     case "audit-restore"->f.jdbc.sql("ALTER TABLE cinenest_payments.bank_instruction_audit DROP CONSTRAINT instruction_e2e_fault").update();
     case "lock"->{var entered=new CountDownLatch(1);var gate=release;lock=CompletableFuture.runAsync(()->new org.springframework.transaction.support.TransactionTemplate(f.transactions).executeWithoutResult(s->{f.jdbc.sql("SELECT grant_id FROM cinenest_identity.access_grant WHERE grant_id=:g FOR UPDATE").param("g",grant).query(UUID.class).single();entered.countDown();try{if(!gate.await(8,TimeUnit.SECONDS))throw new AssertionError("Lock gate timeout");}catch(InterruptedException e){throw new RuntimeException(e);}}));assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();}
     case "unlock"->{release.countDown();lock.get(3,TimeUnit.SECONDS);}
     case "inspect"->{
      assertThat(f.jdbc.sql("SELECT max(version::integer) FROM public.flyway_schema_history WHERE success AND version IS NOT NULL").query(Integer.class).single()).isEqualTo(26);
      long requests=f.jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_payment_request").query(Long.class).single();assertThat(requests).isEqualTo(2);
      var binding=f.jdbc.sql("SELECT instruction_revision_id FROM cinenest_payments.bank_instruction_request_binding WHERE request_id=:r").param("r",UUID.fromString(request.get("request").toString())).query(UUID.class).single();assertThat(binding).isEqualTo(v1);
      assertThat(f.jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_instruction_request_binding").query(Long.class).single()).isEqualTo(1);
      for(String table:List.of("cinenest_payments.incoming_payment_credit","cinenest_payments.payment_allocation","cinenest_booking.booking","cinenest_access.access_credential","cinenest_access.access_command","cinenest_access.access_work","cinenest_notifications.notification_event","cinenest_iot.device")){long n=f.jdbc.sql("SELECT count(*) FROM "+table).query(Long.class).single();assertThat(n).isZero();reply.put(table,n);}
      for(String table:List.of("bank_instruction_revision","bank_instruction_request_binding"))assertThatThrownBy(()->f.jdbc.sql("UPDATE cinenest_payments."+table+" SET instruction_version=instruction_version").update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
      assertThatThrownBy(()->f.jdbc.sql("INSERT INTO cinenest_payments.bank_instruction_revision SELECT gen_random_uuid(),venue_id,instruction_version,mode,reference_id,reference_version,content_sha256,effective_from,effective_until,proposer_id,approver_id,approval_reference,approval_sha256,approved_at,registered_at FROM cinenest_payments.bank_instruction_revision LIMIT 1").update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
      long allowed=f.jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_instruction_audit WHERE outcome='ALLOW' AND reason_code='SCOPED_READ'").query(Long.class).single();assertThat(allowed).isPositive();
      assertThat(f.jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_instruction_audit WHERE outcome='ALLOW' AND action='REQUEST_BOUND'").query(Long.class).single()).isEqualTo(1);
      reply.put("allowedReads",allowed);reply.put("requests",requests);reply.put("bindings",1);reply.put("versionPinned",true);reply.put("immutable",true);reply.put("flyway",26);
     }
     default->throw new IllegalArgumentException();}
     reply.put("ok",true);
    }catch(Exception|AssertionError failure){reply=Map.of("ok",false);}
    Files.writeString(run.resolve("reply.tmp"),JSONObjectUtils.toJSONString(reply));Files.move(run.resolve("reply.tmp"),run.resolve("reply.json"),StandardCopyOption.ATOMIC_MOVE);
   }Thread.sleep(20);}
   assertThat(browser.waitFor(1,TimeUnit.SECONDS)).isTrue();assertThat(browser.exitValue()).isZero();
  }finally{release.countDown();if(browser!=null&&browser.isAlive()){browser.destroy();browser.waitFor(3,TimeUnit.SECONDS);}BankIntegrationTest.IDP.close();}
 }
}
