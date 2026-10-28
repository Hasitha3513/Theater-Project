package com.cinenest;
import com.sun.net.httpserver.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.nio.file.attribute.PosixFilePermissions;import java.time.Instant;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicInteger;
import com.nimbusds.jose.util.JSONObjectUtils;
/** Networked, non-Meta fixture. All credentials/subjects are generated, never real or committed. */
public final class FacebookContractIssuer implements AutoCloseable {
 public final String appId="synthetic-"+UUID.randomUUID().toString().replace("-","");
 public final String secret=UUID.randomUUID()+"-"+UUID.randomUUID();
 public final String knownSubject=UUID.randomUUID().toString();
 public volatile String subject=knownSubject,mode="VALID",redirect;
 public final Path directory,secretFile;
 private final HttpServer server;private final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
 private record Flow(String subject,String mode,String redirect){}
 private final Map<String,Flow> codes=new ConcurrentHashMap<>(),tokens=new ConcurrentHashMap<>();
 public final AtomicInteger exchanges=new AtomicInteger(),inspections=new AtomicInteger();
 public FacebookContractIssuer(){try{
  directory=Files.createTempDirectory("cinenest-facebook-fixture-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));secretFile=Files.createFile(directory.resolve("secret"),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));Files.writeString(secretFile,secret);
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(executor);server.createContext("/",this::handle);server.start();
 }catch(Exception e){throw new IllegalStateException("Synthetic Facebook fixture unavailable");}}
 public String origin(){return "http://127.0.0.1:"+server.getAddress().getPort();}
 public String namespace(){return "urn:cinenest:identity:facebook:app:"+appId;}
 public void reset(){codes.clear();tokens.clear();exchanges.set(0);inspections.set(0);subject=knownSubject;mode="VALID";}
 private Map<String,String> query(HttpExchange x){Map<String,String> v=new HashMap<>();for(String part:Objects.toString(x.getRequestURI().getRawQuery(),"").split("&")){String[] kv=part.split("=",2);v.put(URLDecoder.decode(kv[0],StandardCharsets.UTF_8),kv.length>1?URLDecoder.decode(kv[1],StandardCharsets.UTF_8):"");}return v;}
 private void json(HttpExchange x,int status,Object value)throws Exception {byte[] b=JSONObjectUtils.toJSONString((Map<String,?>)value).getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(status,b.length);try(var out=x.getResponseBody()){out.write(b);}}
 private void handle(HttpExchange x){try{
  if(!x.getRequestMethod().equals("GET")){json(x,405,Map.of());return;}var q=query(x);String path=x.getRequestURI().getPath();
  if(path.equals("/v26.0/dialog/oauth")){
   if(!appId.equals(q.get("client_id"))||!Objects.equals(redirect,q.get("redirect_uri"))||!"code".equals(q.get("response_type"))||!"public_profile".equals(q.get("scope"))||q.get("state")==null){json(x,400,Map.of());return;}
   String code=UUID.randomUUID().toString();codes.put(code,new Flow(subject,mode,redirect));String location=redirect+"?state="+URLEncoder.encode(q.get("state"),StandardCharsets.UTF_8)+(mode.equals("CANCEL")?"&error=access_denied&error_reason=user_denied":"&code="+code);
   x.getResponseHeaders().set("Location",location);x.sendResponseHeaders(302,-1);x.close();return;
  }
  if(path.equals("/v26.0/oauth/access_token")){
   exchanges.incrementAndGet();Flow flow=codes.remove(q.getOrDefault("code",""));
   if(flow==null||!appId.equals(q.get("client_id"))||!secret.equals(q.get("client_secret"))||!flow.redirect.equals(q.get("redirect_uri"))){json(x,400,Map.of());return;}
   String token=UUID.randomUUID()+"-"+UUID.randomUUID();tokens.put(token,flow);json(x,200,Map.of("access_token",token,"token_type","bearer","expires_in",flow.mode.equals("TOKEN_EXPIRED")?-1:300));return;
  }
  if(path.equals("/debug_token")){
   inspections.incrementAndGet();Flow flow=tokens.get(q.getOrDefault("input_token",""));if(flow==null||!(appId+"|"+secret).equals(q.get("access_token"))){json(x,400,Map.of());return;}
   if(flow.mode.equals("TIMEOUT")){Thread.sleep(2500);}
   if(flow.mode.equals("API_ERROR")){json(x,503,Map.of());return;}
   if(flow.mode.equals("MALFORMED")){byte[] b="not-json".getBytes();x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);x.close();return;}
   long now=Instant.now().getEpochSecond();json(x,200,Map.of("data",Map.of("app_id",flow.mode.equals("WRONG_APP")?"other-synthetic-app":appId,"user_id",flow.mode.equals("MISSING_ID")?"":flow.subject,"type",flow.mode.equals("BAD_TYPE")?"APP":"USER","is_valid",!flow.mode.equals("INVALID"),"expires_at",now+(flow.mode.equals("EXPIRED")?-10:300),"data_access_expires_at",now+(flow.mode.equals("DATA_EXPIRED")?-10:600),"scopes",List.of("public_profile"))));return;
  }
  json(x,404,Map.of());
 }catch(Exception failed){try{json(x,400,Map.of());}catch(Exception ignored){x.close();}}}
 @Override public void close()throws Exception{server.stop(0);executor.close();Files.deleteIfExists(secretFile);Files.deleteIfExists(directory);}
 @Override public String toString(){return "Isolated synthetic Facebook fixture [redacted]";}
}
