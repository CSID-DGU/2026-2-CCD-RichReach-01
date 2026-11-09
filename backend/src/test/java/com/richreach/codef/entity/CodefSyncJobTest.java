package com.richreach.codef.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CodefSyncJobTest {

    private static final LocalDateTime STARTED = LocalDateTime.of(2026, 11, 4, 12, 0, 0);
    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    private static CodefSyncJob inProgress() {
        return CodefSyncJob.startCardList(null, "test-batch", STARTED);
    }

    @Test
    @DisplayName("제한 시간 1초 전까지는 진행 중이다")
    void notTimedOutJustBefore() {
        assertThat(inProgress().isTimedOut(TIMEOUT, STARTED.plus(TIMEOUT).minusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("시작한 지 제한 시간이 정확히 지난 순간부터 시간 초과다 (경계값)")
    void timedOutExactlyAtBoundary() {
        assertThat(inProgress().isTimedOut(TIMEOUT, STARTED.plus(TIMEOUT))).isTrue();
    }

    @Test
    @DisplayName("제한 시간이 지나면 시간 초과다")
    void timedOutAfterBoundary() {
        assertThat(inProgress().isTimedOut(TIMEOUT, STARTED.plus(TIMEOUT).plusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("실패했거나 시간 초과인 작업만 실패로 본다 (hasFailed)")
    void hasFailedCoversFailureAndTimeout() {
        LocalDateTime justStarted = STARTED.plusSeconds(1);

        CodefSyncJob running = inProgress();
        assertThat(running.hasFailed(TIMEOUT, justStarted)).isFalse();
        assertThat(running.hasFailed(TIMEOUT, STARTED.plus(TIMEOUT))).isTrue();

        CodefSyncJob failed = inProgress();
        failed.markFailed("CF-12345", true, STARTED.plusSeconds(5));
        assertThat(failed.hasFailed(TIMEOUT, justStarted)).isTrue();

        CodefSyncJob succeeded = inProgress();
        succeeded.markFetched(1, "CF-00000", STARTED.plusSeconds(5));
        assertThat(succeeded.hasFailed(TIMEOUT, STARTED.plus(TIMEOUT).plusHours(1))).isFalse();
    }

    @Test
    @DisplayName("이미 끝난 작업은 시간이 지나도 시간 초과가 아니다")
    void finishedJobIsNeverTimedOut() {
        CodefSyncJob job = inProgress();
        job.markFetched(2, "CF-00000", STARTED.plusSeconds(10));

        assertThat(job.isTimedOut(TIMEOUT, STARTED.plus(TIMEOUT).plusHours(1))).isFalse();
    }

}
