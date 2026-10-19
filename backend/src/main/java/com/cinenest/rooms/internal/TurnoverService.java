package com.cinenest.rooms.internal;

import com.cinenest.identity.ScopedAuthorization;
import com.cinenest.rooms.OperationsEvidence;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class TurnoverService {
 private final TurnoverRepository repository;
 private final RoomRepository rooms;
 private final ScopedAuthorization identity;
 private final List<OperationsEvidence> sources;
 private final boolean enabled;
 private final String definition;
 TurnoverService(TurnoverRepository repository,RoomRepository rooms,ScopedAuthorization identity,List<OperationsEvidence> sources,
  @Value("${cinenest.rooms.turnover.enabled:false}") boolean enabled,
  @Value("${cinenest.rooms.turnover.synthetic-checklist:}") String definition){this.repository=repository;this.rooms=rooms;this.identity=identity;this.sources=sources;this.enabled=enabled;this.definition=definition;}
 private void configured(){if(!enabled||!definition.matches("[A-Z0-9_-]{1,64}:[A-Z0-9_-]{1,64}(,[A-Z0-9_-]{1,64}){0,19}"))throw new RoomFailure(503,"Synthetic turnover is not configured");}
 private void permit(UUID actor,UUID venue,String action,boolean lock){if(!identity.permits(actor,venue,"operations.synthetic.read",lock)||!identity.permits(actor,venue,"operations.synthetic."+action,lock))throw new RoomFailure(403,"Access denied");}
 private static UUID id(Map<String,Object> d,String key){return ConfigurationRules.uuid(ConfigurationRules.text(d,key,36,true));}
 private OperationsEvidence.Fact fact(String source,UUID reference,UUID venue,UUID room){return sources.stream().filter(x->x.source().equals(source)).findFirst().flatMap(x->x.inspect(reference,venue,room)).orElseThrow(()->new RoomFailure(409,"Source reference unavailable or outside room scope"));}
 Map<String,Object> list(UUID actor,UUID venue,int page){if(page<0||page>100000)throw ConfigurationRules.invalid("page");permit(actor,venue,"read",false);return Map.of("items",repository.list(venue,page).stream().map(x->view(actor,venue,x)).toList(),"page",page,"size",25);}
 Map<String,Object> get(UUID actor,UUID venue,UUID id){permit(actor,venue,"read",false);return view(actor,venue,repository.find(venue,id,false));}
 private Map<String,Object> view(UUID actor,UUID venue,Map<String,Object> row){var out=new LinkedHashMap<>(row);try{out.put("checklist",JSONObjectUtils.parse("{\"items\":"+row.get("checklist")+"}").get("items"));}catch(Exception e){throw new IllegalStateException("Invalid checklist");}out.put("entries",repository.entries((UUID)row.get("case_id")));out.put("assignment",row.get("assignee_id")==null?"UNASSIGNED":"ASSIGNED");out.put("physicalOccupancy","UNKNOWN");out.put("physicalReadiness","UNVERIFIED");out.put("inventoryChanged",false);out.put("permittedActions",List.of("read","record","assign","review").stream().filter(x->identity.permits(actor,venue,"operations.synthetic."+x,false)).toList());return out;}
 Map<String,Object> command(UUID actor,UUID venue,UUID target,String action,Map<String,Object> input){
  configured();String permission=switch(action){case "CREATE","ACKNOWLEDGE","CHECKLIST","COMPLETE","EXCEPTION"->"record";case "ASSIGN"->"assign";case "REVIEW","RESOLVE_EXCEPTION"->"review";default->throw ConfigurationRules.invalid("action");};
  repository.lock(actor);permit(actor,venue,permission,true);
  UUID key=id(input,"commandKey");long expected=ConfigurationRules.version(input);
  Set<String> fields=new HashSet<>(Set.of("commandKey","expectedVersion"));fields.addAll(switch(action){case "CREATE"->Set.of("roomId","bookingId");case "ASSIGN"->Set.of("assigneeId");case "CHECKLIST"->Set.of("item","asserted");case "EXCEPTION"->Set.of("category","source","referenceId");case "RESOLVE_EXCEPTION"->Set.of("entryId");default->Set.of();});ConfigurationRules.keys(input,fields.toArray(String[]::new));
  String hash=hash(venue+":"+target+":"+action+":"+JSONObjectUtils.toJSONString(new TreeMap<>(input)));
  var receipt=repository.receipt(actor,key);
  if(receipt.isPresent()){if(!hash.equals(receipt.get().get("payload_hash")))throw new RoomFailure(409,"Command key reused with changed payload");return get(actor,venue,(UUID)receipt.get().get("case_id"));}
  UUID caseId=target;Map<String,Object> evidence=new LinkedHashMap<>();long revision;
  if(action.equals("CREATE")){
   if(expected!=0)throw new RoomFailure(409,"Initial revision must be zero");UUID room=id(input,"roomId");rooms.room(venue,room).orElseThrow(()->new RoomFailure(404,"Room not found"));
   UUID booking=input.get("bookingId")==null?null:id(input,"bookingId");Long bookingRevision=null;
   if(booking!=null)bookingRevision=fact("BOOKING",booking,venue,room).revision();
   var parts=definition.split(":");var items=List.of(parts[1].split(","));if(new HashSet<>(items).size()!=items.size())throw new RoomFailure(503,"Synthetic checklist contains duplicate items");
   caseId=UUID.randomUUID();repository.create(caseId,actor,venue,room,booking,bookingRevision,parts[0],items);revision=0;
  }else{
   var c=repository.find(venue,caseId,true);revision=((Number)c.get("revision")).longValue();if(revision!=expected)throw new RoomFailure(409,"Stale revision; reload and compare");String state=c.get("state").toString();if(state.equals("RESOLVED"))throw new RoomFailure(409,"Case already resolved");
   UUID assignee=(UUID)c.get("assignee_id"),ack=(UUID)c.get("acknowledged_by");var entries=repository.entries(caseId);String next=state;
   switch(action){
    case "ASSIGN"->{if(!state.equals("OPEN"))throw new RoomFailure(409,"Assignment is frozen after acknowledgement");assignee=input.get("assigneeId")==null?null:id(input,"assigneeId");if(assignee!=null&&(!identity.permits(assignee,venue,"operations.synthetic.read",true)||!identity.permits(assignee,venue,"operations.synthetic.record",true)))throw new RoomFailure(409,"Assignee lacks current scoped authority");if(assignee!=null)evidence.put("assigneeId",assignee.toString());}
    case "ACKNOWLEDGE"->{assigned(actor,assignee);if(!state.equals("OPEN"))throw new RoomFailure(409,"Acknowledgement already recorded");ack=actor;next="ACKNOWLEDGED";}
    case "CHECKLIST"->{assigned(actor,assignee);if(!state.equals("ACKNOWLEDGED"))throw new RoomFailure(409,"Acknowledge before asserting checklist");String item=ConfigurationRules.text(input,"item",64,true);if(!c.get("checklist").toString().contains("\""+item+"\"")||!Boolean.TRUE.equals(input.get("asserted")))throw ConfigurationRules.invalid("item/asserted");evidence.put("item",item);evidence.put("asserted",true);}
    case "COMPLETE"->{assigned(actor,assignee);if(!state.equals("ACKNOWLEDGED")||!complete(c,entries))throw new RoomFailure(409,"Required checklist evidence missing");next="ASSERTED";}
    case "EXCEPTION"->{String category=ConfigurationRules.text(input,"category",30,true);if(!Set.of("CLEANING","MAINTENANCE","UNKNOWN_DEVICE").contains(category))throw ConfigurationRules.invalid("category");evidence.put("category",category);if(input.get("referenceId")!=null){String source=ConfigurationRules.text(input,"source",20,true);if(!source.equals("IOT"))throw ConfigurationRules.invalid("source");var f=fact(source,id(input,"referenceId"),venue,(UUID)c.get("room_id"));evidence.put("source",source);evidence.put("referenceId",f.reference().toString());evidence.put("sourceRevision",f.revision());evidence.put("reportedState",f.reportedState());}else if(input.containsKey("source"))throw ConfigurationRules.invalid("referenceId");}
    case "RESOLVE_EXCEPTION"->{UUID entry=id(input,"entryId");if(!unresolved(entries).contains(entry.toString()))throw new RoomFailure(409,"Unresolved exception not found");evidence.put("entryId",entry.toString());}
    case "REVIEW"->{if(!state.equals("ASSERTED")||!complete(c,entries)||!unresolved(entries).isEmpty())throw new RoomFailure(409,"Checklist or unresolved exceptions prevent synthetic resolution");if(assignee==null||!identity.permits(assignee,venue,"operations.synthetic.record",true)||!identity.permits(assignee,venue,"operations.synthetic.read",true))throw new RoomFailure(409,"Assigned authority no longer eligible");next="RESOLVED";evidence.put("outcome","SYNTHETIC_ONLY");}
    default->throw ConfigurationRules.invalid("action");
   }
   repository.update(caseId,next,assignee,ack);revision++;
  }
  repository.evidence(actor,caseId,revision,action,evidence);repository.audit(actor,venue,caseId,revision,action);repository.receipt(actor,key,hash,caseId);return view(actor,venue,repository.find(venue,caseId,false));
 }
 private static void assigned(UUID actor,UUID assignee){if(!actor.equals(assignee))throw new RoomFailure(403,"Only the assigned eligible actor may assert this work");}
 @SuppressWarnings("unchecked") private static Map<String,Object> data(Map<String,Object> entry){return (Map<String,Object>)entry.get("data");}
 static Set<String> unresolved(List<Map<String,Object>> entries){Set<String> ids=new HashSet<>();for(var e:entries){if(e.get("kind").equals("EXCEPTION"))ids.add(e.get("entry_id").toString());if(e.get("kind").equals("RESOLVE_EXCEPTION"))ids.remove(data(e).get("entryId").toString());}return ids;}
 private static boolean complete(Map<String,Object> c,List<Map<String,Object>> entries){Set<String> asserted=new HashSet<>();for(var e:entries)if(e.get("kind").equals("CHECKLIST"))asserted.add(data(e).get("item").toString());try{var items=(List<?>)JSONObjectUtils.parse("{\"items\":"+c.get("checklist")+"}").get("items");return asserted.containsAll(items);}catch(Exception e){throw new IllegalStateException("Invalid checklist");}}
 private static String hash(String text){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
