package com.richreach.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.global.category.SpendingCategory;
import com.richreach.transaction.service.TransactionBenefitAllocator.Item;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 업종별 월 혜택을 거래별로 나누는 계산을 DB 없이 확인한다. */
class TransactionBenefitAllocatorTest {

    private static Item item(long id, SpendingCategory category, long net) {
        return new Item(id, category, net);
    }

    @Test
    @DisplayName("업종의 혜택을 그 업종 거래들의 금액 비율대로 나누고, 합이 월 혜택과 정확히 같다")
    void splitsByAmountAndSumsToTheMonthlyBenefit() {
        Map<Long, Long> result = TransactionBenefitAllocator.allocate(Map.of(SpendingCategory.CAFE, 1_000L),
            List.of(item(1, SpendingCategory.CAFE, 4_000), item(2, SpendingCategory.CAFE, 6_000)));

        assertThat(result).containsEntry(1L, 400L).containsEntry(2L, 600L);
    }

    @Test
    @DisplayName("나누어 떨어지지 않으면 소수 부분이 큰 거래부터 1원씩 더하고, 같으면 ID가 작은 쪽이 먼저다")
    void distributesTheRemainderByLargestFraction() {
        // 100원을 3등분하면 33.33... 씩이다. 소수 부분이 모두 같으므로 ID가 작은 거래가 1원을 받는다.
        Map<Long, Long> equal = TransactionBenefitAllocator.allocate(Map.of(SpendingCategory.CAFE, 100L),
            List.of(item(3, SpendingCategory.CAFE, 1_000), item(1, SpendingCategory.CAFE, 1_000),
                item(2, SpendingCategory.CAFE, 1_000)));
        assertThat(equal).containsEntry(1L, 34L).containsEntry(2L, 33L).containsEntry(3L, 33L);

        // 10원을 1:2로 나누면 3.33 / 6.67 이므로 소수 부분이 큰 두 번째가 1원을 받는다.
        Map<Long, Long> uneven = TransactionBenefitAllocator.allocate(Map.of(SpendingCategory.CAFE, 10L),
            List.of(item(1, SpendingCategory.CAFE, 1_000), item(2, SpendingCategory.CAFE, 2_000)));
        assertThat(uneven).containsEntry(1L, 3L).containsEntry(2L, 7L);
        assertThat(uneven.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(10L);
    }

    @Test
    @DisplayName("업종마다 따로 나눈다. 혜택이 없는 업종의 거래는 0원이다")
    void eachCategoryIsAllocatedSeparately() {
        Map<Long, Long> result = TransactionBenefitAllocator.allocate(Map.of(SpendingCategory.CAFE, 500L),
            List.of(item(1, SpendingCategory.CAFE, 5_000), item(2, SpendingCategory.DINING, 5_000)));

        assertThat(result).containsEntry(1L, 500L).containsEntry(2L, 0L);
    }

    @Test
    @DisplayName("전액 취소되어 금액이 0인 거래는 혜택이 0원이고, 남는 금액도 받지 않는다")
    void canceledTransactionGetsNothing() {
        Map<Long, Long> result = TransactionBenefitAllocator.allocate(Map.of(SpendingCategory.CAFE, 301L),
            List.of(item(1, SpendingCategory.CAFE, 0), item(2, SpendingCategory.CAFE, 1_000),
                item(3, SpendingCategory.CAFE, 2_000)));

        assertThat(result.get(1L)).isZero();
        assertThat(result.get(2L) + result.get(3L)).isEqualTo(301L);
    }

    @Test
    @DisplayName("혜택이 없거나 금액이 모두 0이면 모든 거래가 0원이고, 거래가 없으면 비어 있다")
    void noBenefitOrNoAmount() {
        assertThat(TransactionBenefitAllocator.allocate(Map.of(), List.of(item(1, SpendingCategory.CAFE, 1_000))))
            .containsEntry(1L, 0L);
        assertThat(TransactionBenefitAllocator.allocate(Map.of(SpendingCategory.CAFE, 100L),
            List.of(item(1, SpendingCategory.CAFE, 0)))).containsEntry(1L, 0L);
        assertThat(TransactionBenefitAllocator.allocate(Map.of(SpendingCategory.CAFE, 100L), List.of())).isEmpty();
    }

    @Test
    @DisplayName("같은 입력이면 거래 순서가 달라도 같은 결과가 나온다")
    void deterministicRegardlessOfOrder() {
        List<Item> items = List.of(item(1, SpendingCategory.CAFE, 1_000), item(2, SpendingCategory.CAFE, 1_000),
            item(3, SpendingCategory.CAFE, 1_000));
        Map<SpendingCategory, Long> benefit = Map.of(SpendingCategory.CAFE, 100L);

        assertThat(TransactionBenefitAllocator.allocate(benefit, items))
            .isEqualTo(TransactionBenefitAllocator.allocate(benefit, items.reversed()));
    }

}
