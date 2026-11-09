package com.richreach.benefit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.benefit.dto.BenefitResult;
import com.richreach.benefit.service.BenefitMath.RuleInput;
import com.richreach.global.category.SpendingCategory;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 임시 혜택 계산식을 DB 없이 확인한다. */
class BenefitMathTest {

    private static final Map<SpendingCategory, Long> SPEND = Map.of(
        SpendingCategory.DINING, 300_000L, SpendingCategory.CAFE, 100_000L, SpendingCategory.SHOPPING, 200_000L);

    private static RuleInput rule(Set<SpendingCategory> categories, String ratePercent, Long monthlyLimit) {
        return new RuleInput("r", categories, false, false, ratePercent == null ? null : new BigDecimal(ratePercent),
            "KRW", monthlyLimit, null, null, "BASE", null, null);
    }

    private static RuleInput with(RuleInput base, String capGroup, Long shared, String mode, Long prevMin, Long prevMax,
                                  boolean merchantSpecific, String unit, boolean allMerchants) {
        return new RuleInput(base.id(), base.categories(), allMerchants, merchantSpecific, base.ratePercent(), unit,
            base.monthlyLimit(), capGroup, shared, mode, prevMin, prevMax);
    }

    private static BenefitResult compute(long previous, RuleInput... rules) {
        return BenefitMath.compute(List.of(rules), SPEND, previous, true);
    }

    @Test
    @DisplayName("해당 업종의 소비에 비율을 곱해서 혜택을 구한다")
    void appliesRate() {
        BenefitResult result = compute(0, rule(EnumSet.of(SpendingCategory.DINING), "5.0", null));

        assertThat(result.totalBenefit()).isEqualTo(15_000);
        assertThat(result.benefitByCategory()).containsOnly(Map.entry(SpendingCategory.DINING, 15_000L));
    }

    @Test
    @DisplayName("소비가 없는 업종의 혜택은 0이다")
    void noSpendNoBenefit() {
        BenefitResult result = compute(0, rule(EnumSet.of(SpendingCategory.TRANSPORT), "10", null));

        assertThat(result.totalBenefit()).isZero();
        assertThat(result.benefitByCategory()).isEmpty();
    }

    @Test
    @DisplayName("규칙별 월 한도를 넘지 않는다")
    void monthlyLimit() {
        BenefitResult result = compute(0, rule(EnumSet.of(SpendingCategory.DINING), "10", 20_000L));

        assertThat(result.totalBenefit()).isEqualTo(20_000);
    }

    @Test
    @DisplayName("한 혜택이 여러 업종에 걸치면 소비 비율대로 나누고, 한도도 같은 비율로 줄인다")
    void splitsAcrossCategories() {
        BenefitResult result = compute(0,
            rule(EnumSet.of(SpendingCategory.DINING, SpendingCategory.CAFE), "10", 20_000L));

        // 외식 300,000 : 카페 100,000 = 3 : 1 이고 혜택은 한도 20,000으로 잘린다.
        assertThat(result.benefitByCategory()).containsOnly(Map.entry(SpendingCategory.DINING, 15_000L),
            Map.entry(SpendingCategory.CAFE, 5_000L));
        assertThat(result.totalBenefit()).isEqualTo(20_000);
    }

    @Test
    @DisplayName("같은 한도 묶음의 혜택은 공유 한도를 함께 쓴다")
    void sharedLimit() {
        RuleInput dining = with(rule(EnumSet.of(SpendingCategory.DINING), "10", null), "G", 30_000L, "BASE", null,
            null, false, "KRW", false);
        RuleInput shopping = with(rule(EnumSet.of(SpendingCategory.SHOPPING), "10", null), "G", 30_000L, "BASE", null,
            null, false, "KRW", false);

        BenefitResult result = compute(0, dining, shopping);

        // 원래 30,000 + 20,000 = 50,000 이지만 공유 한도 30,000으로 줄어든다. (3 : 2 비율)
        assertThat(result.totalBenefit()).isEqualTo(30_000);
        assertThat(result.benefitByCategory()).containsOnly(Map.entry(SpendingCategory.DINING, 18_000L),
            Map.entry(SpendingCategory.SHOPPING, 12_000L));
    }

