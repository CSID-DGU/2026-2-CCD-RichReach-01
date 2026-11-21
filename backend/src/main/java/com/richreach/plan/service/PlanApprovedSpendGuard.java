package com.richreach.plan.service;

import com.richreach.global.category.SpendingCategory;
import java.util.Map;

/** 이미 승인된 소비를 업종별 예산보다 낮게 확정하지 못하게 한다. */
final class PlanApprovedSpendGuard {
    private PlanApprovedSpendGuard() {
    }

    static boolean belowBudget(Map<Long, Map<SpendingCategory, Long>> byCard, PlanBudget budget) {
        for (SpendingCategory category : SpendingCategory.values()) {
            long approved = byCard.values().stream()
                .mapToLong(byCategory -> byCategory.getOrDefault(category, 0L)).sum();
            if (budget.byCategory().getOrDefault(category, 0L) < approved) {
                return true;
            }
        }
        return false;
    }
}
