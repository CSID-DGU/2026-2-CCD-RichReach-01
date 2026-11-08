package com.richreach.transaction.entity;

/** 거래의 취소·환불 상태. 취소·환불은 새 거래를 만들지 않고 원거래의 상태를 바꾼다. */
public enum TransactionStatus {

    /** 취소·환불 없이 승인된 거래 */
    APPROVED,
    /** 승인 금액의 일부만 취소·환불된 거래 */
    PARTIALLY_CANCELED,
    /** 승인 금액 전부가 취소·환불된 거래 */
    CANCELED;

    /** 승인 금액과 취소·환불 금액으로 상태를 정한다. 취소 금액은 0 이상, 승인 금액 이하로 본다. */
    public static TransactionStatus of(long amount, long canceledAmount) {
        if (canceledAmount <= 0) {
            return APPROVED;
        }
        return canceledAmount >= amount ? CANCELED : PARTIALLY_CANCELED;
    }

}
