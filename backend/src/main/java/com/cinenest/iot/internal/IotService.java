package com.cinenest.iot.internal;
import com.cinenest.identity.ScopedAuthorization;import com.cinenest.booking.BookingAccess;import com.cinenest.access.DeviceAccess;
import java.util.*;import java.time.*;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service
class IotService {
 static final String LABEL="SYNTHETIC IOT TEST — NO REAL EQUIPMENT CONTROL";
 private final IotRepository repo;private final IotPolicy policy;private final ScopedAuthorization auth;private final BookingAccess bookings;private final DeviceAccess access;private final LightingSimulator simulator;
 IotService(IotRepository r,IotPolicy p,ScopedAuthorization a,BookingAccess b,DeviceAccess x,LightingSimulator s){repo=r;policy=p;auth=a;bookings=b;access=x;simulator=s;}
 private void permit(UUID actor,UUID venue,String permission){if(!auth.eligible(actor,true)||!auth.permits(actor,venue,"iot.synthetic."+permission,true))throw new IotFailure(403);}
 private void rate(UUID actor){repo.rate(actor,policy.positive("rate-seconds"),policy.positive("rate-count"));}
 static UUID id(Map<String,Object>b,String k){try{return UUID.fromString(Objects.toString(b.get(k),""));}catch(RuntimeException e){throw new IotFailure(400);}}
 static long number(Map<String,Object>b,String k){Object v=b.get(k);if(!(v instanceof Number n)||n.longValue()<1||n.doubleValue()!=n.longValue())throw new IotFailure(400);return n.longValue();}
 private Map<String,Object> view(UUID actor,Map<String,Object>d){
  Map<String,Object> v=new LinkedHashMap<>();for(String k:List.of("device_id","venue_id","room_id","capability","enabled","online","revision","desired","observation_sequence","observed_at"))v.put(k,d.get(k));
  boolean fresh=Boolean.TRUE.equals(d.get("online"))&&d.get("observed_at")!=null&&IotRepository.time(d.get("observed_at")).isAfter(repo.now().minusSeconds(policy.positive("freshness-seconds")));
  v.put("observed",fresh?d.get("observed"):"UNKNOWN");v.put("observationFresh",fresh);v.put("physicalOccupancy","UNKNOWN");v.put("physicalReadiness","UNVERIFIED");v.put("synthetic",true);v.put("equipmentControlled",false);v.put("label",LABEL);v.put("commands",repo.commands((UUID)d.get("device_id")));v.put("audit",repo.auditRows((UUID)d.get("device_id")));
  List<String> actions=new ArrayList<>();for(String p:List.of("command","simulate","reconcile"))if(auth.permits(actor,(UUID)d.get("venue_id"),"iot.synthetic."+p,false))actions.add(p);v.put("permittedActions",actions);v.put("asOf",repo.now());return v;
 }
 @Transactional public Object read(UUID actor,UUID venue,UUID device,int page,int size){policy.configured();permit(actor,venue,"read");rate(actor);if(page<0||page>10000||size<1||size>50)throw new IotFailure(400);
  var rows=device==null?repo.devices(venue,page,size):List.of(repo.device(device,venue,false));
  repo.audit(actor,venue,device,null,"READ","ALLOW");return device==null?Map.of("items",rows.stream().map(d->view(actor,d)).toList(),"page",page,"size",size,"synthetic",true,"equipmentControlled",false):view(actor,rows.getFirst());
 }
 private void context(UUID actor,UUID venue,UUID device,BookingAccess.Context b,long revision){
  if(b.revision()!=revision||!b.venue().equals(venue)||!auth.eligible(b.owner(),true)||!access.eligible(b)||bookings.amendmentPending(b.booking()))throw new IotFailure(409);
  Instant now=repo.now();if(now.isBefore(b.start())||!now.isBefore(b.end()))throw new IotFailure(409);
 }
 @Transactional public Object command(UUID actor,UUID venue,UUID device,UUID command,String action,Map<String,Object> input){
  policy.configured();Set<String> allowed=new HashSet<>(List.of("commandKey","expectedRevision","bookingId","bookingRevision"));if(action.equals("COMMAND"))allowed.add("desired");else allowed.addAll(List.of("outcome","observed","observationSequence","observedAt","correlationId"));if(!allowed.containsAll(input.keySet()))throw new IotFailure(400);
  UUID booking=id(input,"bookingId"),key=id(input,"commandKey");long expected=number(input,"expectedRevision"),revision=number(input,"bookingRevision");
  access.lock(booking);String permission=action.equals("COMMAND")?"command":action.equals("SIMULATE")?"simulate":"reconcile";permit(actor,venue,permission);permit(actor,venue,"read");rate(actor);
  var b=bookings.lock(booking).orElseThrow(()->new IotFailure(409));context(actor,venue,device,b,revision);
  var d=repo.device(device,venue,true);if(!b.room().equals(d.get("room_id")))throw new IotFailure(403);
  String hash;try{hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((action+":"+venue+":"+device+":"+command+":"+com.nimbusds.jose.util.JSONObjectUtils.toJSONString(new TreeMap<>(input))).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  var receipts=repo.receipt(actor,key);if(receipts.isPresent()){var receipt=receipts.get();if(!hash.equals(receipt.get("payload_hash")))throw new IotFailure(409);repo.audit(actor,venue,device,(UUID)receipt.get("command_id"),"REPLAY","ALLOW");return view(actor,d);}
  repo.expire(device);Instant now=repo.now();if(!Boolean.TRUE.equals(d.get("enabled")))throw new IotFailure(409);
  if(action.equals("COMMAND")){
   if(expected!=((Number)d.get("revision")).longValue())throw new IotFailure(409);String desired=Objects.toString(input.get("desired"),"");if(!Set.of("ON","OFF").contains(desired))throw new IotFailure(400);
   command=UUID.randomUUID();Instant expiry=now.plusSeconds(policy.positive("ttl-seconds"));if(expiry.isAfter(b.end()))expiry=b.end();
   repo.create(command,device,actor,b,desired,expiry);
  }else{
   var c=repo.command(command,device);if(!booking.equals(c.get("booking_id"))||revision!=((Number)c.get("booking_revision")).longValue()||!b.start().equals(IotRepository.time(c.get("starts_at")))||!b.end().equals(IotRepository.time(c.get("ends_at")))||expected!=((Number)c.get("revision")).longValue()||!id(input,"correlationId").equals(c.get("correlation_id")))throw new IotFailure(409);
   // TTL forbids dispatch, not an attributable lookup of an uncertain prior result.
   if(action.equals("SIMULATE")&&!now.isBefore(IotRepository.time(c.get("expires_at"))))throw new IotFailure(409);
   String state=c.get("state").toString();if(!(action.equals("SIMULATE")?state.equals("PENDING"):state.equals("UNKNOWN")))throw new IotFailure(409);
   if(!Boolean.TRUE.equals(d.get("online")))throw new IotFailure(409);
   if(!auth.eligible((UUID)c.get("actor_id"),true)||(!auth.permits((UUID)c.get("actor_id"),venue,"iot.synthetic.command",true)||!auth.permits((UUID)c.get("actor_id"),venue,"iot.synthetic.read",true)))throw new IotFailure(403);
   if(repo.attempts(command)>=policy.positive("attempt-limit"))throw new IotFailure(429);
   var evidence=simulator.apply(input,now,policy.positive("freshness-seconds"));
   if(evidence.observation()!=null&&(evidence.sequence()<=((Number)d.get("observation_sequence")).longValue()||d.get("observed_at")!=null&&!evidence.observedAt().isAfter(IotRepository.time(d.get("observed_at")))))throw new IotFailure(409);
   repo.evidence(command,device,actor,action,evidence);
  }
  repo.receipt(actor,key,hash,command,action);repo.audit(actor,venue,device,command,action,"ALLOW");return view(actor,repo.device(device,venue,false));
 }
 @Transactional public void expire(){policy.configured();for(UUID device:repo.expiryDevices())repo.expire(device);}
}
