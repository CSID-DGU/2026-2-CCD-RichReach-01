package com.richreach.plan.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.config.PlanProperties;
import com.richreach.plan.entity.MonthlyPlan;
import com.richreach.plan.entity.PlanJob;
import com.richreach.plan.entity.PlanStrategy;
import com.richreach.plan.entity.SolverStatus;
import com.richreach.plan.repository.PlanJobRepository;
import com.richreach.plan.service.PlanBaselineReader.Baseline;
import com.richreach.plan.service.PlanRecalculationComputer.Computation;
import com.richreach.transaction.service.TransactionSummaryService;
import com.richreach.user.service.UserLockService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@SuppressWarnings("unchecked")
class PlanChatLateSpendTest {
    @Test
    void approvedTransactionAfterConfirmationPreventsSavingNewPlan() {
        long userId = 7L;
        YearMonth month = YearMonth.of(2026, 11);
        Clock clock = Clock.fixed(Instant.parse("2026-11-15T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        PlanJob job = PlanJob.startChatRecalculation(userId, "job-1", month.toString(), 100_000L,
            "DINING:100000", "0".repeat(64), "MONTHLY_PLAN",
            java.time.LocalDateTime.now(clock).minusMinutes(1));
        ReflectionTestUtils.setField(job, "id", 91L);

        PlanBudget budget = PlanBudget.decode(month, 100_000L, "DINING:100000");
        MonthlyPlan base = MonthlyPlan.create(userId, 1L, month, PlanStrategy.CP_SAT, 1, 100_000L, 0L);
        ReflectionTestUtils.setField(base, "id", 51L);
        Computation result = new Computation(true, null, null, new Baseline(base, budget, Map.of()),
            budget, Map.of(), Map.of(), 100_000L, 0L, SolverStatus.OPTIMAL);

        PlanJobRepository jobs = mock(PlanJobRepository.class);
        PlanRecalculationComputer computer = mock(PlanRecalculationComputer.class);
        PlanRecalculationSaver saver = mock(PlanRecalculationSaver.class);
        TransactionSummaryService transactions = mock(TransactionSummaryService.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(call -> ((TransactionCallback<Object>) call.getArgument(0))
            .doInTransaction(mock(TransactionStatus.class)));
        doAnswer(call -> {
            Consumer<TransactionStatus> callback = call.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(tx).executeWithoutResult(any());
        when(jobs.findById(91L)).thenReturn(Optional.of(job));
        when(computer.compute(eq(userId), eq(month), any(PlanBudget.class))).thenReturn(result);
        when(transactions.cardCategorySpend(userId, month))
            .thenReturn(Map.of(10L, Map.of(SpendingCategory.DINING, 120_000L)));

        PlanRecalculationWorker worker = new PlanRecalculationWorker(jobs, computer, saver,
            mock(UserLockService.class), transactions, tx,
            new PlanProperties(Duration.ofMinutes(5), Duration.ofSeconds(10)), clock);
        worker.run(91L);

        verify(jobs).failIfInProgress(eq(91L), eq("BUDGET_BELOW_SPENT"),
            eq(PlanChatConfirmService.BELOW_SPENT_REPLY), any());
        verify(saver, never()).save(any(long.class), any(YearMonth.class), any(Computation.class),
            any(), any());
    }
}
