package com.richreach.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserLoginLockTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 12, 0);
    private static final Duration LOCK = Duration.ofMinutes(10);

    private User newUser() {
        return new User("a@example.com", "hash", "테스터", "01012345678", "REAL", NOW);
    }

    @Test
    @DisplayName("처음 가입한 사용자는 실패 횟수 0, 잠기지 않은 상태다")
    void startsUnlocked() {
        User user = newUser();

        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.isLocked(NOW)).isFalse();
    }

    @Test
    @DisplayName("최대 횟수보다 적게 실패하면 잠기지 않는다")
    void notLockedBeforeLimit() {
        User user = newUser();

        for (int i = 0; i < 4; i++) {
            user.recordLoginFailure(NOW, 5, LOCK);
        }

        assertThat(user.getFailedLoginCount()).isEqualTo(4);
        assertThat(user.isLocked(NOW)).isFalse();
    }

    @Test
    @DisplayName("최대 횟수에 닿는 실패에서 잠그고, 잠금 시간이 지나면 풀린다")
    void locksAtLimitAndUnlocksAfterDuration() {
        User user = newUser();

        for (int i = 0; i < 5; i++) {
            user.recordLoginFailure(NOW, 5, LOCK);
        }

        assertThat(user.isLocked(NOW)).isTrue();
        assertThat(user.isLocked(NOW.plusMinutes(9))).isTrue();
        assertThat(user.isLocked(NOW.plusMinutes(10))).isFalse();
    }

    @Test
    @DisplayName("잠금이 풀린 뒤의 첫 실패는 1회부터 다시 센다")
    void countRestartsAfterUnlock() {
        User user = newUser();
        for (int i = 0; i < 5; i++) {
            user.recordLoginFailure(NOW, 5, LOCK);
        }

        user.recordLoginFailure(NOW.plusMinutes(11), 5, LOCK);

        assertThat(user.getFailedLoginCount()).isEqualTo(1);
        assertThat(user.isLocked(NOW.plusMinutes(11))).isFalse();
    }

    @Test
    @DisplayName("로그인에 성공하면 횟수와 잠금이 모두 지워진다")
    void resetClearsEverything() {
        User user = newUser();
        for (int i = 0; i < 5; i++) {
            user.recordLoginFailure(NOW, 5, LOCK);
        }

        user.resetLoginFailures();

        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.isLocked(NOW)).isFalse();
    }

}
