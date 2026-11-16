package com.richreach.transaction.repository;

/**
 * 거래 한 건의 업종과 취소·환불을 뺀 금액. 혜택을 거래별로 나눌 때 쓴다.
 *
 * @param id       거래 ID
 * @param category 업종. 사용자가 고친 값이 있으면 그 값, 없으면 자동 분류 값(분류 전이면 null)
 * @param netAmount 승인 금액에서 취소·환불을 뺀 금액
 */
public record TransactionNet(Long id, String category, long netAmount) {

}
