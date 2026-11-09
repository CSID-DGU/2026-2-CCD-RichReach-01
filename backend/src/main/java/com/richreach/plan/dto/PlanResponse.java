package com.richreach.plan.dto;

import java.util.List;

/**
 * 사용자에게 보여줄 월간 플랜. 금액은 모두 원 단위 정수다.
 *
 * @param planId            플랜 ID
 * @param month             플랜 대상 월({@code yyyy-MM})
 * @param expectedBenefit   이 플랜의 예상 혜택
 * @param baselineBenefit   지금처럼 쓸 때의 예상 혜택(HABIT 플랜의 예상 혜택). HABIT 플랜이 없으면 {@code null}이다.
 *                          (0으로 내리면 "추가 혜택 전부"로 잘못 보이기 때문이다.)
 * @param additionalBenefit {@code expectedBenefit - baselineBenefit}. 비교할 기준이 없으면 {@code null}이다.
 * @param cardTargets       카드별 목표 사용금액. 금액이 큰 카드부터이고, 카드 번호는 담지 않는다.
 */
public record PlanResponse(
    long planId,
    String month,
    long expectedBenefit,
    Long baselineBenefit,
    Long additionalBenefit,
    List<CardTarget> cardTargets) {

    /**
     * 카드 한 장의 목표 사용금액.
     *
     * @param cardId       보유카드 ID({@code GET /api/cards}의 {@code cardId})
     * @param cardName     카드 이름. 카드가 삭제되어 찾을 수 없으면 {@code null}이다.
     * @param targetAmount 이 카드로 쓸 목표 금액(원). 업종별 배분의 합이다.
     */
    public record CardTarget(long cardId, String cardName, long targetAmount) {

    }

}
