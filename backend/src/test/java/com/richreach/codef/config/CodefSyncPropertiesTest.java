package com.richreach.codef.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CodefSyncPropertiesTest {

    @Test
    @DisplayName("0보다 큰 제한 시간은 그대로 쓴다")
    void acceptsPositiveTimeout() {
        assertThat(new CodefSyncProperties(Duration.ofMinutes(5)).timeout()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("0이나 음수인 제한 시간은 앱을 시작할 때 거부한다")
    void rejectsZeroAndNegativeTimeout() {
        assertThatThrownBy(() -> new CodefSyncProperties(Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CodefSyncProperties(Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CodefSyncProperties(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

}
