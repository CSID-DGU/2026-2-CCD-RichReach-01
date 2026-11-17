package com.richreach.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.config.PlanProperties;
import com.richreach.plan.entity.MonthlyPlan;
import com.richreach.plan.entity.PlanChangeReason;
import com.richreach.plan.entity.PlanJob;
import com.richreach.plan.entity.PlanStrategy;
import com.richreach.plan.repository.PlanJobRepository;
import com.richreach.plan.service.PlanBaselineReader.Baseline;
import com.richreach.user.service.UserLockService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** 재계산 요청이 새 작업을 만들지, 진행 중인 작업에 표시만 할지를 DB 없이 확인한다. */
class PlanRecalculationServiceTest {

    private static final long USER = 1L;
    private static final YearMonth MONTH = YearMonth.of(2026, 11);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private UserLockService userLock;
    private PlanBaselineReader baselines;
    private PlanJobRepository jobs;
    private PlanRecalculationWorker worker;
    private PlanRecalculationService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userLock = mock(UserLockService.class);
        baselines = mock(PlanBaselineReader.class);
        jobs = mock(PlanJobRepository.class);
        worker = mock(PlanRecalculationWorker.class);
        TransactionTemplate transaction = mock(TransactionTemplate.class);
        // 트랜잭션 없이 콜백을 그대로 실행한다.
        when(transaction.execute(any())).thenAnswer(invocation ->
            ((TransactionCallback<Object>) invocation.getArgument(0)).doInTransaction(null));
        service = new PlanRecalculationService(userLock, baselines, jobs, worker, transaction,
            new PlanProperties(Duration.ofMinutes(5), Duration.ofSeconds(10)), CLOCK);

        Map<SpendingCategory, Long> budget = new EnumMap<>(SpendingCategory.class);
        budget.put(SpendingCategory.DINING, 100_000L);
        MonthlyPlan plan = MonthlyPlan.create(USER, 1L, MONTH, PlanStrategy.CP_SAT, 1, 100_000, 0);
        when(baselines.read(USER, MONTH)).thenReturn(Optional.of(new Baseline(plan,
            new PlanBudget(MONTH, 100_000, budget), Map.of())));
        // 저장하면 id가 붙은 것처럼 돌려준다.
        when(jobs.save(any(PlanJob.class))).thenAnswer(invocation -> {
            PlanJob saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });
    }

    private static PlanJob runningJob(LocalDateTime startedAt) {
        PlanJob job = PlanJob.startRecalculation(USER, "job-1", MONTH.toString(), 100_000, "DINING:100000",
            "0".repeat(64), PlanChangeReason.TRANSACTION_SYNCED, startedAt);
        ReflectionTestUtils.setField(job, "id", 7L);
        return job;
    }

    private void whenRunning(PlanJob job) {
        when(jobs.findFirstByUserIdAndJobTypeAndPlanMonthAndStatusOrderByIdDesc(USER, PlanJob.TYPE_RECALCULATE,
            MONTH.toString(), PlanJob.STATUS_IN_PROGRESS)).thenReturn(Optional.of(job));
    }

    @Test
    @DisplayName("진행 중인 작업이 없으면 새 작업을 만들고 바로 실행한다")
    void createsAndRunsANewJob() {
        service.requestAndRun(USER, MONTH, PlanChangeReason.TRANSACTION_SYNCED);

        ArgumentCaptor<PlanJob> saved = ArgumentCaptor.forClass(PlanJob.class);
        verify(jobs).save(saved.capture());
        assertThat(saved.getValue().getJobType()).isEqualTo(PlanJob.TYPE_RECALCULATE);
        assertThat(saved.getValue().getRecalcReason()).isEqualTo("TRANSACTION_SYNCED");
        verify(userLock).lock(USER);
        verify(worker).run(99L);
    }

    @Test
    @DisplayName("진행 중인 작업에 표시를 남겼으면(1행) 새 작업을 만들지 않는다")
    void marksTheRunningJob() {
        whenRunning(runningJob(NOW));
        when(jobs.requestRerun(7L)).thenReturn(1);

        service.requestAndRun(USER, MONTH, PlanChangeReason.CATEGORY_EDITED);

        verify(jobs, never()).save(any(PlanJob.class));
        verify(worker, never()).run(anyLong());
    }

    @Test
    @DisplayName("표시하려는 순간 그 작업이 이미 끝났으면(0행) 거래를 반영할 새 작업을 만든다")
    void createsANewJobWhenTheRunningJobAlreadyEnded() {
        whenRunning(runningJob(NOW));
        when(jobs.requestRerun(7L)).thenReturn(0);

        service.requestAndRun(USER, MONTH, PlanChangeReason.CATEGORY_EDITED);

        verify(jobs, times(1)).save(any(PlanJob.class));
        verify(worker).run(99L);
    }

    @Test
    @DisplayName("제한 시간이 지나 멈춘 것으로 보이는 작업은 실패로 기록하고 새 작업을 만든다")
    void replacesAStalledJob() {
        whenRunning(runningJob(NOW.minusMinutes(10)));

        service.requestAndRun(USER, MONTH, PlanChangeReason.TRANSACTION_SYNCED);

        verify(jobs).failIfTimedOut(eq(7L), eq(PlanRecalculationWorker.CODE_TIMEOUT), any(), any(), any());
        verify(jobs, never()).requestRerun(anyLong());
        verify(worker).run(99L);
    }

    @Test
    @DisplayName("이번 달이 아니거나 보여줄 플랜이 없으면 아무것도 하지 않는다")
    void ignoresOtherMonthsAndMissingPlans() {
        service.requestAndRun(USER, MONTH.minusMonths(1), PlanChangeReason.TRANSACTION_SYNCED);
        when(baselines.read(USER, MONTH)).thenReturn(Optional.empty());
        service.requestAndRun(USER, MONTH, PlanChangeReason.TRANSACTION_SYNCED);

        verify(jobs, never()).save(any(PlanJob.class));
        verify(worker, never()).run(anyLong());
    }

}
