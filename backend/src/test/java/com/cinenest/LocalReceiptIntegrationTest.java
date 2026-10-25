package com.cinenest;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;import org.springframework.test.context.DynamicPropertyRegistry;import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"cinenest.booking.extension-reservations.enabled=true","cinenest.booking.extension-reservations.worker-enabled=false","cinenest.booking.extension-reservations.rate-count=5","cinenest.booking.extension-reservations.rate-seconds=60","cinenest.booking.extension-reservations.approval-reference=CN-EXTENSION-ADMISSION-BUDGET-APPROVAL-001 synthetic test","cinenest.booking.amendments.enabled=true","cinenest.bank.refunds.enabled=true","cinenest.bank.refunds.maximum-minor=50000","spring.datasource.hikari.maximum-pool-size=4","spring.datasource.hikari.minimum-idle=0","cinenest.bank.enabled=true","cinenest.bank.ledger.enabled=true","cinenest.bank.ledger.resolution-seconds=60","cinenest.bank.worker-enabled=false","cinenest.bank.policy-version=SYNTHETIC-BANK1","cinenest.bank.lead-seconds=60","cinenest.bank.request-seconds=3000","cinenest.bank.initiation-seconds=900","cinenest.bank.submission-seconds=1200","cinenest.bank.review-seconds=1800","cinenest.bank.hold-seconds=2400","cinenest.bank.escalation-seconds=2500","cinenest.bank.upload-seconds=60","cinenest.bank.retention-seconds=3600","cinenest.bank.cleanup-seconds=1","cinenest.bank.review-opens=00:00","cinenest.bank.review-closes=23:59:59","cinenest.verification.enabled=true","cinenest.verification.worker-enabled=false","cinenest.verification.policy-version=SYNTHETIC-V1","cinenest.verification.upload-seconds=60","cinenest.verification.retention-seconds=3600","cinenest.verification.decision-seconds=600","cinenest.verification.cleanup-seconds=1","cinenest.pricing.synthetic-quotes.enabled=true","cinenest.pricing.synthetic-quotes.tax-profile=SYNTHETIC_FIXED_MINOR_V1","cinenest.pricing.synthetic-quotes.tax-minor=7","cinenest.pricing.synthetic-quotes.approval-reference=SYNTHETIC test fixture only","cinenest.pricing.synthetic-quotes.rate-count=100","cinenest.pricing.synthetic-quotes.rate-seconds=60","cinenest.pricing.publication-worker.enabled=false","cinenest.booking.holds.enabled=true","cinenest.booking.holds.rate-count=100","cinenest.booking.holds.rate-seconds=60","cinenest.booking.holds.approval-reference=SYNTHETIC owner-approved inventory test"})
@org.springframework.test.context.ActiveProfiles("synthetic-quotation")
@EnabledIfEnvironmentVariable(named="CINENEST_LOCAL_RECEIPT_INTEGRATION",matches="true")
class LocalReceiptIntegrationTest {
 @Autowired ConfigurableApplicationContext context;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry p){BankIntegrationTest.properties(p);
  for(String key:List.of("journal","namespace","endpoint","bucket","access","secret","socket"))p.add("cinenest.bank.local-docker."+key,()->System.getenv("QUAL_LOCAL_"+key.toUpperCase()));
  for(String feature:List.of("cinenest.bank.ledger.enabled","cinenest.access.enabled","cinenest.iot.enabled"))p.add(feature,()->"false");
  p.add("cinenest.bank.local-docker.enabled",()->"true");p.add("cinenest.bank.encryption-key",()->System.getenv("QUAL_JOURNAL_KEY"));
 }
 static void pass(String name){System.out.println("LOCAL_INTEGRATION_PASS "+name);}
 @Test void realApiWorkflow()throws Exception{
  assertThat(context.containsBean("ledgerController")).isFalse();
  var f=new BankIntegrationTest();context.getAutowireCapableBeanFactory().autowireBean(f);f.fixtures();
  Object store=context.getBean("localDockerEvidenceStore");
  Object bucket=org.springframework.test.util.ReflectionTestUtils.invokeMethod(store,"request","PUT","/receipts","",new byte[0],false,4096);assertThat((Integer)org.springframework.test.util.ReflectionTestUtils.invokeMethod(bucket,"status")).isIn(200,204);
  Object version=org.springframework.test.util.ReflectionTestUtils.invokeMethod(store,"request","PUT","/receipts","versioning=","<VersioningConfiguration xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\"><Status>Enabled</Status></VersioningConfiguration>".getBytes(),false,4096);assertThat((Integer)org.springframework.test.util.ReflectionTestUtils.invokeMethod(version,"status")).isEqualTo(200);
  try {
   var a=f.signedIn("customer-a");var b=f.signedIn("customer-b");var staff=f.signedIn("staff");f.bankGrants();
   var request=f.claim(a,f.bank(a));var allocated=f.json(f.write(a,"POST","/api/bank/claims/"+request.get("claimId")+"/uploads",JSONObjectUtils.toJSONString(f.cmd(((Number)request.get("revision")).longValue()))));
   String id=allocated.get("uploadId").toString(),evidencePath=f.financePath(allocated)+"/evidence/"+id;
   assertThat(f.bankUpload(b,id,BankIntegrationTest.RECEIPT,"image/png").statusCode()).isEqualTo(404);pass("wrong-owner-upload-denied");
   assertThat(staff.get(evidencePath.replace(BankIntegrationTest.VA.toString(),BankIntegrationTest.VB.toString())).statusCode()).isEqualTo(403);
   assertThat(f.client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(f.base()+"/api/bank/uploads/"+id+"/content")).header("Cookie",a.cookie).header("Content-Type","image/png").PUT(java.net.http.HttpRequest.BodyPublishers.ofByteArray(BankIntegrationTest.RECEIPT)).build(),java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);pass("wrong-venue-and-csrf-denied");
   // Real upstream commit, then deliberately close the forwarding connection without any HTTP response.
   var upstream=new java.util.concurrent.atomic.AtomicInteger();var proxy=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
   proxy.createContext("/upload",exchange->{try{byte[] data=exchange.getRequestBody().readNBytes(5242881);upstream.set(f.bankUpload(a,id,data,"image/png").statusCode());}catch(Exception failure){upstream.set(500);}finally{exchange.close();}});proxy.start();
   try(var transport=new java.net.Socket("127.0.0.1",proxy.getAddress().getPort())){transport.setSoTimeout(15000);var output=transport.getOutputStream();output.write(("PUT /upload HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\nContent-Length: "+BankIntegrationTest.RECEIPT.length+"\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));output.write(BankIntegrationTest.RECEIPT);output.flush();assertThat(transport.getInputStream().read()).isEqualTo(-1);assertThat(upstream.get()).isEqualTo(200);}finally{proxy.stop(0);}
   var current=f.json(a.get(f.bankPath(allocated)));assertThat(current.get("evidence").toString()).contains("QUARANTINED");assertThat(f.bankUpload(a,id,BankIntegrationTest.RECEIPT,"image/png").statusCode()).isEqualTo(409);assertThat(staff.get(evidencePath).statusCode()).isEqualTo(409);pass("upload-lost-result-status-and-no-byte-retry");
   String command=JSONObjectUtils.toJSONString(f.cmd(((Number)current.get("revision")).longValue()));String complete="/api/bank/uploads/"+id+"/complete";
   try(var pool=Executors.newVirtualThreadPerTaskExecutor()){var x=pool.submit(()->f.write(a,"POST",complete,command));var y=pool.submit(()->f.write(a,"POST",complete,command));assertThat(x.get().statusCode()).isEqualTo(200);assertThat(y.get().statusCode()).isEqualTo(200);}pass("concurrent-completion-command-replay");
   Path socket=Path.of(System.getenv("QUAL_LOCAL_SOCKET")),away=socket.resolveSibling("unavailable.sock");Files.move(socket,away);try{f.bankWork();}finally{Files.move(away,socket);}
   assertThat(f.json(a.get(f.bankPath(current))).get("evidence").toString()).contains("QUARANTINED");assertThat(staff.get(evidencePath).statusCode()).isEqualTo(409);pass("real-unix-scanner-unavailable-quarantine");
   f.jdbc.sql("UPDATE cinenest_payments.bank_work SET due_at=clock_timestamp()-interval '1 second' WHERE kind='SCAN'").update();
   f.jdbc.sql("ALTER TABLE cinenest_payments.bank_audit ADD CONSTRAINT local_scan_failure CHECK(action<>'WORK_SCAN')").update();try{f.bankWork();}finally{f.jdbc.sql("ALTER TABLE cinenest_payments.bank_audit DROP CONSTRAINT local_scan_failure").update();}
   assertThat(f.json(a.get(f.bankPath(current))).get("evidence").toString()).contains("QUARANTINED");pass("scan-audit-rollback-with-external-preview-retained");
   f.jdbc.sql("UPDATE cinenest_payments.bank_work SET due_at=clock_timestamp()-interval '1 second' WHERE kind='SCAN'").update();
   try(var pool=Executors.newVirtualThreadPerTaskExecutor()){var x=pool.submit(()->f.bankWork());var y=pool.submit(()->f.bankWork());x.get();y.get();}
   assertThat(f.json(a.get(f.bankPath(current))).get("evidence").toString()).contains("READY");assertThat(staff.get(evidencePath).statusCode()).isEqualTo(200);pass("real-clamav-ready-and-protected-preview");
   f.jdbc.sql("ALTER TABLE cinenest_payments.bank_audit ADD CONSTRAINT local_preview_failure CHECK(action<>'PREVIEW') NOT VALID").update();try{assertThat(staff.get(evidencePath).statusCode()).isEqualTo(409);}finally{f.jdbc.sql("ALTER TABLE cinenest_payments.bank_audit DROP CONSTRAINT local_preview_failure").update();}pass("preview-mandatory-audit-fail-closed");
   assertThat(a.get(evidencePath).statusCode()).isEqualTo(403);assertThat(b.get(f.bankPath(current)).statusCode()).isEqualTo(404);
   var grant=f.jdbc.sql("SELECT grant_id FROM cinenest_identity.access_grant WHERE permission='bank.evidence.read' LIMIT 1").query(UUID.class).single();
   f.jdbc.sql("UPDATE cinenest_identity.access_grant SET state='REVOKED' WHERE grant_id=:g").param("g",grant).update();assertThat(staff.get(evidencePath).statusCode()).isEqualTo(403);pass("owner-preview-and-revoked-authority-denied");
   for(String table:List.of("cinenest_payments.incoming_payment_credit","cinenest_payments.payment_allocation","cinenest_booking.booking","cinenest_access.access_credential","cinenest_access.access_command","cinenest_iot.device"))assertThat(f.jdbc.sql("SELECT count(*) FROM "+table).query(Long.class).single()).isZero();pass("zero-financial-booking-access-mutations");
   var latest=f.json(a.get(f.bankPath(current)));var malformed=f.json(f.write(a,"POST","/api/bank/claims/"+latest.get("claimId")+"/uploads",JSONObjectUtils.toJSONString(f.cmd(((Number)latest.get("revision")).longValue()))));assertThat(f.bankUpload(a,malformed.get("uploadId").toString(),new byte[]{1,2,3},"image/png").statusCode()).isEqualTo(415);pass("malformed-fixture-admission-preserved");
   var row=f.jdbc.sql("SELECT object_key,preview_key FROM cinenest_payments.bank_receipt_evidence WHERE evidence_id=:id").param("id",UUID.fromString(id)).query().singleRow();
   Files.writeString(Path.of(System.getenv("QUAL_RESTART_RECEIPT")),JSONObjectUtils.toJSONString(Map.of("object",row.get("object_key").toString(),"preview",row.get("preview_key").toString(),"digest",BankIntegrationTest.hash(BankIntegrationTest.RECEIPT),"request",current.get("id").toString(),"evidence",id)));
  }finally{BankIntegrationTest.IDP.close();}
 }
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"cinenest.booking.extension-reservations.enabled=true","cinenest.booking.extension-reservations.worker-enabled=false","cinenest.booking.extension-reservations.rate-count=5","cinenest.booking.extension-reservations.rate-seconds=60","cinenest.booking.extension-reservations.approval-reference=CN-EXTENSION-ADMISSION-BUDGET-APPROVAL-001 synthetic test","cinenest.booking.amendments.enabled=true","cinenest.bank.refunds.enabled=true","cinenest.bank.refunds.maximum-minor=50000","spring.datasource.hikari.maximum-pool-size=4","spring.datasource.hikari.minimum-idle=0","cinenest.bank.enabled=true","cinenest.bank.ledger.enabled=true","cinenest.bank.ledger.resolution-seconds=60","cinenest.bank.worker-enabled=false","cinenest.bank.policy-version=SYNTHETIC-BANK1","cinenest.bank.lead-seconds=60","cinenest.bank.request-seconds=3000","cinenest.bank.initiation-seconds=900","cinenest.bank.submission-seconds=1200","cinenest.bank.review-seconds=1800","cinenest.bank.hold-seconds=2400","cinenest.bank.escalation-seconds=2500","cinenest.bank.upload-seconds=60","cinenest.bank.retention-seconds=3600","cinenest.bank.cleanup-seconds=1","cinenest.bank.review-opens=00:00","cinenest.bank.review-closes=23:59:59","cinenest.verification.enabled=true","cinenest.verification.worker-enabled=false","cinenest.verification.policy-version=SYNTHETIC-V1","cinenest.verification.upload-seconds=60","cinenest.verification.retention-seconds=3600","cinenest.verification.decision-seconds=600","cinenest.verification.cleanup-seconds=1","cinenest.pricing.synthetic-quotes.enabled=true","cinenest.pricing.synthetic-quotes.tax-profile=SYNTHETIC_FIXED_MINOR_V1","cinenest.pricing.synthetic-quotes.tax-minor=7","cinenest.pricing.synthetic-quotes.approval-reference=SYNTHETIC test fixture only","cinenest.pricing.synthetic-quotes.rate-count=100","cinenest.pricing.synthetic-quotes.rate-seconds=60","cinenest.pricing.publication-worker.enabled=false","cinenest.booking.holds.enabled=true","cinenest.booking.holds.rate-count=100","cinenest.booking.holds.rate-seconds=60","cinenest.booking.holds.approval-reference=SYNTHETIC owner-approved inventory test"})

