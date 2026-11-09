package com.richreach.plan.dto;

import com.richreach.global.category.SpendingCategory;
import java.util.List;

/**
 * 소비 패턴 분석과 월 예산 추천. 분석 대상 카드의 거래만 보고, 금액은 취소·환불을 뺀 실제 결제 금액(원)이다.
 *
 * @param analyzedMonths 분석에 쓴 개월 수(정수). 수집된 거래가 요청한 기간보다 짧으면({@code partial}) 수집된 기간만큼으로
 *                       줄어들고 반올림한다. (최소 1)
 * @param monthlyAverage 한 달 평균 소비(원). 기간 합계를 <b>응답의 {@code analyzedMonths}로</b> 나눠 원 단위로 반올림한 값
 * @param topCategory    소비가 가장 큰 업종. 기타(ETC)는 뺀다. 같으면 앞 업종이고, 소비가 있는 업종이 기타뿐이거나 소비가 없으면 null
 * @param hasData        분석할 소비가 있는지. false이면 비율과 예산이 모두 0이고 플랜을 만들 수 없다.
 * @param partial        수집된 거래가 요청한 기간을 다 채우지 못했는지 (거래 요약의 {@code partial}과 같은 기준).
 *                       가장 오래된 거래의 날짜로 판단하므로, 소비가 없던 기간과 수집하지 못한 기간을 구분하지 못한다.
 *                       true이면 {@code monthlyAverage}와 예산은 참고용이다.
 * @param categoryRatios 11개 업종의 소비 비율. 항상 같은 순서이고 {@code ratio}(정수 %)의 합은 소비가 있으면 100이다.
 * @param suggestedBudget 추천 예산. 업종별 금액의 합은 항상 총예산과 같다.
 */
public record PlanAnalysisResponse(
    int analyzedMonths,
    long monthlyAverage,
    SpendingCategory topCategory,
    boolean hasData,
    boolean partial,
    List<CategoryRatio> categoryRatios,
    SuggestedBudget suggestedBudget) {

    /**
     * 업종 하나의 소비 비율과 금액.
     *
     * @param ratio            소비 비율(정수 %)
     * @param amount           분석 기간 동안의 소비 금액(원). 취소·환불을 뺀 값
     * @param transactionCount 분석 기간 동안의 거래 건수. 전액 취소된 거래는 세지 않는다.
     * @param rank             소비 순위(1~11, 업종마다 다름). 금액이 큰 순서이고 같으면 건수가 많은 업종, 그것도 같으면 업종 순서가
     *                         앞선 업종이 앞선다. 기타(ETC)도 포함하고, 소비가 없는 업종은 뒤쪽 순위를 받는다.
     */
    public record CategoryRatio(SpendingCategory category, int ratio, long amount, long transactionCount, int rank) {

    }

    /**
     * 추천 예산.
     *
     * @param total      총예산(원). 월평균 소비를 1,000원 단위로 반올림한 값
     * @param byCategory 업종별 예산(원). 11개 업종이 항상 같은 순서로 들어 있고, 합은 {@code total}과 같다.
     */
    public record SuggestedBudget(long total, List<CategoryAmount> byCategory) {

    }

    /** 업종 하나의 예산(원). */
    public record CategoryAmount(SpendingCategory category, long amount) {

    }

}
