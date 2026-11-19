package com.richreach.plan.optimizer;

import com.richreach.benefit.dto.BenefitQuery;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.benefit.service.BenefitCalculator;
import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.optimizer.PlanStrategyInput.CardInput;
import com.richreach.plan.optimizer.RollingPlanResult.RemainingAllocation;
import com.richreach.plan.optimizer.cpsat.CpSatPlanStrategyRunner;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Replans future spending from a full approved-transaction snapshot. The user's confirmed
 * budget is never increased to chase a benefit. If actual spending exceeds it, that excess
 * is fixed history and the future target for the category becomes zero.
 */
@Component
@RequiredArgsConstructor
public class RollingPlanReplanner {

    private final CpSatPlanStrategyRunner cpSat;
    private final BenefitCalculator benefitCalculator;

    public RollingPlanResult replan(RollingPlanInput input) {
        Map<SpendingCategory, Long> approved = new EnumMap<>(SpendingCategory.class);
        Map<Long, Map<SpendingCategory, Long>> fixed = new java.util.HashMap<>();
        Set<Long> eligibleForFuture = new java.util.HashSet<>();
        List<CardInput> cards = new ArrayList<>();
        long approvedBenefit = 0;
        Set<String> notes = new LinkedHashSet<>();
        for (var card : input.cards()) {
            fixed.put(card.userCardId(), card.approvedSpendByCategory());
            if (card.eligibleForFuture()) eligibleForFuture.add(card.userCardId());
            cards.add(new CardInput(card.userCardId(), card.cardProductId(), card.previousMonthSpend()));
            card.approvedSpendByCategory().forEach((category, amount) ->
                approved.merge(category, amount, Math::addExact));
            BenefitResult benefit = benefitCalculator.calculate(new BenefitQuery(card.cardProductId(),
                card.approvedSpendByCategory(), card.previousMonthSpend()));
            approvedBenefit = Math.addExact(approvedBenefit, benefit.totalBenefit());
            notes.addAll(benefit.notes());
        }

        Map<SpendingCategory, Long> effective = new EnumMap<>(SpendingCategory.class);
        Map<SpendingCategory, Long> remaining = new EnumMap<>(SpendingCategory.class);
        for (SpendingCategory category : SpendingCategory.values()) {
            long planned = input.confirmedBudgetByCategory().getOrDefault(category, 0L);
            long spent = approved.getOrDefault(category, 0L);
            if (Math.max(planned, spent) > 0) effective.put(category, Math.max(planned, spent));
            if (planned > spent) remaining.put(category, planned - spent);
        }
        if (remaining.isEmpty()) {
            return new RollingPlanResult(true, approved, remaining, List.of(), approvedBenefit, 0,
                null, new ArrayList<>(notes), null);
        }

        PlanStrategyInput strategyInput = new PlanStrategyInput(input.userId(), input.month(), effective,
            cards, input.timeLimit());
        PlanStrategyResult result = cpSat.runWithCommitted(strategyInput, fixed, eligibleForFuture);
        if (!result.success()) {
            return new RollingPlanResult(false, approved, remaining, List.of(), approvedBenefit, 0,
                result.solverStatus(), new ArrayList<>(notes), result.failureReason());
        }
        List<RemainingAllocation> future = new ArrayList<>();
        for (var allocation : result.allocations()) {
            long alreadySpent = fixed.get(allocation.userCardId()).getOrDefault(allocation.category(), 0L);
            long target = Math.subtractExact(allocation.targetAmount(), alreadySpent);
            if (target < 0) {
                return new RollingPlanResult(false, approved, remaining, List.of(), approvedBenefit, 0,
                    result.solverStatus(), new ArrayList<>(notes), "확정된 거래를 다른 카드로 옮길 수 없습니다.");
            }
            if (target > 0) future.add(new RemainingAllocation(allocation.userCardId(),
                allocation.category(), target));
        }
        Map<SpendingCategory, Long> distributed = new EnumMap<>(SpendingCategory.class);
        future.forEach(allocation -> distributed.merge(allocation.category(), allocation.targetAmount(),
            Math::addExact));
        if (!distributed.equals(remaining)) {
            return new RollingPlanResult(false, approved, remaining, List.of(), approvedBenefit, 0,
                result.solverStatus(), new ArrayList<>(notes), "남은 예산의 카드 배분이 맞지 않습니다.");
        }
        long additionalBenefit = Math.subtractExact(result.expectedBenefit(), approvedBenefit);
        if (additionalBenefit < 0) {
            return new RollingPlanResult(false, approved, remaining, List.of(), approvedBenefit, 0,
                result.solverStatus(), new ArrayList<>(notes), "남은 혜택을 일관되게 계산하지 못했습니다.");
        }
        notes.addAll(result.notes());
        return new RollingPlanResult(true, approved, remaining, future, approvedBenefit,
            additionalBenefit, result.solverStatus(), new ArrayList<>(notes), null);
    }
}
