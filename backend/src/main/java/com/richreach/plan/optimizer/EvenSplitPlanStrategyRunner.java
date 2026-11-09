package com.richreach.plan.optimizer;

import com.richreach.benefit.dto.BenefitQuery;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.benefit.service.BenefitCalculator;
import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.entity.PlanStrategy;
import com.richreach.plan.optimizer.PlanStrategyInput.CardInput;
import com.richreach.plan.optimizer.PlanStrategyResult.Allocation;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 임시 전략: 업종 예산을 카드들에 똑같이 나눈다. 혜택을 높이도록 카드를 고르지 않고, 나눈 뒤의 혜택은 혜택 계산기로 구해서 담는다.
 * 다른 전략(HABIT, GREEDY, CP_SAT)이 구현되기 전에도 플랜 생성이 끝까지 동작하게 하려는 것이다.
 *
 * <p>업종 예산이 카드 수로 나누어떨어지지 않으면 남는 금액을 카드 순서대로 1원씩 더 준다. 그래서 업종의 배분 합은
 * 항상 예산과 정확히 같다. 같은 입력이면 항상 같은 결과다.
 */
@Component
public class EvenSplitPlanStrategyRunner implements PlanStrategyRunner {

    private final BenefitCalculator benefitCalculator;

    public EvenSplitPlanStrategyRunner(BenefitCalculator benefitCalculator) {
        this.benefitCalculator = benefitCalculator;
    }

    @Override
    public PlanStrategy strategy() {
        return PlanStrategy.EVEN_SPLIT;
    }

    @Override
    public PlanStrategyResult run(PlanStrategyInput input) {
        long startedAt = System.nanoTime();
        List<CardInput> cards = input.cards();
        if (cards.isEmpty()) {
            return PlanStrategyResult.failure(null, null, "배분할 카드가 없습니다.");
        }

        // 카드마다 업종별로 나눈 금액
        List<Map<SpendingCategory, Long>> spendByCard = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            spendByCard.add(new EnumMap<>(SpendingCategory.class));
        }
        input.budgetByCategory().forEach((category, budget) -> {
            if (budget <= 0) {
                return;
            }
            long base = budget / cards.size();
            long remainder = budget % cards.size();
            for (int i = 0; i < cards.size(); i++) {
                long amount = base + (i < remainder ? 1 : 0);
                if (amount > 0) {
                    spendByCard.get(i).put(category, amount);
                }
            }
        });

        // 카드마다 혜택을 계산해서 업종별 배분으로 담는다.
        List<Allocation> allocations = new ArrayList<>();
        boolean confirmedOnly = true;
        Set<String> notes = new LinkedHashSet<>();
        for (int i = 0; i < cards.size(); i++) {
            CardInput card = cards.get(i);
            Map<SpendingCategory, Long> spend = spendByCard.get(i);
            if (spend.isEmpty()) {
                continue;
            }
            BenefitResult benefit = benefitCalculator.calculate(
                new BenefitQuery(card.cardProductId(), spend, card.previousMonthSpend()));
            confirmedOnly &= benefit.confirmedOnly();
            notes.addAll(benefit.notes());
            for (SpendingCategory category : SpendingCategory.values()) {
                Long amount = spend.get(category);
                if (amount != null) {
                    allocations.add(new Allocation(card.userCardId(), category, amount,
                        benefit.benefitByCategory().getOrDefault(category, 0L)));
                }
            }
        }
        int solveTimeMs = (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - startedAt) / 1_000_000);
        return PlanStrategyResult.success(allocations, null, solveTimeMs, confirmedOnly, new ArrayList<>(notes));
    }

}
