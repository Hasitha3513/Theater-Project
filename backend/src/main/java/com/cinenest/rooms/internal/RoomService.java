package com.cinenest.rooms.internal;

import com.cinenest.identity.ScopedAuthorization;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class RoomService {
    private final RoomRepository repository;
    private final ScopedAuthorization authorization;
    private final Set<String> operationalApprovals;
    // No approved real/public room photos exist in this repository. Empty until separately supplied and reviewed.
    private static final Set<String> APPROVED_IMAGES=Set.of();
    RoomService(RoomRepository repository,ScopedAuthorization authorization,@Value("${cinenest.rooms.operational-approvals:}") String approvals) {
        this.repository=repository;this.authorization=authorization;
        operationalApprovals=Set.copyOf(Arrays.stream(approvals.split(",")).map(String::trim).filter(v->!v.isEmpty()).toList());
    }
    void require(UUID actor,UUID venue,String action,boolean lock) {if(!authorization.permits(actor,venue,action,lock))throw new RoomFailure(403,"Access denied");}
    Map<String,Object> venue(UUID actor,UUID venue) {
        require(actor,venue,"venue.read",false);var v=repository.venue(venue).orElseThrow(()->new RoomFailure(404,"Not found"));return adminVenue(actor,v);
    }
    List<Map<String,Object>> venues(UUID actor,boolean pub) {
        if(!pub && !authorization.eligible(actor))throw new RoomFailure(403,"Access denied");
        return repository.venues(pub?Set.of():authorization.venues(actor,"venue.read"),pub).stream().map(v->pub?publicVenue(v):adminVenue(actor,v)).toList();
    }
    private Map<String,Object> adminVenue(UUID actor,Map<String,Object> v) {
        v.put("permittedActions",authorization.permits(actor,(UUID)v.get("venueId"),"venue.write",false)?List.of("venue.read","venue.write"):List.of("venue.read"));v.put("configured",complete(v));return v;
    }
    static boolean complete(Map<String,Object> v){return v.get("cleaningBufferMinutes")!=null && !((List<?>)v.get("windows")).isEmpty();}
    static Map<String,Object> publicVenue(Map<String,Object> v) {Map<String,Object> out=new LinkedHashMap<>();for(String k:List.of("venueId","code","displayName","timezone","windows","version"))out.put(k,v.get(k));return out;}
    Map<String,Object> rooms(UUID actor,UUID venue,String q,int minimum,String facility,Boolean enabled,int page,int size,boolean pub) {
        if(q.length()>200 || minimum<0 || page<0 || size<1 || size>100 || facility.length()>64)throw ConfigurationRules.invalid("filters");
        if(!pub)require(actor,venue,"room.read",false);
        var result=new LinkedHashMap<>(repository.rooms(venue,q.trim(),minimum,facility.toUpperCase(Locale.ROOT),enabled,page,size,pub));
        @SuppressWarnings("unchecked") var rows=(List<Map<String,Object>>)result.get("items");
        result.put("items",rows.stream().map(r->present(actor,r,pub)).toList());if(!pub)result.put("permittedActions",authorization.permits(actor,venue,"room.write",false)?List.of("room.read","room.write"):List.of("room.read"));return result;
    }
    Map<String,Object> room(UUID actor,UUID venue,UUID room,boolean pub) {
        if(!pub)require(actor,venue,"room.read",false);
        Map<String,Object> r;
        if(pub){
            // Public room lookup uses a qualified repository query, never exposes disabled/private configuration.
            r=repository.publicRoom(room).orElseThrow(()->new RoomFailure(404,"Not found"));
        }else r=repository.room(venue,room).orElseThrow(()->new RoomFailure(404,"Not found"));
        return present(actor,r,pub);
    }
    private Map<String,Object> present(UUID actor,Map<String,Object> r,boolean pub) {
        var v=repository.venue((UUID)r.get("venueId")).orElseThrow();
        if(pub){Map<String,Object> out=new LinkedHashMap<>();for(String k:List.of("roomId","venueId","code","name","description","capacity","facilities","images"))out.put(k,r.get(k));out.put("venue",publicVenue(v));return out;}
        r.put("permittedActions",authorization.permits(actor,(UUID)r.get("venueId"),"room.write",false)?List.of("room.read","room.write"):List.of("room.read"));r.put("configured",complete(v));return r;
    }
    Map<String,Object> saveVenue(UUID actor,UUID id,Map<String,Object> d,boolean create) {
        require(actor,id,"venue.write",true);
        ConfigurationRules.keys(d,create?new String[]{"venueId","code","displayName","timezone","cleaningBufferMinutes","postCloseCleaningApproved","operationalApprovalReference","enabled","windows"}:new String[]{"code","displayName","timezone","cleaningBufferMinutes","postCloseCleaningApproved","operationalApprovalReference","enabled","windows","expectedVersion"});
        if(!create) {ConfigurationRules.version(d);repository.venue(id).orElseThrow(()->new RoomFailure(404,"Not found"));}
        d=new HashMap<>(d);d.put("code",ConfigurationRules.code(d,"code"));d.put("displayName",ConfigurationRules.text(d,"displayName",200,true));ConfigurationRules.neutral((String)d.get("displayName"));
        d.put("timezone",ConfigurationRules.text(d,"timezone",200,true));ConfigurationRules.zone((String)d.get("timezone"));
        if(d.get("cleaningBufferMinutes")!=null)ConfigurationRules.integer(d,"cleaningBufferMinutes",0,Integer.MAX_VALUE);
        boolean approved=ConfigurationRules.flag(d,"postCloseCleaningApproved");String reference=ConfigurationRules.text(d,"operationalApprovalReference",300,false);
        if(approved && !operationalApprovals.contains(id+":"+reference))throw ConfigurationRules.invalid("operationalApprovalReference");
        d.put("postCloseCleaningApproved",approved);d.put("operationalApprovalReference",reference.isEmpty()?null:reference);d.put("enabled",ConfigurationRules.flag(d,"enabled"));
        var windows=ConfigurationRules.objects(d,"windows");Set<Integer> days=new HashSet<>();
        for(var w:windows){ConfigurationRules.keys(w,"weekday","openingMinute","closingMinute");int day=ConfigurationRules.integer(w,"weekday",1,7);int open=ConfigurationRules.integer(w,"openingMinute",0,1439);int close=ConfigurationRules.integer(w,"closingMinute",1,1440);if(open>=close||!days.add(day))throw ConfigurationRules.invalid("windows");}
        d.put("windows",windows);if((boolean)d.get("enabled")&&!complete(d))throw ConfigurationRules.invalid("incomplete venue");
        repository.saveVenue(id,d,create);repository.audit(actor,id,id,"VENUE",create?"CREATE":"UPDATE",true,create?null:((Number)d.get("expectedVersion")).longValue(),create?0L:((Number)d.get("expectedVersion")).longValue()+1);return venue(actor,id);
    }
    Map<String,Object> saveRoom(UUID actor,UUID venue,UUID id,Map<String,Object> d,boolean create) {
        require(actor,venue,"room.write",true);repository.lockVenue(venue);repository.venue(venue).orElseThrow(()->new RoomFailure(404,"Not found"));
        ConfigurationRules.keys(d,create?new String[]{"code","name","description","capacity","enabled","facilities","images"}:new String[]{"code","name","description","capacity","enabled","facilities","images","expectedVersion"});
        if(!create){ConfigurationRules.version(d);repository.room(venue,id).orElseThrow(()->new RoomFailure(404,"Not found"));}
        d=new HashMap<>(d);d.put("code",ConfigurationRules.code(d,"code"));d.put("name",ConfigurationRules.text(d,"name",200,true));d.put("description",ConfigurationRules.text(d,"description",2000,false));ConfigurationRules.neutral(d.get("name")+" "+d.get("description"));ConfigurationRules.integer(d,"capacity",1,Integer.MAX_VALUE);d.put("enabled",ConfigurationRules.flag(d,"enabled"));
        var facilities=ConfigurationRules.objects(d,"facilities");Set<String> codes=new HashSet<>();
        for(var f:facilities){ConfigurationRules.keys(f,"code","label","sortOrder");f.put("code",ConfigurationRules.code(f,"code"));f.put("label",ConfigurationRules.text(f,"label",300,true));ConfigurationRules.neutral((String)f.get("label"));ConfigurationRules.integer(f,"sortOrder",0,Integer.MAX_VALUE);if(!codes.add((String)f.get("code")))throw ConfigurationRules.invalid("facilities");}d.put("facilities",facilities);
        var images=ConfigurationRules.objects(d,"images");Set<String> paths=new HashSet<>();for(var i:images){ConfigurationRules.keys(i,"assetPath","altText","sortOrder");String path=ConfigurationRules.text(i,"assetPath",2000,true);if(!APPROVED_IMAGES.contains(path)||!paths.add(path))throw ConfigurationRules.invalid("assetPath");ConfigurationRules.text(i,"altText",300,true);ConfigurationRules.integer(i,"sortOrder",0,Integer.MAX_VALUE);}d.put("images",images);
        repository.saveRoom(venue,id,d,create);repository.audit(actor,venue,id,"ROOM",create?"CREATE":"UPDATE",true,create?null:((Number)d.get("expectedVersion")).longValue(),create?0L:((Number)d.get("expectedVersion")).longValue()+1);return room(actor,venue,id,false);
    }
}
