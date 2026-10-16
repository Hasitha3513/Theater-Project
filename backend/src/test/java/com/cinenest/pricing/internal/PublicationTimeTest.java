package com.cinenest.pricing.internal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class PublicationTimeTest {
 @Test void venueLocalConversionIsExplicit(){assertThat(PublicationService.local("2026-10-16T09:00","Asia/Colombo").toString()).isEqualTo("2026-10-16T03:30:00Z");}
 @Test void daylightSavingGapAndAmbiguityReject(){for(String t:new String[]{"2026-03-08T02:30","2026-11-01T01:30"})assertThatThrownBy(()->PublicationService.local(t,"America/New_York")).isInstanceOf(DraftFailure.class);}
 @Test void hashBindsNestedValuesButNotMapIterationOrder(){assertThat(PublicationService.hash(java.util.Map.of("a",1,"b",2))).isEqualTo(PublicationService.hash(java.util.Map.of("b",2,"a",1)));assertThat(PublicationService.hash(java.util.Map.of("a",1))).isNotEqualTo(PublicationService.hash(java.util.Map.of("a",2)));}
}
