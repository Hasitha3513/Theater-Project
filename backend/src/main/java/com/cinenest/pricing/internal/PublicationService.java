package com.cinenest.pricing.internal;
import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.rooms.PackageConstraints;
import java.util.*;
import java.time.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import com.nimbusds.jose.util.JSONObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional
class PublicationService {
 private final PublicationRepository store;private final DraftRepository drafts;private final ScopedAuthorization auth;private final PackageConstraints rooms;
 PublicationService(PublicationRepository store,DraftRepository drafts,ScopedAuthorization auth,PackageConstraints rooms){this.store=store;this.drafts=drafts;this.auth=auth;this.rooms=rooms;}
 @SuppressWarnings("unchecked") static Map<String,Object> object(Map<String,Object> m,String k){return (Map<String,Object>)m.get(k);}
 static Set<UUID> rooms(Map<String,Object>d){Set<UUID> out=new TreeSet<>();for(Object id:(List<?>)d.get("roomIds"))out.add(UUID.fromString(id.toString()));return out;}
 private void require(UUID actor,UUID venue,String action){if(!auth.permits(actor,venue,action,true))throw new DraftFailure(403,"Access denied");}
 private void readPermit(UUID a,UUID v){if(!auth.permits(a,v,"package.read",false))throw new DraftFailure(403,"Access denied");}
 private Map<String,Object> record(UUID v,UUID p,UUID id){return store.versions(v,p).stream().filter(x->x.get("publicationId").equals(id)).findFirst().orElseThrow(()->new DraftFailure(404,"Publication not found"));}
 static Instant local(String raw,String timezone){try{var l=LocalDateTime.parse(raw);var z=ZoneId.of(timezone);var offsets=z.getRules().getValidOffsets(l);if(offsets.size()!=1)throw DraftRules.invalid("ambiguous or invalid venue-local time");return l.toInstant(offsets.getFirst());}catch(DateTimeException e){throw DraftRules.invalid("venue-local date/time");}}
 static Object sorted(Object o){if(o instanceof Map<?,?> m){var out=new TreeMap<String,Object>();m.forEach((k,v)->out.put(k.toString(),sorted(v)));return out;}if(o instanceof Collection<?> c)return c.stream().map(PublicationService::sorted).toList();return o;}
 static String hash(Map<String,Object>d){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSONObjectUtils.toJSONString((Map<String,?>)sorted(d)).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private PackageConstraints.PublicationFacts facts(UUID venue,Set<UUID> ids,boolean wide){return rooms.publication(venue,ids,wide,true).orElseThrow(()->DraftRules.invalid("Enabled venue/rooms and approved minimum policy required"));}
 private Map<String,Object> validate(UUID venue,Map<String,Object> snapshot,Instant start,Instant end,boolean wide){
  var f=facts(venue,rooms(snapshot),wide);int duration=((Number)snapshot.get("includedMinutes")).intValue(),maximumGuests=((Number)snapshot.get("maximumGuests")).intValue();
  if(!start.isBefore(end))throw DraftRules.invalid("service interval");var refs=new ArrayList<Map<String,Object>>();
  // Policy effective at the first service instant, plus every known change before the exclusive end.
  PackageConstraints.Policy at=null;for(var p:f.policies())if(!p.effectiveFrom().isAfter(start))at=p;
  if(at==null)throw DraftRules.invalid("Missing policy at service start");
  for(var p:f.policies())if(p.equals(at)||p.effectiveFrom().isAfter(start)&&p.effectiveFrom().isBefore(end)){if(duration<p.minimumMinutes())throw DraftRules.invalid("Minimum policy incompatible with service interval");refs.add(Map.of("policyId",p.id().toString(),"revision",p.revision(),"minimumMinutes",p.minimumMinutes(),"effectiveFrom",p.effectiveFrom().toString()));}
  if(duration<f.current().minimumMinutes())throw DraftRules.invalid("Current minimum policy incompatible");
  for(var room:f.current().rooms())if(maximumGuests>room.capacity())throw DraftRules.invalid("Room capacity changed");
  var allowed=drafts.options(venue);for(var option:DraftRules.objects(snapshot,"options")){
   var rate=allowed.stream().filter(x->x.get("serviceId").toString().equals(option.get("serviceId").toString())&&x.get("rateVersionId").toString().equals(option.get("rateVersionId").toString())).findFirst().orElseThrow(()->DraftRules.invalid("Shared service withdrawn"));
   for(var room:f.current().rooms())if(!room.facilities().contains(rate.get("requiredFacilityCode")))throw DraftRules.invalid("Room service compatibility changed");
  }
  return Map.of("timezone",f.timezone(),"roomIds",f.current().rooms().stream().map(x->x.roomId().toString()).sorted().toList(),"policies",refs,"currentPolicyId",f.current().policyId().toString(),"currentPolicyRevision",f.current().policyRevision());
 }
 private Map<String,Object> prepare(UUID actor,UUID venue,UUID pkg,Map<String,Object> input){
  if(input.containsKey("replacesPublicationId"))require(actor,venue,"package.deactivate");
  DraftRules.keys(input,"expectedRevision","salesFromLocal","salesToLocal","sessionFromLocal","sessionToLocal","reason","idempotencyKey","contentHash","replacesPublicationId","replacesRevision");
  var draft=drafts.find(venue,pkg,false).orElseThrow(()->new DraftFailure(404,"Draft not found"));long rev=DraftRules.revision(input,"expectedRevision");if(rev!=((Number)draft.get("revision")).longValue())throw new DraftFailure(409,"Draft changed");
  UUID author=store.author(pkg,rev);if(actor.equals(author))throw new DraftFailure(403,"Independent publisher required");
  var snapshot=DraftService.editable(draft);snapshot.remove("reason");var f=facts(venue,rooms(draft),(boolean)draft.get("venueWide"));
  Map<String,Object> dates=new LinkedHashMap<>();Map<String,Object> result=new LinkedHashMap<>();
  for(String key:List.of("salesFrom","salesTo","sessionFrom","sessionTo")){String raw=DraftRules.text(input,key+"Local",40,true);dates.put(key+"Local",raw);result.put(key,local(raw,f.timezone()).toString());}
  Instant salesFrom=Instant.parse(result.get("salesFrom").toString()),salesTo=Instant.parse(result.get("salesTo").toString()),sessionFrom=Instant.parse(result.get("sessionFrom").toString()),sessionTo=Instant.parse(result.get("sessionTo").toString());
  if(!salesFrom.isBefore(salesTo)||!salesTo.isAfter(Instant.now())||!sessionFrom.isBefore(sessionTo))throw DraftRules.invalid("publication intervals");
  snapshot.put("roomIds",f.current().rooms().stream().map(x->x.roomId().toString()).sorted().toList());snapshot.put("venueWide",false);
  var options=new ArrayList<Map<String,Object>>();var refs=drafts.options(venue);
  for(var o:DraftRules.objects(draft,"options")){var ref=refs.stream().filter(x->x.get("rateVersionId").toString().equals(o.get("rateVersionId").toString())).findFirst().orElseThrow(()->DraftRules.invalid("Service rate unavailable"));var frozen=new LinkedHashMap<>(ref);frozen.put("serviceId",ref.get("serviceId").toString());frozen.put("rateVersionId",ref.get("rateVersionId").toString());frozen.put("mode",o.get("mode"));frozen.put("additionalChargeMinor",o.get("mode").equals("INCLUDED")?"0":ref.get("unitPriceMinor"));options.add(frozen);}
  snapshot.put("options",options);snapshot.put("priceLabel","INDICATIVE - NOT A PAYABLE QUOTATION");snapshot.put("taxStatus","UNRESOLVED");snapshot.put("feeStatus","UNRESOLVED");snapshot.put("purchasable",false);
  var policy=validate(venue,snapshot,sessionFrom,sessionTo,false);result.putAll(Map.of("snapshot",snapshot,"policyRefs",policy,"localDates",dates,"timezone",f.timezone(),"sourceRevision",rev,"authorId",author,"publisherId",actor,"packageId",pkg,"reason",DraftRules.text(input,"reason",500,true)));
  // The review hash binds content, authoritative facts, exact scope, dates, author and reason.
  if(input.containsKey("replacesPublicationId")){result.put("replacesPublicationId",DraftRules.id(DraftRules.text(input,"replacesPublicationId",36,true)).toString());result.put("replacesRevision",DraftRules.revision(input,"replacesRevision"));}
  result.put("contentHash",hash(result));result.put("idempotencyKey",DraftRules.id(DraftRules.text(input,"idempotencyKey",36,true)));return result;
 }
 Map<String,Object> preview(UUID a,UUID v,UUID p,Map<String,Object> input){require(a,v,"package.publish");store.lock(v,p);var out=prepare(a,v,p,input);out.put("label","PUBLICATION REVIEW - INDICATIVE ONLY");return out;}
 Map<String,Object> publish(UUID a,UUID v,UUID p,Map<String,Object> input){require(a,v,"package.publish");store.lock(v,p);UUID key=DraftRules.id(DraftRules.text(input,"idempotencyKey",36,true));String requestHash=hash(input);var prior=store.replay(p,key);if(prior.isPresent()){if(!prior.get().get("requestHash").equals(requestHash)||!prior.get().get("publisherId").equals(a))throw new DraftFailure(409,"Idempotency key already used");return prior.get();}
  var d=prepare(a,v,p,input);if(!d.get("contentHash").equals(input.get("contentHash")))throw new DraftFailure(409,"Publication review changed; preview again");
  if(input.containsKey("replacesPublicationId")){UUID old=DraftRules.id(DraftRules.text(input,"replacesPublicationId",36,true));var before=record(v,p,old);if(!Set.of("ACTIVE","SCHEDULED").contains(before.get("state")))throw new DraftFailure(409,"Replacement is not active or scheduled");store.state(old,DraftRules.revision(input,"replacesRevision"),"INACTIVE","Explicit atomic replacement");store.work(old,"DONE",null);drafts.audit(a,v,p,"REPLACE",true,((Number)before.get("revision")).longValue(),((Number)before.get("revision")).longValue()+1,DraftRules.text(input,"reason",500,true));}else if(input.containsKey("replacesRevision"))throw DraftRules.invalid("replacement identity");
  UUID id=UUID.randomUUID();d.put("publicationId",id);d.put("versionId",UUID.randomUUID());d.put("requestHash",requestHash);d.put("state",Instant.parse(d.get("salesFrom").toString()).isAfter(Instant.now())?"SCHEDULED":"ACTIVE");store.insert(d);drafts.audit(a,v,p,"PUBLISH",true,null,0L,d.get("reason").toString());if(d.get("state").equals("ACTIVE"))store.work(id,"DONE",null);return record(v,p,id);
 }
 Map<String,Object> list(UUID a,UUID v,UUID p){readPermit(a,v);var items=store.versions(v,p);return Map.of("items",items,"permittedActions",actions(a,v));}
 private List<String> actions(UUID a,UUID v){var out=new ArrayList<String>();for(String action:List.of("package.publish","package.deactivate"))if(auth.permits(a,v,action,false))out.add(action);return out;}
 Map<String,Object> detail(UUID a,UUID v,UUID p,UUID id){readPermit(a,v);return record(v,p,id);}
 private String commandHash(Map<String,Object> input,String action){var m=new HashMap<>(input);m.put("action",action);return hash(m);}
 private boolean replayCommand(Map<String,Object>d,UUID a,Map<String,Object> input,String action){UUID key=DraftRules.id(DraftRules.text(input,"idempotencyKey",36,true));var receipt=store.commandReceipt((UUID)d.get("packageId"),key);if(receipt.isEmpty())return false;if(!d.get("publicationId").equals(receipt.get().get("publication"))||!a.equals(receipt.get().get("actor"))||!commandHash(input,action).equals(receipt.get().get("hash")))throw new DraftFailure(409,"Command idempotency conflict");return true;}
 Map<String,Object> deactivate(UUID a,UUID v,UUID p,UUID id,Map<String,Object> input){require(a,v,"package.deactivate");store.lock(v,p);DraftRules.keys(input,"expectedRevision","reason","idempotencyKey");var d=record(v,p,id);DraftRules.id(DraftRules.text(input,"idempotencyKey",36,true));long expected=DraftRules.revision(input,"expectedRevision");String reason=DraftRules.text(input,"reason",500,true);
  if(replayCommand(d,a,input,"deactivate"))return d;
  store.state(id,expected,"INACTIVE",reason);store.work(id,"DONE",null);store.command(id,DraftRules.id(input.get("idempotencyKey").toString()),commandHash(input,"deactivate"),a);drafts.audit(a,v,p,"DEACTIVATE",true,expected,expected+1,reason);return record(v,p,id);
 }
 Map<String,Object> retry(UUID a,UUID v,UUID p,UUID id,Map<String,Object> input){require(a,v,"package.publish");store.lock(v,p);DraftRules.keys(input,"expectedRevision","reason","idempotencyKey");DraftRules.id(DraftRules.text(input,"idempotencyKey",36,true));var d=record(v,p,id);if(replayCommand(d,a,input,"retry"))return d;long expected=DraftRules.revision(input,"expectedRevision");if(expected!=((Number)d.get("revision")).longValue())throw new DraftFailure(409,"Publication changed");if(!d.get("state").equals("BLOCKED"))throw new DraftFailure(409,"Only failed scheduled activation can be retried");if(!a.equals(d.get("publisherId"))||!d.get("failureReason").toString().startsWith("ACTIVATION:"))throw new DraftFailure(409,"New independent publication approval required");
  validate(v,object(d,"snapshot"),Instant.parse(d.get("sessionFrom").toString()),Instant.parse(d.get("sessionTo").toString()),false);if(!Instant.parse(d.get("salesTo").toString()).isAfter(Instant.now()))throw new DraftFailure(409,"Sales interval ended");store.state(id,expected,"SCHEDULED",null);store.work(id,"PENDING",null);store.command(id,DraftRules.id(input.get("idempotencyKey").toString()),commandHash(input,"retry"),a);drafts.audit(a,v,p,"RETRY",true,expected,expected+1,DraftRules.text(input,"reason",500,true));return record(v,p,id);
 }
 List<UUID> due(){return store.due();}
 void activate(UUID id){var entry=store.job(id).orElse(null);if(entry==null)return;UUID v=(UUID)entry.get("venueId"),p=(UUID)entry.get("packageId"),actor=(UUID)entry.get("publisherId");
  // Lock identity first, matching foreground commands; no external operations in this transaction.
  boolean permitted=auth.permits(actor,v,"package.publish",true);store.lock(v,p);var d=record(v,p,id);if(!d.get("state").equals("SCHEDULED")){store.work(id,"DONE",null);return;}if(Instant.parse(d.get("salesFrom").toString()).isAfter(Instant.now()))return;
  try{if(!permitted)throw new DraftFailure(403,"Publisher no longer eligible");if(!Instant.parse(d.get("salesTo").toString()).isAfter(Instant.now()))throw DraftRules.invalid("Sales interval expired");validate(v,object(d,"snapshot"),Instant.parse(d.get("sessionFrom").toString()),Instant.parse(d.get("sessionTo").toString()),false);store.state(id,((Number)d.get("revision")).longValue(),"ACTIVE",null);store.work(id,"DONE",null);drafts.audit(actor,v,p,"ACTIVATE",true,null,((Number)d.get("revision")).longValue()+1,"Validated scheduled activation");}
  catch(DraftFailure invalid){block(v,p,d,"ACTIVATION: "+invalid.getMessage(),actor);}
 }
 private void block(UUID v,UUID p,Map<String,Object>d,String reason,UUID actor){store.state((UUID)d.get("publicationId"),((Number)d.get("revision")).longValue(),"BLOCKED",reason);store.work((UUID)d.get("publicationId"),"FAILED",reason);drafts.audit(actor,v,p,"INVALIDATE",true,null,((Number)d.get("revision")).longValue()+1,reason);}
 Map<String,Object> catalog(UUID v,UUID pkg,UUID room,String sessionStart,String sessionEnd){if((sessionStart==null)!=(sessionEnd==null))throw DraftRules.invalid("session interval pair");Instant start=null,end=null;try{if(sessionStart!=null){start=Instant.parse(sessionStart);end=Instant.parse(sessionEnd);if(!start.isBefore(end))throw DraftRules.invalid("session interval");}}catch(DateTimeException invalid){throw DraftRules.invalid("session instants");}
  var out=new ArrayList<Map<String,Object>>();for(var candidate:store.catalog(v)){UUID p=(UUID)candidate.get("packageId");if(pkg!=null&&!pkg.equals(p))continue;store.lock(v,p);var d=record(v,p,(UUID)candidate.get("publicationId"));if(!d.get("state").equals("ACTIVE")||!Instant.parse(d.get("salesTo").toString()).isAfter(Instant.now()))continue;
   try{validate(v,object(d,"snapshot"),Instant.parse(d.get("sessionFrom").toString()),Instant.parse(d.get("sessionTo").toString()),false);}catch(DraftFailure invalid){block(v,p,d,"CATALOG: "+invalid.getMessage(),null);continue;}
   if(room!=null&&!rooms(object(d,"snapshot")).contains(room))continue;if(start!=null&&(start.isBefore(Instant.parse(d.get("sessionFrom").toString()))||end.isAfter(Instant.parse(d.get("sessionTo").toString()))))continue;
   Map<String,Object> item=new LinkedHashMap<>(object(d,"snapshot"));for(String privateKey:List.of("policyId","policyRevision","reason"))item.remove(privateKey);item.put("packageId",p);item.put("versionId",d.get("versionId"));item.put("salesFrom",d.get("salesFrom"));item.put("salesTo",d.get("salesTo"));item.put("sessionFrom",d.get("sessionFrom"));item.put("sessionTo",d.get("sessionTo"));item.put("timezone",d.get("timezone"));out.add(item);
  }if(pkg!=null&&out.isEmpty())throw new DraftFailure(404,"Published package not found");return Map.of("items",out,"purchasable",false,"priceLabel","INDICATIVE - tax and checkout fees unresolved; not a payable quotation");
 }
}
