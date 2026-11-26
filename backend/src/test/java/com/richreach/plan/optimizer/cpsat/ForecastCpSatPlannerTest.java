package com.richreach.plan.optimizer.cpsat;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.benefit.config.BenefitProperties;
import com.richreach.benefit.dto.BenefitPurchase;
import com.richreach.benefit.service.SimpleBenefitCalculator;
import com.richreach.card.dto.CalculationRule;
import com.richreach.card.service.BenefitRuleQueryService;
import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.optimizer.PlanStrategyInput;
import com.richreach.plan.optimizer.PlanStrategyInput.CardInput;
import com.richreach.plan.optimizer.PlanStrategyResult;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ForecastCpSatPlannerTest {
    private static final YearMonth MONTH = YearMonth.of(2026, 11);
    private final BenefitProperties properties = new BenefitProperties("simple",
        BenefitProperties.RuleSelection.READY_ONLY);
    private final Rules rules = new Rules();
    private final CpSatPlanStrategyRunner runner = new CpSatPlanStrategyRunner(rules,
        new SimpleBenefitCalculator(rules, properties), properties);

    private static CalculationRule rule(long id, int rate, Long minimum, Long cap) {
        return new CalculationRule(id, Set.of(SpendingCategory.CAFE), false, false,
            BigDecimal.valueOf(rate), "KRW", cap, null, null, null, null, null, null, null,
            "BASE", null, null, true, minimum);
    }

    private static BenefitPurchase forecast(long id, long amount, int day) {
        return new BenefitPurchase(id, LocalDateTime.of(2026, 11, day, 12, 0),
            SpendingCategory.CAFE, amount, true, true);
    }

    private PlanStrategyInput input(BenefitPurchase... purchases) {
        long budget = java.util.Arrays.stream(purchases).mapToLong(BenefitPurchase::amount).sum();
        return new PlanStrategyInput(1, MONTH, Map.of(SpendingCategory.CAFE, budget),
            List.of(new CardInput(11, 101, 300_000), new CardInput(22, 202, 300_000)),
            Duration.ofSeconds(5), List.of(purchases), Map.of());
    }

    @Test
    void minimumIsCheckedAgainstEachForecastPurchase() {
        rules.first = List.of(rule(1, 20, 10_000L, 4_000L));
        rules.second = List.of(rule(2, 5, null, null));

        PlanStrategyResult small = runner.run(input(forecast(-1, 5_000, 1), forecast(-2, 5_000, 2)));
        assertThat(small.success()).isTrue();
        assertThat(small.expectedBenefit()).isEqualTo(500);
        assertThat(small.allocations()).singleElement()
            .satisfies(allocation -> assertThat(allocation.userCardId()).isEqualTo(22));

        PlanStrategyResult large = runner.run(input(forecast(-1, 10_000, 1)));
        assertThat(large.success()).isTrue();
        assertThat(large.expectedBenefit()).isEqualTo(2_000);
        assertThat(large.allocations()).singleElement()
            .satisfies(allocation -> assertThat(allocation.userCardId()).isEqualTo(11));
    }

    @Test
    void approvedPurchaseConsumesTheSharedWalletBeforeFutureAllocation() {
        rules.first = List.of(new CalculationRule(1L, Set.of(SpendingCategory.CAFE), false, false,
            BigDecimal.valueOf(20), "KRW", null, "CAFE_WALLET", 3_000L,
            null, null, null, null, null, "BASE", null, null, true));
        rules.second = List.of(rule(2, 5, null, null));
        BenefitPurchase approved = new BenefitPurchase(1L,
            LocalDateTime.of(2026, 11, 1, 12, 0), SpendingCategory.CAFE, 10_000, true);
        PlanStrategyInput input = new PlanStrategyInput(1, MONTH,
            Map.of(SpendingCategory.CAFE, 20_000L),
            List.of(new CardInput(11, 101, 300_000), new CardInput(22, 202, 300_000)),
            Duration.ofSeconds(5), List.of(forecast(-1, 10_000, 2)), Map.of(11L, List.of(approved)));

        PlanStrategyResult result = runner.runWithCommitted(input,
            Map.of(11L, Map.of(SpendingCategory.CAFE, 10_000L)));

        assertThat(result.success()).isTrue();
        assertThat(result.expectedBenefit()).isEqualTo(3_000);
        assertThat(result.allocations()).singleElement().satisfies(allocation -> {
            assertThat(allocation.userCardId()).isEqualTo(11L);
            assertThat(allocation.targetAmount()).isEqualTo(20_000L);
        });
    }

    private static final class Rules extends BenefitRuleQueryService {
        private List<CalculationRule> first = List.of();
        private List<CalculationRule> second = List.of();

        Rules() {
            super(null, null, null);
        }

        @Override
        public List<CalculationRule> findForCalculation(long cardProductId) {
            return cardProductId == 101 ? first : second;
        }
    }
}
