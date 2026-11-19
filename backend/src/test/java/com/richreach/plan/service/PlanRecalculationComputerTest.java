package com.richreach.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.benefit.dto.BenefitQuery;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.benefit.service.BenefitCalculator;
import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.config.PlanProperties;
import com.richreach.plan.entity.MonthlyPlan;
import com.richreach.plan.entity.PlanStrategy;
import com.richreach.plan.entity.SolverStatus;
import com.richreach.plan.optimizer.RollingPlanInput;
import com.richreach.plan.optimizer.RollingPlanReplanner;
import com.richreach.plan.optimizer.RollingPlanResult;
import com.richreach.plan.optimizer.RollingPlanResult.RemainingAllocation;
import com.richreach.plan.service.PlanBaselineReader.Baseline;
import com.richreach.plan.service.PlanRecalculationComputer.Computation;
import com.richreach.plannedexpense.dto.PlannedExpenseResponse;
import com.richreach.plannedexpense.service.PlannedExpenseService;
import com.richreach.transaction.service.TransactionSummaryService;
import java.time.Duration;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 재계산 입력 조립과 결과 정리를 DB와 CP-SAT 없이 확인한다. */
class PlanRecalculationComputerTest {

    private static final long USER = 1L;
    private static final YearMonth MONTH = YearMonth.of(2026, 11);

    private PlanBaselineReader baselines;
    private OwnedCardQueryService ownedCards;
    private TransactionSummaryService transactions;
    private RollingPlanReplanner replanner;
    private BenefitCalculator calculator;
    private PlannedExpenseService plannedExpenses;
    private PlanRecalculationComputer computer;

    @BeforeEach
    void setUp() {
        baselines = mock(PlanBaselineReader.class);
        ownedCards = mock(OwnedCardQueryService.class);
        transactions = mock(TransactionSummaryService.class);
        replanner = mock(RollingPlanReplanner.class);
        calculator = mock(BenefitCalculator.class);
        plannedExpenses = mock(PlannedExpenseService.class);
        computer = new PlanRecalculationComputer(baselines, ownedCards, transactions, replanner, calculator,
            new PlanProperties(Duration.ofMinutes(5), Duration.ofSeconds(10)), plannedExpenses);
        when(baselines.read(USER, MONTH)).thenReturn(Optional.of(baseline(Map.of(SpendingCategory.DINING, 300_000L,
            SpendingCategory.CAFE, 100_000L))));
        when(transactions.cardSpend(USER, MONTH.minusMonths(1))).thenReturn(Map.of(10L, 700_000L));
        when(calculator.calculate(any())).thenReturn(BenefitResult.none(true, List.of()));
    }

    private static Baseline baseline(Map<SpendingCategory, Long> budget) {
        MonthlyPlan plan = MonthlyPlan.create(USER, 1L, MONTH, PlanStrategy.CP_SAT, 1, 400_000, 5_000);
        long total = budget.values().stream().mapToLong(Long::longValue).sum();
        Map<SpendingCategory, Long> byCategory = new EnumMap<>(SpendingCategory.class);
        byCategory.putAll(budget);
        return new Baseline(plan, new PlanBudget(MONTH, total, byCategory), Map.of());
    }

    private static OwnedCardResponse card(long id, Long productId, boolean selected) {
        return new OwnedCardResponse(id, productId, null, "카드" + id, "1111", null,
            OwnedCardResponse.AnalysisStatus.UNAVAILABLE, selected, null);
    }

    private static RollingPlanResult success(RemainingAllocation... remaining) {
        return new RollingPlanResult(true, Map.of(), Map.of(), List.of(remaining), 0, 0, SolverStatus.OPTIMAL,
            List.of(), null);
    }

