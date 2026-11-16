package com.richreach.transaction.dto;

import com.richreach.codef.entity.CardCompany;
import com.richreach.global.category.SpendingCategory;
import java.time.LocalDate;
import java.util.List;

/**
 * 기간별 또는 월별 거래 요약. 기간은 승인일 기준이고, 금액은 취소·환불을 뺀 실제 결제 금액(원)이다.
 *
 * @param period           기간 조회의 기간 (1M, 3M, 6M). 월 조회({@code month})면 null
 * @param month            월 조회의 월({@code yyyy-MM}). 기간 조회({@code period})면 null
 * @param from             기간 시작일 (포함). 월 조회면 그달 1일
 * @param to               기간 종료일 (포함). 기간 조회는 오늘, 월 조회는 말일이고 이번 달이면 오늘
 * @param collectedFrom    거래를 수집한 기간의 시작일. 모든 카드사 계정에 수집 기록이 있으면 그 중 가장 늦은 시작일이고,
 *                         기록이 없는 계정이 있으면 가장 오래된 거래의 일자다. 둘 다 없으면 null
 * @param partial          수집한 기간이 요청한 기간을 다 채우지 못했는지. {@code collectedFrom}이 기간 시작일보다 늦으면 true다.
 *                         수집 기록이 있으면 소비가 없던 기간과 수집하지 못한 기간을 구분하지만, 기록이 없는 계정이 있으면
 *                         가장 오래된 거래로 추정하므로 참고용이다.
 * @param totalAmount      기간 합계. 취소·환불을 뺀 금액
 * @param canceledAmount   기간 안에 승인된 거래 중 취소·환불된 금액. 취소가 일어난 날이 아니라 원거래의 승인일로 센다.
 *                         (기간 전에 승인한 거래를 기간 안에 환불해도 여기에 들어가지 않는다.)
 * @param transactionCount 거래 수. 전액 취소된 거래는 세지 않는다.
 * @param benefitAmount    월 조회({@code month})일 때 그달 거래의 <b>예상</b> 혜택 합계(원). 카드사가 실제로 지급한 금액이 아니라
 *                         카드 혜택 규칙으로 계산한 추정값이고, 합계와 같은 카드 범위({@code cardScope})의 거래를 본다. 거래 목록의
 *                         {@code benefitAmount}를 더한 값과 같다. 기간 조회({@code period})이거나 계산할 수 있는 카드가 없으면 null이다.
 * @param categories       화면용 11개 업종별 소비. 거래가 없는 업종도 0으로 포함하며 항상 같은 순서다.
 * @param cards            보유카드(활성 카드 전체)별 같은 기간의 거래 건수와 금액. 카드 범위({@code cardScope})와 상관없이
 *                         모든 활성 카드를 {@code selected} 표시와 함께 내려주고, 거래가 없는 카드는 0이다.
 *                         합계와 업종은 카드 범위를 따르지만 이 목록은 따르지 않는다.
 */
public record TransactionSummaryResponse(
    String period,
    String month,
    LocalDate from,
    LocalDate to,
    LocalDate collectedFrom,
    boolean partial,
    long totalAmount,
    long canceledAmount,
    long transactionCount,
    Long benefitAmount,
    List<CategorySummary> categories,
    List<CardSummary> cards) {

    /** 예상 혜택 합계를 채운 같은 요약. */
    public TransactionSummaryResponse withBenefitAmount(Long benefit) {
        return new TransactionSummaryResponse(period, month, from, to, collectedFrom, partial, totalAmount, canceledAmount,
            transactionCount, benefit, categories, cards);
    }

    /**
     * 업종 하나의 소비.
     *
     * @param category         화면용 업종 코드
     * @param amount           취소·환불을 뺀 금액
     * @param ratio            전체 합계에서 차지하는 비율 (0~1, 소수 넷째 자리까지). 합계가 0이면 0
     * @param transactionCount 거래 수. 전액 취소된 거래는 세지 않는다.
     */
    public record CategorySummary(SpendingCategory category, long amount, double ratio, long transactionCount) {

    }

    /**
     * 보유카드 한 장의 소비.
     *
     * @param cardId           보유카드 ID
     * @param cardCompany      카드사
     * @param cardName         카드명
     * @param maskedNumber     카드 번호의 마지막 4자리. 알 수 없으면 빈 문자열
     * @param selected         분석 대상인지
     * @param amount           취소·환불을 뺀 금액
     * @param transactionCount 거래 수. 전액 취소된 거래는 세지 않는다.
     */
    public record CardSummary(Long cardId, CardCompany cardCompany, String cardName, String maskedNumber,
                              boolean selected, long amount, long transactionCount) {

    }

}
