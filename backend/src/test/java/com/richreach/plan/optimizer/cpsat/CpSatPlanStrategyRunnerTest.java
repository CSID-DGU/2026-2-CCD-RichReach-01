package com.richreach.plan.optimizer.cpsat;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.benefit.config.BenefitProperties;
import com.richreach.benefit.service.SimpleBenefitCalculator;
import com.richreach.card.dto.CalculationRule;
import com.richreach.card.service.BenefitRuleQueryService;
import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.optimizer.PlanStrategyInput;
import com.richreach.plan.optimizer.PlanStrategyInput.CardInput;
import com.richreach.plan.optimizer.PlanStrategyResult;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CpSatPlanStrategyRunnerTest {

    private final InMemoryRules rules = new InMemoryRules();
    private final BenefitProperties properties = new BenefitProperties("simple",
        BenefitProperties.RuleSelection.READY_ONLY);
    private final CpSatPlanStrategyRunner runner = new CpSatPlanStrategyRunner(rules,
        new SimpleBenefitCalculator(rules, properties), properties);

    @Test
    void monthlyCapDivertsRemainingSpendToSecondCard() {
        rules.put(101L, List.of(rateRule(1, "10", 5_000L, true)));
        rules.put(202L, List.of(rateRule(2, "5", null, true)));
        PlanStrategyInput input = input(Map.of(SpendingCategory.CAFE, 100_000L));

        PlanStrategyResult result = runner.run(input);

        assertThat(result.success()).isTrue();
        assertThat(result.violations(input)).isEmpty();
        assertThat(result.expectedBenefit()).isEqualTo(7_500L);
        assertThat(result.allocations()).extracting(PlanStrategyResult.Allocation::targetAmount)
            .containsExactlyInAnyOrder(50_000L, 50_000L);
    }

    @Test
    void unverifiedRuleDoesNotProducePlanInReadyOnlyMode() {
        rules.put(101L, List.of(rateRule(1, "10", null, false)));
        rules.put(202L, List.of());

        PlanStrategyResult result = runner.run(input(Map.of(SpendingCategory.CAFE, 10_000L)));

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).contains("계산 가능한");
    }

    @Test
    void previousMonthThresholdExcludesIneligibleCard() {
        CalculationRule tiered = new CalculationRule(3L, Set.of(SpendingCategory.CAFE), false, false,
            new BigDecimal("10"), "KRW", null, null, null, null, null, null, null,
            null, "BASE", 300_000L, null, true);
        rules.put(101L, List.of(tiered));
        rules.put(202L, List.of(rateRule(2, "5", null, true)));
        PlanStrategyInput input = new PlanStrategyInput(1, YearMonth.of(2026, 11),
            Map.of(SpendingCategory.CAFE, 10_000L),
            List.of(new CardInput(11, 101, 200_000), new CardInput(22, 202, 300_000)),
            Duration.ofSeconds(5));

        PlanStrategyResult result = runner.run(input);

        assertThat(result.success()).isTrue();
        assertThat(result.violations(input)).isEmpty();
        assertThat(result.allocations()).singleElement()
            .satisfies(allocation -> {
                assertThat(allocation.userCardId()).isEqualTo(22);
                assertThat(allocation.expectedBenefit()).isEqualTo(500);
            });
    }

    @Test
    void absentCardsReturnFailure() {
        PlanStrategyInput input = new PlanStrategyInput(1, YearMonth.of(2026, 11),
            Map.of(SpendingCategory.CAFE, 10_000L), List.of(), Duration.ofSeconds(3));

        assertThat(runner.run(input).failureReason()).contains("보유카드");
    }

    private static PlanStrategyInput input(Map<SpendingCategory, Long> budget) {
        return new PlanStrategyInput(1, YearMonth.of(2026, 11), budget,
            List.of(new CardInput(11, 101, 300_000), new CardInput(22, 202, 300_000)),
            Duration.ofSeconds(5));
    }

    private static CalculationRule rateRule(long id, String percent, Long cap, boolean ready) {
        return new CalculationRule(id, Set.of(SpendingCategory.CAFE), false, false,
            new BigDecimal(percent), "KRW", cap, null, null, null, null, null, null,
            null, "BASE", null, null, ready);
    }

    private static final class InMemoryRules extends BenefitRuleQueryService {
        private final java.util.Map<Long, List<CalculationRule>> entries = new java.util.HashMap<>();

        private InMemoryRules() {
            super(null, null, null);
        }

        private void put(long cardProductId, List<CalculationRule> rules) {
            entries.put(cardProductId, rules);
        }

        @Override
        public List<CalculationRule> findForCalculation(long cardProductId) {
            return entries.getOrDefault(cardProductId, List.of());
        }
    }
}
