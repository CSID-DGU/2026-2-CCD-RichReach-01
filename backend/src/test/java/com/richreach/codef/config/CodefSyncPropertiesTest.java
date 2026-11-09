package com.richreach.codef.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CodefSyncPropertiesTest {

    private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);
    private static final Duration ONE_HOUR = Duration.ofHours(1);

    @Test
    @DisplayName("0보다 큰 제한 시간은 그대로 쓴다")
    void acceptsPositiveDurations() {
        CodefSyncProperties properties = new CodefSyncProperties(FIVE_MINUTES, ONE_HOUR);

        assertThat(properties.timeout()).isEqualTo(FIVE_MINUTES);
        assertThat(properties.retryBlock()).isEqualTo(ONE_HOUR);
    }

    @Test
    @DisplayName("0이나 음수인 제한 시간은 앱을 시작할 때 거부한다")
    void rejectsZeroAndNegativeTimeout() {
        assertThatThrownBy(() -> new CodefSyncProperties(Duration.ZERO, ONE_HOUR))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CodefSyncProperties(Duration.ofSeconds(-1), ONE_HOUR))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CodefSyncProperties(null, ONE_HOUR))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("0이나 음수인 재시도 제한 시간은 앱을 시작할 때 거부한다")
    void rejectsZeroAndNegativeRetryBlock() {
        assertThatThrownBy(() -> new CodefSyncProperties(FIVE_MINUTES, Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CodefSyncProperties(FIVE_MINUTES, Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CodefSyncProperties(FIVE_MINUTES, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

}
