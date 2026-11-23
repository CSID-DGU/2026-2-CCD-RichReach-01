package com.richreach.recommendation.dto;

/**
 * 결제 전 수동 입력 추천 요청({@code POST /api/recommendations/manual}). 금액과 업종을 직접 골라 보낸다.
 * 값의 검증은 서비스가 하고, 올바르지 않으면 400({@code RECOMMEND-001})이다.
 *
 * @param category 업종 코드. {@code SpendingCategory} 이름 11개 중 하나이고 대소문자는 가리지 않는다.
 * @param amount   결제 금액(원). 1~100,000,000
 */
public record ManualRecommendationRequest(String category, Long amount) {

}
