package com.richreach.transaction.repository;

/**
 * 보유카드와 업종별로 모은 금액.
 *
 * @param userCardId 보유카드 ID
 * @param category   업종. 사용자가 고친 값이 있으면 그 값, 없으면 자동 분류 값(분류 전이면 null)
 * @param netAmount  취소·환불을 뺀 금액
 */
public record CardCategoryTotal(Long userCardId, String category, Long netAmount) {

}
