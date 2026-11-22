package com.richreach.codef.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CodefAutoSyncPropertiesTest {

    private static CodefAutoSyncProperties with(String cron, Duration minInterval, Duration gap, int maxUsers) {
        return new CodefAutoSyncProperties(true, cron, minInterval, gap, maxUsers);
    }

    @Test
    @DisplayName("잘못된 값은 앱을 시작할 때 거부한다")
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> with(" ", Duration.ofHours(1), Duration.ZERO, 1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> with("0 0 6 * * *", Duration.ZERO, Duration.ZERO, 1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> with("0 0 6 * * *", Duration.ofHours(1), Duration.ofSeconds(-1), 1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> with("0 0 6 * * *", Duration.ofHours(1), Duration.ZERO, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

}
