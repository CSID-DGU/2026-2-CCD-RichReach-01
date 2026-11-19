package com.richreach.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.plan.config.PlanProperties;
import com.richreach.plan.dto.PlanChatConfirmRequest;
import com.richreach.plan.dto.PlanCreateRequest;
import com.richreach.plan.dto.PlanCreateRequest.Budget;
import com.richreach.plan.dto.PlanCreateRequest.CategoryBudget;
import com.richreach.plan.entity.MonthlyPlan;
import com.richreach.plan.entity.PlanJob;
import com.richreach.plan.entity.PlanStrategy;
import com.richreach.plan.repository.PlanJobRepository;
import com.richreach.plan.service.PlanBaselineReader.Baseline;
import com.richreach.transaction.service.TransactionSummaryService;
import com.richreach.user.service.UserLockService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@SuppressWarnings("unchecked")
class PlanChatConfirmServiceTest {
    private static final long USER = 7;
    private static final YearMonth MONTH = YearMonth.of(2026, 11);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T03:00:00Z"),
        ZoneId.of("Asia/Seoul"));

    private final UserLockService userLock = mock(UserLockService.class);
    private final PlanBaselineReader baselines = mock(PlanBaselineReader.class);
    private final PlanInputAssembler inputs = mock(PlanInputAssembler.class);
    private final TransactionSummaryService transactions = mock(TransactionSummaryService.class);
    private final PlanJobRepository jobs = mock(PlanJobRepository.class);
    private final PlanRecalculationWorker worker = mock(PlanRecalculationWorker.class);
    private PlanChatConfirmService service;

    @BeforeEach
    void setUp() {
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(call ->
            ((TransactionCallback<Object>) call.getArgument(0)).doInTransaction(null));
        service = new PlanChatConfirmService(userLock, baselines, inputs, transactions, jobs, worker,
            Runnable::run, tx, new PlanProperties(Duration.ofMinutes(5), Duration.ofSeconds(10)), CLOCK);

        MonthlyPlan plan = MonthlyPlan.create(USER, 1L, MONTH, PlanStrategy.CP_SAT, 1, 100_000, 0);
        ReflectionTestUtils.setField(plan, "id", 51L);
        when(baselines.read(USER, MONTH)).thenReturn(Optional.of(new Baseline(plan,
            new PlanBudget(MONTH, 100_000, Map.of(SpendingCategory.DINING, 100_000L)), Map.of())));
        when(inputs.analysisTargets(USER)).thenReturn(List.of(mock(OwnedCardResponse.class)));
        when(transactions.cardCategorySpend(USER, MONTH)).thenReturn(Map.of());
        when(jobs.save(any(PlanJob.class))).thenAnswer(call -> {
            PlanJob job = call.getArgument(0);
            ReflectionTestUtils.setField(job, "id", 91L);
            return job;
        });
    }

    private static PlanChatConfirmRequest request(long basePlanId, boolean confirmed, long dining, long cafe) {
        return new PlanChatConfirmRequest(basePlanId, confirmed,
            new PlanCreateRequest(MONTH.toString(), new Budget(dining + cafe,
                List.of(new CategoryBudget("DINING", dining), new CategoryBudget("CAFE", cafe)))),
            "IMMEDIATE_DISCOUNT");
    }

    @Test
    void confirmedProposalCreatesRecalculationJobWithPreference() {
        var response = service.confirm(USER, request(51, true, 80_000, 20_000));
        ArgumentCaptor<PlanJob> saved = ArgumentCaptor.forClass(PlanJob.class);
        verify(jobs).save(saved.capture());
        assertThat(saved.getValue().getRecalcReason()).isEqualTo("CHAT_CONFIRMED");
        assertThat(saved.getValue().getRequestedPreference()).isEqualTo("IMMEDIATE_DISCOUNT");
        assertThat(response.jobId()).isEqualTo(saved.getValue().getJobId());
        assertThat(response.needsClarification()).isFalse();
        verify(worker).run(91L);
    }

    @Test
    void belowApprovedCategoryIsClarifiedWithoutStartingJob() {
        when(transactions.cardCategorySpend(USER, MONTH))
            .thenReturn(Map.of(10L, Map.of(SpendingCategory.CAFE, 25_000L)));
        var response = service.confirm(USER, request(51, true, 80_000, 20_000));
        assertThat(response.needsClarification()).isTrue();
        assertThat(response.reply()).isEqualTo(PlanChatConfirmService.BELOW_SPENT_REPLY);
        assertThat(response.jobId()).isNull();
        verify(jobs, never()).save(any());
    }

    @Test
    void staleBasePlanIsClarifiedWithoutStartingJob() {
        var response = service.confirm(USER, request(50, true, 80_000, 20_000));
        assertThat(response.reply()).isEqualTo(PlanChatConfirmService.STALE_REPLY);
        verify(jobs, never()).save(any());
    }

    @Test
    void unconfirmedOrMalformedProposalCannotStartJob() {
        assertThatThrownBy(() -> service.confirm(USER, request(51, false, 80_000, 20_000)))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> service.confirm(USER, request(51, true, -1, 100_000)))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_BUDGET));
        verify(jobs, never()).save(any());
    }
}
