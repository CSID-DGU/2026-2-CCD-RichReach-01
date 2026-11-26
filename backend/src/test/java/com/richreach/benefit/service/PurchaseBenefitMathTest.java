package com.richreach.benefit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.benefit.dto.BenefitPurchase;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.card.dto.CalculationRule;
import com.richreach.global.category.SpendingCategory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PurchaseBenefitMathTest {

    private static CalculationRule rule(long id, int percent, Long min, Long cap, String group, Long shared,
                                        boolean merchantSpecific) {
        return new CalculationRule(id, Set.of(SpendingCategory.CAFE), false, merchantSpecific,
            BigDecimal.valueOf(percent), "KRW", cap, group, shared, null, null, null, null, null,
            "BASE", null, null, true, min);
    }

    private static BenefitPurchase purchase(long id, long amount, int day, boolean identified) {
        return new BenefitPurchase(id, LocalDateTime.of(2026, 11, day, 12, 0), SpendingCategory.CAFE,
            amount, identified);
    }

    @Test
    void transactionMinimumUsesTheCurrentPurchaseNotTheMonthlySum() {
        CalculationRule rule = rule(1, 20, 10_000L, 4_000L, null, null, false);
        Map<Long, BenefitResult> result = PurchaseBenefitMath.calculate(List.of(rule), List.of(
            purchase(1, 9_999, 1, true), purchase(2, 10_000, 2, true), purchase(3, 20_000, 3, true)), 0, true);
        assertThat(result.get(1L).totalBenefit()).isZero();
        assertThat(result.get(2L).totalBenefit()).isEqualTo(2_000);
        assertThat(result.get(3L).totalBenefit()).isEqualTo(2_000); // 월 한도 4천 원의 잔액
        assertThat(PurchaseBenefitMath.calculate(List.of(rule), List.of(purchase(3, 20_000, 3, true)), 0, true)
            .get(3L).totalBenefit()).isEqualTo(4_000);
    }

    @Test
    void perPurchaseCeilingIsSeparateFromTheMonthlyWallet() {
        CalculationRule rule = new CalculationRule(1L, Set.of(SpendingCategory.CAFE), false, false,
            BigDecimal.valueOf(20), "KRW", null, null, null, null, null, null, null, null,
            "BASE", null, null, true, 10_000L, 4_000L);
        Map<Long, BenefitResult> result = PurchaseBenefitMath.calculate(List.of(rule), List.of(
            purchase(1, 10_000, 1, true), purchase(2, 50_000, 2, true)), 0, true);
        assertThat(result.get(1L).totalBenefit()).isEqualTo(2_000);
        assertThat(result.get(2L).totalBenefit()).isEqualTo(4_000);
    }

    @Test
    void sharedCapIsOneWalletAndRefundReplaysIt() {
        List<CalculationRule> rules = List.of(
            rule(1, 20, null, null, "CAFE", 5_000L, false),
            rule(2, 10, null, null, "CAFE", 5_000L, false));
        List<BenefitPurchase> purchases = List.of(purchase(1, 10_000, 1, true),
            purchase(2, 10_000, 2, true), purchase(3, 10_000, 3, true));
        Map<Long, BenefitResult> result = PurchaseBenefitMath.calculate(rules, purchases, 0, true);
        assertThat(result.values().stream().mapToLong(BenefitResult::totalBenefit).sum()).isEqualTo(5_000);
        assertThat(result.get(3L).totalBenefit()).isZero();
        Map<Long, BenefitResult> refunded = PurchaseBenefitMath.calculate(rules, List.of(
            purchase(1, 0, 1, true), purchase(2, 10_000, 2, true), purchase(3, 10_000, 3, true)), 0, true);
        assertThat(refunded.get(3L).totalBenefit()).isPositive();
    }

    @Test
    void unidentifiedAndPaymentRouteRulesDoNotCreateBenefit() {
        List<CalculationRule> rules = List.of(rule(1, 20, null, null, null, null, false),
            rule(2, 50, null, null, null, null, true));
        Map<Long, BenefitResult> result = PurchaseBenefitMath.calculate(rules, List.of(
            purchase(1, 10_000, 1, false), purchase(2, 10_000, 2, true)), 0, true);
        assertThat(result.get(1L).totalBenefit()).isZero();
        assertThat(result.get(2L).totalBenefit()).isEqualTo(2_000);
    }
}
