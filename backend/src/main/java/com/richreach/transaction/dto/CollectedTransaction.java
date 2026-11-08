package com.richreach.transaction.dto;

import java.time.LocalDateTime;

/**
 * 수집한 거래 한 건. 카드사 응답을 이 모양으로 바꿔서 거래 저장에 넘긴다.
 * (카드사 응답의 필드 이름과 구조는 이 모양을 만드는 쪽에서만 알면 된다.)
 *
 * <p>가맹점명과 금액은 소비 정보라서 {@code toString}에서 가린다.
 *
 * @param approvalNo        승인번호. 없으면 null이나 빈 문자열
 * @param approvedAt        승인일시. 시각 없이 일자만 있으면 그 날 00:00:00. 없으면 저장하지 않는다.
 * @param merchantName      가맹점명. 없으면 null이나 빈 문자열
 * @param amount            승인 금액(원). 0 이상이어야 하고, 음수면 저장하지 않는다.
 * @param canceledAmount    취소·환불된 금액(원). 취소가 없으면 0. 승인 금액보다 크면 승인 금액으로 본다.
 * @param installmentMonths 할부 개월. 일시불이면 0
 * @param overseas          해외 결제 여부
 * @param category          자동 분류한 업종 카테고리. 아직 분류하지 못했으면 null
 */
public record CollectedTransaction(String approvalNo, LocalDateTime approvedAt, String merchantName, long amount,
                                   long canceledAmount, int installmentMonths, boolean overseas, String category) {

    @Override
    public String toString() {
        return "CollectedTransaction[****]";
    }

}
