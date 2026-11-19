package com.richreach.plan.optimizer;

import com.richreach.global.category.SpendingCategory;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * A complete snapshot for recalculating the remainder of a month. The approved-spend maps
 * already include the latest approved transaction. The caller removes canceled/refunded
 * transactions before building this snapshot; it must not send the same event as a delta.
 */
public record RollingPlanInput(long userId, YearMonth month,
                               Map<SpendingCategory, Long> confirmedBudgetByCategory,
                               List<CardState> cards, Duration timeLimit) {

    public RollingPlanInput {
        if (userId <= 0 || month == null || timeLimit == null || timeLimit.isNegative()
            || timeLimit.isZero() || confirmedBudgetByCategory == null || cards == null || cards.isEmpty()) {
            throw new IllegalArgumentException("월, 예산, 보유카드, 계산 시간 제한이 필요합니다.");
        }
        confirmedBudgetByCategory = Map.copyOf(confirmedBudgetByCategory);
        cards = List.copyOf(cards);
        confirmedBudgetByCategory.forEach((category, amount) -> {
            if (category == null || amount == null || amount < 0) {
                throw new IllegalArgumentException("업종 예산은 0 이상이어야 합니다.");
            }
        });
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (CardState card : cards) {
            if (card == null || !seen.add(card.userCardId())) {
                throw new IllegalArgumentException("보유카드가 없거나 중복되었습니다.");
            }
        }
    }

    public record CardState(long userCardId, long cardProductId, long previousMonthSpend,
                            Map<SpendingCategory, Long> approvedSpendByCategory,
                            boolean eligibleForFuture) {
        public CardState(long userCardId, long cardProductId, long previousMonthSpend,
                         Map<SpendingCategory, Long> approvedSpendByCategory) {
            this(userCardId, cardProductId, previousMonthSpend, approvedSpendByCategory, true);
        }

        public CardState {
            if (userCardId <= 0 || cardProductId <= 0 || previousMonthSpend < 0
                || approvedSpendByCategory == null) {
                throw new IllegalArgumentException("카드 상태가 올바르지 않습니다.");
            }
            approvedSpendByCategory = Map.copyOf(approvedSpendByCategory);
            approvedSpendByCategory.forEach((category, amount) -> {
                if (category == null || amount == null || amount < 0) {
                    throw new IllegalArgumentException("확정 소비액은 0 이상이어야 합니다.");
                }
            });
        }
    }
}
