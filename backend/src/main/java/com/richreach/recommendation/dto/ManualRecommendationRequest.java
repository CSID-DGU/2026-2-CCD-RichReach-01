package com.richreach.recommendation.dto;

/**
 * 결제 전 수동 입력 추천 요청({@code POST /api/recommendations/manual}). 업종은 꼭 보내고, 금액은 모르면 보내지 않아도 된다.
 * 질문 글자에서 업종과 금액을 읽는 일은 앱이 하고 서버는 계산만 한다. 값의 검증은 서비스가 하고, 올바르지 않으면
 * 400({@code RECOMMEND-001})이다.
 *
 * @param category 업종 코드. {@code SpendingCategory} 이름 11개 중 하나이고 대소문자는 가리지 않는다.
 * @param amount   결제 금액(원). 1~100,000,000. 보내지 않으면(null) 금액 구간별 추천({@code RANGE})을 준다.
 */
public record ManualRecommendationRequest(String category, Long amount) {

}
