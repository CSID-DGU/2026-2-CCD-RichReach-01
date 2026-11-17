package com.richreach.plan.dto;

import com.richreach.global.category.SpendingCategory;
import java.util.List;

/**
 * 월간 플랜의 카드별 상세(PLAN-02). 카드마다 목표와 이번 달 사용 금액, 전월실적 구간, 월 혜택 한도, 업종별 사용 금액을 담는다.
 * 금액은 모두 원 단위 정수다.
 *
 * <p><b>{@code null}은 "알 수 없음"이다.</b> 0원이나 "한도 없음"으로 보여주면 안 된다. 카드 상품과 매칭되지 않았거나 혜택 규칙이
 * 없으면 구간과 한도, 혜택이 비어 있다. 실적과 한도, 혜택은 카드의 혜택 규칙으로 계산한 <b>추정값</b>이고 카드사가 알려주는 값이
 * 아니다.
 *
 * @param planId 플랜 ID
 * @param month  플랜 대상 월({@code yyyy-MM})
 * @param cards  플랜에 배분이 있는 카드. 목표 금액이 큰 카드부터이다.
 */
public record PlanCardTargetsResponse(long planId, String month, List<CardDetail> cards) {

    /**
     * 카드 한 장의 상세.
     *
     * @param cardId             보유카드 ID
     * @param cardName           카드 이름. 카드가 삭제되어 찾을 수 없으면 null
     * @param targetAmount       이번 달 목표 사용 금액(업종별 배분의 합)
     * @param spentAmount        이번 달 사용 금액. 취소·환불을 뺀 값이다.
     * @param remainingTarget    남은 목표 금액({@code max(0, targetAmount - spentAmount)})
     * @param expectedBenefit    플랜대로 쓸 때 기대하는 예상 혜택
     * @param currentBenefit     지금까지 쓴 금액만으로 계산한 예상 혜택. 카드 상품과 매칭되지 않았으면 null
     * @param performance        전월실적 구간과 이번 달 실적
     * @param benefitLimit       월 혜택 한도
     * @param categories         업종별 목표와 사용 금액. 목표가 있거나 이번 달에 쓴 업종이고 업종 고정 순서이다.
     */
    public record CardDetail(
        long cardId,
        String cardName,
        long targetAmount,
        long spentAmount,
        long remainingTarget,
        long expectedBenefit,
        Long currentBenefit,
        Performance performance,
        BenefitLimit benefitLimit,
        List<CategoryAmount> categories) {

    }

    /**
     * 전월실적 구간과 이번 달 실적. 구간은 혜택 규칙의 전월실적 조건에서 모은 값이라 혜택마다 다른 구간이 섞여 있을 수 있다.
     *
     * @param tiers                  전월실적 구간 하한(원) 목록, 작은 것부터. 구간이 없으면 빈 목록
     * @param previousMonthSpend     전월 사용 금액. 이번 달 혜택의 구간을 정하는 값이다.
     * @param appliedTier            전월 사용 금액이 채운 가장 높은 구간 하한. 하나도 못 채웠거나 구간이 없으면 null
     * @param currentMonthAmount     이번 달 사용 금액. 다음 달 혜택의 구간을 정하는 값이다.
     * @param currentMonthAmountExact {@code currentMonthAmount}가 카드사의 실적 인정 금액과 같다고 볼 수 있는지.
     *                               실적 제외 조건(상품권, 세금 등)이 문장으로만 있어 금액에서 빼지 못하므로 항상 false이다.
     * @param achievedTier           이번 달 사용 금액이 채운 가장 높은 구간 하한. 없으면 null
     * @param nextTier               이번 달 사용 금액으로 아직 채우지 못한 가장 낮은 구간 하한. 모두 채웠거나 구간이 없으면 null
     * @param shortfallToNextTier    {@code nextTier}까지 남은 금액. {@code nextTier}가 null이면 null
     * @param exclusionNotes         전월실적 산정에서 제외되는 조건(원문 문장). 실제 제외 금액은 반영되어 있지 않다.
     */
    public record Performance(
        List<Long> tiers,
        long previousMonthSpend,
        Long appliedTier,
        long currentMonthAmount,
        boolean currentMonthAmountExact,
        Long achievedTier,
        Long nextTier,
        Long shortfallToNextTier,
        List<String> exclusionNotes) {

    }

    /**
     * 월 혜택 한도. 전월실적 조건을 채운 혜택들의 월 한도를 합친 추정값이다.
     *
     * @param monthlyLimit    월 혜택 한도 합. 한도가 없거나 미확인인 혜택이 있거나 규칙이 없으면 null
     * @param usedAmount      지금까지 쓴 금액으로 받는 예상 혜택({@code currentBenefit}). 알 수 없으면 null
     * @param remainingAmount 남은 한도({@code max(0, monthlyLimit - usedAmount)}). 둘 중 하나라도 null이면 null
     */
    public record BenefitLimit(Long monthlyLimit, Long usedAmount, Long remainingAmount) {

    }

    /**
     * 카드 안에서 업종 하나의 목표와 사용 금액.
     *
     * @param category        업종
     * @param targetAmount    이 카드로 이 업종에서 쓸 목표 금액. 목표가 없는 업종이면 0
     * @param spentAmount     이번 달 이 카드로 이 업종에서 쓴 금액(취소·환불 제외)
     * @param expectedBenefit 목표대로 쓸 때 기대하는 혜택. 목표가 없는 업종이면 0
     */
    public record CategoryAmount(SpendingCategory category, long targetAmount, long spentAmount, long expectedBenefit) {

    }

}
