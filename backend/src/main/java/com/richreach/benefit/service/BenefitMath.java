package com.richreach.benefit.service;

import com.richreach.benefit.dto.BenefitResult;
import com.richreach.global.category.SpendingCategory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 임시 계산기의 계산식. DB 없이 규칙 목록과 소비만 받아 계산하므로 따로 확인할 수 있다.
 *
 * <p>단순화한 부분 (결과의 {@code notes}에 남긴다)
 * <ul>
 *   <li>비율(rate) 혜택만 계산한다. 정액·횟수·주유 리터당 같은 혜택은 계산하지 않는다.</li>
 *   <li>가맹점이 지정된 혜택은 업종만으로 거래와 이을 수 없어서 뺀다.</li>
 *   <li>{@code rule_mode}가 REPLACES_BASE, PREFER_HIGHER, ANNUAL_BONUS인 규칙은 다른 규칙과의 관계를 따져야 해서 뺀다.
 *       (BASE, ADDITIONAL, 값 없음은 서로 더한다.)</li>
 *   <li>마일리지(MILE) 혜택은 원화 가치를 알 수 없어서 뺀다. 포인트(POINT)는 1점을 1원으로 본다.</li>
 *   <li>전월실적 조건은 하한(이상)과 상한(미만)만 본다.</li>
 *   <li>한도는 규칙별 월 한도와, 같은 한도 묶음(cap_group_id)의 공유 한도를 적용한다. 일 한도와 횟수 한도, 할인대상 결제금액
 *       한도는 적용하지 않고, 그런 한도가 있는 규칙이 혜택을 냈으면 {@code notes}에 남긴다.</li>
 * </ul>
 * 한 혜택이 여러 업종에 걸치면 혜택을 그 업종들의 소비 비율대로 나눠서 업종별로 담는다. 업종별 금액은 원 단위로 내림한다.
 */
final class BenefitMath {

    private static final int SCALE = 10;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private BenefitMath() {
    }

    /**
     * 혜택 규칙 한 개의 계산에 필요한 값.
     *
     * @param categories        혜택이 적용되는 업종
     * @param allMerchants      업종과 상관없이 모든 소비에 적용되는지
     * @param merchantSpecific  가맹점이 지정된 혜택인지
     * @param ratePercent       혜택 비율(%). 비율 혜택이 아니면 null
     * @param rewardUnit        KRW, POINT, MILE. 없으면 null
     * @param monthlyLimit      월 혜택 한도. 없으면 null
     * @param capGroupId        한도를 함께 쓰는 묶음 ID. 없으면 null
     * @param sharedMonthlyLimit 묶음의 공유 월 한도. 없으면 null
     * @param ruleMode          BASE, ADDITIONAL, REPLACES_BASE, PREFER_HIGHER, ANNUAL_BONUS. 없으면 null
     * @param previousMonthMin  전월실적 하한(이상). 없으면 null
     * @param previousMonthMaxExclusive 전월실적 상한(미만). 없으면 null
     * @param unappliedLimits   규칙에 있지만 이 계산식이 적용하지 않는 한도의 이름. 있으면 결과가 실제보다 클 수 있어
     *                          그 규칙이 혜택을 냈을 때 {@code notes}에 남긴다.
     */
    record RuleInput(String id, Set<SpendingCategory> categories, boolean allMerchants, boolean merchantSpecific,
                     BigDecimal ratePercent, String rewardUnit, Long monthlyLimit, String capGroupId,
                     Long sharedMonthlyLimit, String ruleMode, Long previousMonthMin,
                     Long previousMonthMaxExclusive, List<String> unappliedLimits) {

        RuleInput {
            unappliedLimits = unappliedLimits == null ? List.of() : List.copyOf(unappliedLimits);
        }
    }

    /** 규칙 한 개가 낸 혜택. 업종별 금액을 소수까지 가진다. */
    private static final class Contribution {

        private final RuleInput rule;
        private final Map<SpendingCategory, BigDecimal> byCategory = new EnumMap<>(SpendingCategory.class);

        private Contribution(RuleInput rule) {
            this.rule = rule;
        }

        private BigDecimal total() {
            return byCategory.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /** 모든 업종의 혜택에 {@code numerator / denominator}를 곱한다. 곱셈을 먼저 하고 마지막에 한 번만 나눈다. */
        private void scaleTo(BigDecimal numerator, BigDecimal denominator) {
            byCategory.replaceAll((category, value) -> value.multiply(numerator)
                .divide(denominator, SCALE, RoundingMode.HALF_UP));
        }
    }

