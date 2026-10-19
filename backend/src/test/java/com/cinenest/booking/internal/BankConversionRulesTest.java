package com.cinenest.booking.internal;
import com.cinenest.booking.QuotationHolds;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BankConversionRulesTest {
 final Instant now=Instant.parse("2026-10-19T04:30:00Z");final UUID id=UUID.randomUUID();
 QuotationHolds.Facts hold(String state,long revision,Instant expires){return new QuotationHolds.Facts(id,id,id,id,now.plusSeconds(3600),now.plusSeconds(7200),expires,state,revision,2,"Asia/Colombo",0,now.plusSeconds(7200),"SYNTHETIC");}
 String reason(QuotationHolds.Facts h,long revision,Instant expires,boolean converted){return BankRequestHoldAdapter.refusal(h,revision,expires,now,converted);}
 @Test void currentHoldCanConvert(){assertThat(reason(hold("ACTIVE",1,now.plusSeconds(600)),1,now.plusSeconds(2400),false)).isEqualTo("ELIGIBLE");}
 @Test void unavailableAndConvertedFailClosed(){assertThat(reason(null,1,now.plusSeconds(2400),false)).isEqualTo("HOLD_UNAVAILABLE");assertThat(reason(hold("ACTIVE",2,now.plusSeconds(2400)),1,now.plusSeconds(2400),true)).isEqualTo("HOLD_ALREADY_CONVERTED");}
 @Test void expiryAndStateAndRevisionAreCurrent(){assertThat(reason(hold("ACTIVE",1,now),1,now.plusSeconds(2400),false)).isEqualTo("HOLD_EXPIRED");for(String state:java.util.List.of("RELEASED","INVALIDATED"))assertThat(reason(hold(state,2,now.plusSeconds(600)),2,now.plusSeconds(2400),false)).isEqualTo("HOLD_INELIGIBLE");assertThat(reason(hold("ACTIVE",2,now.plusSeconds(600)),1,now.plusSeconds(2400),false)).isEqualTo("HOLD_CHANGED");}
 @Test void conversionDeadlineIsStrictlyBetweenNowAndSession(){var h=hold("ACTIVE",1,now.plusSeconds(600));for(Instant end:java.util.List.of(now,h.start()))assertThat(reason(h,1,end,false)).isEqualTo("CONVERSION_DEADLINE_INVALID");}
}
