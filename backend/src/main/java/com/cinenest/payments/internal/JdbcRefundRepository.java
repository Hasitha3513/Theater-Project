package com.cinenest.payments.internal;

import java.util.*;
import com.nimbusds.jose.util.JSONObjectUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRefundRepository implements RefundRepository {
 private final JdbcClient jdbc;
 JdbcRefundRepository(JdbcClient jdbc){this.jdbc=jdbc;}
 private List<Map<String,Object>> rows(String sql,Object... args){return jdbc.sql(sql).params(args).query().listOfRows();}
 private void update(String sql,Object... args){jdbc.sql(sql).params(args).update();}
 public Map<String,Object> refund(UUID id,boolean lock){var r=rows("SELECT * FROM cinenest_payments.refund_obligation WHERE refund_id=?"+(lock?" FOR UPDATE":""),id);if(r.isEmpty())throw new BankFailure(404,"Refund not found");return r.getFirst();}
 public List<UUID> queue(UUID venue){return jdbc.sql("SELECT refund_id FROM cinenest_payments.refund_obligation WHERE venue_id=? ORDER BY created_at DESC,refund_id LIMIT 100").param(venue).query(UUID.class).list();}
 public Optional<Map<String,Object>> replay(UUID a,UUID key,String hash){var r=rows("SELECT payload_hash,result::text FROM cinenest_payments.refund_command WHERE actor_id=? AND command_key=?",a,key);if(r.isEmpty())return Optional.empty();if(!hash.equals(r.getFirst().get("payload_hash")))throw new BankFailure(409,"Changed command payload");try{return Optional.of(JSONObjectUtils.parse(r.getFirst().get("result").toString()));}catch(Exception e){throw new IllegalStateException("Invalid persisted refund result",e);}}
 public void command(UUID a,UUID key,String hash,Map<String,Object> result){update("INSERT INTO cinenest_payments.refund_command(actor_id,command_key,payload_hash,result) VALUES(?,?,?,CAST(? AS jsonb))",a,key,hash,JSONObjectUtils.toJSONString(result));}
 public void create(UUID id,UUID c,UUID r,UUID owner,UUID venue,long amount,String policy,Map<String,Object> snapshot){update("INSERT INTO cinenest_payments.refund_obligation(refund_id,credit_id,request_id,owner_id,venue_id,amount_minor,currency,policy_hash,policy_snapshot,state) VALUES(?,?,?,?,?,?,'LKR',?,CAST(? AS jsonb),'REQUESTED')",id,c,r,owner,venue,amount,policy,JSONObjectUtils.toJSONString(snapshot));}
 public void transition(UUID id,String state,UUID approver){update("UPDATE cinenest_payments.refund_obligation SET state=?,revision=revision+1,approver_id=COALESCE(approver_id,?) WHERE refund_id=?",state,approver,id);}
 public Optional<Map<String,Object>> destination(UUID id){return rows("SELECT * FROM cinenest_payments.refund_destination_version WHERE refund_id=?",id).stream().findFirst();}
 public void destination(UUID id,String ref,UUID proposer,UUID verifier,String hash){update("INSERT INTO cinenest_payments.refund_destination_version(refund_id,version,destination_reference,proposer_id,verifier_id,manifest_hash) VALUES(?,1,?,?,?,?)",id,ref,proposer,verifier,hash);}
 public void decision(UUID id,UUID actor,String action,String reason){update("INSERT INTO cinenest_payments.refund_decision(decision_id,refund_id,actor_id,action,reason) VALUES(?,?,?,?,?)",UUID.randomUUID(),id,actor,action,reason);}
 public List<Map<String,Object>> decisions(UUID id){return rows("SELECT action,recorded_at FROM cinenest_payments.refund_decision WHERE refund_id=? ORDER BY recorded_at,decision_id",id);}
 public List<Map<String,Object>> attempts(UUID id){return rows("SELECT * FROM cinenest_payments.refund_attempt WHERE refund_id=? ORDER BY sequence",id);}
 public void attempt(UUID id,UUID refund,int sequence,UUID actor,UUID key){update("INSERT INTO cinenest_payments.refund_attempt(attempt_id,refund_id,sequence,destination_version,executor_id,operation_key) VALUES(?,?,?,1,?,?)",id,refund,sequence,actor,key);}
 public List<Map<String,Object>> evidence(UUID id){return rows("SELECT fixture_key,phase,recorded_at FROM cinenest_payments.refund_outgoing_evidence WHERE attempt_id=? ORDER BY recorded_at",id);}
 public void evidence(UUID attempt,String fixture,UUID recorder,UUID verifier,String hash){update("INSERT INTO cinenest_payments.refund_outgoing_evidence(evidence_id,attempt_id,fixture_key,recorder_id,phase,verifier_id,manifest_hash) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID(),attempt,fixture,recorder,verifier==null?"RECORDED":"VERIFIED",verifier,hash);}
 public void work(UUID id,String state){update("INSERT INTO cinenest_payments.refund_work(refund_id,state) VALUES(?,?) ON CONFLICT(refund_id) DO UPDATE SET state=EXCLUDED.state,revision=refund_work.revision+1,updated_at=clock_timestamp()",id,state);}
 public Optional<String> work(UUID id){return jdbc.sql("SELECT state FROM cinenest_payments.refund_work WHERE refund_id=?").param(id).query(String.class).optional();}
 public List<Map<String,Object>> audit(UUID id){return rows("SELECT action,outcome,recorded_at FROM cinenest_payments.bank_audit WHERE financial_resource_id=? ORDER BY recorded_at,event_id",id);}
}
