package com.cinenest.rooms.internal;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;
import static com.cinenest.rooms.internal.AdvertisingMediaStore.*;

/** Test-only, local protocol adapter. Never a Spring bean or an AWS adapter. */
final class LocalAdvertisingMediaTestAdapter implements AdvertisingMediaStore {
    record Reply(int status,Map<String,List<String>> headers,byte[] body){}
    record Entry(String key,String version,boolean marker){}
    record Intent(Identity identity,UUID command,String digest,int bytes){}
    interface Monitor { void check(); }
    static final int MAX_OBJECT=2*1024*1024, MAX_VERSIONS=10;
    static final long MAX_SUBMITTED=20L*1024*1024;
    private URI endpoint; final String bucket,prefix;
    private final String access,secret;
    private final Monitor monitor;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<UUID,Intent> intents=new HashMap<>();
    private final Map<UUID,Reference> receipts=new HashMap<>();
    private final Map<Identity,UUID> identities=new HashMap<>();
    private long submitted; private int creates; private boolean aborted;
    boolean discardNextPutReply;
    private long cleanupDeadline=Long.MAX_VALUE;
    LocalAdvertisingMediaTestAdapter(URI endpoint,String run,String access,String secret,Monitor monitor){
        if(!endpoint.getScheme().equals("http")||!endpoint.getHost().equals("127.0.0.1")||endpoint.getPort()<1024||endpoint.getUserInfo()!=null||!endpoint.getPath().isEmpty()||endpoint.getQuery()!=null||endpoint.getFragment()!=null||!run.matches("[a-f0-9]{32}")||!access.matches("[A-Za-z0-9]{16,64}")||secret.length()<24)throw fail(Outcome.INVALID);
        this.endpoint=endpoint;bucket="cn-ads-a-"+run;prefix="runs/"+run+"/";this.access=access;this.secret=secret;this.monitor=Objects.requireNonNull(monitor);
    }
    synchronized void reconnectLocal(URI value){if(!value.getScheme().equals("http")||!value.getHost().equals("127.0.0.1")||value.getPort()<1024||value.getUserInfo()!=null||!value.getPath().isEmpty()||value.getQuery()!=null||value.getFragment()!=null)throw fail(Outcome.INVALID);endpoint=value;}
    static Failure fail(Outcome o){return new Failure(o);}
    static String hash(byte[] b){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}catch(GeneralSecurityException e){throw fail(Outcome.UNAVAILABLE);}}
    static byte[] mac(byte[] key,String text)throws Exception{var m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return m.doFinal(text.getBytes(StandardCharsets.UTF_8));}
    static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8).replace("+","%20");}
    String path(Identity id){return "/"+bucket+"/"+prefix+id.venueId()+"/"+id.objectId()+"/"+id.revision();}
    Reply wire(String method,String path,String query,byte[] body,boolean createOnly,boolean signed){
        try{
            String date=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.now()),day=date.substring(0,8),scope=day+"/us-east-1/s3/aws4_request",digest=hash(body);
            String names="host;"+(createOnly?"if-none-match;":"")+"x-amz-content-sha256;x-amz-date";
            String heads="host:"+endpoint.getRawAuthority()+"\n"+(createOnly?"if-none-match:*\n":"")+"x-amz-content-sha256:"+digest+"\nx-amz-date:"+date+"\n";
            String canonical=method+"\n"+path+"\n"+query+"\n"+heads+"\n"+names+"\n"+digest;
            var b=HttpRequest.newBuilder(URI.create(endpoint+path+(query.isEmpty()?"":"?"+query))).timeout(Duration.ofNanos(Math.max(1,Math.min(TimeUnit.SECONDS.toNanos(10),cleanupDeadline-System.nanoTime())))).header("x-amz-content-sha256",digest).header("x-amz-date",date);
            if(signed){byte[] key=mac(mac(mac(mac(("AWS4"+secret).getBytes(StandardCharsets.UTF_8),day),"us-east-1"),"s3"),"aws4_request");String sig=HexFormat.of().formatHex(mac(key,"AWS4-HMAC-SHA256\n"+date+"\n"+scope+"\n"+hash(canonical.getBytes(StandardCharsets.UTF_8))));b.header("Authorization","AWS4-HMAC-SHA256 Credential="+access+"/"+scope+", SignedHeaders="+names+", Signature="+sig);}
            if(createOnly)b.header("If-None-Match","*");
            var response=client.send(b.method(method,HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofInputStream());
            try(var input=response.body();var executor=Executors.newVirtualThreadPerTaskExecutor()){
                var future=executor.submit(()->input.readNBytes(MAX_OBJECT+1));
                try{byte[] bytes=future.get(Math.max(1,Math.min(TimeUnit.SECONDS.toNanos(10),cleanupDeadline-System.nanoTime())),TimeUnit.NANOSECONDS);if(bytes.length>MAX_OBJECT)throw fail(Outcome.LIMIT_EXCEEDED);return new Reply(response.statusCode(),response.headers().map(),bytes);}
                finally{future.cancel(true);input.close();executor.shutdownNow();}
            }
        }catch(Failure f){throw f;}catch(InterruptedException e){Thread.currentThread().interrupt();throw fail(Outcome.UNAVAILABLE);}catch(Exception e){System.err.println("AD_MEDIA_A_TRANSPORT_"+(e instanceof java.net.http.HttpTimeoutException?"HTTP_TIMEOUT":e instanceof TimeoutException?"BODY_TIMEOUT":e instanceof java.io.IOException?"IO_FAILURE":"INTERNAL"));throw fail(Outcome.UNAVAILABLE);}
    }
    static org.w3c.dom.Document xml(byte[] bytes){try{if(bytes.length>65536)throw fail(Outcome.UNAVAILABLE);var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);f.setXIncludeAware(false);f.setExpandEntityReferences(false);return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));}catch(Exception e){throw fail(Outcome.UNAVAILABLE);}}
    List<Entry> inventory(){monitor.check();try{return inventoryUnchecked();}finally{monitor.check();}}
    private List<Entry> inventoryUnchecked(){var r=wire("GET","/"+bucket,"versions=",new byte[0],false,true);if(r.status()==404)return List.of();if(r.status()!=200)throw fail(Outcome.UNAVAILABLE);var d=xml(r.body());if(d.getElementsByTagName("IsTruncated").getLength()!=1||!d.getElementsByTagName("IsTruncated").item(0).getTextContent().equals("false"))throw fail(Outcome.UNAVAILABLE);var found=new ArrayList<Entry>();for(String type:List.of("Version","DeleteMarker")){var nodes=d.getElementsByTagName(type);for(int i=0;i<nodes.getLength();i++){var n=(org.w3c.dom.Element)nodes.item(i);String key=n.getElementsByTagName("Key").item(0).getTextContent(),version=n.getElementsByTagName("VersionId").item(0).getTextContent();if(!key.startsWith(prefix)||key.contains("..")||!version.matches("[A-Za-z0-9._~+/=-]{1,512}")||version.equals("null"))throw fail(Outcome.UNSUPPORTED);found.add(new Entry(key,version,type.equals("DeleteMarker")));}}return found;}
    void check(){if(aborted)throw fail(Outcome.LIMIT_EXCEEDED);try{monitor.check();if(inventory().size()>MAX_VERSIONS||submitted>MAX_SUBMITTED||creates>MAX_VERSIONS)throw fail(Outcome.LIMIT_EXCEEDED);}catch(Failure f){if(f.outcome()==Outcome.LIMIT_EXCEEDED)aborted=true;throw f;}catch(Exception e){aborted=true;throw fail(Outcome.UNAVAILABLE);}}
    synchronized Reply request(String method,String path,String query,byte[] body,boolean createOnly,int potentialVersions,boolean signed){
        check();if(body.length>MAX_OBJECT||submitted+body.length>MAX_SUBMITTED||creates+potentialVersions>MAX_VERSIONS){aborted=true;throw fail(Outcome.LIMIT_EXCEEDED);}submitted+=body.length;creates+=potentialVersions;
        try{var reply=wire(method,path,query,body,createOnly,signed);if(discardNextPutReply&&potentialVersions==1){discardNextPutReply=false;throw fail(Outcome.UNAVAILABLE);}return reply;}finally{check();}
    }
    void initialize(){var r=request("PUT","/"+bucket,"",new byte[0],false,0,true);if(r.status()!=200&&r.status()!=204)throw fail(Outcome.UNAVAILABLE);byte[] b="<VersioningConfiguration xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\"><Status>Enabled</Status></VersioningConfiguration>".getBytes(StandardCharsets.UTF_8);r=request("PUT","/"+bucket,"versioning=",b,false,0,true);if(r.status()!=200)throw fail(Outcome.UNSUPPORTED);r=request("GET","/"+bucket,"versioning=",new byte[0],false,0,true);if(r.status()!=200||!new String(r.body(),StandardCharsets.UTF_8).contains("<Status>Enabled</Status>"))throw fail(Outcome.UNSUPPORTED);}
    @Override public synchronized Reference store(Identity id,UUID command,byte[] bytes){
        Objects.requireNonNull(id);Objects.requireNonNull(command);if(bytes==null||bytes.length<1||bytes.length>MAX_OBJECT)throw fail(Outcome.INVALID);bytes=bytes.clone();var intent=new Intent(id,command,hash(bytes),bytes.length);var old=intents.get(command);
        if(old!=null&&!old.equals(intent)||identities.containsKey(id)&&!identities.get(id).equals(command))throw fail(Outcome.CONFLICT);
        if(receipts.containsKey(command)){var ref=receipts.get(command);readExact(ref);return ref;}
        if(old==null){intents.put(command,intent);identities.put(id,command);var r=request("PUT",path(id),"",bytes,true,1,true);if(r.status()!=200&&r.status()!=412)throw fail(Outcome.UNAVAILABLE);}
        var list=inventory().stream().filter(e->e.key().equals(path(id).substring(bucket.length()+2))).toList();if(list.size()!=1||list.getFirst().marker())throw fail(Outcome.STALE);
        var ref=new Reference(id,new Version(list.getFirst().version()),intent.digest(),intent.bytes());readExact(ref);receipts.put(command,ref);return ref;
    }
    @Override public synchronized byte[] readExact(Reference ref){
        check();var versions=inventory().stream().filter(e->e.key().equals(path(ref.identity()).substring(bucket.length()+2))).toList();if(versions.isEmpty())throw fail(Outcome.NOT_FOUND);if(versions.size()!=1||versions.getFirst().marker()||!versions.getFirst().version().equals(ref.version().value()))throw fail(Outcome.STALE);
        return exactBytes(ref);
    }
    byte[] exactBytes(Reference ref){var r=request("GET",path(ref.identity()),"versionId="+enc(ref.version().value()),new byte[0],false,0,true);if(r.status()==404)throw fail(Outcome.NOT_FOUND);if(r.status()!=200)throw fail(Outcome.UNAVAILABLE);String returned=r.headers().getOrDefault("x-amz-version-id",List.of("")).getFirst();if(!returned.equals(ref.version().value()))throw fail(Outcome.UNSUPPORTED);if(r.body().length!=ref.bytes()||!hash(r.body()).equals(ref.sha256()))throw fail(Outcome.INTEGRITY_FAILED);return r.body().clone();}
    @Override public synchronized void removeExact(Reference ref){check();var owned=inventory().stream().anyMatch(e->!e.marker()&&e.key().equals(path(ref.identity()).substring(bucket.length()+2))&&e.version().equals(ref.version().value()));if(!owned)return;exactBytes(ref);var r=request("DELETE",path(ref.identity()),"versionId="+enc(ref.version().value()),new byte[0],false,0,true);if(r.status()!=200&&r.status()!=204)throw fail(Outcome.UNAVAILABLE);if(inventory().stream().anyMatch(e->e.version().equals(ref.version().value())))throw fail(Outcome.UNAVAILABLE);}
    synchronized void cleanup(){cleanupDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);monitor.check();for(var e:inventory()){var r=wire("DELETE","/"+bucket+"/"+e.key(),"versionId="+enc(e.version()),new byte[0],false,true);if(r.status()!=200&&r.status()!=204)throw fail(Outcome.UNAVAILABLE);monitor.check();}if(!inventory().isEmpty())throw fail(Outcome.UNAVAILABLE);var r=wire("DELETE","/"+bucket,"",new byte[0],false,true);if(r.status()!=200&&r.status()!=204)throw fail(Outcome.UNAVAILABLE);if(wire("HEAD","/"+bucket,"",new byte[0],false,true).status()!=404)throw fail(Outcome.UNAVAILABLE);monitor.check();}
    long submitted(){return submitted;}int creates(){return creates;}
}
