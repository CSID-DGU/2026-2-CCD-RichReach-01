package com.richreach.plan.optimizer;

import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.entity.SolverStatus;
import java.util.List;
import java.util.Map;

/** The remaining plan only; approved transactions are never moved to another card. */
public record RollingPlanResult(boolean success,
                                Map<SpendingCategory, Long> approvedByCategory,
                                Map<SpendingCategory, Long> remainingByCategory,
                                List<RemainingAllocation> remainingAllocations,
                                long estimatedApprovedBenefit,
                                long estimatedRemainingBenefit,
                                SolverStatus solverStatus,
                                List<String> notes, String failureReason) {

    public RollingPlanResult {
        approvedByCategory = Map.copyOf(approvedByCategory);
        remainingByCategory = Map.copyOf(remainingByCategory);
        remainingAllocations = List.copyOf(remainingAllocations);
        notes = List.copyOf(notes);
    }

    public record RemainingAllocation(long userCardId, SpendingCategory category, long targetAmount,
                                      long expectedBenefit) {
        public RemainingAllocation(long userCardId, SpendingCategory category, long targetAmount) {
            this(userCardId, category, targetAmount, 0);
        }
    }
}
