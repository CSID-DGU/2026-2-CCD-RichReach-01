package com.richreach.plan.optimizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.benefit.config.BenefitProperties;
import com.richreach.benefit.service.SimpleBenefitCalculator;
import com.richreach.card.dto.CalculationRule;
import com.richreach.card.service.BenefitRuleQueryService;
import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.entity.SolverStatus;
import com.richreach.plan.optimizer.RollingPlanInput.CardState;
import com.richreach.plan.optimizer.cpsat.CpSatPlanStrategyRunner;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RollingPlanReplannerTest {

    private final Rules rules = new Rules();
    private final BenefitProperties properties = new BenefitProperties("simple",
        BenefitProperties.RuleSelection.READY_ONLY);
    private final SimpleBenefitCalculator calculator = new SimpleBenefitCalculator(rules, properties);
    private final RollingPlanReplanner replanner = new RollingPlanReplanner(
        new CpSatPlanStrategyRunner(rules, calculator, properties), calculator);

    @Test
    void approvedSpendingStaysOnItsCardAndOnlyTheRemainderIsAllocated() {
        rules.put(101, List.of(rate(1, "10", 5_000L, null)));
        rules.put(202, List.of(rate(2, "5", null, null)));
        RollingPlanResult result = replanner.replan(input(100_000,
            Map.of(SpendingCategory.CAFE, 40_000L), Map.of()));

        assertThat(result.success()).isTrue();
        assertThat(result.solverStatus()).isEqualTo(SolverStatus.OPTIMAL);
        assertThat(result.approvedByCategory()).containsEntry(SpendingCategory.CAFE, 40_000L);
        assertThat(result.remainingByCategory()).containsEntry(SpendingCategory.CAFE, 60_000L);
        assertThat(result.remainingAllocations()).containsExactlyInAnyOrder(
            new RollingPlanResult.RemainingAllocation(11, SpendingCategory.CAFE, 10_000, 1_000),
            new RollingPlanResult.RemainingAllocation(22, SpendingCategory.CAFE, 50_000, 2_500));
        assertThat(result.estimatedApprovedBenefit()).isEqualTo(4_000);
        assertThat(result.estimatedRemainingBenefit()).isEqualTo(3_500);
    }

    @Test
    void aLaterApprovalRecalculatesFromTheWholeSnapshot() {
        rules.put(101, List.of(rate(1, "10", 5_000L, null)));
        rules.put(202, List.of(rate(2, "5", null, null)));
        RollingPlanResult result = replanner.replan(input(100_000,
            Map.of(SpendingCategory.CAFE, 40_000L), Map.of(SpendingCategory.CAFE, 20_000L)));

        assertThat(result.success()).isTrue();
        assertThat(result.remainingByCategory()).containsEntry(SpendingCategory.CAFE, 40_000L);
        assertThat(result.remainingAllocations()).containsExactlyInAnyOrder(
            new RollingPlanResult.RemainingAllocation(11, SpendingCategory.CAFE, 10_000, 1_000),
            new RollingPlanResult.RemainingAllocation(22, SpendingCategory.CAFE, 30_000, 1_500));
        assertThat(result.estimatedApprovedBenefit()).isEqualTo(5_000);
        assertThat(result.estimatedRemainingBenefit()).isEqualTo(2_500);
    }

    @Test
    void aDeselectedCardKeepsHistoryButReceivesNoFutureTarget() {
        rules.put(101, List.of(rate(1, "10", null, null)));
        rules.put(202, List.of(rate(2, "5", null, null)));
        RollingPlanInput snapshot = new RollingPlanInput(1, YearMonth.of(2026, 11),
            Map.of(SpendingCategory.CAFE, 100_000L), List.of(
                new CardState(11, 101, 300_000, Map.of(SpendingCategory.CAFE, 40_000L), false),
                new CardState(22, 202, 300_000, Map.of(), true)), Duration.ofSeconds(5));

        RollingPlanResult result = replanner.replan(snapshot);

        assertThat(result.success()).isTrue();
        assertThat(result.remainingAllocations()).containsExactly(
            new RollingPlanResult.RemainingAllocation(22, SpendingCategory.CAFE, 60_000, 3_000));
        assertThat(result.estimatedApprovedBenefit()).isEqualTo(4_000);
        assertThat(result.estimatedRemainingBenefit()).isEqualTo(3_000);
    }

    @Test
    void exceedingTheBudgetDoesNotInventNewSpending() {
        rules.put(101, List.of(rate(1, "10", 5_000L, null)));
        RollingPlanResult result = replanner.replan(input(50_000,
            Map.of(SpendingCategory.CAFE, 70_000L), Map.of()));

        assertThat(result.success()).isTrue();
        assertThat(result.approvedByCategory()).containsEntry(SpendingCategory.CAFE, 70_000L);
        assertThat(result.remainingByCategory()).isEmpty();
        assertThat(result.remainingAllocations()).isEmpty();
        assertThat(result.estimatedRemainingBenefit()).isZero();
    }

    @Test
    void unsupportedCountLimitIsNotUsedToInflateThePlan() {
        rules.put(101, List.of(rate(1, "10", null, 1)));
        rules.put(202, List.of(rate(2, "5", null, null)));
        RollingPlanResult result = replanner.replan(input(100_000, Map.of(), Map.of()));

        assertThat(result.success()).isTrue();
        assertThat(result.remainingAllocations()).containsExactly(
            new RollingPlanResult.RemainingAllocation(22, SpendingCategory.CAFE, 100_000, 5_000));
        assertThat(result.estimatedRemainingBenefit()).isEqualTo(5_000);
    }

    @Test
    void onlyUnusableRulesLeaveTheExistingPlanUntouched() {
        rules.put(101, List.of(rate(1, "10", null, 1)));
        RollingPlanResult result = replanner.replan(input(100_000, Map.of(), Map.of()));

        assertThat(result.success()).isFalse();
        assertThat(result.remainingAllocations()).isEmpty();
        assertThat(result.failureReason()).contains("계산 가능한");
    }

    @Test
    void aRefundIsRepresentedByAReplacementSnapshot() {
        rules.put(101, List.of(rate(1, "10", 5_000L, null)));
        rules.put(202, List.of(rate(2, "5", null, null)));
        RollingPlanResult before = replanner.replan(input(100_000,
            Map.of(SpendingCategory.CAFE, 40_000L), Map.of()));
        RollingPlanResult after = replanner.replan(input(100_000,
            Map.of(SpendingCategory.CAFE, 30_000L), Map.of()));

        assertThat(before.remainingByCategory()).containsEntry(SpendingCategory.CAFE, 60_000L);
        assertThat(after.remainingByCategory()).containsEntry(SpendingCategory.CAFE, 70_000L);
    }

    @Test
    void threeCardsAndTwoHundredApprovalsUseTheCurrentAggregate() {
        rules.put(101, List.of(rate(1, "10", 20_000L, null)));
        rules.put(202, List.of(rate(2, "5", null, null)));
        rules.put(303, List.of(rate(3, "3", null, null)));
        long[] byCard = new long[3];
        for (int approval = 0; approval < 200; approval++) byCard[approval % 3] += 1_000;
        RollingPlanInput snapshot = new RollingPlanInput(1, YearMonth.of(2026, 11),
            Map.of(SpendingCategory.CAFE, 300_000L), List.of(
                new CardState(11, 101, 300_000, Map.of(SpendingCategory.CAFE, byCard[0])),
                new CardState(22, 202, 300_000, Map.of(SpendingCategory.CAFE, byCard[1])),
                new CardState(33, 303, 300_000, Map.of(SpendingCategory.CAFE, byCard[2]))),
            Duration.ofSeconds(5));

        RollingPlanResult result = replanner.replan(snapshot);

        assertThat(result.success()).isTrue();
        assertThat(result.solverStatus()).isIn(SolverStatus.OPTIMAL, SolverStatus.FEASIBLE);
        assertThat(result.approvedByCategory()).containsEntry(SpendingCategory.CAFE, 200_000L);
        assertThat(result.remainingByCategory()).containsEntry(SpendingCategory.CAFE, 100_000L);
        assertThat(result.remainingAllocations().stream()
            .mapToLong(RollingPlanResult.RemainingAllocation::targetAmount).sum()).isEqualTo(100_000L);
    }

    private static RollingPlanInput input(long budget, Map<SpendingCategory, Long> first,
                                          Map<SpendingCategory, Long> second) {
        return new RollingPlanInput(1, YearMonth.of(2026, 11),
            Map.of(SpendingCategory.CAFE, budget), List.of(
                new CardState(11, 101, 300_000, first),
                new CardState(22, 202, 300_000, second)), Duration.ofSeconds(5));
    }

    private static CalculationRule rate(long id, String percent, Long cap, Integer monthlyCount) {
        return new CalculationRule(id, Set.of(SpendingCategory.CAFE), false, false,
            new BigDecimal(percent), "KRW", cap, null, null, null, null, monthlyCount, null,
            null, "BASE", null, null, true);
    }

    private static final class Rules extends BenefitRuleQueryService {
        private final java.util.Map<Long, List<CalculationRule>> entries = new java.util.HashMap<>();

        private Rules() {
            super(null, null, null);
        }

        private void put(long cardProductId, List<CalculationRule> value) {
            entries.put(cardProductId, value);
        }

        @Override
        public List<CalculationRule> findForCalculation(long cardProductId) {
            return entries.getOrDefault(cardProductId, List.of());
        }
    }
}
