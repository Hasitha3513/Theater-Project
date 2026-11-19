package com.cinenest.booking.internal;
import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.api.Test;
class SyntheticSessionServiceTest {
    @Test void purchasedStartWindowIsExclusiveAtEnd(){
        var start=Instant.parse("2026-11-19T00:00:00Z");
        var end=start.plusSeconds(60);
        assertThat(SyntheticSessionService.within(start.minusNanos(1),start,end)).isFalse();
        assertThat(SyntheticSessionService.within(start,start,end)).isTrue();
        assertThat(SyntheticSessionService.within(end.minusNanos(1),start,end)).isTrue();
        assertThat(SyntheticSessionService.within(end,start,end)).isFalse();
    }
}
