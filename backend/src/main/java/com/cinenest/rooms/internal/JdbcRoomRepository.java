package com.cinenest.rooms.internal;

import java.util.*;
import java.sql.Types;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRoomRepository implements RoomRepository {
    private final JdbcClient jdbc;
    JdbcRoomRepository(JdbcClient jdbc) { this.jdbc=jdbc; }
    static final String COMPLETE="v.enabled AND v.cleaning_buffer_minutes IS NOT NULL AND EXISTS(SELECT 1 FROM cinenest_rooms.venue_operating_window w WHERE w.venue_id=v.venue_id)";
    public Optional<Map<String,Object>> venue(UUID id) {
        return jdbc.sql("SELECT * FROM cinenest_rooms.venue WHERE venue_id=:id").param("id",id).query((rs,n)-> {
            Map<String,Object> v=new LinkedHashMap<>();v.put("venueId",rs.getObject("venue_id",UUID.class));v.put("code",rs.getString("code"));v.put("displayName",rs.getString("display_name"));v.put("timezone",rs.getString("timezone"));
            v.put("cleaningBufferMinutes",rs.getObject("cleaning_buffer_minutes"));v.put("postCloseCleaningApproved",rs.getBoolean("post_close_cleaning_approved"));v.put("operationalApprovalReference",rs.getString("operational_approval_reference"));v.put("enabled",rs.getBoolean("enabled"));v.put("version",rs.getLong("version"));return v;
        }).optional().map(v->{v.put("windows",jdbc.sql("SELECT weekday,opening_minute,closing_minute FROM cinenest_rooms.venue_operating_window WHERE venue_id=:id ORDER BY weekday").param("id",id).query((rs,n)->Map.<String,Object>of("weekday",rs.getInt(1),"openingMinute",rs.getInt(2),"closingMinute",rs.getInt(3))).list());return v;});
    }
    public Optional<Map<String,Object>> publicRoom(UUID id) {
        return jdbc.sql("SELECT r.venue_id FROM cinenest_rooms.room r JOIN cinenest_rooms.venue v USING(venue_id) WHERE r.room_id=:id AND r.enabled AND " + COMPLETE).param("id",id).query(UUID.class).optional().flatMap(venue->room(venue,id));
    }
    public Optional<Map<String,Object>> room(UUID venue, UUID id) {
        return jdbc.sql("SELECT * FROM cinenest_rooms.room WHERE venue_id=:venue AND room_id=:id").param("venue",venue).param("id",id).query((rs,n)-> {
            Map<String,Object> r=new LinkedHashMap<>();r.put("roomId",rs.getObject("room_id",UUID.class));r.put("venueId",rs.getObject("venue_id",UUID.class));r.put("code",rs.getString("code"));r.put("name",rs.getString("name"));r.put("description",rs.getString("description"));r.put("capacity",rs.getInt("capacity"));r.put("enabled",rs.getBoolean("enabled"));r.put("version",rs.getLong("version"));return r;
        }).optional().map(r->{r.put("facilities",jdbc.sql("SELECT code,label,sort_order FROM cinenest_rooms.room_facility WHERE room_id=:id ORDER BY sort_order,code").param("id",id).query((rs,n)->Map.<String,Object>of("code",rs.getString(1),"label",rs.getString(2),"sortOrder",rs.getInt(3))).list());
            r.put("images",jdbc.sql("SELECT image_id,asset_path,alt_text,sort_order FROM cinenest_rooms.room_image WHERE room_id=:id ORDER BY sort_order,image_id").param("id",id).query((rs,n)->Map.<String,Object>of("imageId",rs.getObject(1,UUID.class),"assetPath",rs.getString(2),"altText",rs.getString(3),"sortOrder",rs.getInt(4))).list());return r;});
    }
    public List<Map<String,Object>> venues(Set<UUID> scope, boolean publicOnly) {
        if(!publicOnly && scope.isEmpty())return List.of();
        String sql="SELECT v.venue_id FROM cinenest_rooms.venue v WHERE "+(publicOnly?COMPLETE:"v.venue_id IN (:scope)")+" ORDER BY v.code,v.venue_id";
        var query=jdbc.sql(sql);if(!publicOnly)query.param("scope",scope);
        return query.query(UUID.class).list().stream().map(id->venue(id).orElseThrow()).toList();
    }
    public Map<String,Object> rooms(UUID venue,String q,int minimum,String facility,Boolean enabled,int page,int size,boolean publicOnly) {
        String where=" FROM cinenest_rooms.room r JOIN cinenest_rooms.venue v ON v.venue_id=r.venue_id WHERE (:venue::uuid IS NULL OR r.venue_id=:venue) AND (position(lower(:q) in lower(r.code))>0 OR position(lower(:q) in lower(r.name))>0) AND r.capacity>=:minimum AND (:facility='' OR EXISTS(SELECT 1 FROM cinenest_rooms.room_facility f WHERE f.room_id=r.room_id AND f.code=:facility))"+(publicOnly?" AND r.enabled AND "+COMPLETE:" AND (:enabled::boolean IS NULL OR r.enabled=:enabled)");
        var count=bind(jdbc.sql("SELECT count(*)"+where),venue,q,minimum,facility,enabled,publicOnly).query(Long.class).single();
        var rows=bind(jdbc.sql("SELECT r.venue_id,r.room_id"+where+" ORDER BY r.code,r.room_id LIMIT :size OFFSET :offset"),venue,q,minimum,facility,enabled,publicOnly).param("size",size).param("offset",Math.multiplyExact((long)page,size)).query((rs,n)->room(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class)).orElseThrow()).list();
        return Map.of("items",rows,"total",count,"page",page,"size",size);
    }
    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec s,UUID venue,String q,int minimum,String facility,Boolean enabled,boolean pub) {
        s.param("venue",venue,Types.OTHER).param("q",q).param("minimum",minimum).param("facility",facility);if(!pub)s.param("enabled",enabled,Types.BOOLEAN);return s;
    }
    public void saveVenue(UUID id,Map<String,Object> d,boolean create) {
        String columns="code=:code,display_name=:name,timezone=:zone,cleaning_buffer_minutes=:buffer,post_close_cleaning_approved=:approved,operational_approval_reference=:reference,enabled=:enabled";
        var s=jdbc.sql(create?"INSERT INTO cinenest_rooms.venue(venue_id,code,display_name,timezone,cleaning_buffer_minutes,post_close_cleaning_approved,operational_approval_reference,enabled) VALUES(:id,:code,:name,:zone,:buffer,:approved,:reference,:enabled)":"UPDATE cinenest_rooms.venue SET "+columns+",version=version+1,updated_at=clock_timestamp() WHERE venue_id=:id AND version=:version");
        s.param("id",id).param("code",d.get("code")).param("name",d.get("displayName")).param("zone",d.get("timezone")).param("buffer",d.get("cleaningBufferMinutes"),Types.INTEGER).param("approved",d.get("postCloseCleaningApproved")).param("reference",d.get("operationalApprovalReference"),Types.VARCHAR).param("enabled",d.get("enabled"));
        if(!create)s.param("version",d.get("expectedVersion"));if(s.update()!=1)throw new RoomFailure(409,"Configuration changed; reload and compare");
        jdbc.sql("DELETE FROM cinenest_rooms.venue_operating_window WHERE venue_id=:id").param("id",id).update();
        for(var w:ConfigurationRules.objects(d,"windows"))jdbc.sql("INSERT INTO cinenest_rooms.venue_operating_window VALUES(:id,:day,:open,:close)").param("id",id).param("day",w.get("weekday")).param("open",w.get("openingMinute")).param("close",w.get("closingMinute")).update();
    }
    public void saveRoom(UUID venue,UUID id,Map<String,Object> d,boolean create) {
        var s=jdbc.sql(create?"INSERT INTO cinenest_rooms.room(room_id,venue_id,code,name,description,capacity,enabled) VALUES(:id,:venue,:code,:name,:description,:capacity,:enabled)":"UPDATE cinenest_rooms.room SET code=:code,name=:name,description=:description,capacity=:capacity,enabled=:enabled,version=version+1,updated_at=clock_timestamp() WHERE room_id=:id AND venue_id=:venue AND version=:version");
        s.param("id",id).param("venue",venue).param("code",d.get("code")).param("name",d.get("name")).param("description",d.get("description")).param("capacity",d.get("capacity")).param("enabled",d.get("enabled"));
        if(!create)s.param("version",d.get("expectedVersion"));if(s.update()!=1)throw new RoomFailure(409,"Configuration changed; reload and compare");
        jdbc.sql("DELETE FROM cinenest_rooms.room_facility WHERE room_id=:id").param("id",id).update();jdbc.sql("DELETE FROM cinenest_rooms.room_image WHERE room_id=:id").param("id",id).update();
        for(var f:ConfigurationRules.objects(d,"facilities"))jdbc.sql("INSERT INTO cinenest_rooms.room_facility VALUES(:id,:code,:label,:sort)").param("id",id).param("code",f.get("code")).param("label",f.get("label")).param("sort",f.get("sortOrder")).update();
        for(var i:ConfigurationRules.objects(d,"images"))jdbc.sql("INSERT INTO cinenest_rooms.room_image VALUES(:image,:id,:path,:alt,:sort)").param("image",UUID.randomUUID()).param("id",id).param("path",i.get("assetPath")).param("alt",i.get("altText")).param("sort",i.get("sortOrder")).update();
    }
    public void audit(UUID actor,UUID venue,UUID resource,String type,String action,boolean allowed,Long oldVersion,Long newVersion) {
        jdbc.sql("INSERT INTO cinenest_rooms.configuration_audit(event_id,actor_id,venue_id,resource_type,resource_id,action,outcome,old_version,new_version,changed_fields,correlation_id) VALUES(:id,:actor,:venue,:type,:resource,:action,:outcome,:old,:new,:fields,:correlation)")
            .param("id",UUID.randomUUID()).param("actor",actor,Types.OTHER).param("venue",venue,Types.OTHER).param("type",type).param("resource",resource,Types.OTHER).param("action",action).param("outcome",allowed?"ALLOW":"DENY").param("old",oldVersion,Types.BIGINT).param("new",newVersion,Types.BIGINT).param("fields",newVersion==null?"":type.equals("VENUE")?"code,displayName,timezone,cleaningBufferMinutes,postCloseCleaningApproved,operationalApprovalReference,enabled,windows":"code,name,description,capacity,enabled,facilities,images").param("correlation",UUID.randomUUID()).update();
    }
}
