package com.richreach.transaction.service;

import com.richreach.global.category.SpendingCategory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 업종별 월 혜택을 그 업종의 거래들에 나눈다. DB 없이 숫자만으로 계산하므로 따로 확인할 수 있다.
 *
 * <ul>
 *   <li>한 업종의 혜택은 그 업종 거래들의 금액(취소·환불을 뺀 값) 비율대로 나눈다. 전액 취소되어 금액이 0인 거래는 0원이다.</li>
 *   <li>원 단위로 내림해서 나누고, 남는 금액은 소수 부분이 큰 거래부터 1원씩 더한다. 그래서 업종별로 나눈 합이 월 혜택과
 *       정확히 같다. 소수 부분이 같으면 거래 ID가 작은 쪽이 먼저다.</li>
 *   <li>같은 입력이면 항상 같은 결과가 나온다.</li>
 * </ul>
 */
final class TransactionBenefitAllocator {

    private TransactionBenefitAllocator() {
    }

    /** 나눌 거래 한 건. */
    record Item(long id, SpendingCategory category, long netAmount) {

    }

    /**
     * @param benefitByCategory 업종별 월 혜택(원). 없는 업종은 0이다.
     * @param items             그 달 그 카드의 거래들
     * @return 거래 ID별 혜택(원). 모든 거래가 들어 있고, 혜택이 없으면 0이다.
     */
    static Map<Long, Long> allocate(Map<SpendingCategory, Long> benefitByCategory, List<Item> items) {
        Map<SpendingCategory, List<Item>> byCategory = new EnumMap<>(SpendingCategory.class);
        for (Item item : items) {
            byCategory.computeIfAbsent(item.category(), key -> new ArrayList<>()).add(item);
        }
        Map<Long, Long> result = new HashMap<>();
        for (Map.Entry<SpendingCategory, List<Item>> entry : byCategory.entrySet()) {
            long benefit = benefitByCategory.getOrDefault(entry.getKey(), 0L);
            allocateCategory(benefit, entry.getValue(), result);
        }
        return result;
    }

    private static void allocateCategory(long benefit, List<Item> items, Map<Long, Long> result) {
        long total = items.stream().mapToLong(Item::netAmount).sum();
        if (benefit <= 0 || total <= 0) {
            items.forEach(item -> result.put(item.id(), 0L));
            return;
        }
        record Share(Item item, long floor, long remainder) {

        }
        List<Share> shares = new ArrayList<>();
        long assigned = 0;
        for (Item item : items) {
            // benefit x 금액 / 합계. 곱한 뒤에 나눠서 소수 부분을 정확히 남긴다. (금액이 크지 않아 long 범위를 넘지 않는다)
            long scaled = Math.multiplyExact(benefit, item.netAmount());
            long floor = scaled / total;
            shares.add(new Share(item, floor, scaled % total));
            assigned += floor;
        }
        long leftover = benefit - assigned;
        shares.sort(Comparator.comparingLong(Share::remainder).reversed().thenComparingLong(share -> share.item().id()));
        Map<Long, Long> extra = new HashMap<>();
        for (int i = 0; i < shares.size() && leftover > 0; i++) {
            Share share = shares.get(i);
            // 금액이 0인 거래는 몫이 없으므로 남는 금액을 받지 않는다.
            if (share.item().netAmount() > 0) {
                extra.put(share.item().id(), 1L);
                leftover--;
            }
        }
        for (Share share : shares) {
            result.put(share.item().id(), share.floor() + extra.getOrDefault(share.item().id(), 0L));
        }
    }

}
