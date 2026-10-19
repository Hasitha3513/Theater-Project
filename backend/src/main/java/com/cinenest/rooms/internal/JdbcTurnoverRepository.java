package com.cinenest.rooms.internal;
import java.util.*;import java.sql.Types;
import com.nimbusds.jose.util.JSONObjectUtils;
import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.stereotype.Repository;
@Repository
class JdbcTurnoverRepository implements TurnoverRepository {
 private final JdbcClient jdbc;JdbcTurnoverRepository(JdbcClient jdbc){this.jdbc=jdbc;}
 private JdbcClient.StatementSpec sql(String s,Object... args){var q=jdbc.sql(s);for(int i=0;i<args.length;i+=2)q.param(args[i].toString(),args[i+1],args[i+1]==null?Types.OTHER:Types.OTHER);return q;}
 public void lock(UUID actor){jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:id,17017))").param("id",actor.toString()).query(Object.class).list();}
 public Optional<Map<String,Object>> receipt(UUID actor,UUID key){return sql("SELECT * FROM cinenest_rooms.turnover_receipt WHERE actor_id=:a AND command_key=:k","a",actor,"k",key).query().listOfRows().stream().findFirst();}
 public Map<String,Object> find(UUID venue,UUID id,boolean lock){return sql("SELECT * FROM cinenest_rooms.turnover_case WHERE case_id=:id AND venue_id=:v"+(lock?" FOR UPDATE":""),"id",id,"v",venue).query().listOfRows().stream().findFirst().orElseThrow(()->new RoomFailure(404,"Case not found"));}
 public List<Map<String,Object>> list(UUID venue,int page){return sql("SELECT * FROM cinenest_rooms.turnover_case WHERE venue_id=:v ORDER BY created_at DESC,case_id LIMIT 25 OFFSET :o","v",venue,"o",page*25).query().listOfRows();}
 public List<Map<String,Object>> entries(UUID id){return sql("SELECT entry_id,actor_id,case_revision,kind,data,created_at FROM cinenest_rooms.turnover_entry WHERE case_id=:id ORDER BY case_revision,created_at,entry_id","id",id).query().listOfRows().stream().map(r->{try{r.put("data",JSONObjectUtils.parse(r.get("data").toString()));return r;}catch(Exception e){throw new IllegalStateException("Invalid retained evidence");}}).toList();}
 public void create(UUID id,UUID actor,UUID venue,UUID room,UUID booking,Long bookingRevision,String version,List<String> checklist){sql("INSERT INTO cinenest_rooms.turnover_case(case_id,venue_id,room_id,booking_id,booking_revision,state,checklist_version,checklist,created_by) VALUES(:id,:v,:r,:b,:br,'OPEN',:ver,CAST(:items AS jsonb),:a)","id",id,"v",venue,"r",room,"b",booking,"br",bookingRevision,"ver",version,"items","[\""+String.join("\",\"",checklist)+"\"]","a",actor).update();}
 public void update(UUID id,String state,UUID assignee,UUID ack){sql("UPDATE cinenest_rooms.turnover_case SET state=:s,assignee_id=:a,acknowledged_by=:ack,revision=revision+1,updated_at=clock_timestamp() WHERE case_id=:id","s",state,"a",assignee,"ack",ack,"id",id).update();}
 public void evidence(UUID actor,UUID id,long revision,String action,Map<String,Object> data){sql("INSERT INTO cinenest_rooms.turnover_entry(entry_id,case_id,actor_id,case_revision,kind,data) VALUES(:e,:id,:a,:rev,:k,CAST(:d AS jsonb))","e",UUID.randomUUID(),"id",id,"a",actor,"rev",revision,"k",action,"d",JSONObjectUtils.toJSONString(data)).update();}
 public void receipt(UUID actor,UUID key,String hash,UUID id){sql("INSERT INTO cinenest_rooms.turnover_receipt(actor_id,command_key,payload_hash,case_id) VALUES(:a,:k,:h,:id)","a",actor,"k",key,"h",hash,"id",id).update();}
 public void audit(UUID actor,UUID venue,UUID id,long revision,String action){sql("INSERT INTO cinenest_rooms.turnover_audit VALUES(:x,:a,:v,:id,:action,:rev,clock_timestamp())","x",UUID.randomUUID(),"a",actor,"v",venue,"id",id,"action",action,"rev",revision).update();}
}
