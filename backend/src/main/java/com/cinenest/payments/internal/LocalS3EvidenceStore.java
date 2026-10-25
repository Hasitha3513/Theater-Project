package com.cinenest.payments.internal;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;

/** Restricted local S3 protocol adapter, not an AWS/production adapter. */
final class LocalS3EvidenceStore implements PrivateEvidenceStore {
    record Reply(int status,Map<String,List<String>> headers,byte[] body) {}
    private final URI endpoint;private final String bucket,access,secret;
    private final LocalEvidenceReceiptJournal journal;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    LocalS3EvidenceStore(URI endpoint,String bucket,String access,String secret,LocalEvidenceReceiptJournal journal) {
        if(!endpoint.getScheme().equals("http")||!endpoint.getHost().equals("127.0.0.1")||endpoint.getPort()<1||endpoint.getRawQuery()!=null||endpoint.getRawUserInfo()!=null||endpoint.getRawFragment()!=null||!Set.of("","/").contains(endpoint.getPath())||!bucket.matches("[a-z0-9-]{3,63}")||!access.matches("[A-Za-z0-9]{8,128}")||secret.length()<24)throw LocalEvidenceReceiptJournal.unavailable();
        this.endpoint=URI.create("http://127.0.0.1:"+endpoint.getPort());this.bucket=bucket;this.access=access;this.secret=secret;this.journal=journal;
    }
    private static byte[] mac(byte[] key,String text)throws Exception{var m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return m.doFinal(text.getBytes(StandardCharsets.UTF_8));}
    private static String enc(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");}
    private Reply request(String method,String path,String query,byte[] body,boolean createOnly,int maximum)throws Exception {
        String date=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.now()),day=date.substring(0,8),scope=day+"/us-east-1/s3/aws4_request",digest=BankPolicy.hash(body);
        String names="host;"+(createOnly?"if-none-match;":"")+"x-amz-content-sha256;x-amz-date";
        String headers="host:"+endpoint.getRawAuthority()+"\n"+(createOnly?"if-none-match:*\n":"")+"x-amz-content-sha256:"+digest+"\nx-amz-date:"+date+"\n";
        String canonical=method+"\n"+path+"\n"+query+"\n"+headers+"\n"+names+"\n"+digest;
        byte[] signing=mac(mac(mac(mac(("AWS4"+secret).getBytes(StandardCharsets.UTF_8),day),"us-east-1"),"s3"),"aws4_request");
        String signature=HexFormat.of().formatHex(mac(signing,"AWS4-HMAC-SHA256\n"+date+"\n"+scope+"\n"+BankPolicy.hash(canonical)));
        var builder=HttpRequest.newBuilder(URI.create(endpoint+path+(query.isEmpty()?"":"?"+query))).timeout(Duration.ofSeconds(10)).header("x-amz-content-sha256",digest).header("x-amz-date",date)
            .header("Authorization","AWS4-HMAC-SHA256 Credential="+access+"/"+scope+", SignedHeaders="+names+", Signature="+signature);
        if(createOnly)builder.header("If-None-Match","*");
        var response=client.send(builder.method(method,HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofInputStream());
        try(var input=response.body()){
            var reader=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();var future=reader.submit(()->input.readNBytes(maximum+1));
            try {byte[] bytes=future.get(10,java.util.concurrent.TimeUnit.SECONDS);if(bytes.length>maximum)throw LocalEvidenceReceiptJournal.unavailable();return new Reply(response.statusCode(),response.headers().map(),bytes);}
            finally {future.cancel(true);input.close();reader.shutdownNow();}
        }
    }
    private org.w3c.dom.Document document(byte[] data)throws Exception {
        var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);f.setXIncludeAware(false);f.setExpandEntityReferences(false);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(data));
    }
    private String key(UUID id){return journal.namespace()+"/"+id;}
    private String path(UUID id){return "/"+bucket+"/"+key(id);}
    private List<String> versions(UUID id)throws Exception {
        var r=request("GET","/"+bucket,"prefix="+enc(key(id))+"&versions=",new byte[0],false,65536);if(r.status()!=200)throw LocalEvidenceReceiptJournal.unavailable();
        var d=document(r.body());if(!d.getElementsByTagName("IsTruncated").item(0).getTextContent().equals("false"))throw LocalEvidenceReceiptJournal.unavailable();
        List<String> found=new ArrayList<>();
        for(String type:List.of("Version","DeleteMarker")){var nodes=d.getElementsByTagName(type);for(int i=0;i<nodes.getLength();i++){var element=(org.w3c.dom.Element)nodes.item(i);if(element.getElementsByTagName("Key").item(0).getTextContent().equals(key(id))){if(type.equals("DeleteMarker"))throw LocalEvidenceReceiptJournal.unavailable();found.add(element.getElementsByTagName("VersionId").item(0).getTextContent());}}}
        if(found.size()>1||found.stream().anyMatch(v->!v.matches("[A-Za-z0-9._~+/=-]{1,512}")||v.equals("null")))throw LocalEvidenceReceiptJournal.unavailable();return found;
    }
    private byte[] verified(LocalEvidenceReceiptJournal.Entry e,String version)throws Exception {
        var r=request("GET",path(e.id()),"versionId="+enc(version),new byte[0],false,BankPolicy.MAX_BYTES+28);
        if(r.status()!=200)throw LocalEvidenceReceiptJournal.unavailable();byte[] plain=journal.decryptObject(e.id(),r.body());
        if(plain.length!=e.size()||!BankPolicy.hash(plain).equals(e.digest()))throw LocalEvidenceReceiptJournal.unavailable();return plain;
    }
    public synchronized void put(UUID id,byte[] bytes) {
        try {
            boolean fresh=journal.find(id)==null;var e=journal.intent(id,bytes);
            if(e.version()!=null){get(id);return;}
            if(fresh){
                var mode=request("GET","/"+bucket,"versioning=",new byte[0],false,4096);
                if(mode.status()!=200||!document(mode.body()).getElementsByTagName("Status").item(0).getTextContent().equals("Enabled"))throw LocalEvidenceReceiptJournal.unavailable();
                try {var r=request("PUT",path(id),"",journal.encryptObject(id,bytes),true,4096);if(r.status()!=200&&r.status()!=412)throw LocalEvidenceReceiptJournal.unavailable();}
                catch(IOException unknown){/* Recover only from exact immutable intent and authoritative version listing. */}
            }
            var versions=versions(id);if(versions.size()!=1)throw LocalEvidenceReceiptJournal.unavailable();verified(e,versions.getFirst());journal.bind(id,versions.getFirst());
        }catch(BankFailure failure){throw failure;}catch(Exception failure){throw LocalEvidenceReceiptJournal.unavailable();}
    }
    public synchronized byte[] get(UUID id) {
        try {var e=journal.find(id);if(e==null||e.deleted()||e.version()==null)throw LocalEvidenceReceiptJournal.unavailable();var v=versions(id);if(v.size()!=1||!v.getFirst().equals(e.version()))throw LocalEvidenceReceiptJournal.unavailable();return verified(e,e.version());}catch(Exception failure){throw LocalEvidenceReceiptJournal.unavailable();}
    }
    public synchronized void delete(UUID id) {
        try {var e=journal.find(id);if(e==null)return;var v=versions(id);if(e.deleted()){if(!v.isEmpty())throw LocalEvidenceReceiptJournal.unavailable();return;}
            if(!v.isEmpty()){String version=v.getFirst();if(e.version()!=null&&!e.version().equals(version))throw LocalEvidenceReceiptJournal.unavailable();verified(e,version);
                var r=request("DELETE",path(id),"versionId="+enc(version),new byte[0],false,4096);if(r.status()!=200&&r.status()!=204)throw LocalEvidenceReceiptJournal.unavailable();}
            if(!versions(id).isEmpty())throw LocalEvidenceReceiptJournal.unavailable();journal.deleted(id);
        }catch(Exception failure){throw LocalEvidenceReceiptJournal.unavailable();}
    }
    public synchronized Set<UUID> olderThan(Instant cutoff){Set<UUID> ids=new HashSet<>();for(var e:journal.entries())if(!e.deleted()&&e.created().isBefore(cutoff))ids.add(e.id());return Set.copyOf(ids);}
}
