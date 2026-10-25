package com.cinenest.payments.internal;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

/** Opt-in real local containers; never registered as application beans. No AWS qualification. */
@EnabledIfEnvironmentVariable(named="CINENEST_LOCAL_RECEIPT",matches="true")
class LocalReceiptQualificationTest {
 static final int MAX=5*1024*1024;
 record Receipt(String key,String version,String sha,int size){}
 record Scan(String outcome,String engine,String signatures){}
 static String env(String k){return Objects.requireNonNull(System.getenv(k),k);}
 static String hash(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
 static byte[] hmac(byte[] key,String s)throws Exception{var m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return m.doFinal(s.getBytes(StandardCharsets.UTF_8));}
 static String xml(byte[] b,String tag)throws Exception{
  var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);f.setXIncludeAware(false);f.setExpandEntityReferences(false);
  var n=f.newDocumentBuilder().parse(new ByteArrayInputStream(b)).getElementsByTagName(tag);return n.getLength()==0?"":n.item(0).getTextContent();
 }
 static class Store {
  String endpoint=env("QUAL_S3_URL");final String access=env("QUAL_ACCESS"),secret=env("QUAL_SECRET");
  Store(){var uri=URI.create(endpoint);if(!uri.getScheme().equals("http")||!uri.getHost().equals("127.0.0.1")||uri.getPort()<1)throw new IllegalArgumentException("Local qualification endpoint required");}
  final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
  HttpResponse<byte[]> request(String method,String path,String query,byte[] data,boolean signed)throws Exception{
   var uri=URI.create(endpoint+path+(query.isEmpty()?"":"?"+query));var b=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10));
   String digest=hash(data),date=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.now()),day=date.substring(0,8),scope=day+"/us-east-1/s3/aws4_request";
   if(signed){String headers="host:"+uri.getRawAuthority()+"\nx-amz-content-sha256:"+digest+"\nx-amz-date:"+date+"\n",names="host;x-amz-content-sha256;x-amz-date";
    String canonical=method+"\n"+path+"\n"+query+"\n"+headers+"\n"+names+"\n"+digest;
    byte[] key=hmac(hmac(hmac(hmac(("AWS4"+secret).getBytes(StandardCharsets.UTF_8),day),"us-east-1"),"s3"),"aws4_request");
    String sig=HexFormat.of().formatHex(hmac(key,"AWS4-HMAC-SHA256\n"+date+"\n"+scope+"\n"+hash(canonical.getBytes(StandardCharsets.UTF_8))));
    b.header("x-amz-date",date).header("x-amz-content-sha256",digest).header("Authorization","AWS4-HMAC-SHA256 Credential="+access+"/"+scope+", SignedHeaders="+names+", Signature="+sig);
   }
   return client.send(b.method(method,HttpRequest.BodyPublishers.ofByteArray(data)).build(),HttpResponse.BodyHandlers.ofByteArray());
  }
  Receipt put(String key,byte[] data)throws Exception{
   if(data.length==0||data.length>MAX)throw new IllegalArgumentException("Size rejected");
   var r=request("PUT","/receipts/"+key,"",data,true);assertThat(r.statusCode()).isEqualTo(200);
   String version=r.headers().firstValue("x-amz-version-id").orElseThrow();assertThat(version).isNotBlank().isNotEqualTo("null");return new Receipt(key,version,hash(data),data.length);
  }
  String query(Receipt r){return "versionId="+URLEncoder.encode(r.version(),StandardCharsets.UTF_8).replace("+","%20");}
  byte[] get(Receipt r)throws Exception{var response=request("GET","/receipts/"+r.key(),query(r),new byte[0],true);if(response.statusCode()!=200)throw new IOException("Version unavailable");byte[] b=response.body();if(b.length!=r.size()||!hash(b).equals(r.sha()))throw new IOException("Integrity rejected");return b;}
  void delete(Receipt r)throws Exception{assertThat(request("DELETE","/receipts/"+r.key(),query(r),new byte[0],true).statusCode()).isIn(200,204);}
 }
 static class Scanner {
  Scan scan(byte[] bytes,long deadline,Instant signatureTime)throws Exception{
   if(bytes.length==0||bytes.length>MAX)return new Scan("QUARANTINED_SIZE","","");
   if(signatureTime.isBefore(Instant.now().minus(Duration.ofHours(24))))return new Scan("QUARANTINED_STALE","","");
   var version=new ProcessBuilder("docker","exec",env("QUAL_SCANNER"),"clamdscan","--version").redirectErrorStream(true).start();
   if(!version.waitFor(5,TimeUnit.SECONDS)) {version.destroyForcibly();return new Scan("QUARANTINED_UNAVAILABLE","","");}
   String meta=new String(version.getInputStream().readNBytes(4096),StandardCharsets.UTF_8).strip();if(version.exitValue()!=0||!meta.startsWith("ClamAV "))return new Scan("QUARANTINED_UNAVAILABLE","","");
   var p=new ProcessBuilder("docker","exec","-i",env("QUAL_SCANNER"),"clamdscan","--stream","--no-summary","-").redirectErrorStream(true).start();
   try(var output=p.getOutputStream()){output.write(bytes);}catch(IOException interrupted){p.destroyForcibly();return new Scan("QUARANTINED_UNAVAILABLE",meta,signatureTime.toString());}
   if(!p.waitFor(deadline,TimeUnit.SECONDS)){p.destroyForcibly();return new Scan("QUARANTINED_TIMEOUT",meta,signatureTime.toString());}
   String reply=new String(p.getInputStream().readNBytes(4096),StandardCharsets.UTF_8).strip();
   return new Scan(p.exitValue()==0&&reply.matches("[^\\r\\n]+: OK")?"CLEAN":p.exitValue()==1&&reply.endsWith(" FOUND")?"QUARANTINED_MALWARE":"QUARANTINED_ERROR",meta,signatureTime.toString());
  }
 }
 static void image(byte[] bytes)throws Exception{try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){var rs=ImageIO.getImageReaders(input);if(!rs.hasNext())throw new IOException("Malformed image");var r=rs.next();try{r.setInput(input);if((long)r.getWidth(0)*r.getHeight(0)>20000000)throw new IOException("Dimensions rejected");r.read(0);}finally{r.dispose();}}}
 static void pass(String s){System.out.println("LOCAL_RECEIPT_PASS "+s);}
 static Receipt replay(Connection db,UUID command,String payload)throws Exception{
  try(var p=db.prepareStatement("SELECT object_key,version,payload FROM qualification_receipt WHERE command=?")){p.setObject(1,command);try(var r=p.executeQuery()){if(!r.next()||!payload.equals(r.getString(3)))throw new IllegalArgumentException("Command payload conflict");return new Receipt(r.getString(1),r.getString(2),r.getString(3),-1);}}
 }
 @Test void realContainers()throws Exception{
  var store=new Store();var scanner=new Scanner();var bytes=new ByteArrayOutputStream();ImageIO.write(new java.awt.image.BufferedImage(8,8,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);byte[] clean=bytes.toByteArray();
  try(var db=DriverManager.getConnection(env("QUAL_DB_URL"),"qualification",env("QUAL_DB_PASSWORD"));var sql=db.createStatement()){
   sql.execute("CREATE TABLE qualification_receipt(command uuid primary key,payload char(64) not null,object_key text not null,version text,state text not null,audit integer not null default 0)");
   assertThat(store.request("PUT","/receipts","",new byte[0],true).statusCode()).isIn(200,204);
   assertThat(store.request("PUT","/receipts","versioning=","<VersioningConfiguration xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\"><Status>Enabled</Status></VersioningConfiguration>".getBytes(StandardCharsets.UTF_8),true).statusCode()).isEqualTo(200);
   Receipt first=store.put(UUID.randomUUID().toString(),clean);assertThat(store.get(first)).isEqualTo(clean);pass("private-upload-version-sha256");
   assertThat(store.request("GET","/receipts/"+first.key(),store.query(first),new byte[0],false).statusCode()).isIn(401,403);pass("anonymous-denied");
   Receipt second=store.put(first.key(),new byte[]{1,2,3});assertThat(second.version()).isNotEqualTo(first.version());assertThat(store.get(first)).isEqualTo(clean);pass("exact-old-version-restoration");
   assertThatThrownBy(()->store.get(new Receipt(first.key(),first.version(),"0".repeat(64),first.size()))).isInstanceOf(IOException.class);pass("tamper-denied");
   UUID command=UUID.randomUUID();try(var p=db.prepareStatement("INSERT INTO qualification_receipt VALUES (?,?,?,NULL,'UPLOADING',0)")){p.setObject(1,command);p.setString(2,first.sha());p.setString(3,first.key());p.executeUpdate();}
   // A stored object with no committed version receipt models loss after external PUT.
   db.setAutoCommit(false);try(var p=db.prepareStatement("UPDATE qualification_receipt SET version=?,state='QUARANTINED',audit=audit+1 WHERE command=? AND payload=?")){p.setString(1,first.version());p.setObject(2,command);p.setString(3,first.sha());assertThat(p.executeUpdate()).isEqualTo(1);}db.rollback();
   try(var r=sql.executeQuery("SELECT state FROM qualification_receipt")){r.next();assertThat(r.getString(1)).isEqualTo("UPLOADING");}
   // Recover only the exact independently verified original object; never re-PUT uncertain bytes.
   assertThat(store.get(first)).isEqualTo(clean);try(var p=db.prepareStatement("UPDATE qualification_receipt SET version=?,state='QUARANTINED',audit=audit+1 WHERE command=? AND state='UPLOADING'")){p.setString(1,first.version());p.setObject(2,command);assertThat(p.executeUpdate()).isEqualTo(1);assertThat(p.executeUpdate()).isZero();}db.commit();db.setAutoCommit(true);
   assertThatThrownBy(()->sql.execute("INSERT INTO qualification_receipt SELECT * FROM qualification_receipt")).isInstanceOf(SQLException.class);pass("lost-response-rollback-idempotent-recovery");
   assertThat(replay(db,command,first.sha()).version()).isEqualTo(first.version());assertThatThrownBy(()->replay(db,command,hash(new byte[]{9}))).isInstanceOf(IllegalArgumentException.class);
   try(var r=sql.executeQuery("SELECT audit FROM qualification_receipt")){r.next();assertThat(r.getInt(1)).isEqualTo(1);}pass("changed-payload-replay-rejected");
   Instant signatures=Instant.parse(env("QUAL_SIGNATURE_TIME"));Scan good=scanner.scan(clean,60,signatures);assertThat(good.outcome()).isEqualTo("CLEAN");assertThat(good.engine()).contains("1.5.4");image(clean);pass("real-clamav-clean-image");
   byte[] eicar=("X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*").getBytes(StandardCharsets.US_ASCII);assertThat(scanner.scan(eicar,60,signatures).outcome()).isEqualTo("QUARANTINED_MALWARE");pass("real-clamav-eicar-preview-denied");
   assertThat(scanner.scan(new byte[MAX+1],60,signatures).outcome()).isEqualTo("QUARANTINED_SIZE");assertThatThrownBy(()->store.put("oversized",new byte[MAX+1])).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->image(new byte[]{1,2,3})).isInstanceOf(IOException.class);pass("oversized-malformed-preview-denied");
   assertThat(scanner.scan(clean,60,Instant.now().minus(Duration.ofHours(25))).outcome()).isEqualTo("QUARANTINED_STALE");pass("stale-signatures-denied");
   assertThat(new ProcessBuilder("docker","restart",env("QUAL_STORAGE")).start().waitFor()).isZero();
   var portProcess=new ProcessBuilder("docker","port",env("QUAL_STORAGE"),"8333/tcp").start();String mapping=new String(portProcess.getInputStream().readAllBytes(),StandardCharsets.UTF_8).strip();assertThat(portProcess.waitFor()).isZero();String rediscovered="http://"+mapping;System.out.println("LOCAL_RECEIPT_RESTART_PORT_CHANGED "+!store.endpoint.equals(rediscovered));store.endpoint=rediscovered;
   boolean restored=false;for(int i=0;i<30;i++){try{restored=Arrays.equals(store.get(first),clean);if(restored)break;}catch(Exception pending){Thread.sleep(200);}}assertThat(restored).isTrue();pass("storage-restart-version-persistence");
   var pause=new ProcessBuilder("docker","pause",env("QUAL_SCANNER")).start();assertThat(pause.waitFor()).isZero();try{assertThat(scanner.scan(clean,1,signatures).outcome()).startsWith("QUARANTINED");}finally{new ProcessBuilder("docker","unpause",env("QUAL_SCANNER")).start().waitFor();}pass("scanner-timeout-fail-closed");
   var stop=new ProcessBuilder("docker","stop","--time","2",env("QUAL_SCANNER")).start();assertThat(stop.waitFor()).isZero();assertThat(scanner.scan(clean,1,signatures).outcome()).isEqualTo("QUARANTINED_UNAVAILABLE");pass("scanner-unavailable-fail-closed");
   store.delete(second);assertThat(store.get(first)).isEqualTo(clean);store.delete(first);assertThatThrownBy(()->store.get(first)).isInstanceOf(IOException.class);pass("controlled-version-cleanup");
   try(var r=sql.executeQuery("SELECT count(*) FROM information_schema.schemata WHERE schema_name LIKE 'cinenest_%'")){r.next();assertThat(r.getInt(1)).isZero();}pass("isolated-db-no-financial-booking-access-schema");
   assertThat(new ProcessBuilder("docker","restart",env("QUAL_DATABASE")).start().waitFor()).isZero();boolean recovered=false;
   var dbPort=new ProcessBuilder("docker","port",env("QUAL_DATABASE"),"5432/tcp").start();String dbMapping=new String(dbPort.getInputStream().readAllBytes(),StandardCharsets.UTF_8).strip();assertThat(dbPort.waitFor()).isZero();
   for(int i=0;i<30;i++){try(var again=DriverManager.getConnection("jdbc:postgresql://"+dbMapping+"/receipt_qualification","qualification",env("QUAL_DB_PASSWORD"));var check=again.createStatement();var r=check.executeQuery("SELECT state,audit FROM qualification_receipt")){r.next();recovered=r.getString(1).equals("QUARANTINED")&&r.getInt(2)==1;break;}catch(SQLException pending){Thread.sleep(200);}}assertThat(recovered).isTrue();pass("database-restart-receipt-audit-persistence");
  }
 }
}
