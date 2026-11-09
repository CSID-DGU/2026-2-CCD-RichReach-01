package com.richreach.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.richreach.global.category.SpendingCategory;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 소비 비율과 예산 추천의 계산식을 DB 없이 확인한다. 합계가 항상 맞는지와 같은 입력이면 같은 결과인지가 핵심이다. */
class SpendingAnalysisCalculatorTest {

    private static Map<SpendingCategory, Long> amounts(Object... pairs) {
        Map<SpendingCategory, Long> map = new EnumMap<>(SpendingCategory.class);
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((SpendingCategory) pairs[i], ((Number) pairs[i + 1]).longValue());
        }
        return map;
    }

    private static long sum(Map<SpendingCategory, Long> values) {
        return values.values().stream().mapToLong(Long::longValue).sum();
    }

    // ---- 비율 ----

    @Test
    @DisplayName("비율은 11개 업종을 모두 담고 합이 100이다")
    void ratiosCoverEveryCategoryAndSumToHundred() {
        Map<SpendingCategory, Integer> ratios = SpendingAnalysisCalculator.ratios(amounts(
            SpendingCategory.DINING, 230_000, SpendingCategory.CAFE, 65_600, SpendingCategory.SHOPPING, 180_000,
            SpendingCategory.TRANSPORT, 120_000, SpendingCategory.ETC, 130_000));

        assertThat(ratios).hasSize(SpendingCategory.values().length);
        assertThat(ratios.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(100);
        assertThat(ratios.get(SpendingCategory.FUEL_VEHICLE)).isZero();
    }

    @Test
    @DisplayName("1/3씩이면 34, 33, 33이고 남는 1은 업종 순서가 앞선 쪽이 받는다")
    void ratiosHandleThirds() {
        Map<SpendingCategory, Integer> ratios = SpendingAnalysisCalculator.ratios(amounts(
            SpendingCategory.DINING, 100, SpendingCategory.CAFE, 100, SpendingCategory.SHOPPING, 100));

        assertThat(ratios.get(SpendingCategory.DINING)).isEqualTo(34);
        assertThat(ratios.get(SpendingCategory.CAFE)).isEqualTo(33);
        assertThat(ratios.get(SpendingCategory.SHOPPING)).isEqualTo(33);
    }

    @Test
    @DisplayName("남는 몫은 소수 부분이 큰 업종이 먼저 받는다")
    void ratiosGiveLeftoverToLargestRemainder() {
        // 정확한 비율은 DINING 50.5%, CAFE 49.5%이다. 소수 부분은 같아서(0.5) 비중이 큰 DINING이 먼저 받는다.
        Map<SpendingCategory, Integer> ratios = SpendingAnalysisCalculator.ratios(amounts(
            SpendingCategory.CAFE, 495, SpendingCategory.DINING, 505));

        assertThat(ratios.get(SpendingCategory.DINING)).isEqualTo(51);
        assertThat(ratios.get(SpendingCategory.CAFE)).isEqualTo(49);
    }

    @Test
    @DisplayName("소비가 없으면 비율은 모두 0이다")
    void ratiosAreZeroWithoutSpending() {
        assertThat(SpendingAnalysisCalculator.ratios(Map.of()).values()).containsOnly(0);
        assertThat(SpendingAnalysisCalculator.ratios(amounts(SpendingCategory.CAFE, 0)).values()).containsOnly(0);
    }

    @Test
    @DisplayName("한 업종에만 소비가 있으면 그 업종이 100이다")
    void singleCategoryIsHundred() {
        Map<SpendingCategory, Integer> ratios = SpendingAnalysisCalculator.ratios(amounts(SpendingCategory.EDUCATION, 1));

        assertThat(ratios.get(SpendingCategory.EDUCATION)).isEqualTo(100);
    }

    // ---- 총예산 ----

    @Test
    @DisplayName("총예산은 월평균을 1,000원 단위로 반올림한다 (500원은 올린다)")
    void roundsBudgetTotal() {
        assertThat(SpendingAnalysisCalculator.roundBudgetTotal(820_000)).isEqualTo(820_000);
        assertThat(SpendingAnalysisCalculator.roundBudgetTotal(820_499)).isEqualTo(820_000);
        assertThat(SpendingAnalysisCalculator.roundBudgetTotal(820_500)).isEqualTo(821_000);
        assertThat(SpendingAnalysisCalculator.roundBudgetTotal(499)).isZero();
        assertThat(SpendingAnalysisCalculator.roundBudgetTotal(500)).isEqualTo(1_000);
        assertThat(SpendingAnalysisCalculator.roundBudgetTotal(0)).isZero();
        assertThat(SpendingAnalysisCalculator.roundBudgetTotal(-5)).isZero();
    }

    // ---- 업종 예산 ----

    @Test
    @DisplayName("업종 예산은 1,000원의 배수이고 합이 총예산과 정확히 같다")
    void budgetSumsToTotalInThousands() {
        Map<SpendingCategory, Long> spend = amounts(
            SpendingCategory.DINING, 1_234_567, SpendingCategory.CAFE, 87_654, SpendingCategory.SHOPPING, 456_789,
            SpendingCategory.TRANSPORT, 99_999, SpendingCategory.CONVENIENCE_STORE, 12_345,
            SpendingCategory.TELECOM_SUBSCRIPTION, 55_000, SpendingCategory.ETC, 3);

        Map<SpendingCategory, Long> budget = SpendingAnalysisCalculator.budgetByCategory(820_000, spend);

        assertThat(sum(budget)).isEqualTo(820_000);
        assertThat(budget).hasSize(SpendingCategory.values().length);
        assertThat(budget.values()).allSatisfy(amount -> assertThat(amount % 1_000).isZero());
    }

    @Test
    @DisplayName("소비가 없는 업종의 예산은 0이다")
    void budgetIsZeroWhereThereIsNoSpending() {
        Map<SpendingCategory, Long> budget = SpendingAnalysisCalculator.budgetByCategory(100_000,
            amounts(SpendingCategory.DINING, 3, SpendingCategory.CAFE, 1));

        assertThat(budget.get(SpendingCategory.DINING)).isEqualTo(75_000);
        assertThat(budget.get(SpendingCategory.CAFE)).isEqualTo(25_000);
        assertThat(budget.get(SpendingCategory.SHOPPING)).isZero();
    }

    @Test
    @DisplayName("1,000원으로 나눠 떨어지지 않는 몫은 소수 부분이 큰 업종부터 1,000원씩 받는다")
    void budgetLeftoverGoesToLargestRemainder() {
        // 3,000원을 1 : 1 : 1 로 나누면 1,000원씩이다. 2,000원을 1 : 1 : 1 로 나누면 1,000원이 남아 업종 순서가 앞선 쪽이 받는다.
        Map<SpendingCategory, Long> even = amounts(
            SpendingCategory.DINING, 5, SpendingCategory.CAFE, 5, SpendingCategory.SHOPPING, 5);

        assertThat(SpendingAnalysisCalculator.budgetByCategory(3_000, even)).containsEntry(SpendingCategory.DINING, 1_000L)
            .containsEntry(SpendingCategory.CAFE, 1_000L).containsEntry(SpendingCategory.SHOPPING, 1_000L);
        Map<SpendingCategory, Long> twoThousand = SpendingAnalysisCalculator.budgetByCategory(2_000, even);
        assertThat(sum(twoThousand)).isEqualTo(2_000);
        assertThat(twoThousand.get(SpendingCategory.DINING)).isEqualTo(1_000);
        assertThat(twoThousand.get(SpendingCategory.CAFE)).isEqualTo(1_000);
        assertThat(twoThousand.get(SpendingCategory.SHOPPING)).isZero();
    }

    @Test
    @DisplayName("같은 입력이면 항상 같은 결과다 (입력 순서와 상관없이)")
    void isDeterministic() {
        Map<SpendingCategory, Long> first = amounts(
            SpendingCategory.DINING, 7, SpendingCategory.CAFE, 7, SpendingCategory.SHOPPING, 7,
            SpendingCategory.EDUCATION, 7);
        Map<SpendingCategory, Long> reversed = amounts(
            SpendingCategory.EDUCATION, 7, SpendingCategory.SHOPPING, 7, SpendingCategory.CAFE, 7,
            SpendingCategory.DINING, 7);

        assertThat(SpendingAnalysisCalculator.budgetByCategory(10_000, first))
            .isEqualTo(SpendingAnalysisCalculator.budgetByCategory(10_000, reversed));
        assertThat(SpendingAnalysisCalculator.ratios(first)).isEqualTo(SpendingAnalysisCalculator.ratios(reversed));
    }

    @Test
    @DisplayName("총예산이 0이거나 소비가 없으면 예산은 모두 0이다")
    void budgetIsZeroWithoutTotalOrSpending() {
        assertThat(SpendingAnalysisCalculator.budgetByCategory(0, amounts(SpendingCategory.DINING, 5)).values())
            .containsOnly(0L);
        assertThat(SpendingAnalysisCalculator.budgetByCategory(50_000, Map.of()).values()).containsOnly(0L);
    }

    @Test
    @DisplayName("총예산이 1,000원의 배수가 아니면 거부한다")
    void rejectsTotalThatIsNotAMultipleOfUnit() {
        assertThatThrownBy(() -> SpendingAnalysisCalculator.budgetByCategory(1_500, amounts(SpendingCategory.CAFE, 1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- 가장 큰 업종 ----

    @Test
    @DisplayName("소비가 가장 큰 업종을 찾고, 같으면 앞 업종이며, 소비가 없으면 null이다")
    void topCategory() {
        assertThat(SpendingAnalysisCalculator.topCategory(amounts(
            SpendingCategory.CAFE, 10, SpendingCategory.DINING, 30, SpendingCategory.SHOPPING, 20)))
            .isEqualTo(SpendingCategory.DINING);
        assertThat(SpendingAnalysisCalculator.topCategory(amounts(
            SpendingCategory.SHOPPING, 10, SpendingCategory.CAFE, 10))).isEqualTo(SpendingCategory.CAFE);
        assertThat(SpendingAnalysisCalculator.topCategory(Map.of())).isNull();
        assertThat(SpendingAnalysisCalculator.topCategory(amounts(SpendingCategory.CAFE, 0))).isNull();
    }

}
