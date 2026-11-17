package com.richreach.plan.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.service.PlannedExpenseBudgetOverlay.Item;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 예정지출을 확정 예산에 얹는 규칙을 DB 없이 확인한다. */
class PlannedExpenseBudgetOverlayTest {

    private static final YearMonth MONTH = YearMonth.of(2026, 11);

    private static PlanBudget budget(Object... pairs) {
        Map<SpendingCategory, Long> byCategory = new EnumMap<>(SpendingCategory.class);
        long total = 0;
        for (int i = 0; i < pairs.length; i += 2) {
            byCategory.put((SpendingCategory) pairs[i], (Long) pairs[i + 1]);
            total += (Long) pairs[i + 1];
        }
        return new PlanBudget(MONTH, total, byCategory);
    }

    @Test
    @DisplayName("예정지출이 없으면 예산을 그대로 돌려준다")
    void noItems() {
        PlanBudget base = budget(SpendingCategory.DINING, 300_000L);

        assertThat(PlannedExpenseBudgetOverlay.apply(base, List.of())).isSameAs(base);
    }

    @Test
    @DisplayName("예산 밖 추가 지출은 그 업종 예산과 총예산에 금액을 더한다")
    void extraAddsToTheCategoryAndTotal() {
        PlanBudget base = budget(SpendingCategory.DINING, 300_000L, SpendingCategory.CAFE, 100_000L);

        PlanBudget result = PlannedExpenseBudgetOverlay.apply(base,
            List.of(new Item(SpendingCategory.CULTURE_TRAVEL, 300_000, true)));

        assertThat(result.total()).isEqualTo(700_000);
        assertThat(result.byCategory()).containsEntry(SpendingCategory.DINING, 300_000L)
            .containsEntry(SpendingCategory.CAFE, 100_000L).containsEntry(SpendingCategory.CULTURE_TRAVEL, 300_000L);
    }

    @Test
    @DisplayName("기존 예산 안에서 조정하면 총예산은 그대로이고 다른 업종이 비율대로 줄며 그 업종에 금액이 더해진다")
    void withinBudgetKeepsTheTotal() {
        PlanBudget base = budget(SpendingCategory.DINING, 300_000L, SpendingCategory.CAFE, 100_000L);

        // 8만원을 조정하면 외식은 6만원, 카페는 2만원 줄어든다.
        PlanBudget result = PlannedExpenseBudgetOverlay.apply(base,
            List.of(new Item(SpendingCategory.LIFE_HEALTH, 80_000, false)));

        assertThat(result.total()).isEqualTo(400_000);
        assertThat(result.byCategory()).containsEntry(SpendingCategory.DINING, 240_000L)
            .containsEntry(SpendingCategory.CAFE, 80_000L).containsEntry(SpendingCategory.LIFE_HEALTH, 80_000L);
    }

    @Test
    @DisplayName("나누어떨어지지 않으면 나머지가 큰 업종부터 1원씩 더 줄여 줄인 금액의 합이 정확히 같다")
    void remainderIsDistributedExactly() {
        PlanBudget base = budget(SpendingCategory.DINING, 100L, SpendingCategory.CAFE, 100L,
            SpendingCategory.SHOPPING, 100L);

        PlanBudget result = PlannedExpenseBudgetOverlay.apply(base,
            List.of(new Item(SpendingCategory.ETC, 100, false)));

        // 100원을 셋으로 나누면 33원씩이고 남은 1원은 나머지가 같으니 선언 순서가 앞선 외식이 가져간다.
        assertThat(result.byCategory()).containsEntry(SpendingCategory.DINING, 66L)
            .containsEntry(SpendingCategory.CAFE, 67L).containsEntry(SpendingCategory.SHOPPING, 67L)
            .containsEntry(SpendingCategory.ETC, 100L);
        assertThat(result.total()).isEqualTo(300);
    }

    @Test
    @DisplayName("줄일 금액이 총예산 이상이면 모든 업종을 비우고 예정지출 금액만 남는다")
    void reductionIsCappedAtTheBudget() {
        PlanBudget base = budget(SpendingCategory.DINING, 50_000L, SpendingCategory.CAFE, 50_000L);

        PlanBudget result = PlannedExpenseBudgetOverlay.apply(base,
            List.of(new Item(SpendingCategory.LIFE_HEALTH, 130_000, false)));

        assertThat(result.byCategory()).containsOnlyKeys(SpendingCategory.LIFE_HEALTH);
        assertThat(result.total()).isEqualTo(130_000);
    }

    @Test
    @DisplayName("두 방식과 같은 업종의 여러 건이 함께 있어도 업종별로 합산한다")
    void combinesBothMethods() {
        PlanBudget base = budget(SpendingCategory.DINING, 200_000L, SpendingCategory.CAFE, 200_000L);

        PlanBudget result = PlannedExpenseBudgetOverlay.apply(base, List.of(
            new Item(SpendingCategory.SHOPPING, 40_000, false),
            new Item(SpendingCategory.SHOPPING, 10_000, true),
            new Item(SpendingCategory.CULTURE_TRAVEL, 100_000, true)));

        // 조정 4만원은 외식·카페에서 2만원씩 줄고, 추가 지출 11만원은 총예산에 더해진다.
        assertThat(result.byCategory()).containsEntry(SpendingCategory.DINING, 180_000L)
            .containsEntry(SpendingCategory.CAFE, 180_000L).containsEntry(SpendingCategory.SHOPPING, 50_000L)
            .containsEntry(SpendingCategory.CULTURE_TRAVEL, 100_000L);
        assertThat(result.total()).isEqualTo(510_000);
    }

    @Test
    @DisplayName("확정 예산 객체는 바꾸지 않는다")
    void doesNotMutateTheBase() {
        PlanBudget base = budget(SpendingCategory.DINING, 300_000L, SpendingCategory.CAFE, 100_000L);

        PlannedExpenseBudgetOverlay.apply(base, List.of(new Item(SpendingCategory.ETC, 80_000, false)));

        assertThat(base.byCategory()).containsEntry(SpendingCategory.DINING, 300_000L)
            .containsEntry(SpendingCategory.CAFE, 100_000L).hasSize(2);
        assertThat(base.total()).isEqualTo(400_000);
    }

    @Test
    @DisplayName("아주 큰 금액에서도 넘치지 않는다")
    void handlesLargeAmounts() {
        PlanBudget base = budget(SpendingCategory.DINING, PlanBudget.MAX_TOTAL / 2,
            SpendingCategory.CAFE, PlanBudget.MAX_TOTAL / 2);

        PlanBudget result = PlannedExpenseBudgetOverlay.apply(base,
            List.of(new Item(SpendingCategory.ETC, 10_000_000_000L, false)));

        assertThat(result.total()).isEqualTo(PlanBudget.MAX_TOTAL);
    }

}
