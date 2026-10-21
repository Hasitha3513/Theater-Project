package com.cinenest.booking.internal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class InventoryCalendarTest {
 @Test void serializedCeilingCountsActualUtf8BytesAndNeverTruncates(){assertThat(InventoryCalendar.encode(Map.of("x","a".repeat(512*1024-8)))).hasSize(512*1024);assertThatThrownBy(()->InventoryCalendar.encode(Map.of("x","a".repeat(512*1024-7)))).isInstanceOfSatisfying(InventoryFailure.class,e->assertThat(e.status).isEqualTo(503));assertThatThrownBy(()->InventoryCalendar.encode(Map.of("x","ආ".repeat(200000)))).isInstanceOf(InventoryFailure.class);}
}
