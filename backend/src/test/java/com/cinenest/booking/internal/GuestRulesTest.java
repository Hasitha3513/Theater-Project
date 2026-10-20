package com.cinenest.booking.internal;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;
class GuestRulesTest {
 @Test void canonicalNumericLabels(){assertThat(GuestRules.label(" synthetic-12 ")).isEqualTo("SYNTHETIC-12");assertThat(GuestRules.label("ＳＹＮＴＨＥＴＩＣ-１")).isEqualTo("SYNTHETIC-1");}
 @Test void personalAndInvalidValuesRejected(){for(Object v:new Object[]{"", "Jane Doe", "a@example.invalid", "SYNTHETIC-0", "SYNTHETIC-01", "SYNTHETIC-1000000", 18, "SYNTHETIC-1\nSYNTHETIC-2"})assertThatThrownBy(()->GuestRules.label(v)).isInstanceOf(InventoryFailure.class);assertThatThrownBy(()->GuestRules.label(null)).isInstanceOf(InventoryFailure.class);}
}
