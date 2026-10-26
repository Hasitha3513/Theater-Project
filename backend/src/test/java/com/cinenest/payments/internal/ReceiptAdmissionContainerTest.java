package com.cinenest.payments.internal;

import java.net.URI;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

/** Pure candidate utility + existing actual adapters. Never registered in the customer workflow. */
@EnabledIfEnvironmentVariable(named="CINENEST_LOCAL_RECEIPT_INTEGRATION",matches="true")
class ReceiptAdmissionContainerTest {
    @TempDir Path directory;
    @Test void candidateImagesUseExistingPrivateStorageAndScannerWithoutAdmission() throws Exception {
        var validator=new ReceiptImageValidator();var policy=new ReceiptAdmissionPolicy();
        try(var journal=new LocalEvidenceReceiptJournal(directory.resolve("journal"),UUID.randomUUID(),new byte[32])) {
            // Reuse the harness's bounded bucket; this journal's random namespace isolates all objects.
            String name="receipts";
            var store=new LocalS3EvidenceStore(URI.create(System.getenv("QUAL_LOCAL_ENDPOINT")),name,System.getenv("QUAL_LOCAL_ACCESS"),System.getenv("QUAL_LOCAL_SECRET"),journal);
            LocalS3EvidenceStore.Reply existing=org.springframework.test.util.ReflectionTestUtils.invokeMethod(store,"request","GET","/"+name,"versioning=",new byte[0],false,4096);
            if(existing.status()!=200) {
                assertThat(existing.status()).isEqualTo(404);
                LocalS3EvidenceStore.Reply bucket=org.springframework.test.util.ReflectionTestUtils.invokeMethod(store,"request","PUT","/"+name,"",new byte[0],false,4096);assertThat(bucket.status()).isIn(200,204);
                LocalS3EvidenceStore.Reply versioning=org.springframework.test.util.ReflectionTestUtils.invokeMethod(store,"request","PUT","/"+name,"versioning=","<VersioningConfiguration xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\"><Status>Enabled</Status></VersioningConfiguration>".getBytes(),false,4096);assertThat(versioning.status()).isEqualTo(200);
            } else assertThat(new String(existing.body(),java.nio.charset.StandardCharsets.UTF_8)).contains("<Status>Enabled</Status>");
            var scanner=new ClamAvSocketClient(Path.of(System.getenv("QUAL_LOCAL_SOCKET")),10);
            for(String format:List.of("png","jpeg")) {
                var safe=validator.validate(ReceiptAdmissionTest.image(format,16,16),"image/"+format);UUID id=UUID.randomUUID();
                store.put(id,safe.png());String version=journal.find(id).version();store.put(id,safe.png());assertThat(journal.find(id).version()).isEqualTo(version);
                assertThat(scanner.scan(store.get(id)).signatureVersion()).matches("[0-9]+");
                assertThat(policy.decision()).isEqualTo(ReceiptAdmissionPolicy.Decision.DENY);assertThatThrownBy(policy::requireAdmission).isInstanceOf(BankFailure.class);
                store.delete(id);assertThat(journal.find(id).deleted()).isTrue();
            }
            System.out.println("RECEIPT_ADMISSION_PASS png-jpeg-private-versioned-store-and-real-scan");
            byte[] eicar="X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*".getBytes();
            assertThatThrownBy(()->validator.validate(eicar,"image/png")).isInstanceOf(BankFailure.class);
            String actual=org.springframework.test.util.ReflectionTestUtils.invokeMethod(scanner,"exchange",eicar,System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10));
            assertThat(actual).endsWith(" FOUND"); // Prove malware detection, not merely an arbitrary transport failure.
            assertThatThrownBy(()->scanner.scan(eicar)).isInstanceOf(BankFailure.class);
            assertThatThrownBy(()->new ClamAvSocketClient(directory.resolve("missing.sock"),1).scan(ReceiptAdmissionTest.image("png",1,1))).isInstanceOf(BankFailure.class);
            System.out.println("RECEIPT_ADMISSION_PASS malware-and-unavailable-scanner-fail-closed");
            assertThat(policy.decision()).isEqualTo(ReceiptAdmissionPolicy.Decision.DENY);
            System.out.println("RECEIPT_ADMISSION_PASS no-customer-admission-or-financial-wiring");
        }
    }
}
