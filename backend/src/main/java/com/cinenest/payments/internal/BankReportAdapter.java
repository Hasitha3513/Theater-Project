package com.cinenest.payments.internal;
import com.cinenest.payments.BankBalanceReport;import com.cinenest.identity.ScopedAuthorization;import java.util.*;import java.time.*;
import org.springframework.stereotype.Component;import org.springframework.transaction.PlatformTransactionManager;import org.springframework.transaction.TransactionDefinition;import org.springframework.transaction.support.TransactionTemplate;
@Component
class BankReportAdapter implements BankBalanceReport {
 private final BankReportRepository repository;private final ScopedAuthorization identity;private final TransactionTemplate snapshot;
 BankReportAdapter(BankReportRepository repository,ScopedAuthorization identity,PlatformTransactionManager manager){this.repository=repository;this.identity=identity;snapshot=new TransactionTemplate(manager);snapshot.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);}
 public void denied(UUID actor,UUID venue){snapshot.executeWithoutResult(tx->repository.denied(actor,venue));}
 public Snapshot snapshot(UUID actor,Query query){return snapshot.execute(tx->{
  var at=repository.snapshotTime();var s=repository.summary(query);if(Boolean.TRUE.equals(s.get("invalid")))throw new ReportInvariant();
  var total=BankReportMath.exact(s.get("total"));var base=BankReportMath.amounts(s.get("c"),s.get("a"),s.get("t"),s.get("r"),true);
  var summary=new Amounts(base.c(),base.a(),base.t(),base.r(),base.u(),base.f(),""+BankReportMath.exact(s.get("available")),""+BankReportMath.exact(s.get("unavailable")));
  var rows=repository.rows(query).stream().map(r->{boolean disputed=Boolean.TRUE.equals(r.get("disputed"));var amounts=BankReportMath.amounts(r.get("c"),r.get("a"),r.get("t"),r.get("r"),!disputed);UUID credit=(UUID)r.get("credit_id");return new Row(credit,time(r.get("credited_at")),time(r.get("verified_at")),amounts,Boolean.TRUE.equals(r.get("reversed"))?"SOURCE_REVERSED":disputed?"DISPUTED":"ELIGIBLE",repository.exceptions(credit),identity.permits(actor,query.venue(),"bank.synthetic.credit.read",false));}).toList();
  repository.audit(actor,query.venue(),query.currency(),at);return new Snapshot(at,"SYNTHETIC-BANK-BALANCES-V1",total,summary,rows);
 });}
 private static Instant time(Object value){if(value instanceof java.sql.Timestamp t)return t.toInstant();if(value instanceof OffsetDateTime t)return t.toInstant();throw new ReportInvariant();}
}
