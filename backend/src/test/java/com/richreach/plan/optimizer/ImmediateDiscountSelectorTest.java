package com.richreach.plan.optimizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.benefit.config.BenefitProperties;
import com.richreach.benefit.service.SimpleBenefitCalculator;
import com.richreach.card.dto.CalculationRule;
import com.richreach.card.service.BenefitRuleQueryService;
import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.optimizer.ImmediateDiscountSelector.CardState;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ImmediateDiscountSelectorTest {

    @Test
    void choosesMarginalBenefitAfterExistingCardReachesMonthlyCap() {
        InMemoryRules rules = new InMemoryRules();
        rules.put(101L, List.of(rateRule(1, "10", 5_000L)));
        rules.put(202L, List.of(rateRule(2, "5", null)));
        BenefitProperties properties = new BenefitProperties("simple",
            BenefitProperties.RuleSelection.READY_ONLY);
        ImmediateDiscountSelector selector = new ImmediateDiscountSelector(
            new SimpleBenefitCalculator(rules, properties));
        CardState cappedCard = new CardState(11, 101, 300_000,
            Map.of(SpendingCategory.CAFE, 50_000L));
        CardState secondCard = new CardState(22, 202, 300_000, Map.of());

        ImmediateDiscountSelector.Choice choice = selector.choose(SpendingCategory.CAFE, 10_000,
            List.of(cappedCard, secondCard));

        assertThat(choice.selected().userCardId()).isEqualTo(22);
        assertThat(choice.selected().incrementalBenefit()).isEqualTo(500);
        assertThat(choice.candidates().get(1).incrementalBenefit()).isZero();
        assertThat(cappedCard.monthlySpend()).containsEntry(SpendingCategory.CAFE, 50_000L);
    }

    private static CalculationRule rateRule(long id, String percent, Long cap) {
        return new CalculationRule(id, Set.of(SpendingCategory.CAFE), false, false,
            new BigDecimal(percent), "KRW", cap, null, null, null, null, null, null,
            null, "BASE", null, null, true);
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