    static BenefitResult compute(List<RuleInput> rules, Map<SpendingCategory, Long> spend, long previousMonthSpend,
                                 boolean confirmedOnly) {
        Set<String> notes = new LinkedHashSet<>();
        List<Contribution> contributions = new ArrayList<>();

        for (RuleInput rule : rules) {
            String skipReason = skipReason(rule, previousMonthSpend);
            if (skipReason != null) {
                notes.add(skipReason);
                continue;
            }
            Map<SpendingCategory, Long> targets = new EnumMap<>(SpendingCategory.class);
            for (Map.Entry<SpendingCategory, Long> entry : spend.entrySet()) {
                if (entry.getValue() > 0 && (rule.allMerchants() || rule.categories().contains(entry.getKey()))) {
                    targets.put(entry.getKey(), entry.getValue());
                }
            }
            long targetSpend = targets.values().stream().mapToLong(Long::longValue).sum();
            if (targetSpend == 0) {
                continue;
            }
            BigDecimal raw = BigDecimal.valueOf(targetSpend).multiply(rule.ratePercent())
                .divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
            BigDecimal capped = rule.monthlyLimit() == null ? raw : raw.min(BigDecimal.valueOf(rule.monthlyLimit()));
            Contribution contribution = new Contribution(rule);
            for (Map.Entry<SpendingCategory, Long> entry : targets.entrySet()) {
                contribution.byCategory.put(entry.getKey(), capped.multiply(BigDecimal.valueOf(entry.getValue()))
                    .divide(BigDecimal.valueOf(targetSpend), SCALE, RoundingMode.HALF_UP));
            }
            contributions.add(contribution);
            if (!rule.unappliedLimits().isEmpty()) {
                notes.add("규칙에 있는 " + String.join(", ", rule.unappliedLimits())
                    + "는 적용하지 않아 예상 혜택이 실제보다 클 수 있습니다.");
            }
        }

        applySharedLimits(contributions);

        Map<SpendingCategory, BigDecimal> sums = new EnumMap<>(SpendingCategory.class);
        for (Contribution contribution : contributions) {
            contribution.byCategory.forEach((category, value) -> sums.merge(category, value, BigDecimal::add));
        }
        Map<SpendingCategory, Long> benefitByCategory = new LinkedHashMap<>();
        long total = 0;
        for (SpendingCategory category : SpendingCategory.values()) {
            BigDecimal value = sums.get(category);
            long won = value == null ? 0 : value.setScale(0, RoundingMode.FLOOR).longValueExact();
            if (won > 0) {
                benefitByCategory.put(category, won);
                total += won;
            }
        }
        return new BenefitResult(total, benefitByCategory, confirmedOnly, new ArrayList<>(notes));
    }

    /** 같은 한도 묶음의 규칙들이 낸 혜택 합이 공유 한도를 넘으면 묶음 전체를 같은 비율로 줄인다. */
    private static void applySharedLimits(List<Contribution> contributions) {
        Map<String, List<Contribution>> groups = new LinkedHashMap<>();
        for (Contribution contribution : contributions) {
            if (contribution.rule.capGroupId() != null) {
                groups.computeIfAbsent(contribution.rule.capGroupId(), key -> new ArrayList<>()).add(contribution);
            }
        }
        for (List<Contribution> group : groups.values()) {
            Long limit = group.stream().map(c -> c.rule.sharedMonthlyLimit()).filter(java.util.Objects::nonNull)
                .min(Long::compare).orElse(null);
            if (limit == null) {
                continue;
            }
            BigDecimal sum = group.stream().map(Contribution::total).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal cap = BigDecimal.valueOf(limit);
            if (sum.compareTo(cap) > 0) {
                // 비율을 미리 잘라 두면 한도에 못 미치는 값이 나오므로(예: 10,000 / 30,000 = 0.333…), 곱한 뒤에 나눈다.
                group.forEach(c -> c.scaleTo(cap, sum));
            }
        }
    }

    /** 이 규칙을 계산에서 빼는 이유. 계산해도 되면 null이다. */
    private static String skipReason(RuleInput rule, long previousMonthSpend) {
        if (rule.merchantSpecific()) {
            return "가맹점이 지정된 혜택은 업종으로 매칭할 수 없어 제외했습니다.";
        }
        if (rule.ratePercent() == null) {
            return "비율이 아닌 혜택(정액, 횟수, 면제 등)은 계산하지 않았습니다.";
        }
        if ("MILE".equals(rule.rewardUnit())) {
            return "마일리지 혜택은 원화 가치를 알 수 없어 제외했습니다.";
        }
        String mode = rule.ruleMode();
        if ("REPLACES_BASE".equals(mode) || "PREFER_HIGHER".equals(mode) || "ANNUAL_BONUS".equals(mode)) {
            return "다른 혜택과의 관계를 따져야 하는 규칙(" + mode + ")은 계산하지 않았습니다.";
        }
        if (rule.previousMonthMin() != null && previousMonthSpend < rule.previousMonthMin()) {
            return "전월실적 조건을 채우지 못한 혜택은 제외했습니다.";
        }
        if (rule.previousMonthMaxExclusive() != null && previousMonthSpend >= rule.previousMonthMaxExclusive()) {
            return "전월실적이 구간을 넘어서 적용되지 않는 혜택은 제외했습니다.";
        }
        return null;
    }

}
