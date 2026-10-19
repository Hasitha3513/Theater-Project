package com.cinenest.payments.internal;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;import java.math.BigDecimal;
class BankReportMathTest {
 @Test void exactArithmeticPreservesBeyondJavascriptPrecision(){var x=BankReportMath.amounts("9007199254740993","3","4","5",true);assertThat(x.c()).isEqualTo("9007199254740993");assertThat(x.f()).isEqualTo("9007199254740981");}
 @Test void invalidMissingNegativeFractionAndOverflowReject(){for(Object x:new Object[]{null,"-1","1.1","9223372036854775808"})assertThatThrownBy(()->BankReportMath.exact(x)).isInstanceOf(ReportInvariant.class);assertThatThrownBy(()->BankReportMath.amounts(1,1,1,0,true)).isInstanceOf(ReportInvariant.class);}
 @Test void unavailableIsDistinctFromZero(){var x=BankReportMath.amounts(100,20,10,5,false);assertThat(x.available()).isNull();assertThat(x.unavailable()).isEqualTo("65");}
}
