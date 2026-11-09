package com.richreach.plan.dto;

import com.richreach.global.category.SpendingCategory;
import java.util.List;

/**
 * 업종별 사용계획. 배분이 있는 업종만 업종 고정 순서로 담는다. 금액은 모두 원 단위 정수다.
 *
 * @param planId     플랜 ID
 * @param month      플랜 대상 월({@code yyyy-MM})
 * @param categories 업종별 배분
 */
public record PlanCategoriesResponse(long planId, String month, List<CategoryPlan> categories) {

    /**
     * 업종 하나의 배분.
     *
     * @param category        업종
     * @param targetAmount    이 업종의 목표 소비 금액(카드별 금액의 합)
     * @param expectedBenefit 이 업종에서 기대하는 혜택(카드별 혜택의 합)
     * @param cards           이 업종을 쓸 카드와 금액. 금액이 큰 카드부터이다.
     */
    public record CategoryPlan(SpendingCategory category, long targetAmount, long expectedBenefit,
                               List<CardAmount> cards) {

    }

    /**
     * 업종 안에서 카드 한 장의 배분.
     *
     * @param cardId          보유카드 ID
     * @param cardName        카드 이름. 카드가 삭제되어 찾을 수 없으면 {@code null}이다.
     * @param targetAmount    이 카드로 이 업종에서 쓸 금액
     * @param expectedBenefit 그때 기대하는 혜택
     */
    public record CardAmount(long cardId, String cardName, long targetAmount, long expectedBenefit) {

    }

}
