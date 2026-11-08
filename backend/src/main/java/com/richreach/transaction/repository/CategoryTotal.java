package com.richreach.transaction.repository;

/**
 * 기간 안의 거래를 업종별로 모은 합계(조회 결과). 업종은 사용자가 고친 값이 있으면 그 값, 없으면 자동 분류 값이고
 * 분류 전이면 null이다.
 *
 * @param category 사용자가 고친 값 또는 자동 분류 값. 분류 전이면 null
 * @param netAmount 취소·환불을 뺀 금액
 * @param canceledAmount 취소·환불된 금액
 * @param transactionCount 취소·환불을 빼고도 금액이 남은 거래 수
 */
public record CategoryTotal(String category, Long netAmount, Long canceledAmount, Long transactionCount) {

}
