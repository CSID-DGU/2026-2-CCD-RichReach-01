package com.richreach.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimesTest {

    @Test
    @DisplayName("현재 시각을 초 단위로 자르고, 밀리초와 나노초는 버린다")
    void truncatesToSeconds() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-05T03:04:05.987654321Z"), ZoneId.of("Asia/Seoul"));

        assertThat(Times.nowSeconds(clock)).isEqualTo(LocalDateTime.of(2026, 11, 5, 12, 4, 5));
    }

    @Test
    @DisplayName("주입한 Clock의 시간대를 따른다")
    void followsClockZone() {
        Instant instant = Instant.parse("2026-11-05T00:00:00Z");

        assertThat(Times.nowSeconds(Clock.fixed(instant, ZoneId.of("UTC"))).getHour()).isEqualTo(0);
        assertThat(Times.nowSeconds(Clock.fixed(instant, ZoneId.of("Asia/Seoul"))).getHour()).isEqualTo(9);
    }

}
