package com.cinenest.payments.internal;
import java.math.BigDecimal;import com.cinenest.payments.BankBalanceReport.Amounts;
/** Exact arithmetic. Missing/negative/overflow is an error, never a zero fallback. */
final class BankReportMath {
 private BankReportMath(){}
 static long exact(Object value){try{if(value==null)throw new ArithmeticException();long x=new BigDecimal(value.toString()).longValueExact();if(x<0)throw new ArithmeticException();return x;}catch(ArithmeticException|NumberFormatException e){throw new ReportInvariant();}}
 static Amounts amounts(Object c,Object a,Object t,Object r,boolean eligible){try{long cc=exact(c),aa=exact(a),tt=exact(t),rr=exact(r),u=Math.subtractExact(Math.subtractExact(cc,aa),tt),f=Math.subtractExact(u,rr);if(f<0)throw new ReportInvariant();return new Amounts(""+cc,""+aa,""+tt,""+rr,""+u,""+f,eligible?""+f:null,eligible?"0":""+f);}catch(ArithmeticException e){throw new ReportInvariant();}}
}
