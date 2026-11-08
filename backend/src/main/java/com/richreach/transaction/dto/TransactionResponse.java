package com.richreach.transaction.dto;

import com.richreach.global.category.SpendingCategory;
import com.richreach.transaction.entity.CardTransaction;
import com.richreach.transaction.entity.TransactionStatus;
import java.time.LocalDateTime;

/**
 * 거래 한 건. 목록, 상세, 카테고리 수정 응답이 같은 모양을 쓴다. 금액은 원 단위다.
 * 카드 번호와 승인번호는 내려주지 않는다. 카드는 보유카드 ID({@code cardId})로만 알려준다.
 *
 * @param transactionId    거래 ID
 * @param cardId           거래가 발생한 보유카드 ID
 * @param approvedAt       승인일시
 * @param merchantName     가맹점명. 없으면 빈 문자열
 * @param amount           승인 금액
 * @param canceledAmount   취소·환불된 금액
 * @param netAmount        취소·환불을 뺀 실제 결제 금액
 * @param status           취소 상태 (APPROVED, PARTIALLY_CANCELED, CANCELED)
 * @param category         화면용 업종. 사용자가 고친 값이 있으면 그 값, 없으면 자동 분류 값이고 분류 전이거나 6개 업종이 아니면 ETC
 * @param autoCategory     자동 분류 값. 사용자가 고친 값과 상관없이 시스템이 분류한 업종 (분류 전이거나 6개 업종이 아니면 ETC)
 * @param categoryEdited   사용자가 업종을 고쳤는지. true이면 {@code category}는 고친 값이다.
 * @param installmentMonths 할부 개월. 0이면 일시불
 * @param overseas         해외 결제 여부
 */
public record TransactionResponse(
    Long transactionId,
    Long cardId,
    LocalDateTime approvedAt,
    String merchantName,
    long amount,
    long canceledAmount,
    long netAmount,
    TransactionStatus status,
    SpendingCategory category,
    SpendingCategory autoCategory,
    boolean categoryEdited,
    int installmentMonths,
    boolean overseas) {

    public static TransactionResponse from(CardTransaction transaction) {
        return new TransactionResponse(
            transaction.getId(),
            transaction.getUserCardId(),
            transaction.getApprovedAt(),
            transaction.getMerchantName(),
            transaction.getAmount(),
            transaction.getCanceledAmount(),
            transaction.netAmount(),
            transaction.getStatus(),
            SpendingCategory.fromCode(transaction.effectiveCategory()).orElse(SpendingCategory.ETC),
            SpendingCategory.fromCode(transaction.getCategory()).orElse(SpendingCategory.ETC),
            transaction.getCategoryOverride() != null,
            transaction.getInstallmentMonths(),
            transaction.isOverseas());
    }

}