@org.springframework.test.context.ActiveProfiles("synthetic-quotation")
@EnabledIfEnvironmentVariable(named="CINENEST_LOCAL_RECEIPT_INTEGRATION",matches="true")
class LocalReceiptApplicationRestartTest {
 @Autowired ConfigurableApplicationContext context;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry p){LocalReceiptIntegrationTest.properties(p);}
 @Test void freshApplicationRecoversProtectedEvidence()throws Exception{
  var f=new BankIntegrationTest();context.getAutowireCapableBeanFactory().autowireBean(f);
  try {
   // The synthetic issuer is a new local fixture process/port, not a new customer identity.
   f.jdbc.sql("UPDATE cinenest_identity.external_identity SET issuer=:issuer WHERE account_id IN (:a,:b,:staff)").param("issuer",BankIntegrationTest.IDP.issuer()).param("a",BankIntegrationTest.A).param("b",BankIntegrationTest.B).param("staff",BankIntegrationTest.STAFF).update();
   var receipt=JSONObjectUtils.parse(Files.readString(Path.of(System.getenv("QUAL_RESTART_RECEIPT"))));
   var customer=f.signedIn("customer-a");var staff=f.signedIn("staff");String request="/api/bank/requests/"+receipt.get("request");
   var state=f.json(customer.get(request));assertThat(state.get("evidence").toString()).contains("READY");assertThat(state.get("paymentVerified")).isEqualTo(false);assertThat(state.get("bookingConfirmed")).isEqualTo(false);
   String preview="/api/admin/venues/"+BankIntegrationTest.VA+"/bank/requests/"+receipt.get("request")+"/evidence/"+receipt.get("evidence");
   assertThat(staff.get(preview).statusCode()).isEqualTo(403);f.grant("bank.evidence.read","VENUE",BankIntegrationTest.VA);assertThat(staff.get(preview).statusCode()).isEqualTo(200);
   LocalReceiptIntegrationTest.pass("fresh-spring-boot-protected-status-preview-and-revocation");
   assertThat(f.jdbc.sql("SELECT count(*) FROM cinenest_payments.bank_receipt_evidence").query(Integer.class).single()).isEqualTo(2);
   for(String table:List.of("cinenest_payments.incoming_payment_credit","cinenest_payments.payment_allocation","cinenest_booking.booking","cinenest_access.access_credential"))assertThat(f.jdbc.sql("SELECT count(*) FROM "+table).query(Long.class).single()).isZero();
   LocalReceiptIntegrationTest.pass("fresh-application-database-and-financial-invariants");
  }finally{BankIntegrationTest.IDP.close();}
 }
}
