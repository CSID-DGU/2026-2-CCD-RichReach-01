package com.richreach.plan.service;

import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.entity.PlanChangeItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 재계산 전후의 카드·업종별 목표 금액을 비교해서 바뀐 내용을 만든다. DB 없이 금액만으로 계산하므로 따로 확인할 수 있다.
 *
 * <ul>
 *   <li>{@code AMOUNT}: 카드·업종별 목표 금액이 달라진 칸마다 하나. 없던 칸은 0원에서 시작한 것으로 본다.</li>
 *   <li>{@code PRIORITY}: 업종의 우선 카드(목표 금액이 가장 큰 카드, 같으면 카드 ID가 작은 쪽)가 달라진 업종마다 하나.
 *       전후 모두 그 업종에 배분이 있을 때만 만든다.</li>
 *   <li>결과는 업종 순서(화면용 업종 정의 순서), 같은 업종에서는 우선 카드 변경 먼저, 그다음 카드 ID 순이라 항상 같은 순서다.</li>
 * </ul>
 * 금액은 소비 정보라서 로그에 남기지 않는다.
 */
final class PlanChangeBuilder {

    private PlanChangeBuilder() {
    }

    /** 바뀐 내용 한 줄. 변경 기록에 저장하기 전의 값이다. */
    record Draft(PlanChangeItem.Type type, SpendingCategory category, Long userCardId, Long previousCardId,
                 long previousAmount, long currentAmount) {

    }

    /**
     * @param previous 바뀌기 전 보유카드별·업종별 목표 금액
     * @param current  바뀐 뒤 보유카드별·업종별 목표 금액
     */
    static List<Draft> diff(Map<Long, Map<SpendingCategory, Long>> previous,
                            Map<Long, Map<SpendingCategory, Long>> current) {
        List<Draft> drafts = new ArrayList<>();
        for (SpendingCategory category : SpendingCategory.values()) {
            Map<Long, Long> before = byCard(previous, category);
            Map<Long, Long> after = byCard(current, category);

            Long previousTop = topCard(before);
            Long currentTop = topCard(after);
            if (previousTop != null && currentTop != null && !previousTop.equals(currentTop)) {
                drafts.add(new Draft(PlanChangeItem.Type.PRIORITY, category, currentTop, previousTop,
                    before.get(previousTop), after.get(currentTop)));
            }

            Set<Long> cards = new TreeSet<>();
            cards.addAll(before.keySet());
            cards.addAll(after.keySet());
            for (Long card : cards) {
                long was = before.getOrDefault(card, 0L);
                long now = after.getOrDefault(card, 0L);
                if (was != now) {
                    drafts.add(new Draft(PlanChangeItem.Type.AMOUNT, category, card, null, was, now));
                }
            }
        }
        return drafts;
    }

    /** 그 업종의 카드별 목표 금액. 0원 이하인 칸은 없는 것으로 본다. */
    private static Map<Long, Long> byCard(Map<Long, Map<SpendingCategory, Long>> allocations, SpendingCategory category) {
        Map<Long, Long> result = new TreeMap<>();
        allocations.forEach((card, byCategory) -> {
            long amount = byCategory.getOrDefault(category, 0L);
            if (amount > 0) {
                result.put(card, amount);
            }
        });
        return result;
    }

    /** 목표 금액이 가장 큰 카드. 같으면 카드 ID가 작은 쪽이고, 배분이 없으면 null이다. */
    private static Long topCard(Map<Long, Long> byCard) {
        Long top = null;
        for (Map.Entry<Long, Long> entry : byCard.entrySet()) {
            // byCard는 카드 ID 오름차순이라, 처음 만난 최댓값이 ID가 작은 쪽이다.
            if (top == null || entry.getValue() > byCard.get(top)) {
                top = entry.getKey();
            }
        }
        return top;
    }

}
