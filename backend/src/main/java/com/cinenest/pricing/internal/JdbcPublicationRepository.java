package com.cinenest.pricing.internal;
import java.util.*;
import java.time.*;
import com.nimbusds.jose.util.JSONObjectUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
@Repository
class JdbcPublicationRepository implements PublicationRepository {
 private final JdbcClient jdbc;
 JdbcPublicationRepository(JdbcClient jdbc){this.jdbc=jdbc;}
 public void lock(UUID venue,UUID pkg){if(jdbc.sql("SELECT package_id FROM cinenest_pricing.package WHERE package_id=:p AND venue_id=:v FOR UPDATE").param("p",pkg).param("v",venue).query(UUID.class).optional().isEmpty())throw new DraftFailure(404,"Package not found");}
 public UUID author(UUID pkg,long revision){return jdbc.sql("SELECT actor_id FROM cinenest_pricing.package_draft_revision WHERE package_id=:p AND revision=:r").param("p",pkg).param("r",revision).query(UUID.class).optional().orElseThrow(()->new DraftFailure(409,"Draft history missing"));}
 private static final String SELECT="SELECT p.*,v.source_revision,v.content_hash,v.snapshot::text,v.policy_refs::text,v.author_id,v.publisher_id,v.reason,v.version_no,k.venue_id FROM cinenest_pricing.package_publication p JOIN cinenest_pricing.package_version v USING(version_id,package_id) JOIN cinenest_pricing.package k USING(package_id) ";
 private List<Map<String,Object>> query(String where,Map<String,?> args){return jdbc.sql(SELECT+where).params(args).query((rs,n)->{
  Map<String,Object>d=new LinkedHashMap<>();for(String k:List.of("publication_id","version_id","package_id","venue_id","author_id","publisher_id","idempotency_key","last_command_key","last_command_actor"))d.put(camel(k),rs.getObject(k,UUID.class));
  for(String k:List.of("revision","source_revision","version_no"))d.put(camel(k),rs.getLong(k));for(String k:List.of("state","timezone","content_hash","request_hash","reason","last_command_hash"))d.put(camel(k),rs.getString(k));
  for(String k:List.of("sales_from","sales_to","session_from","session_to"))d.put(camel(k),rs.getTimestamp(k).toInstant().toString());
  d.put("failureReason",rs.getString("failure_reason"));d.put("snapshot",json(rs.getString("snapshot")));d.put("policyRefs",json(rs.getString("policy_refs")));d.put("localDates",json(rs.getString("local_dates")));return d;
 }).list();}
 private static String camel(String x){var p=x.split("_");String out=p[0];for(int i=1;i<p.length;i++)out+=Character.toUpperCase(p[i].charAt(0))+p[i].substring(1);return out;}
 static Map<String,Object> json(String raw){try{return JSONObjectUtils.parse(raw);}catch(java.text.ParseException e){throw new IllegalStateException("Invalid publication snapshot",e);}}
 public List<Map<String,Object>> versions(UUID venue,UUID pkg){return query("WHERE k.venue_id=:v AND p.package_id=:p ORDER BY v.version_no DESC",Map.of("v",venue,"p",pkg));}
 public List<Map<String,Object>> catalog(UUID venue){return query("WHERE k.venue_id=:v AND p.state='ACTIVE' AND p.sales_from<=clock_timestamp() AND p.sales_to>clock_timestamp() ORDER BY k.code,v.version_no",Map.of("v",venue));}
 public Optional<Map<String,Object>> replay(UUID pkg,UUID key){return query("WHERE p.package_id=:p AND p.idempotency_key=:k",Map.of("p",pkg,"k",key)).stream().findFirst();}
 public void insert(Map<String,Object>d){
  jdbc.sql("INSERT INTO cinenest_pricing.package_version(version_id,package_id,version_no,source_revision,content_hash,snapshot,policy_refs,author_id,publisher_id,reason) VALUES(:version,:pkg,(SELECT COALESCE(max(version_no),0)+1 FROM cinenest_pricing.package_version WHERE package_id=:pkg),:source,:hash,CAST(:snapshot AS jsonb),CAST(:policies AS jsonb),:author,:publisher,:reason)")
   .params(Map.of("version",d.get("versionId"),"pkg",d.get("packageId"),"source",d.get("sourceRevision"),"hash",d.get("contentHash"),"snapshot",JSONObjectUtils.toJSONString(PublicationService.object(d,"snapshot")),"policies",JSONObjectUtils.toJSONString(PublicationService.object(d,"policyRefs")),"author",d.get("authorId"),"publisher",d.get("publisherId"),"reason",d.get("reason"))).update();
  for(UUID room:PublicationService.rooms(PublicationService.object(d,"snapshot")))jdbc.sql("INSERT INTO cinenest_pricing.package_version_room VALUES(:v,:r)").param("v",d.get("versionId")).param("r",room).update();
  for(var o:DraftRules.objects(PublicationService.object(d,"snapshot"),"options"))jdbc.sql("INSERT INTO cinenest_pricing.package_version_option VALUES(:v,:s,:r,CAST(:o AS jsonb))").param("v",d.get("versionId")).param("s",UUID.fromString(o.get("serviceId").toString())).param("r",UUID.fromString(o.get("rateVersionId").toString())).param("o",JSONObjectUtils.toJSONString(o)).update();
  var args=new HashMap<String,Object>();for(String k:List.of("publicationId","versionId","packageId","timezone","state","idempotencyKey","requestHash"))args.put(k,d.get(k));
  args.put("rooms",roomsSql(PublicationService.rooms(PublicationService.object(d,"snapshot"))));for(String k:List.of("salesFrom","salesTo","sessionFrom","sessionTo"))args.put(k,OffsetDateTime.parse(d.get(k).toString()));args.put("localDates",JSONObjectUtils.toJSONString(PublicationService.object(d,"localDates")));
  jdbc.sql("INSERT INTO cinenest_pricing.package_publication(publication_id,version_id,package_id,room_ids,sales_from,sales_to,session_from,session_to,timezone,local_dates,state,idempotency_key,request_hash) VALUES(:publicationId,:versionId,:packageId,CAST(:rooms AS uuid[]),:salesFrom,:salesTo,:sessionFrom,:sessionTo,:timezone,CAST(:localDates AS jsonb),:state,:idempotencyKey,:requestHash)").params(args).update();
  jdbc.sql("INSERT INTO cinenest_pricing.package_publication_work VALUES(:id,:due,0,'PENDING',NULL,clock_timestamp())").param("id",d.get("publicationId")).param("due",OffsetDateTime.parse(d.get("salesFrom").toString())).update();
 }
 private static String roomsSql(Set<UUID> rooms){return "{"+String.join(",",rooms.stream().sorted().map(UUID::toString).toList())+"}";}
 public void state(UUID id,long expected,String state,String reason){if(jdbc.sql("UPDATE cinenest_pricing.package_publication SET state=:s,failure_reason=:f,revision=revision+1 WHERE publication_id=:id AND revision=:r").param("s",state).param("f",reason,java.sql.Types.VARCHAR).param("id",id).param("r",expected).update()!=1)throw new DraftFailure(409,"Publication changed; reload and compare");}
 public Optional<Map<String,Object>> commandReceipt(UUID pkg,UUID key){return jdbc.sql("SELECT actor_id,command_hash,publication_id FROM cinenest_pricing.configuration_audit WHERE package_id=:p AND command_key=:k").param("p",pkg).param("k",key).query((rs,n)->Map.<String,Object>of("actor",rs.getObject(1,UUID.class),"hash",rs.getString(2),"publication",rs.getObject(3,UUID.class))).optional();}
 public void command(UUID id,UUID key,String hash,UUID actor){jdbc.sql("INSERT INTO cinenest_pricing.configuration_audit(event_id,actor_id,venue_id,package_id,action,outcome,reason,correlation_id,command_key,command_hash,publication_id) SELECT :event,:a,k.venue_id,p.package_id,'COMMAND_KEY','ALLOW','Idempotency receipt',:correlation,:key,:hash,p.publication_id FROM cinenest_pricing.package_publication p JOIN cinenest_pricing.package k USING(package_id) WHERE p.publication_id=:id").params(Map.of("event",UUID.randomUUID(),"a",actor,"correlation",UUID.randomUUID(),"key",key,"hash",hash,"id",id)).update();jdbc.sql("UPDATE cinenest_pricing.package_publication SET last_command_key=:k,last_command_hash=:h,last_command_actor=:a WHERE publication_id=:id").params(Map.of("id",id,"k",key,"h",hash,"a",actor)).update();}
 public void work(UUID id,String state,String error){jdbc.sql("UPDATE cinenest_pricing.package_publication_work SET state=:s,attempts=attempts+1,last_error=:e,updated_at=clock_timestamp(),next_attempt=clock_timestamp() WHERE publication_id=:id").param("s",state).param("e",error,java.sql.Types.VARCHAR).param("id",id).update();}
 public Optional<Map<String,Object>> job(UUID id){return query("WHERE p.publication_id=:id",Map.of("id",id)).stream().findFirst();}
 public List<UUID> due(){return jdbc.sql("SELECT publication_id FROM cinenest_pricing.package_publication_work WHERE state='PENDING' AND next_attempt<=clock_timestamp() ORDER BY next_attempt LIMIT 100").query(UUID.class).list();}
}
