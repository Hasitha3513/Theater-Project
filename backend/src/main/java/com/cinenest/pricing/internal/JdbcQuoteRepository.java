package com.cinenest.pricing.internal;

import com.nimbusds.jose.util.JSONObjectUtils;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcQuoteRepository implements QuoteRepository {
    private final JdbcClient jdbc;
    JdbcQuoteRepository(JdbcClient jdbc){this.jdbc=jdbc;}
    public Instant now(){return jdbc.sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single().toInstant();}
    private Optional<Quote> find(String where,Object value,boolean lock){return jdbc.sql("SELECT * FROM cinenest_pricing.quotation WHERE "+where+"=:value"+(lock?" FOR UPDATE":"")).param("value",value).query((rs,n)->new Quote(rs.getObject("quote_id",UUID.class),rs.getObject("owner_id",UUID.class),rs.getObject("hold_id",UUID.class),rs.getObject("venue_id",UUID.class),rs.getObject("room_id",UUID.class),rs.getObject("package_id",UUID.class),rs.getLong("current_revision"))).optional();}
    public Optional<Quote> quote(UUID id,boolean lock){return find("quote_id",id,lock);}
    public Optional<Quote> byHold(UUID hold){return find("hold_id",hold,false);}
    public Revision revision(UUID quote,long revision){return jdbc.sql("SELECT * FROM cinenest_pricing.quotation_revision WHERE quote_id=:q AND revision=:r").param("q",quote).param("r",revision).query((rs,n)->new Revision(rs.getLong("revision"),rs.getObject("version_id",UUID.class),rs.getObject("policy_id",UUID.class),rs.getLong("hold_revision"),rs.getTimestamp("expires_at").toInstant(),rs.getString("payment_method"),JdbcPublicationRepository.json(rs.getString("snapshot")),rs.getString("content_hash"))).single();}
    public Optional<Long> accepted(UUID quote){return jdbc.sql("SELECT revision FROM cinenest_pricing.quotation_acceptance WHERE quote_id=:q").param("q",quote).query(Long.class).optional();}
    public Optional<Command> command(UUID actor,UUID key){return jdbc.sql("SELECT * FROM cinenest_pricing.quotation_command WHERE actor_id=:a AND command_key=:k").param("a",actor).param("k",key).query((rs,n)->new Command(rs.getString("action"),rs.getString("command_hash"),rs.getObject("quote_id",UUID.class),rs.getLong("revision"))).optional();}
    public long attempts(UUID actor,int seconds){return jdbc.sql("SELECT count(*) FROM cinenest_pricing.quotation_audit WHERE actor_id=:a AND action='ATTEMPT' AND recorded_at>clock_timestamp()-make_interval(secs=>:s)").param("a",actor).param("s",seconds).query(Long.class).single();}
    public void audit(UUID actor,UUID quote,Long revision,String action){jdbc.sql("INSERT INTO cinenest_pricing.quotation_audit(event_id,actor_id,quote_id,revision,action,correlation_id) VALUES(:e,:a,:q,:r,:action,:c)").param("e",UUID.randomUUID()).param("a",actor,java.sql.Types.OTHER).param("q",quote,java.sql.Types.OTHER).param("r",revision,java.sql.Types.BIGINT).param("action",action).param("c",UUID.randomUUID()).update();}
    public void policy(UUID id,String profile,long tax,String reference){jdbc.sql("INSERT INTO cinenest_pricing.quotation_policy_revision(policy_id,profile,currency,synthetic,tax_minor,bank_fee_minor,gateway_fee_minor,approval_reference) VALUES(:id,:profile,'LKR',TRUE,:tax,0,0,:ref) ON CONFLICT DO NOTHING").params(Map.of("id",id,"profile",profile,"tax",tax,"ref",reference)).update();}
    public void create(Quote q){jdbc.sql("INSERT INTO cinenest_pricing.quotation(quote_id,owner_id,hold_id,venue_id,room_id,package_id,current_revision) VALUES(:id,:a,:h,:v,:room,:pkg,1)").params(Map.of("id",q.id(),"a",q.owner(),"h",q.hold(),"v",q.venue(),"room",q.room(),"pkg",q.pkg())).update();}
    public void append(Quote q,Revision r){jdbc.sql("INSERT INTO cinenest_pricing.quotation_revision(quote_id,revision,version_id,policy_id,hold_revision,expires_at,payment_method,snapshot,content_hash,total_minor) VALUES(:q,:r,:version,:policy,:holdRevision,:expires,:method,CAST(:snapshot AS jsonb),:hash,:total)").params(Map.of("q",q.id(),"r",r.revision(),"version",r.version(),"policy",r.policy(),"holdRevision",r.holdRevision(),"expires",r.expires().atOffset(ZoneOffset.UTC),"method",r.method(),"snapshot",JSONObjectUtils.toJSONString(r.snapshot()),"hash",r.hash(),"total",Long.parseLong(r.snapshot().get("totalMinor").toString()))).update();
        int position=0;for(var line:DraftRules.objects(r.snapshot(),"lines"))jdbc.sql("INSERT INTO cinenest_pricing.quotation_line VALUES(:q,:r,:p,:kind,:label,:quantity,:unit,:amount,CAST(:ref AS jsonb))").params(Map.of("q",q.id(),"r",r.revision(),"p",position++,"kind",line.get("kind"),"label",line.get("label"),"quantity",line.get("quantity"),"unit",Long.parseLong(line.get("unitMinor").toString()),"amount",Long.parseLong(line.get("amountMinor").toString()),"ref",JSONObjectUtils.toJSONString(PublicationService.object(line,"reference")))).update();
        jdbc.sql("UPDATE cinenest_pricing.quotation SET current_revision=:r WHERE quote_id=:q").param("q",q.id()).param("r",r.revision()).update();
    }
    public void accept(Quote q,UUID actor,String hash){jdbc.sql("INSERT INTO cinenest_pricing.quotation_acceptance(quote_id,revision,actor_id,content_hash,synthetic) VALUES(:q,:r,:a,:hash,TRUE)").params(Map.of("q",q.id(),"r",q.revision(),"a",actor,"hash",hash)).update();}
    public void command(UUID actor,UUID key,String action,String hash,UUID quote,long revision){jdbc.sql("INSERT INTO cinenest_pricing.quotation_command(actor_id,command_key,command_hash,quote_id,revision,action) VALUES(:a,:key,:hash,:q,:r,:action)").params(Map.of("a",actor,"key",key,"hash",hash,"q",quote,"r",revision,"action",action)).update();}
}
