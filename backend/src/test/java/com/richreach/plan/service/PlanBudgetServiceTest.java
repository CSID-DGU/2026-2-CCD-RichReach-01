package com.richreach.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.plan.config.PlanProperties;
import com.richreach.plan.dto.PlanCreateRequest;
import com.richreach.plan.dto.PlanCreateRequest.Budget;
import com.richreach.plan.dto.PlanCreateRequest.CategoryBudget;
import com.richreach.plan.dto.PlanJobStartResponse;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** 예산 수정 요청의 검증, 작업 만들기, 멱등성, 중복 실행 방지를 DB 없이 확인한다. */
@SuppressWarnings("unchecked")
class PlanBudgetServiceTest {

    private static final long USER = 1L;
    private static final YearMonth MONTH = YearMonth.of(2026, 11);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private UserLockService userLock;
    private PlanBaselineReader baselines;
    private PlanInputAssembler inputs;
    private PlanJobRepository jobs;
    private PlanRecalculationWorker worker;
    private PlanBudgetService service;

    private PlanBudgetService serviceWith(TaskExecutor executor) {
        TransactionTemplate transaction = mock(TransactionTemplate.class);
        // 트랜잭션 없이 콜백을 그대로 실행한다.
        when(transaction.execute(any())).thenAnswer(invocation ->
            ((TransactionCallback<Object>) invocation.getArgument(0)).doInTransaction(null));
        return new PlanBudgetService(userLock, baselines, inputs, jobs, worker, executor, transaction,
            new PlanProperties(Duration.ofMinutes(5), Duration.ofSeconds(10)), CLOCK);
    }

    @BeforeEach
    void setUp() {
        userLock = mock(UserLockService.class);
        baselines = mock(PlanBaselineReader.class);
        inputs = mock(PlanInputAssembler.class);
        jobs = mock(PlanJobRepository.class);
        worker = mock(PlanRecalculationWorker.class);
        service = serviceWith(Runnable::run);

        Map<SpendingCategory, Long> current = new EnumMap<>(SpendingCategory.class);
        current.put(SpendingCategory.DINING, 100_000L);
        MonthlyPlan plan = MonthlyPlan.create(USER, 1L, MONTH, PlanStrategy.CP_SAT, 1, 100_000, 0);
        when(baselines.read(USER, MONTH)).thenReturn(Optional.of(new Baseline(plan,
            new PlanBudget(MONTH, 100_000, current), Map.of())));
        when(inputs.analysisTargets(USER)).thenReturn(List.of(mock(OwnedCardResponse.class)));
        // 저장하면 id가 붙은 것처럼 돌려준다.
        when(jobs.save(any(PlanJob.class))).thenAnswer(invocation -> {
            PlanJob saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });
    }

    private static PlanCreateRequest request(String month, long dining, long cafe) {
        return new PlanCreateRequest(month, new Budget(dining + cafe, List.of(
            new CategoryBudget("DINING", dining), new CategoryBudget("CAFE", cafe))));
    }

    private static PlanJob runningJob(PlanChangeReason reason, String requestHash, LocalDateTime startedAt) {
        PlanJob job = PlanJob.startRecalculation(USER, "job-1", MONTH.toString(), 100_000, "DINING:100000",
            requestHash, reason, startedAt);
        ReflectionTestUtils.setField(job, "id", 7L);
        return job;
    }

