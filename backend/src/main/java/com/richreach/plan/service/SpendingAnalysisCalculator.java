package com.richreach.plan.service;

import com.richreach.global.category.SpendingCategory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 소비 비율과 예산 추천의 계산식. DB 없이 업종별 금액만 받아 계산하므로 따로 확인할 수 있다.
 * 월간 플랜 생성이 소비 프로필을 만들 때도 같은 계산을 쓴다.
 *
 * <p>나누는 일은 모두 최대 잔여법으로 한다. 몫을 내림해서 나누고, 남은 몫을 소수 부분이 큰 업종부터 하나씩 준다.
 * 그래서 비율의 합은 항상 100, 업종 예산의 합은 항상 총예산과 정확히 같다. 소수 부분이 같으면 비중이 큰 업종이,
 * 그것도 같으면 업종 순서가 앞선 업종이 먼저 받아서 같은 입력이면 항상 같은 결과가 나온다.
 */
final class SpendingAnalysisCalculator {

    /** 예산을 나누는 단위(원). 총예산과 업종 예산은 이 단위의 배수다. */
    static final long BUDGET_UNIT = 1_000L;

    private static final int PERCENT_TOTAL = 100;

    private SpendingAnalysisCalculator() {
    }

    /** 업종별 소비 비율(정수 %). 모든 업종을 담고, 합이 100이다. 소비가 없으면 모두 0이다. */
    static Map<SpendingCategory, Integer> ratios(Map<SpendingCategory, Long> amounts) {
        Map<SpendingCategory, Long> shares = allocate(PERCENT_TOTAL, amounts);
        Map<SpendingCategory, Integer> result = new EnumMap<>(SpendingCategory.class);
        shares.forEach((category, share) -> result.put(category, Math.toIntExact(share)));
        return result;
    }

    /** 총예산(원)을 1,000원 단위로 반올림한다. 0 이하면 0이다. */
    static long roundBudgetTotal(long monthlyAverage) {
        if (monthlyAverage <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(monthlyAverage).divide(BigDecimal.valueOf(BUDGET_UNIT), 0, RoundingMode.HALF_UP)
            .longValueExact() * BUDGET_UNIT;
    }

    /**
     * 총예산을 소비 비중대로 업종에 나눈다. 모든 업종을 담고, 각 금액은 1,000원의 배수이며, 합은 {@code total}과 같다.
     * {@code total}은 1,000원의 배수여야 한다. 소비가 없거나 총예산이 0이면 모두 0이다.
     */
    static Map<SpendingCategory, Long> budgetByCategory(long total, Map<SpendingCategory, Long> amounts) {
        if (total % BUDGET_UNIT != 0) {
            throw new IllegalArgumentException("총예산은 " + BUDGET_UNIT + "원의 배수여야 합니다.");
        }
        Map<SpendingCategory, Long> units = allocate(total / BUDGET_UNIT, amounts);
        Map<SpendingCategory, Long> result = new EnumMap<>(SpendingCategory.class);
        units.forEach((category, count) -> result.put(category, count * BUDGET_UNIT));
        return result;
    }

    /** 소비가 가장 큰 업종. 같으면 앞 업종이고, 소비가 없으면 null이다. */
    static SpendingCategory topCategory(Map<SpendingCategory, Long> amounts) {
        SpendingCategory top = null;
        long max = 0;
        for (SpendingCategory category : SpendingCategory.values()) {
            long amount = amounts.getOrDefault(category, 0L);
            if (amount > max) {
                max = amount;
                top = category;
            }
        }
        return top;
    }

    /**
     * {@code totalUnits}를 비중({@code weights})대로 나눈다. 음수 비중은 0으로 본다. 비중의 합이 0이면 모두 0이다.
     * 모든 업종을 업종 순서대로 담고, 합은 {@code totalUnits}와 같다.
     */
    private static Map<SpendingCategory, Long> allocate(long totalUnits, Map<SpendingCategory, Long> weights) {
        Map<SpendingCategory, Long> result = new EnumMap<>(SpendingCategory.class);
        long weightSum = 0;
        for (SpendingCategory category : SpendingCategory.values()) {
            weightSum += Math.max(0, weights.getOrDefault(category, 0L));
        }
        if (totalUnits <= 0 || weightSum == 0) {
            for (SpendingCategory category : SpendingCategory.values()) {
                result.put(category, 0L);
            }
            return result;
        }

        /** 업종 하나의 몫. 내림한 값과, 내림하고 남은 나머지(소수 부분)를 정수로 가진다. */
        record Share(SpendingCategory category, long weight, long floor, long remainder) {

        }
        List<Share> shares = new ArrayList<>();
        long given = 0;
        for (SpendingCategory category : SpendingCategory.values()) {
            long weight = Math.max(0, weights.getOrDefault(category, 0L));
            long numerator = Math.multiplyExact(totalUnits, weight);
            long floor = numerator / weightSum;
            shares.add(new Share(category, weight, floor, numerator % weightSum));
            given += floor;
        }

        // 남은 몫은 소수 부분이 큰 업종부터 하나씩 준다. 같으면 비중이 큰 업종, 그것도 같으면 업종 순서가 앞선 업종이 먼저다.
        long leftover = totalUnits - given;
        List<Share> byPriority = new ArrayList<>(shares);
        byPriority.sort(Comparator.comparingLong(Share::remainder).reversed()
            .thenComparing(Comparator.comparingLong(Share::weight).reversed())
            .thenComparing(share -> share.category().ordinal()));
        Map<SpendingCategory, Long> extra = new EnumMap<>(SpendingCategory.class);
        for (int i = 0; i < leftover; i++) {
            extra.put(byPriority.get(i).category(), 1L);
        }
        for (Share share : shares) {
            result.put(share.category(), share.floor() + extra.getOrDefault(share.category(), 0L));
        }
        return result;
    }

}