    @Test
    @DisplayName("전월실적 조건(하한 이상, 상한 미만)을 판단한다")
    void previousMonthTier() {
        RuleInput base = rule(EnumSet.of(SpendingCategory.DINING), "5", null);
        RuleInput needs300 = with(base, null, null, "BASE", 300_000L, null, false, "KRW", false);
        RuleInput below500 = with(base, null, null, "BASE", null, 500_000L, false, "KRW", false);

        assertThat(compute(299_999, needs300).totalBenefit()).isZero();
        assertThat(compute(299_999, needs300).notes()).hasSize(1);
        assertThat(compute(300_000, needs300).totalBenefit()).isEqualTo(15_000);
        assertThat(compute(499_999, below500).totalBenefit()).isEqualTo(15_000);
        assertThat(compute(500_000, below500).totalBenefit()).isZero();
    }

    @Test
    @DisplayName("전 가맹점 혜택은 모든 소비에 적용한다")
    void allMerchants() {
        RuleInput all = with(rule(EnumSet.noneOf(SpendingCategory.class), "1", null), null, null, "BASE", null, null,
            false, "KRW", true);

        assertThat(compute(0, all).totalBenefit()).isEqualTo(6_000);
    }

    @Test
    @DisplayName("계산하지 않는 혜택(가맹점 지정, 비율 없음, 마일리지, 관계를 따져야 하는 규칙)은 빼고 이유를 남긴다")
    void skipsUnsupportedRules() {
        RuleInput base = rule(EnumSet.of(SpendingCategory.DINING), "5", null);
        BenefitResult result = compute(0,
            with(base, null, null, "BASE", null, null, true, "KRW", false),
            rule(EnumSet.of(SpendingCategory.DINING), null, null),
            with(base, null, null, "BASE", null, null, false, "MILE", false),
            with(base, null, null, "REPLACES_BASE", null, null, false, "KRW", false),
            with(base, null, null, "PREFER_HIGHER", null, null, false, "KRW", false),
            with(base, null, null, "ANNUAL_BONUS", null, null, false, "KRW", false));

        assertThat(result.totalBenefit()).isZero();
        assertThat(result.notes()).hasSize(6);
    }

    @Test
    @DisplayName("BASE, ADDITIONAL, 값 없음 규칙은 서로 더하고, 포인트는 1점을 1원으로 본다")
    void addsBaseAndAdditional() {
        RuleInput base = rule(EnumSet.of(SpendingCategory.DINING), "2", null);
        BenefitResult result = compute(0,
            with(base, null, null, "BASE", null, null, false, "POINT", false),
            with(base, null, null, "ADDITIONAL", null, null, false, "KRW", false),
            with(base, null, null, null, null, null, false, null, false));

        assertThat(result.totalBenefit()).isEqualTo(18_000);
    }

    @Test
    @DisplayName("같은 이유로 뺀 규칙은 안내를 한 번만 남긴다")
    void dedupesNotes() {
        RuleInput base = rule(EnumSet.of(SpendingCategory.DINING), "5", null);
        RuleInput merchant = with(base, null, null, "BASE", null, null, true, "KRW", false);

        assertThat(compute(0, merchant, merchant, merchant).notes()).hasSize(1);
    }

    @Test
    @DisplayName("소비나 규칙이 없으면 혜택은 0이고, 결과 합계는 업종별 합과 같다")
    void emptyInputsAndConsistency() {
        assertThat(BenefitMath.compute(List.of(), SPEND, 0, true).totalBenefit()).isZero();
        assertThat(BenefitMath.compute(List.of(rule(EnumSet.of(SpendingCategory.DINING), "5", null)), Map.of(), 0, true)
            .totalBenefit()).isZero();

        BenefitResult result = compute(0, rule(EnumSet.of(SpendingCategory.DINING, SpendingCategory.CAFE,
            SpendingCategory.SHOPPING), "3.3", 7_777L));
        assertThat(result.totalBenefit())
            .isEqualTo(result.benefitByCategory().values().stream().mapToLong(Long::longValue).sum());
        assertThat(result.totalBenefit()).isLessThanOrEqualTo(7_777);
    }

    @Test
    @DisplayName("확인된 규칙만 썼는지(confirmedOnly)를 결과에 그대로 담는다")
    void keepsConfirmedFlag() {
        assertThat(BenefitMath.compute(List.of(), SPEND, 0, false).confirmedOnly()).isFalse();
        assertThat(BenefitMath.compute(List.of(), SPEND, 0, true).confirmedOnly()).isTrue();
    }

}