    private void whenRunning(PlanJob job) {
        when(jobs.findFirstByUserIdAndJobTypeAndPlanMonthAndStatusOrderByIdDesc(USER, PlanJob.TYPE_RECALCULATE,
            MONTH.toString(), PlanJob.STATUS_IN_PROGRESS)).thenReturn(Optional.of(job));
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("올바른 이번 달 예산이면 BUDGET_CHANGED 재계산 작업을 만들고 요청한 예산을 담아 실행한다")
    void createsABudgetChangeJob() {
        PlanJobStartResponse response = service.start(USER, request("2026-11", 60_000, 40_000));

        ArgumentCaptor<PlanJob> saved = ArgumentCaptor.forClass(PlanJob.class);
        verify(jobs).save(saved.capture());
        PlanJob job = saved.getValue();
        assertThat(job.getJobType()).isEqualTo(PlanJob.TYPE_RECALCULATE);
        assertThat(job.getRecalcReason()).isEqualTo("BUDGET_CHANGED");
        assertThat(job.getBudgetTotal()).isEqualTo(100_000);
        assertThat(job.getBudgetEncoded()).contains("DINING:60000").contains("CAFE:40000");
        assertThat(response.jobId()).isEqualTo(job.getJobId());
        verify(userLock).lock(USER);
        verify(worker).run(99L);
    }

    @Test
    @DisplayName("예산이 올바르지 않으면 PLAN-003이고 작업을 만들지 않는다")
    void rejectsAnInvalidBudget() {
        PlanCreateRequest sumMismatch = new PlanCreateRequest("2026-11", new Budget(50_000L, List.of(
            new CategoryBudget("DINING", 60_000L))));
        PlanCreateRequest unknownCategory = new PlanCreateRequest("2026-11", new Budget(10_000L, List.of(
            new CategoryBudget("NOPE", 10_000L))));

        assertError(() -> service.start(USER, sumMismatch), ErrorCode.INVALID_BUDGET);
        assertError(() -> service.start(USER, unknownCategory), ErrorCode.INVALID_BUDGET);
        assertError(() -> service.start(USER, null), ErrorCode.INVALID_INPUT_VALUE);
        verify(jobs, never()).save(any(PlanJob.class));
    }

    @Test
    @DisplayName("이번 달이 아니거나 월 형식이 틀리면 COMMON-400이다")
    void onlyThisMonthCanBeChanged() {
        assertError(() -> service.start(USER, request("2026-10", 60_000, 40_000)), ErrorCode.INVALID_INPUT_VALUE);
        assertError(() -> service.start(USER, request("2026-12", 60_000, 40_000)), ErrorCode.INVALID_INPUT_VALUE);
        assertError(() -> service.start(USER, request("2026-13", 60_000, 40_000)), ErrorCode.INVALID_INPUT_VALUE);
        verify(jobs, never()).save(any(PlanJob.class));
    }

    @Test
    @DisplayName("보여줄 플랜이 없으면 PLAN-001, 분석 대상 카드가 없으면 PLAN-006이다")
    void requiresAPlanAndAnalysisCards() {
        when(inputs.analysisTargets(USER)).thenReturn(List.of());
        assertError(() -> service.start(USER, request("2026-11", 60_000, 40_000)), ErrorCode.PLAN_NO_ANALYSIS_CARD);

        when(baselines.read(USER, MONTH)).thenReturn(Optional.empty());
        assertError(() -> service.start(USER, request("2026-11", 60_000, 40_000)), ErrorCode.PLAN_NOT_FOUND);
        verify(jobs, never()).save(any(PlanJob.class));
    }

    @Test
    @DisplayName("거래 반영 재계산이 진행 중이면 PLAN-005이고, 같은 달에 다른 예산 수정이 진행 중이어도 PLAN-005다")
    void rejectsWhileAnotherRecalculationIsRunning() {
        whenRunning(runningJob(PlanChangeReason.TRANSACTION_SYNCED, "0".repeat(64), NOW));
        assertError(() -> service.start(USER, request("2026-11", 60_000, 40_000)), ErrorCode.PLAN_JOB_ALREADY_RUNNING);

        whenRunning(runningJob(PlanChangeReason.BUDGET_CHANGED, "1".repeat(64), NOW));
        assertError(() -> service.start(USER, request("2026-11", 60_000, 40_000)), ErrorCode.PLAN_JOB_ALREADY_RUNNING);

        verify(jobs, never()).save(any(PlanJob.class));
        verify(worker, never()).run(anyLong());
    }

    @Test
    @DisplayName("진행 중인 예산 수정과 같은 요청이 다시 오면 새 작업을 만들지 않고 그 작업의 jobId를 돌려준다")
    void sameRequestIsIdempotent() {
        // 같은 요청의 지문은 PlanBudget이 만든다.
        Map<SpendingCategory, Long> byCategory = new EnumMap<>(SpendingCategory.class);
        byCategory.put(SpendingCategory.DINING, 60_000L);
        byCategory.put(SpendingCategory.CAFE, 40_000L);
        String fingerprint = new PlanBudget(MONTH, 100_000, byCategory).fingerprint();
        whenRunning(runningJob(PlanChangeReason.BUDGET_CHANGED, fingerprint, NOW));

        PlanJobStartResponse response = service.start(USER, request("2026-11", 60_000, 40_000));

        assertThat(response.jobId()).isEqualTo("job-1");
        verify(jobs, never()).save(any(PlanJob.class));
        verify(worker, never()).run(anyLong());
    }

    @Test
    @DisplayName("제한 시간이 지나 멈춘 것으로 보이는 작업은 실패로 기록하고 새 작업을 만든다")
    void replacesAStalledJob() {
        whenRunning(runningJob(PlanChangeReason.TRANSACTION_SYNCED, "0".repeat(64), NOW.minusMinutes(10)));

        service.start(USER, request("2026-11", 60_000, 40_000));

        verify(jobs).failIfTimedOut(eq(7L), eq(PlanRecalculationWorker.CODE_TIMEOUT), any(), any(), any());
        verify(worker).run(99L);
    }

    @Test
    @DisplayName("실행기가 가득 차 작업을 받지 못하면 작업을 실패로 기록한다")
    void marksTheJobFailedWhenTheExecutorIsFull() {
        TaskExecutor full = task -> {
            throw new TaskRejectedException("full");
        };

        serviceWith(full).start(USER, request("2026-11", 60_000, 40_000));

        verify(worker).markRejected(99L);
        verify(worker, never()).run(anyLong());
    }

}
