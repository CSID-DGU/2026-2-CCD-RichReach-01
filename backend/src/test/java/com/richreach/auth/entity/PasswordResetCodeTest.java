package com.richreach.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordResetCodeTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 12, 0, 0);
    private static final Duration TTL = Duration.ofMinutes(3);
    private static final Duration RESEND = Duration.ofSeconds(60);

    private PasswordResetCode code() {
        return new PasswordResetCode(1L, "hash", NOW, TTL);
    }

    @Test
    @DisplayName("유효 시간이 지나면 만료되고, 만료 시각 정각에도 만료로 본다")
    void expiresAfterTtl() {
        PasswordResetCode code = code();

        assertThat(code.isExpired(NOW.plusMinutes(2).plusSeconds(59))).isFalse();
        assertThat(code.isExpired(NOW.plusMinutes(3))).isTrue();
        assertThat(code.isExpired(NOW.plusMinutes(4))).isTrue();
    }

    @Test
    @DisplayName("마지막 발송 뒤 재전송 대기 시간이 지나야 다시 보낼 수 있다")
    void resendIsLimited() {
        PasswordResetCode code = code();

        assertThat(code.canResend(NOW.plusSeconds(59), RESEND)).isFalse();
        assertThat(code.canResend(NOW.plusSeconds(60), RESEND)).isTrue();
    }

    @Test
    @DisplayName("틀린 횟수가 최대치에 닿으면 소진 상태가 되고, 다시 발급하면 풀린다")
    void exhaustedAtMaxAttempts() {
        PasswordResetCode code = code();
        for (int i = 0; i < 4; i++) {
            code.recordFailure();
        }
        assertThat(code.isExhausted(5)).isFalse();

        code.recordFailure();
        assertThat(code.isExhausted(5)).isTrue();

        code.renew("new-hash", NOW.plusMinutes(2), TTL);
        assertThat(code.isExhausted(5)).isFalse();
    }

    @Test
    @DisplayName("틀릴 때마다 횟수가 늘고, 다시 발급하면 횟수와 시각이 새로 시작된다")
    void renewResetsEverything() {
        PasswordResetCode code = code();
        assertThat(code.recordFailure()).isEqualTo(1);
        assertThat(code.recordFailure()).isEqualTo(2);

        LocalDateTime later = NOW.plusMinutes(5);
        code.renew("new-hash", later, TTL);

        assertThat(code.getFailedAttempts()).isZero();
        assertThat(code.getCodeHash()).isEqualTo("new-hash");
        assertThat(code.getSentAt()).isEqualTo(later);
        assertThat(code.isExpired(later.plusMinutes(2))).isFalse();
    }

}
