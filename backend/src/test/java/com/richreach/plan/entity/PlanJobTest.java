package com.richreach.plan.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 플랜 생성 작업의 상태 전이와 시간 초과 판단을 DB 없이 확인한다. */
class PlanJobTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 12, 0);
    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    private static PlanJob started() {
        return PlanJob.start(1L, "job-1", "2026-12", 400_000, "DINING:400000", "hash", NOW);
    }

    @Test
    @DisplayName("막 시작한 작업은 진행 중이고 진행률은 0이다")
    void startsInProgress() {
        PlanJob job = started();

        assertThat(job.isInProgress()).isTrue();
        assertThat(job.getProgress()).isZero();
        assertThat(job.getPlanId()).isNull();
    }

    @Test
    @DisplayName("진행률은 커질 때만 바뀌고 완료 전에는 99를 넘지 않는다")
    void progressOnlyGrowsAndStopsAt99() {
        PlanJob job = started();

        job.updateProgress(30);
        job.updateProgress(10);
        assertThat(job.getProgress()).isEqualTo(30);
        job.updateProgress(150);
        assertThat(job.getProgress()).isEqualTo(99);
    }

    @Test
    @DisplayName("완료하면 진행률은 100이고 플랜이 연결된다")
    void completeSetsProgressAndPlan() {
        PlanJob job = started();

        job.complete(7L, NOW.plusSeconds(3));

        assertThat(job.getStatus()).isEqualTo(PlanJob.STATUS_COMPLETED);
        assertThat(job.getProgress()).isEqualTo(100);
        assertThat(job.getPlanId()).isEqualTo(7L);
        assertThat(job.isInProgress()).isFalse();
    }

    @Test
    @DisplayName("실패하면 코드와 이유를 남기고, 긴 이유는 컬럼 길이에 맞게 자른다")
    void failRecordsReasonAndTruncates() {
        PlanJob job = started();

        job.fail("NO_STRATEGY_RESULT", "x".repeat(PlanJob.FAILURE_REASON_MAX_LENGTH + 50), NOW.plusSeconds(1));

        assertThat(job.getStatus()).isEqualTo(PlanJob.STATUS_FAILED);
        assertThat(job.getFailureCode()).isEqualTo("NO_STRATEGY_RESULT");
        assertThat(job.getFailureReason()).hasSize(PlanJob.FAILURE_REASON_MAX_LENGTH);
    }

    @Test
    @DisplayName("끝난 작업의 진행률은 더 이상 바뀌지 않는다")
    void finishedJobIgnoresProgress() {
        PlanJob job = started();
        job.fail("X", "이유", NOW);

        job.updateProgress(50);

        assertThat(job.getProgress()).isZero();
    }

    @Test
    @DisplayName("진행 중인 채로 제한 시간이 지나면 멈춘 것으로 보고, 끝난 작업은 시간 초과로 보지 않는다")
    void timeoutAppliesOnlyToInProgressJobs() {
        PlanJob job = started();

        assertThat(job.isTimedOut(TIMEOUT, NOW.plusMinutes(5))).isFalse();
        assertThat(job.isTimedOut(TIMEOUT, NOW.plusMinutes(5).plusSeconds(1))).isTrue();

        job.complete(1L, NOW.plusMinutes(1));
        assertThat(job.isTimedOut(TIMEOUT, NOW.plusHours(1))).isFalse();
    }

}
