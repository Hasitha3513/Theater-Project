package com.cinenest.payments.internal;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
class LocalReceiptJournalTest {
 @TempDir Path directory;
 @Test void encryptedIntentBindingRestartAndTombstoneAreDurable()throws Exception{
  Path root=directory.resolve("journal");UUID namespace=UUID.randomUUID(),id=UUID.randomUUID();byte[] bytes="SYNTHETIC TEST".getBytes(),key=new byte[32];
  try(var j=new LocalEvidenceReceiptJournal(root,namespace,key)){byte[] cipher=j.encryptObject(id,bytes);assertThat(cipher).isNotEqualTo(bytes);assertThat(j.decryptObject(id,cipher)).isEqualTo(bytes);assertThatThrownBy(()->j.decryptObject(UUID.randomUUID(),cipher)).isInstanceOf(BankFailure.class);cipher[15]^=1;assertThatThrownBy(()->j.decryptObject(id,cipher)).isInstanceOf(BankFailure.class);j.intent(id,bytes);j.bind(id,"v1");assertThat(j.find(id).version()).isEqualTo("v1");assertThatThrownBy(()->new LocalEvidenceReceiptJournal(root,namespace,key)).isInstanceOf(BankFailure.class);assertThatThrownBy(()->j.intent(id,new byte[]{9})).isInstanceOf(BankFailure.class);assertThatThrownBy(()->j.bind(id,"v2")).isInstanceOf(BankFailure.class);}
  assertThat(Files.readString(root.resolve(id+".intent"),java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("SYNTHETIC",BankPolicy.hash(bytes));
  try(var j=new LocalEvidenceReceiptJournal(root,namespace,key)){assertThat(j.find(id).version()).isEqualTo("v1");j.deleted(id);}
  try(var j=new LocalEvidenceReceiptJournal(root,namespace,key)){assertThat(j.find(id).deleted()).isTrue();assertThatThrownBy(()->j.intent(id,bytes)).isInstanceOf(BankFailure.class);}
 }
 @Test void wrongKeyNamespaceTamperAndSymlinksFailClosed()throws Exception{
  Path root=directory.resolve("journal");UUID ns=UUID.randomUUID(),id=UUID.randomUUID();byte[] key=new byte[32];
  try(var j=new LocalEvidenceReceiptJournal(root,ns,key)){j.intent(id,new byte[]{1});}
  byte[] wrong=key.clone();wrong[0]=1;assertThatThrownBy(()->new LocalEvidenceReceiptJournal(root,ns,wrong)).isInstanceOf(BankFailure.class);assertThatThrownBy(()->new LocalEvidenceReceiptJournal(root,UUID.randomUUID(),key)).isInstanceOf(BankFailure.class);
  Path record=root.resolve(id+".intent");byte[] saved=Files.readAllBytes(record),bad=saved.clone();bad[15]^=1;Files.write(record,bad);assertThatThrownBy(()->new LocalEvidenceReceiptJournal(root,ns,key)).isInstanceOf(BankFailure.class);Files.write(record,saved);
  Path link=directory.resolve("link");Files.createSymbolicLink(link,root);assertThatThrownBy(()->new LocalEvidenceReceiptJournal(link,ns,key)).isInstanceOf(BankFailure.class);
 }
 @Test void remoteEndpointsAndMissingSocketsAreRejected()throws Exception{
  try(var j=new LocalEvidenceReceiptJournal(directory.resolve("journal"),UUID.randomUUID(),new byte[32])){assertThatThrownBy(()->new LocalS3EvidenceStore(java.net.URI.create("https://s3.amazonaws.com"),"receipts","syntheticaccess","syntheticsecret0123456789012345",j)).isInstanceOf(BankFailure.class);}
  assertThatThrownBy(()->new ClamAvSocketClient(directory.resolve("missing.sock"),1).scan(new byte[]{1})).isInstanceOf(BankFailure.class);
 }
 @Test void malformedAndStaleScannerMetadataFailClosed()throws Exception{
  int i=0;for(String reply:List.of("BROKEN","ClamAV 1.5.4/1/Sat Jan 1 00:00:00 2000")){
   Path socket=directory.resolve("metadata-"+(i++)+".sock");try(var server=java.nio.channels.ServerSocketChannel.open(java.net.StandardProtocolFamily.UNIX)){
    server.bind(java.net.UnixDomainSocketAddress.of(socket));Thread worker=Thread.ofVirtual().start(()->{try(var client=server.accept()){var input=java.nio.channels.Channels.newInputStream(client);while(input.read()!=0){}java.nio.channels.Channels.newOutputStream(client).write((reply+"\0").getBytes());}catch(Exception ignored){}});
    try{assertThatThrownBy(()->new ClamAvSocketClient(socket,1).scan(new byte[]{1})).isInstanceOf(BankFailure.class);}finally{worker.interrupt();worker.join(2000);}
   }
  }
 }
 @Test void scannerDeadlineIsBoundedAcrossProtocolStages()throws Exception{
  Path socket=directory.resolve("deadline.sock");try(var server=java.nio.channels.ServerSocketChannel.open(java.net.StandardProtocolFamily.UNIX)){
   server.bind(java.net.UnixDomainSocketAddress.of(socket));var release=new java.util.concurrent.CountDownLatch(1);
   Thread worker=Thread.ofVirtual().start(()->{try{try(var version=server.accept()){var input=java.nio.channels.Channels.newInputStream(version);while(input.read()!=0){}String date=java.time.format.DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy",java.util.Locale.ENGLISH).withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.now().minusSeconds(60));java.nio.channels.Channels.newOutputStream(version).write(("ClamAV 1.5.4/1/"+date+"\0").getBytes());}try(var scan=server.accept()){release.await();}}catch(Exception ignored){}});
   try{assertThatThrownBy(()->new ClamAvSocketClient(socket,1).scan(new byte[]{1})).isInstanceOf(BankFailure.class);}finally{release.countDown();worker.interrupt();worker.join(2000);}
  }
 }
 @Test void enabledModeCannotTargetProductionProfileOrUnisolatedDatabase(){
  var env=new org.springframework.mock.env.MockEnvironment().withProperty("spring.datasource.url","jdbc:postgresql://127.0.0.1:1234/receipt_qualification");env.setActiveProfiles("production");assertThatThrownBy(()->new LocalDockerEvidenceConfiguration(env)).isInstanceOf(BankFailure.class);
  env.setActiveProfiles("synthetic-quotation");env.setProperty("spring.datasource.url","jdbc:postgresql://127.0.0.1:1234/cinenest");assertThatThrownBy(()->new LocalDockerEvidenceConfiguration(env)).isInstanceOf(BankFailure.class);
 }
}
