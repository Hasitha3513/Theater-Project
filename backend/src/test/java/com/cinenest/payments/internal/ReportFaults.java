package com.cinenest.payments.internal;
import com.cinenest.payments.BankBalanceReport;import java.util.*;import java.time.*;
import org.springframework.context.ApplicationContext;import org.springframework.test.util.ReflectionTestUtils;
/** Isolated test seam; no production pause/configuration or network capability. */
public final class ReportFaults {
 private ReportFaults(){}
 public static Object afterSummary(ApplicationContext context,Runnable action){Object bean=context.getBean(BankBalanceReport.class);var old=(BankReportRepository)ReflectionTestUtils.getField(bean,"repository");var once=new java.util.concurrent.atomic.AtomicBoolean();BankReportRepository wrapper=new BankReportRepository(){
  public Instant snapshotTime(){return old.snapshotTime();}
  public Map<String,Object> summary(BankBalanceReport.Query q){var result=old.summary(q);if(once.compareAndSet(false,true))action.run();return result;}
  public List<Map<String,Object>> rows(BankBalanceReport.Query q){return old.rows(q);}
  public List<BankBalanceReport.ExceptionFact> exceptions(UUID id){return old.exceptions(id);}
  public void audit(UUID a,UUID v,String c,Instant t){old.audit(a,v,c,t);}
  public void denied(UUID a,UUID v){old.denied(a,v);}
 };ReflectionTestUtils.setField(bean,"repository",wrapper);return old;}
 public static void restore(ApplicationContext context,Object old){ReflectionTestUtils.setField(context.getBean(BankBalanceReport.class),"repository",old);}
}
