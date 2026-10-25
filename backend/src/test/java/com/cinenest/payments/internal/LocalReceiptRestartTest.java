package com.cinenest.payments.internal;
import java.nio.file.*;import java.net.URI;import java.util.*;
import com.nimbusds.jose.util.JSONObjectUtils;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;
/** Separate Maven JVM: proves persisted bindings rather than retained Java objects. */
@EnabledIfEnvironmentVariable(named="CINENEST_LOCAL_RECEIPT_INTEGRATION",matches="true")
class LocalReceiptRestartTest {
 @Test void separateProcessRecoversExactVersionsAndCleansOnlyOwnedObjects()throws Exception{
  var receipt=JSONObjectUtils.parse(Files.readString(Path.of(System.getenv("QUAL_RESTART_RECEIPT"))));
  try(var journal=new LocalEvidenceReceiptJournal(Path.of(System.getenv("QUAL_LOCAL_JOURNAL")),UUID.fromString(System.getenv("QUAL_LOCAL_NAMESPACE")),Base64.getDecoder().decode(System.getenv("QUAL_JOURNAL_KEY")))){
   var store=new LocalS3EvidenceStore(URI.create(System.getenv("QUAL_LOCAL_ENDPOINT")),"receipts",System.getenv("QUAL_LOCAL_ACCESS"),System.getenv("QUAL_LOCAL_SECRET"),journal);
   UUID id=UUID.fromString(receipt.get("object").toString()),preview=UUID.fromString(receipt.get("preview").toString());byte[] original=store.get(id);
   assertThat(BankPolicy.hash(original)).isEqualTo(receipt.get("digest"));assertThat(store.get(preview)).isNotEmpty();String version=journal.find(id).version();
   LocalS3EvidenceStore.Reply wire=org.springframework.test.util.ReflectionTestUtils.invokeMethod(store,"request","GET","/receipts/"+journal.namespace()+"/"+id,"versionId="+java.net.URLEncoder.encode(version,java.nio.charset.StandardCharsets.UTF_8),new byte[0],false,BankPolicy.MAX_BYTES+28);
   assertThat(wire.status()).isEqualTo(200);assertThat(wire.body()).hasSize(original.length+28).isNotEqualTo(original);assertThat(BankPolicy.hash(wire.body())).isNotEqualTo(receipt.get("digest"));
   System.out.println("LOCAL_INTEGRATION_PASS encrypted-provider-object-and-bound-plaintext-integrity");
   store.put(id,original);assertThat(journal.find(id).version()).isEqualTo(version);assertThatThrownBy(()->store.put(id,new byte[]{9})).isInstanceOf(BankFailure.class);
   System.out.println("LOCAL_INTEGRATION_PASS fresh-jvm-version-journal-recovery-and-replay");
   var scanner=new ClamAvSocketClient(Path.of(System.getenv("QUAL_LOCAL_SOCKET")),2);
   assertThat(scanner.scan(original).signatureVersion()).matches("[0-9]+");
   assertThatThrownBy(()->scanner.scan("X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*".getBytes())).isInstanceOf(BankFailure.class);
   System.out.println("LOCAL_INTEGRATION_PASS unix-socket-clean-and-eicar-fail-closed");
   store.delete(id);store.delete(id);store.delete(preview);assertThatThrownBy(()->store.get(id)).isInstanceOf(BankFailure.class);assertThat(journal.find(id).deleted()).isTrue();
   System.out.println("LOCAL_INTEGRATION_PASS exact-version-cleanup-and-durable-tombstone");
  }
 }
}