    @Test
    @DisplayName("분석 대상 카드는 앞으로 배분하고, 분석 대상이 아니어도 이번 달에 쓴 카드는 쓴 금액만 고정해서 넘긴다")
    void buildsTheSnapshotFromApprovedSpend() {
        when(ownedCards.list(USER)).thenReturn(List.of(
            card(10L, 100L, true),    // 분석 대상, 이번 달 소비 있음
            card(11L, 200L, true),    // 분석 대상, 소비 없음
            card(12L, 300L, false),   // 분석 대상 아님, 소비 있음 -> 쓴 금액만 고정
            card(13L, 400L, false),   // 분석 대상 아님, 소비 없음 -> 제외
            card(14L, null, true)));  // 카드 상품과 매칭되지 않음 -> 제외
        when(transactions.cardCategorySpend(USER, MONTH)).thenReturn(Map.of(
            10L, Map.of(SpendingCategory.DINING, 50_000L), 12L, Map.of(SpendingCategory.CAFE, 20_000L)));
        when(replanner.replan(any())).thenReturn(success());

        computer.compute(USER, MONTH);

        ArgumentCaptor<RollingPlanInput> input = ArgumentCaptor.forClass(RollingPlanInput.class);
        verify(replanner).replan(input.capture());
        assertThat(input.getValue().confirmedBudgetByCategory()).containsEntry(SpendingCategory.DINING, 300_000L)
            .containsEntry(SpendingCategory.CAFE, 100_000L);
        assertThat(input.getValue().cards()).extracting(RollingPlanInput.CardState::userCardId)
            .containsExactly(10L, 11L, 12L);
        assertThat(input.getValue().cards()).extracting(RollingPlanInput.CardState::eligibleForFuture)
            .containsExactly(true, true, false);
        assertThat(input.getValue().cards().get(0).previousMonthSpend()).isEqualTo(700_000L);
        assertThat(input.getValue().cards().get(2).approvedSpendByCategory()).containsEntry(SpendingCategory.CAFE, 20_000L);
        assertThat(input.getValue().timeLimit()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("예정지출은 확정 예산에 얹어 엔진에 넣고, 결과에 담는 확정 예산은 얹기 전 그대로다")
    void overlaysPlannedExpensesOnTheInput() {
        when(ownedCards.list(USER)).thenReturn(List.of(card(10L, 100L, true)));
        when(plannedExpenses.listForBudget(USER, MONTH)).thenReturn(List.of(
            new PlannedExpenseResponse(1L, "2026-11", "제주도 숙소", 300_000, null, 300_000, "CULTURE_TRAVEL", "EXTRA"),
            new PlannedExpenseResponse(2L, "2026-11", "노트북", 400_000, 4, 100_000, "SHOPPING", "WITHIN_BUDGET")));
        when(replanner.replan(any())).thenReturn(success());

        Computation result = computer.compute(USER, MONTH);

        ArgumentCaptor<RollingPlanInput> input = ArgumentCaptor.forClass(RollingPlanInput.class);
        verify(replanner).replan(input.capture());
        // 조정 10만원은 외식 30만원·카페 10만원에서 7만5천원·2만5천원 줄고, 추가 30만원은 총예산에 더해진다.
        assertThat(input.getValue().confirmedBudgetByCategory())
            .containsEntry(SpendingCategory.DINING, 225_000L).containsEntry(SpendingCategory.CAFE, 75_000L)
            .containsEntry(SpendingCategory.SHOPPING, 100_000L).containsEntry(SpendingCategory.CULTURE_TRAVEL, 300_000L);
        assertThat(result.budget().total()).isEqualTo(400_000L);
        assertThat(result.budget().byCategory()).containsOnlyKeys(SpendingCategory.DINING, SpendingCategory.CAFE);
    }

    @Test
    @DisplayName("조정 예정지출은 이미 쓴 금액을 뺀 남은 예산에서만 줄여서 엔진에 넣는다")
    void withinBudgetOnlyCutsTheRemainingBudget() {
        when(ownedCards.list(USER)).thenReturn(List.of(card(10L, 100L, true)));
        // 외식 예산 30만원 중 25만원을 이미 썼다. 남은 예산은 외식 5만원, 카페 10만원이다.
        when(transactions.cardCategorySpend(USER, MONTH)).thenReturn(Map.of(
            10L, Map.of(SpendingCategory.DINING, 250_000L)));
        when(plannedExpenses.listForBudget(USER, MONTH)).thenReturn(List.of(
            new PlannedExpenseResponse(2L, "2026-11", "노트북", 30_000, null, 30_000, "SHOPPING", "WITHIN_BUDGET")));
        when(replanner.replan(any())).thenReturn(success());

        computer.compute(USER, MONTH);

        ArgumentCaptor<RollingPlanInput> input = ArgumentCaptor.forClass(RollingPlanInput.class);
        verify(replanner).replan(input.capture());
        // 3만원을 남은 예산(5만:10만)의 비율대로 줄이면 외식 1만원, 카페 2만원이다. 쓴 금액 아래로 내려가지 않는다.
        assertThat(input.getValue().confirmedBudgetByCategory())
            .containsEntry(SpendingCategory.DINING, 290_000L).containsEntry(SpendingCategory.CAFE, 80_000L)
            .containsEntry(SpendingCategory.SHOPPING, 30_000L);
    }

    @Test
    @DisplayName("카드별 목표는 이미 쓴 금액에 남은 목표를 더한 이번 달 전체 목표이고, 혜택은 그 목표로 카드마다 계산한다")
    void targetsAreApprovedPlusRemaining() {
        when(ownedCards.list(USER)).thenReturn(List.of(card(10L, 100L, true), card(11L, 200L, true)));
        when(transactions.cardCategorySpend(USER, MONTH)).thenReturn(Map.of(
            10L, Map.of(SpendingCategory.DINING, 50_000L)));
        when(replanner.replan(any())).thenReturn(success(
            new RemainingAllocation(10L, SpendingCategory.DINING, 100_000),
            new RemainingAllocation(11L, SpendingCategory.DINING, 150_000),
            new RemainingAllocation(11L, SpendingCategory.CAFE, 100_000)));
        when(calculator.calculate(any(BenefitQuery.class))).thenAnswer(invocation -> {
            BenefitQuery query = invocation.getArgument(0);
            long dining = query.monthlySpend().getOrDefault(SpendingCategory.DINING, 0L);
            return new BenefitResult(dining / 100, Map.of(SpendingCategory.DINING, dining / 100), true, List.of());
        });

        Computation result = computer.compute(USER, MONTH);

        assertThat(result.success()).isTrue();
        assertThat(result.targets().get(10L)).containsEntry(SpendingCategory.DINING, 150_000L);
        assertThat(result.targets().get(11L)).containsEntry(SpendingCategory.DINING, 150_000L)
            .containsEntry(SpendingCategory.CAFE, 100_000L);
        assertThat(result.totalSpend()).isEqualTo(400_000L);
        assertThat(result.benefits().get(10L)).containsEntry(SpendingCategory.DINING, 1_500L);
        assertThat(result.totalBenefit()).isEqualTo(3_000L);
        assertThat(result.solverStatus()).isEqualTo(SolverStatus.OPTIMAL);
    }

    @Test
    @DisplayName("예산을 새로 정했으면 지금 플랜의 예산 대신 그 예산으로 계산하고, 결과에도 그 예산을 담는다")
    void usesTheBudgetOverride() {
        when(ownedCards.list(USER)).thenReturn(List.of(card(10L, 100L, true)));
        when(transactions.cardCategorySpend(USER, MONTH)).thenReturn(Map.of());
        when(replanner.replan(any())).thenReturn(success(new RemainingAllocation(10L, SpendingCategory.DINING, 50_000)));
        Map<SpendingCategory, Long> byCategory = new EnumMap<>(SpendingCategory.class);
        byCategory.put(SpendingCategory.DINING, 50_000L);
        PlanBudget override = new PlanBudget(MONTH, 50_000, byCategory);

        Computation result = computer.compute(USER, MONTH, override);

        ArgumentCaptor<RollingPlanInput> input = ArgumentCaptor.forClass(RollingPlanInput.class);
        verify(replanner).replan(input.capture());
        assertThat(input.getValue().confirmedBudgetByCategory()).containsOnlyKeys(SpendingCategory.DINING)
            .containsEntry(SpendingCategory.DINING, 50_000L);
        assertThat(result.budget()).isEqualTo(override);
        // 지금 플랜의 예산(외식 30만원, 카페 10만원)은 쓰지 않는다.
        assertThat(result.baseline().budget().byCategory()).containsEntry(SpendingCategory.DINING, 300_000L);
    }

    @Test
    @DisplayName("플랜이 없거나 예산을 읽을 수 없거나 분석 대상 카드가 없으면 엔진을 부르지 않고 실패한다")
    void failsWithoutCallingTheEngine() {
        when(baselines.read(USER, MONTH)).thenReturn(Optional.empty());
        assertThat(computer.compute(USER, MONTH).failureCode()).isEqualTo(PlanRecalculationComputer.CODE_NO_PLAN);

        when(baselines.read(USER, MONTH)).thenReturn(Optional.of(baseline(Map.of())));
        assertThat(computer.compute(USER, MONTH).failureCode()).isEqualTo(PlanRecalculationComputer.CODE_NO_BUDGET);

        when(baselines.read(USER, MONTH)).thenReturn(Optional.of(baseline(Map.of(SpendingCategory.DINING, 1L))));
        when(ownedCards.list(USER)).thenReturn(List.of(card(10L, 100L, false)));
        when(transactions.cardCategorySpend(USER, MONTH)).thenReturn(Map.of());
        assertThat(computer.compute(USER, MONTH).failureCode())
            .isEqualTo(PlanRecalculationComputer.CODE_NO_ANALYSIS_CARD);

        verify(replanner, never()).replan(any());
    }

    @Test
    @DisplayName("엔진이 실패하거나 예외를 던져도 예외 없이 실패 결과를 돌려준다")
    void engineFailuresBecomeFailedComputations() {
        when(ownedCards.list(USER)).thenReturn(List.of(card(10L, 100L, true)));
        when(transactions.cardCategorySpend(USER, MONTH)).thenReturn(Map.of());

        when(replanner.replan(any())).thenReturn(new RollingPlanResult(false, Map.of(), Map.of(), List.of(), 0, 0,
            SolverStatus.INFEASIBLE, List.of(), "조건이 충돌합니다."));
        Computation failed = computer.compute(USER, MONTH);
        assertThat(failed.success()).isFalse();
        assertThat(failed.failureCode()).isEqualTo(PlanRecalculationComputer.CODE_SOLVER_FAILED);
        assertThat(failed.failureReason()).isEqualTo("조건이 충돌합니다.");

        when(replanner.replan(any())).thenThrow(new IllegalStateException("boom"));
        Computation errored = computer.compute(USER, MONTH);
        assertThat(errored.failureCode()).isEqualTo(PlanRecalculationComputer.CODE_SOLVER_ERROR);
        assertThat(errored.failureReason()).doesNotContain("boom");
    }

}
