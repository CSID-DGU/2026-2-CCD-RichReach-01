package com.richreach.plan.service;

import com.richreach.global.category.SpendingCategory;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 확정 예산에 이번 달 예정지출을 얹어 재계산에 쓸 예산을 만든다. DB 없이 값만으로 계산하므로 따로 확인할 수 있다.
 * 확정 예산(소비 프로필)은 바꾸지 않는다. 이 결과는 재계산 엔진에 넣는 입력일 뿐이고, 예정지출을 지우거나 고치면 다음 재계산이
 * 처음부터 다시 얹는다. (그래서 같은 예정지출이 두 번 더해지지 않는다)
 *
 * <ul>
 *   <li><b>예산 밖 추가 지출</b>: 그 업종 예산에 금액을 더한다. 총예산도 그만큼 늘어난다.</li>
 *   <li><b>기존 예산 안에서 조정</b>: 총예산은 그대로 두려고 업종 예산을 줄이고, 그 업종 예산에 금액을 더한다. 줄일 수 있는
 *       것은 업종별 <b>남은 예산</b>(예산 - 이미 쓴 금액, 0 미만이면 0)뿐이다. 이미 쓴 금액은 재계산 엔진이 그 카드에
 *       고정하므로, 쓴 금액까지 줄이면 줄인 만큼이 실제로 확보되지 않아 총액이 확정 예산을 넘기 때문이다. 줄이는 금액은 남은
 *       예산의 비율대로 나누고(원 단위 나머지는 나머지가 큰 업종부터 1원씩 배분), 줄일 금액이 남은 예산 전체보다 크면 남은
 *       예산만큼만 줄인다. 이때 모자란 만큼은 확보할 수 없어서 총예산이 그만큼 늘어난다.</li>
 *   <li>금액은 이번 달에 반영되는 금액(할부면 월 금액)이다.</li>
 *   <li>이미 쓴 금액보다 낮아진 업종 예산은 없다. (줄이는 것은 남은 예산뿐이라서)</li>
 * </ul>
 * 금액은 소비 정보라서 로그에 남기지 않는다.
 */
final class PlannedExpenseBudgetOverlay {

    /**
     * 예산에 반영할 예정지출 한 건.
     *
     * @param monthlyAmount 이번 달에 반영되는 금액(원). 0보다 크다.
     * @param extra         true면 예산 밖 추가 지출, false면 기존 예산 안에서 조정
     */
    record Item(SpendingCategory category, long monthlyAmount, boolean extra) {

    }

    private PlannedExpenseBudgetOverlay() {
    }

    /**
     * 예정지출이 없으면 받은 예산을 그대로 돌려준다.
     *
     * @param spentByCategory 이번 달에 이미 쓴 금액(재계산 엔진이 카드에 고정하는 승인 소비)의 업종별 합계
     */
    static PlanBudget apply(PlanBudget base, List<Item> items, Map<SpendingCategory, Long> spentByCategory) {
        if (items.isEmpty()) {
            return base;
        }
        long within = items.stream().filter(item -> !item.extra()).mapToLong(Item::monthlyAmount).sum();
        Map<SpendingCategory, Long> byCategory = new EnumMap<>(SpendingCategory.class);
        byCategory.putAll(base.byCategory());
        reduce(byCategory, within, spentByCategory);
        for (Item item : items) {
            byCategory.merge(item.category(), item.monthlyAmount(), Long::sum);
        }
        long total = byCategory.values().stream().mapToLong(Long::longValue).sum();
        return new PlanBudget(base.month(), total, byCategory);
    }

    /**
     * 업종별 남은 예산에서 {@code amount}원을 남은 예산의 비율대로 줄인다. 남은 예산의 합보다 크면 남은 예산만 모두 줄인다.
     * 예산이 쓴 금액과 같아진 업종은 그대로 두고, 0원이 된 업종은 뺀다.
     */
    private static void reduce(Map<SpendingCategory, Long> byCategory, long amount,
                               Map<SpendingCategory, Long> spentByCategory) {
        Map<SpendingCategory, Long> headroom = new EnumMap<>(SpendingCategory.class);
        long room = 0;
        for (Map.Entry<SpendingCategory, Long> entry : byCategory.entrySet()) {
            long left = Math.max(entry.getValue() - spentByCategory.getOrDefault(entry.getKey(), 0L), 0L);
            if (left > 0) {
                headroom.put(entry.getKey(), left);
                room += left;
            }
        }
        long cut = Math.min(amount, room);
        if (cut <= 0) {
            return;
        }
        BigInteger cutTotal = BigInteger.valueOf(cut);
        BigInteger roomTotal = BigInteger.valueOf(room);

        // 업종마다 cut * 남은 예산 / 합계의 내림값을 줄이고, 남은 금액은 나머지가 큰 업종부터 1원씩 줄인다.
        record Share(SpendingCategory category, long floor, BigInteger remainder) {

        }
        List<Share> shares = new ArrayList<>();
        long reduced = 0;
        for (Map.Entry<SpendingCategory, Long> entry : headroom.entrySet()) {
            BigInteger[] division = cutTotal.multiply(BigInteger.valueOf(entry.getValue()))
                .divideAndRemainder(roomTotal);
            shares.add(new Share(entry.getKey(), division[0].longValueExact(), division[1]));
            reduced += division[0].longValueExact();
        }
        long left = cut - reduced;
        // 정렬은 안정적이라 나머지가 같으면 업종 선언 순서가 앞선 업종이 먼저다. 나머지가 0보다 큰 업종은 남은 금액 이상이다.
        shares.sort(Comparator.comparing(Share::remainder).reversed());
        for (Share share : shares) {
            long reduction = share.floor() + (left > 0 && share.remainder().signum() > 0 ? 1 : 0);
            if (left > 0 && share.remainder().signum() > 0) {
                left--;
            }
            long remaining = byCategory.get(share.category()) - reduction;
            if (remaining > 0) {
                byCategory.put(share.category(), remaining);
            } else {
                byCategory.remove(share.category());
            }
        }
    }

}
