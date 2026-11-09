package com.richreach.plan.dto;

import java.util.List;

/**
 * 플랜 생성 요청 ({@code POST /api/plans}). 온보딩에서 사용자가 확정한 월 예산을 보낸다.
 * 값의 검증은 {@code PlanBudget}이 하고, 올바르지 않으면 400({@code PLAN-003})이다.
 *
 * @param month  플랜을 만들 월 (예: 2026-12)
 * @param budget 확정한 예산
 */
public record PlanCreateRequest(String month, Budget budget) {

    /**
     * @param total      총예산(원)
     * @param byCategory 업종별 예산(원). 합이 {@code total}과 같아야 한다.
     */
    public record Budget(Long total, List<CategoryBudget> byCategory) {

    }

    /**
     * @param category 업종 코드 (SpendingCategory 이름, 대소문자 무시)
     * @param amount   업종 예산(원). 0 이상
     */
    public record CategoryBudget(String category, Long amount) {

    }

}
