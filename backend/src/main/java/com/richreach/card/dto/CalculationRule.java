package com.richreach.card.dto;

import com.richreach.global.category.SpendingCategory;
import java.math.BigDecimal;
import java.util.Set;

/**
 * 혜택 계산에 필요한 규칙 한 개. 카드 도메인의 혜택 규칙과 전월실적 구간을 계산하는 쪽이 쓰기 좋게 합친 값이다.
 * 혜택 업종 코드는 화면용 업종으로 이미 바꿔서 담는다. 값이 null이면 미확정 또는 해당 없음이다.
 *
 * @param categories                 혜택이 적용되는 화면용 업종
 * @param allMerchants               업종과 상관없이 모든 소비에 적용되는지
 * @param merchantSpecific           가맹점 또는 결제수단이 지정돼 월간 업종 합계로 판정할 수 없는지
 * @param ratePercent                혜택 비율(%). 비율 혜택이 아니면 null
 * @param rewardUnit                 KRW, POINT, MILE
 * @param monthlyLimit               월 혜택 한도
 * @param capGroupId                 한도를 함께 쓰는 묶음 ID
 * @param sharedMonthlyLimit         묶음의 공유 월 한도
 * @param dailyLimit                 일 한도
 * @param dailyCountLimit            일 횟수 한도
 * @param monthlyCountLimit          월 횟수 한도
 * @param annualCountLimit           연 횟수 한도
 * @param eligibleSpendMonthlyLimit  월 할인대상 결제금액 한도
 * @param ruleMode                   BASE, ADDITIONAL, REPLACES_BASE, PREFER_HIGHER, ANNUAL_BONUS
 * @param previousMonthMinKrw        전월실적 하한(이상)
 * @param previousMonthMaxExclusiveKrw 전월실적 상한(미만)
 * @param ruleEngineReady            사람이 원문과 대조해 확인한 규칙인지
 * @param perTransactionMinKrw       건별 최소 결제금액. 월간 업종 합계 계산에서는 이 규칙을 제외한다.
 */
public record CalculationRule(
    Long id,
    Set<SpendingCategory> categories,
    boolean allMerchants,
    boolean merchantSpecific,
    BigDecimal ratePercent,
    String rewardUnit,
    Long monthlyLimit,
    String capGroupId,
    Long sharedMonthlyLimit,
    Long dailyLimit,
    Integer dailyCountLimit,
    Integer monthlyCountLimit,
    Integer annualCountLimit,
    Long eligibleSpendMonthlyLimit,
    String ruleMode,
    Long previousMonthMinKrw,
    Long previousMonthMaxExclusiveKrw,
    boolean ruleEngineReady,
    Long perTransactionMinKrw) {

    /** 기존 계산기 테스트와 미리보기 코드가 쓰는 생성자. */
    public CalculationRule(Long id, Set<SpendingCategory> categories, boolean allMerchants,
                           boolean merchantSpecific, BigDecimal ratePercent, String rewardUnit,
                           Long monthlyLimit, String capGroupId, Long sharedMonthlyLimit, Long dailyLimit,
                           Integer dailyCountLimit, Integer monthlyCountLimit, Integer annualCountLimit,
                           Long eligibleSpendMonthlyLimit, String ruleMode, Long previousMonthMinKrw,
                           Long previousMonthMaxExclusiveKrw, boolean ruleEngineReady) {
        this(id, categories, allMerchants, merchantSpecific, ratePercent, rewardUnit, monthlyLimit,
            capGroupId, sharedMonthlyLimit, dailyLimit, dailyCountLimit, monthlyCountLimit,
            annualCountLimit, eligibleSpendMonthlyLimit, ruleMode, previousMonthMinKrw,
            previousMonthMaxExclusiveKrw, ruleEngineReady, null);
    }

}
