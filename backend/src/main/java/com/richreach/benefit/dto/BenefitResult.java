package com.richreach.benefit.dto;

import com.richreach.global.category.SpendingCategory;
import java.util.List;
import java.util.Map;

/**
 * 혜택 계산 결과.
 *
 * @param totalBenefit      예상 혜택 합계(원). {@code benefitByCategory}의 합과 같다.
 * @param benefitByCategory 업종별 예상 혜택(원). 혜택이 없는 업종은 비어 있어도 된다.
 * @param confirmedOnly     사람이 원문과 대조해 확인한 규칙만 썼는지. false이면 확인 전 규칙이 섞여 있어서 참고용이다.
 * @param notes             계산에서 빼거나 단순화한 내용(예: "가맹점 지정 혜택은 업종으로 매칭할 수 없어 제외"). 화면과 로그 점검용이다.
 */
public record BenefitResult(long totalBenefit, Map<SpendingCategory, Long> benefitByCategory, boolean confirmedOnly,
                            List<String> notes) {

    public BenefitResult {
        benefitByCategory = Map.copyOf(benefitByCategory);
        notes = List.copyOf(notes);
    }

    /** 혜택이 전혀 없는 결과. */
    public static BenefitResult none(boolean confirmedOnly, List<String> notes) {
        return new BenefitResult(0, Map.of(), confirmedOnly, notes);
    }

}
