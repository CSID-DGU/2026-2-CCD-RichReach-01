package com.richreach.plan.optimizer;

import com.richreach.global.category.SpendingCategory;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * 플랜 전략(HABIT, GREEDY, CP_SAT)에 주는 입력. "이번 달 예산을 어느 카드로 쓰면 혜택이 가장 클까?"를 풀기 위한 재료다.
 * 모든 금액은 <b>원 단위 정수(long)</b>이고, 0 이상이다.
 *
 * <p>이 입력은 읽기 전용이다. 전략은 입력을 바꾸거나 DB에 저장하지 않는다. (저장은 플랜 생성 서비스가 한다.)
 *
 * @param userId           플랜을 만드는 사용자. 전략은 보통 쓸 일이 없고, 로그나 점검용이다.
 * @param month            플랜 대상 월 (예: 2026-12)
 * @param budgetByCategory 사용자가 확정한 업종별 예산(원). 11개 업종 중 예산이 없는 업종은 없거나 0이다.
 *                         <b>각 업종의 예산 전부를 카드들에 나눠 담아야 한다.</b> (아래 "총소비 불변" 참고)
 * @param cards            이번 플랜에서 쓸 수 있는 카드. 사용자가 분석 대상으로 고른 카드뿐이고, 신규 카드는 없다.
 * @param timeLimit        이 전략에 허용된 계산 시간. 풀이 시간 제한(예: CP-SAT의 max_time_in_seconds)에 그대로 쓴다.
 *                         시간 안에 끝나지 않으면 그때까지 찾은 해를 {@code FEASIBLE}로 돌려주거나 실패로 알린다.
 */
public record PlanStrategyInput(
    long userId,
    YearMonth month,
    Map<SpendingCategory, Long> budgetByCategory,
    List<CardInput> cards,
    Duration timeLimit) {

    public PlanStrategyInput {
        budgetByCategory = budgetByCategory == null ? Map.of() : Map.copyOf(budgetByCategory);
        cards = cards == null ? List.of() : List.copyOf(cards);
        budgetByCategory.forEach((category, amount) -> {
            if (amount == null || amount < 0) {
                throw new IllegalArgumentException("예산은 0 이상이어야 합니다.");
            }
        });
        if (timeLimit == null || timeLimit.isZero() || timeLimit.isNegative()) {
            throw new IllegalArgumentException("계산 시간 제한은 0보다 커야 합니다.");
        }
    }

    /** 총예산(원). 모든 업종 예산의 합이다. */
    public long totalBudget() {
        return budgetByCategory.values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * 플랜에 쓸 수 있는 카드 한 장. 카드 번호 같은 민감정보는 담지 않는다.
     *
     * @param userCardId         보유카드 ID ({@code user_cards.id}). 배분 결과에는 <b>이 값</b>을 쓴다.
     * @param cardProductId      카드 상품 ID ({@code card_products.id}). 혜택 규칙을 찾을 때 쓴다. (보유카드 ID와 다르다.)
     * @param previousMonthSpend 이 카드의 전월 실적(원). 혜택의 전월실적 조건을 판단할 때 쓴다.
     */
    public record CardInput(long userCardId, long cardProductId, long previousMonthSpend) {

        public CardInput {
            if (previousMonthSpend < 0) {
                throw new IllegalArgumentException("전월 실적은 0 이상이어야 합니다.");
            }
        }
    }

}
