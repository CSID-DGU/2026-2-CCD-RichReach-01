package com.richreach.benefit.dto;

import com.richreach.global.category.SpendingCategory;
import java.util.Map;

/**
 * 혜택 계산 요청. 카드 한 장에 한 달 동안 이만큼 쓰면 혜택이 얼마인지를 묻는다.
 *
 * @param cardProductId       혜택을 계산할 카드 상품 ID({@code card_products.id}). 보유카드 ID가 아니다.
 * @param monthlySpend        그 카드로 한 달 동안 쓸 금액(원). 업종별로 담고, 없는 업종은 0이다. 음수는 없다.
 * @param previousMonthSpend  그 카드의 전월 실적(원). 혜택의 전월실적 조건을 판단하는 데 쓴다.
 */
public record BenefitQuery(long cardProductId, Map<SpendingCategory, Long> monthlySpend, long previousMonthSpend) {

    public BenefitQuery {
        monthlySpend = monthlySpend == null ? Map.of() : Map.copyOf(monthlySpend);
        if (previousMonthSpend < 0) {
            throw new IllegalArgumentException("전월 실적은 0 이상이어야 합니다.");
        }
        monthlySpend.values().forEach(amount -> {
            if (amount == null || amount < 0) {
                throw new IllegalArgumentException("소비 금액은 0 이상이어야 합니다.");
            }
        });
    }

}
