package com.richreach.plan.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.entity.PlanChangeItem;
import com.richreach.plan.service.PlanChangeBuilder.Draft;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 재계산 전후의 카드·업종별 목표 금액 비교를 DB 없이 확인한다. */
class PlanChangeBuilderTest {

    private static Map<Long, Map<SpendingCategory, Long>> plan(Object... cells) {
        Map<Long, Map<SpendingCategory, Long>> result = new HashMap<>();
        for (int i = 0; i < cells.length; i += 3) {
            result.computeIfAbsent((Long) cells[i], id -> new EnumMap<>(SpendingCategory.class))
                .put((SpendingCategory) cells[i + 1], ((Number) cells[i + 2]).longValue());
        }
        return result;
    }

    @Test
    @DisplayName("같은 배분이면 바뀐 내용이 없다")
    void identicalAllocationsHaveNoChanges() {
        var same = plan(1L, SpendingCategory.DINING, 300_000, 2L, SpendingCategory.CAFE, 100_000);

        assertThat(PlanChangeBuilder.diff(same, plan(1L, SpendingCategory.DINING, 300_000, 2L,
            SpendingCategory.CAFE, 100_000))).isEmpty();
    }

    @Test
    @DisplayName("카드·업종별 금액이 달라진 칸마다 AMOUNT 한 줄이고, 없던 칸은 0원에서 시작한 것으로 본다")
    void amountChangesPerCell() {
        var before = plan(1L, SpendingCategory.DINING, 300_000);
        var after = plan(1L, SpendingCategory.DINING, 50_000, 2L, SpendingCategory.DINING, 250_000);

        List<Draft> drafts = PlanChangeBuilder.diff(before, after);

        assertThat(drafts).filteredOn(d -> d.type() == PlanChangeItem.Type.AMOUNT).containsExactly(
            new Draft(PlanChangeItem.Type.AMOUNT, SpendingCategory.DINING, 1L, null, 300_000, 50_000),
            new Draft(PlanChangeItem.Type.AMOUNT, SpendingCategory.DINING, 2L, null, 0, 250_000));
    }

    @Test
    @DisplayName("업종의 우선 카드(목표 금액이 가장 큰 카드)가 바뀌면 PRIORITY 한 줄이 먼저 나온다")
    void priorityChangeComesFirst() {
        var before = plan(1L, SpendingCategory.DINING, 300_000);
        var after = plan(1L, SpendingCategory.DINING, 50_000, 2L, SpendingCategory.DINING, 250_000);

        List<Draft> drafts = PlanChangeBuilder.diff(before, after);

        assertThat(drafts.get(0)).isEqualTo(new Draft(PlanChangeItem.Type.PRIORITY, SpendingCategory.DINING, 2L, 1L,
            300_000, 250_000));
        assertThat(drafts).hasSize(3);
    }

    @Test
    @DisplayName("우선 카드가 같으면 금액만 달라져도 PRIORITY는 없다")
    void noPriorityChangeWhenTheTopCardStays() {
        var before = plan(1L, SpendingCategory.DINING, 300_000, 2L, SpendingCategory.DINING, 100_000);
        var after = plan(1L, SpendingCategory.DINING, 280_000, 2L, SpendingCategory.DINING, 120_000);

        assertThat(PlanChangeBuilder.diff(before, after)).extracting(Draft::type)
            .containsOnly(PlanChangeItem.Type.AMOUNT);
    }

    @Test
    @DisplayName("금액이 같으면 카드 ID가 작은 쪽이 우선 카드다")
    void tiesGoToTheLowerCardId() {
        var before = plan(5L, SpendingCategory.CAFE, 100_000, 3L, SpendingCategory.CAFE, 100_000);
        var after = plan(5L, SpendingCategory.CAFE, 120_000, 3L, SpendingCategory.CAFE, 80_000);

        List<Draft> drafts = PlanChangeBuilder.diff(before, after);

        // 전: 3번(같은 금액이라 ID가 작음), 후: 5번 → 우선 카드가 3번에서 5번으로 바뀐다.
        assertThat(drafts.get(0)).isEqualTo(new Draft(PlanChangeItem.Type.PRIORITY, SpendingCategory.CAFE, 5L, 3L,
            100_000, 120_000));
    }

    @Test
    @DisplayName("전후 한쪽에만 배분이 있는 업종은 PRIORITY 없이 AMOUNT만 만든다")
    void categoryAppearingOrDisappearingOnlyHasAmountChanges() {
        var before = plan(1L, SpendingCategory.DINING, 100_000);
        var after = plan(1L, SpendingCategory.CAFE, 100_000);

        assertThat(PlanChangeBuilder.diff(before, after)).extracting(Draft::type)
            .containsOnly(PlanChangeItem.Type.AMOUNT);
    }

    @Test
    @DisplayName("결과 순서는 업종 정의 순서이고 같은 입력이면 항상 같다")
    void deterministicOrder() {
        var before = plan(1L, SpendingCategory.CAFE, 100_000, 1L, SpendingCategory.DINING, 100_000);
        var after = plan(1L, SpendingCategory.CAFE, 50_000, 1L, SpendingCategory.DINING, 50_000);

        List<Draft> first = PlanChangeBuilder.diff(before, after);

        assertThat(first).isEqualTo(PlanChangeBuilder.diff(before, after));
        List<SpendingCategory> categories = first.stream().map(Draft::category).toList();
        assertThat(categories).isSortedAccordingTo(java.util.Comparator.comparingInt(Enum::ordinal));
    }

}
