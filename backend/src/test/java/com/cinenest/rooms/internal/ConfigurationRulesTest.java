package com.cinenest.rooms.internal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ConfigurationRulesTest {
 @Test void midnightIsNextLocalDayAndIntervalsAreDistinct(){var local=ConfigurationRules.local(LocalDate.of(2026,10,16),1440,"Asia/Colombo");assertThat(local.toLocalDate()).isEqualTo(LocalDate.of(2026,10,17));var start=Instant.parse("2026-10-16T12:00:00Z");assertThat(ConfigurationRules.blockedUntil(start,90,7)).isEqualTo(start.plusSeconds(97*60));}
 @Test void dstGapsAndOverlapsAreRejected(){assertThatThrownBy(()->ConfigurationRules.local(LocalDate.of(2026,3,8),150,"America/New_York")).isInstanceOf(RoomFailure.class);assertThatThrownBy(()->ConfigurationRules.local(LocalDate.of(2026,11,1),90,"America/New_York")).isInstanceOf(RoomFailure.class);}
 @Test void normalizationAndIntegerTypesAreStrict(){assertThat(ConfigurationRules.code(Map.of("code"," a-b "),"code")).isEqualTo("A-B");assertThatThrownBy(()->ConfigurationRules.integer(Map.of("capacity",1.5),"capacity",1,100)).isInstanceOf(RoomFailure.class);assertThatThrownBy(()->ConfigurationRules.version(Map.of("expectedVersion",-1))).isInstanceOf(RoomFailure.class);}
}
