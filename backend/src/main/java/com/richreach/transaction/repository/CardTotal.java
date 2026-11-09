package com.richreach.transaction.repository;

/**
 * 기간 안의 거래를 보유카드별로 모은 합계(조회 결과).
 *
 * @param userCardId       보유카드 ID
 * @param netAmount        취소·환불을 뺀 금액
 * @param transactionCount 전액 취소된 거래를 뺀 거래 수. 승인 금액이 0원인 거래도 센다.
 */
public record CardTotal(Long userCardId, Long netAmount, Long transactionCount) {

}
