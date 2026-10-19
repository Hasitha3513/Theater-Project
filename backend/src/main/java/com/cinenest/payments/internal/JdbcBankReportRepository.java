package com.cinenest.payments.internal;
import com.cinenest.payments.BankBalanceReport;import java.util.*;import java.time.*;import java.sql.Types;
import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.stereotype.Repository;
@Repository
class JdbcBankReportRepository implements BankReportRepository {
 private final JdbcClient jdbc;JdbcBankReportRepository(JdbcClient jdbc){this.jdbc=jdbc;}
 // Separate correlated source aggregates prevent join fan-out. Returns are counted once per obligation.
 private static final String POPULATION="""
 WITH facts AS (
 SELECT c.credit_id,c.credited_at,v.verified_at,c.amount_minor::numeric c,
 COALESCE((SELECT sum(a.amount_minor) FROM cinenest_payments.payment_allocation a WHERE a.credit_id=c.credit_id),0) a,
 COALESCE((SELECT sum(r.amount_minor) FROM cinenest_payments.refund_obligation r WHERE r.credit_id=c.credit_id AND r.state='SIMULATED_CONFIRMED'),0) t,
 COALESCE((SELECT sum(r.amount_minor) FROM cinenest_payments.refund_obligation r WHERE r.credit_id=c.credit_id AND r.state IN ('APPROVED','ATTEMPT_PREPARED','AWAITING_EVIDENCE','FAILED_CONFIRMED','UNKNOWN_RECONCILIATION_REQUIRED')),0) r,
 EXISTS(SELECT 1 FROM cinenest_payments.bank_credit_adjustment d WHERE d.credit_id=c.credit_id) disputed,
 EXISTS(SELECT 1 FROM cinenest_payments.bank_credit_adjustment d WHERE d.credit_id=c.credit_id AND d.approver_id IS NOT NULL) reversed,
 (v.status IS DISTINCT FROM 'SIMULATED_VERIFIED' OR NOT s.synthetic OR EXISTS(
 SELECT 1 FROM cinenest_payments.refund_obligation r WHERE r.credit_id=c.credit_id AND r.state='SIMULATED_CONFIRMED' AND NOT EXISTS(
 SELECT 1 FROM cinenest_payments.refund_attempt a JOIN cinenest_payments.refund_outgoing_evidence e ON e.attempt_id=a.attempt_id
 WHERE a.refund_id=r.refund_id AND e.phase='VERIFIED' AND e.fixture_key='SYNTHETIC-CONFIRMED' AND e.verifier_id IS NOT NULL AND e.verifier_id<>e.recorder_id AND e.verifier_id<>a.executor_id AND e.verifier_id<>r.owner_id))) invalid
 FROM cinenest_payments.incoming_payment_credit c JOIN cinenest_payments.bank_credit_source_revision s USING(source_id)
 LEFT JOIN cinenest_payments.bank_credit_verification v USING(credit_id)
 WHERE c.venue_id=:venue AND c.currency=:currency AND c.source_kind='SYNTHETIC_BANK'
 AND (:fromTime::timestamptz IS NULL OR c.credited_at>=:fromTime)
 AND (:toTime::timestamptz IS NULL OR c.credited_at<:toTime)
 ), balances AS (SELECT *,c-a-t u,c-a-t-r f FROM facts)
 """;
 private JdbcClient.StatementSpec query(String sql,BankBalanceReport.Query q){return jdbc.sql(POPULATION+sql).param("venue",q.venue()).param("currency",q.currency()).param("fromTime",q.from()==null?null:OffsetDateTime.ofInstant(q.from(),ZoneOffset.UTC),Types.TIMESTAMP_WITH_TIMEZONE).param("toTime",q.to()==null?null:OffsetDateTime.ofInstant(q.to(),ZoneOffset.UTC),Types.TIMESTAMP_WITH_TIMEZONE);}
 public Instant snapshotTime(){return jdbc.sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single().toInstant();}
 public Map<String,Object> summary(BankBalanceReport.Query q){return query("SELECT count(*) total,COALESCE(sum(c),0) c,COALESCE(sum(a),0) a,COALESCE(sum(t),0) t,COALESCE(sum(r),0) r,COALESCE(sum(CASE WHEN NOT disputed THEN f ELSE 0 END),0) available,COALESCE(sum(CASE WHEN disputed THEN f ELSE 0 END),0) unavailable,COALESCE(bool_or(invalid OR f<0),false) invalid FROM balances",q).query().singleRow();}
 public List<Map<String,Object>> rows(BankBalanceReport.Query q){return query("SELECT * FROM balances ORDER BY credited_at,credit_id LIMIT :size OFFSET :offset",q).param("size",q.size()).param("offset",Math.multiplyExact(q.page(),q.size())).query().listOfRows();}
 public List<BankBalanceReport.ExceptionFact> exceptions(UUID credit){return jdbc.sql("SELECT kind,state FROM cinenest_payments.bank_financial_exception WHERE credit_id=:c ORDER BY recorded_at,exception_id").param("c",credit).query((r,n)->new BankBalanceReport.ExceptionFact(r.getString(1),r.getString(2))).list();}
 public void denied(UUID actor,UUID venue){jdbc.sql("INSERT INTO cinenest_payments.bank_audit(event_id,actor_id,action,outcome,correlation_id) VALUES(:id,:a,'SYNTHETIC_REPORT_DENIED','DENIED',:v)").param("id",UUID.randomUUID()).param("a",actor).param("v",venue).update();}
 public void audit(UUID actor,UUID venue,String currency,Instant snapshot){jdbc.sql("INSERT INTO cinenest_payments.bank_audit(event_id,actor_id,action,outcome,correlation_id) VALUES(:id,:a,'SYNTHETIC_REPORT_READ',:outcome,:v)").param("id",UUID.randomUUID()).param("a",actor).param("v",venue).param("outcome","GENERATED:"+currency+":"+snapshot).update();}
}
