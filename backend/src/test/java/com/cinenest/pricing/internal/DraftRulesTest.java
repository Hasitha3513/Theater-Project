package com.cinenest.pricing.internal;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class DraftRulesTest {
 @Test void exactMinorUnitParsingNeverRounds(){assertThat(DraftRules.minor("1234",true)).isEqualTo(1234);assertThat(DraftRules.minor("1",true)).isEqualTo(1);for(String value:List.of("1.234","-1","NaN","1e2","9223372036854775808"))assertThatThrownBy(()->DraftRules.minor(value,true)).isInstanceOf(DraftFailure.class);}
 @Test void overflowAndPositiveBaseAreChecked(){assertThatThrownBy(()->DraftRules.add(Long.MAX_VALUE,1)).isInstanceOf(DraftFailure.class);assertThatThrownBy(()->DraftRules.multiply(Long.MAX_VALUE,2)).isInstanceOf(DraftFailure.class);assertThatThrownBy(()->DraftRules.minor("0",true)).isInstanceOf(DraftFailure.class);assertThat(DraftRules.minor("0",false)).isZero();}
 @Test void invalidIntegersAndDuplicateRoomsReject(){assertThatThrownBy(()->DraftRules.integer(Map.of("duration",1.5),"duration",1,1000)).isInstanceOf(DraftFailure.class);String id=UUID.randomUUID().toString();assertThatThrownBy(()->DraftRules.roomIds(Map.of("roomIds",List.of(id,id)))).isInstanceOf(DraftFailure.class);}
}
