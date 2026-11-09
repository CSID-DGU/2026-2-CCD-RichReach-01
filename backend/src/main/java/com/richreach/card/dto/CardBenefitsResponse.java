package com.richreach.card.dto;

import com.richreach.card.entity.BenefitRule;
import com.richreach.card.entity.CardProduct;
import com.richreach.card.entity.PerformanceExclusion;
import com.richreach.card.entity.PerformanceTier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 카드 상품의 기본 정보와 혜택.
 *
 * <p><b>빈칸은 {@code null}로 내려간다.</b> {@code null}은 "알 수 없음(원문에 없거나 추출하지 못함)"이지 0원, 무제한,
 * 조건 없음이 아니다. 화면은 {@code null}을 0이나 "무제한"으로 보여주면 안 된다. 한도가 있는지는 숫자가 아니라
 * {@link Benefit#monthlyLimitStatus}로 구분한다.
 *
 * <p>같은 혜택이 요약과 본문에서 반복 추출될 수 있어서, 혜택 목록을 그대로 합산하면 안 된다.
 *
 * @param cardId             카드 상품 ID ({@code card_products.id})
 * @param sourceSnapshotDate 혜택 정보의 원문 저장일. 카드사의 현재 상품 정보와 다를 수 있다.
 * @param recommendable      신규 추천 후보인지. false여도(발급 중단 등) 이미 가진 카드의 분석에는 쓴다.
 * @param exclusions         실적·혜택 제외 조건. 혜택이 아니라 카드 단위로만 연결되어 있다.
 */
public record CardBenefitsResponse(
    Long cardId,
    String issuerCode,
    String issuer,
    String cardName,
    String cardType,
    String cardStatus,
    boolean recommendable,
    LocalDate sourceSnapshotDate,
    List<Benefit> benefits,
    List<Exclusion> exclusions) {

    /**
     * 혜택 한 건. 긴 원문(혜택 조건 {@code conditions}, 적용 대상 원문 {@code targetRaw})은 담지 않는다.
     *
     * @param category              업종 코드. '|'로 여러 개가 묶여 있을 수 있다.
     * @param monthlyLimitStatus    NOT_STATED, NOT_EXTRACTED, LIMITED, SHARED_LIMIT, UNLIMITED 등. null이면 미확정이다.
     * @param includedInCalculation 사람이 원문과 대조해 확인한 규칙인지. false면 계산에 쓰면 안 되는 자동 추출값이다.
     * @param performance           실적 조건. 실적 조건이 없는(또는 추출되지 않은) 혜택이면 null이다.
     */
    public record Benefit(
        Long id,
        String benefitType,
        String category,
        String categoryName,
        String merchants,
        String targetsNormalized,
        String paymentMethods,
        BigDecimal ratePercent,
        BigDecimal rateMinPercent,
        BigDecimal rateMaxPercent,
        Long amount,
        String rewardUnit,
        String amountBasis,
        Long monthlyLimit,
        String monthlyLimitUnit,
        String monthlyLimitStatus,
        Long dailyLimit,
        Long sharedMonthlyLimit,
        String capGroupId,
        Long eligibleSpendMonthlyLimitKrw,
        Integer dailyCountLimit,
        Integer monthlyCountLimit,
        Integer annualCountLimit,
        String ruleMode,
        String reviewStatus,
        boolean includedInCalculation,
        PerformanceCondition performance) {

        static Benefit of(BenefitRule rule, PerformanceTier tier) {
            return new Benefit(rule.getId(), rule.getBenefitType(), rule.getCategory(), rule.getCategoryName(),
                rule.getMerchants(), rule.getTargetsNormalized(), rule.getPaymentMethods(), rule.getRatePercent(),
                rule.getRateMinPercent(), rule.getRateMaxPercent(), rule.getAmount(), rule.getRewardUnit(),
                rule.getAmountBasis(), rule.getMonthlyLimit(), rule.getMonthlyLimitUnit(),
                rule.getMonthlyLimitStatus(), rule.getDailyLimit(), rule.getSharedMonthlyLimit(),
                rule.getCapGroupId(), rule.getEligibleSpendMonthlyLimitKrw(), rule.getDailyCountLimit(),
                rule.getMonthlyCountLimit(), rule.getAnnualCountLimit(), rule.getRuleMode(), rule.getReviewStatus(),
                rule.isRuleEngineReady(), tier == null ? null : PerformanceCondition.of(tier));
        }

    }

    /**
     * 혜택이 적용되는 실적 조건. 하한은 이상, 상한은 미만이다. null은 조건 없음이 아니라 미확정이다.
     *
     * @param previousMonthRequirementStatus MINIMUM, NONE, NOT_STATED. 일부 카드에만 값이 있다.
     */
    public record PerformanceCondition(
        Long previousMonthMinKrw,
        Long previousMonthMaxExclusiveKrw,
        String previousMonthRequirementStatus,
        Long annualSpendMinKrw) {

        static PerformanceCondition of(PerformanceTier tier) {
            return new PerformanceCondition(tier.getPreviousMonthMinKrw(), tier.getPreviousMonthMaxExclusiveKrw(),
                tier.getPreviousMonthRequirementStatus(), tier.getAnnualSpendMinKrw());
        }

    }

    /**
     * 실적 산정이나 혜택에서 제외되는 대상 조건. 제외 조건은 원문 자체가 내용이라 원문 그대로 담는다.
     *
     * @param appliesTo PERFORMANCE(전월실적 산정에서 제외), BENEFIT(혜택에서 제외)
     */
    public record Exclusion(Long id, String appliesTo, String conditionType, String conditionText) {

        static Exclusion of(PerformanceExclusion exclusion) {
            return new Exclusion(exclusion.getId(), exclusion.getAppliesTo(), exclusion.getConditionType(),
                exclusion.getConditionText());
        }

    }

    public static CardBenefitsResponse of(CardProduct card, List<BenefitRule> rules, List<PerformanceTier> tiers,
                                          List<PerformanceExclusion> exclusions) {
        java.util.Map<Long, PerformanceTier> tierByRuleId = new java.util.HashMap<>();
        tiers.forEach(tier -> tierByRuleId.put(tier.getBenefitRule().getId(), tier));
        return new CardBenefitsResponse(card.getId(), card.getIssuerCode(), card.getIssuer(), card.getCardName(),
            card.getCardType(), card.getCardStatus(), card.isRecommendable(), card.getSourceSnapshotDate(),
            rules.stream().map(rule -> Benefit.of(rule, tierByRuleId.get(rule.getId()))).toList(),
            exclusions.stream().map(Exclusion::of).toList());
    }

}
