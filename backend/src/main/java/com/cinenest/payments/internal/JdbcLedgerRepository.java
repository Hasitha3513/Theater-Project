package com.cinenest.payments.internal;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
@Repository
class JdbcLedgerRepository implements LedgerRepository {
 private final JdbcClient jdbc;JdbcLedgerRepository(JdbcClient jdbc){this.jdbc=jdbc;}
 private List<Map<String,Object>> query(String sql,Map<String,?> p){return jdbc.sql(sql).params(p).query().listOfRows();}
 private Map<String,Object> one(String sql,Map<String,?> p){var rows=query(sql,p);if(rows.isEmpty())throw new BankFailure(404,"Financial resource not found");return rows.getFirst();}
 public List<Map<String,Object>> overdueExceptions(Map<String,?> p){return query("SELECT exception_id,request_id FROM cinenest_payments.bank_financial_exception WHERE state='OPEN' AND venue_id=:v AND due_at<=clock_timestamp() ORDER BY exception_id FOR UPDATE SKIP LOCKED",p);}
 public int escalateException(Map<String,?> p){return jdbc.sql("UPDATE cinenest_payments.bank_financial_exception SET state='ESCALATED',revision=revision+1 WHERE exception_id=:id").params(p).update();}
 public long refundCommitted(Map<String,?> p){return ((Number)one("SELECT cinenest_payments.refund_committed(:c) AS amount",p).get("amount")).longValue();}
 public long allocatedCredit(Map<String,?> p){return ((Number)one("SELECT COALESCE(sum(amount_minor),0) AS amount FROM cinenest_payments.payment_allocation WHERE credit_id=:c",p).get("amount")).longValue();}
 public long allocatedRequest(Map<String,?> p){return ((Number)one("SELECT COALESCE(sum(amount_minor),0) AS amount FROM cinenest_payments.payment_allocation WHERE request_id=:r",p).get("amount")).longValue();}
 public List<Map<String,Object>> adjustments(Map<String,?> p){return query("SELECT adjustment_id FROM cinenest_payments.bank_credit_adjustment WHERE credit_id=:c",p);}
 public List<Map<String,Object>> correctionHistory(Map<String,?> p){return query("SELECT adjustment_id,CASE WHEN approver_id IS NULL THEN 'PENDING_INDEPENDENT_APPROVAL' ELSE 'SOURCE_REVERSED' END AS status FROM cinenest_payments.bank_credit_adjustment WHERE credit_id=:c",p);}
 public int createException(Map<String,?> p){return jdbc.sql("INSERT INTO cinenest_payments.bank_financial_exception(exception_id,venue_id,request_id,credit_id,kind,due_at,state) VALUES(:id,:v,:r,:c,:kind,clock_timestamp()+:seconds*INTERVAL '1 second','OPEN') ON CONFLICT DO NOTHING").params(p).update();}
 public Map<String,Object> adjustmentCredit(Map<String,?> p){return one("SELECT credit_id FROM cinenest_payments.bank_credit_adjustment WHERE adjustment_id=:id",p);}
 public List<Map<String,Object>> matchedOwner(Map<String,?> p){return query("SELECT r.owner_id FROM cinenest_payments.bank_credit_match m JOIN cinenest_payments.bank_payment_request r USING(request_id) WHERE credit_id=:c",p);}
 public int sourceRevision(Map<String,?> p){return jdbc.sql("INSERT INTO cinenest_payments.bank_credit_source_revision(source_id,fixture_key,manifest_hash,synthetic) VALUES(:id,:key,:hash,true) ON CONFLICT(source_id) DO NOTHING").params(p).update();}
 public int recordCredit(Map<String,?> p){return jdbc.sql("INSERT INTO cinenest_payments.incoming_payment_credit VALUES(:id,:source,:v,:account,:movement,:kind,:amount,'LKR',:at,clock_timestamp())").params(p).update();}
 public int verifyCredit(Map<String,?> p){return jdbc.sql("INSERT INTO cinenest_payments.bank_credit_verification(verification_id,credit_id,verifier_id,status,reason) VALUES(:id,:c,:a,'SIMULATED_VERIFIED',:reason)").params(p).update();}
 public int matchCredit(Map<String,?> p){return jdbc.sql("INSERT INTO cinenest_payments.bank_credit_match VALUES(:c,:r,:a,:reason,clock_timestamp())").params(p).update();}
 public int resolveUnmatched(Map<String,?> p){return jdbc.sql("UPDATE cinenest_payments.bank_financial_exception SET state='RESOLVED',revision=revision+1 WHERE credit_id=:c AND kind='UNMATCHED'").params(p).update();}
 public Map<String,Object> acceptedRequestTerms(Map<String,?> p){return one("SELECT quote_revision,quote_hash FROM cinenest_payments.bank_payment_request WHERE request_id=:r",p);}
 public int allocate(Map<String,?> p){return jdbc.sql("INSERT INTO cinenest_payments.payment_allocation VALUES(:id,:c,:r,:amount,'LKR',:a,:key,clock_timestamp())").params(p).update();}
 public int resolvePartial(Map<String,?> p){return jdbc.sql("UPDATE cinenest_payments.bank_financial_exception SET state='RESOLVED',revision=revision+1 WHERE request_id=:r AND kind='PARTIAL'").params(p).update();}
 public int proposeCorrection(Map<String,?> p){return jdbc.sql("INSERT INTO cinenest_payments.bank_credit_adjustment(adjustment_id,credit_id,source_key,proposer_id,reason) VALUES(:id,:c,:key,:a,:reason)").params(p).update();}
 public Map<String,Object> lockCorrection(Map<String,?> p){return one("SELECT a.* FROM cinenest_payments.bank_credit_adjustment a JOIN cinenest_payments.incoming_payment_credit c USING(credit_id) WHERE adjustment_id=:id AND venue_id=:v FOR UPDATE OF a,c",p);}
 public List<Map<String,Object>> matchedRequest(Map<String,?> p){return query("SELECT r.owner_id,r.request_id,r.revision,r.amount_minor FROM cinenest_payments.bank_credit_match m JOIN cinenest_payments.bank_payment_request r USING(request_id) WHERE credit_id=:c",p);}
 public int approveCorrection(Map<String,?> p){return jdbc.sql("UPDATE cinenest_payments.bank_credit_adjustment SET approver_id=:a,approved_at=clock_timestamp() WHERE adjustment_id=:id AND approver_id IS NULL").params(p).update();}
 public Map<String,Object> lockException(Map<String,?> p){return one("SELECT * FROM cinenest_payments.bank_financial_exception WHERE exception_id=:id AND venue_id=:v FOR UPDATE",p);}
 public int assignException(Map<String,?> p){return jdbc.sql("UPDATE cinenest_payments.bank_financial_exception SET assigned_to=:a,state=:s,revision=revision+1 WHERE exception_id=:id").params(p).update();}
 public int audit(Map<String,?> p){return jdbc.sql("INSERT INTO cinenest_payments.bank_audit(event_id,actor_id,action,outcome,correlation_id,financial_resource_id,venue_id,reason) VALUES(:id,:a,:action,'SYNTHETIC_ONLY',:key,:resource,:v,:reason)").params(p).update();}
 public long fundingShortfall(Map<String,?> p){return ((Number)one("SELECT COALESCE(sum(p.amount_minor),0) AS amount FROM cinenest_payments.payment_allocation p JOIN cinenest_payments.bank_credit_adjustment a USING(credit_id) WHERE p.request_id=:r AND a.approver_id IS NOT NULL",p).get("amount")).longValue();}
 public List<Map<String,Object>> requestDisputes(Map<String,?> p){return query("SELECT a.adjustment_id FROM cinenest_payments.bank_credit_adjustment a JOIN cinenest_payments.bank_credit_match m USING(credit_id) WHERE m.request_id=:r",p);}
 public Map<String,Object> exceptionDetail(Map<String,?> p){return one("SELECT * FROM cinenest_payments.bank_financial_exception WHERE exception_id=:id AND venue_id=:v",p);}
 public Map<String,Object> credit(Map<String,?> p,boolean lock){return one("SELECT c.*,v.verifier_id,v.verified_at,s.fixture_key,s.manifest_hash FROM cinenest_payments.incoming_payment_credit c JOIN cinenest_payments.bank_credit_verification v USING(credit_id) JOIN cinenest_payments.bank_credit_source_revision s USING(source_id) WHERE credit_id=:c AND venue_id=:v"+(lock?" FOR UPDATE OF c":""),p);}
 public List<Map<String,Object>> creditIds(Map<String,?> p){return query("SELECT credit_id FROM cinenest_payments.incoming_payment_credit WHERE venue_id=:v ORDER BY credit_id LIMIT 100",p);}
 public List<Map<String,Object>> exceptionIds(Map<String,?> p){return query("SELECT exception_id FROM cinenest_payments.bank_financial_exception WHERE venue_id=:v ORDER BY exception_id LIMIT 100",p);}
}
