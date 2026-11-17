package com.richreach.benefit.service;

import com.richreach.card.dto.CalculationRule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * 카드의 혜택 규칙에서 전월실적 구간과 월 혜택 한도를 정리한다. DB 없이 규칙 목록만으로 계산하므로 따로 확인할 수 있다.
 * 사람이 원문과 대조해 확인한 규칙({@link CalculationRule#ruleEngineReady()})만 쓴다. 확인 전 규칙은 틀릴 수 있어서 구간과 한도에
 * 넣지 않는다.
 *
 * <p>두 값 모두 <b>규칙을 합친 추정</b>이다. 카드사가 알려주는 값이 아니고, 카드 전체의 한도가 규칙에 한 숫자로 적혀 있지도 않다.
 */
public final class CardPerformanceAnalyzer {

    private CardPerformanceAnalyzer() {
    }

    /**
     * 전월실적 구간의 하한(원)을 작은 것부터. 실적 조건이 있는 규칙의 하한을 모은 값이고 같은 값은 한 번만 나온다. 0원 이하는
     * 조건이 없는 것과 같아서 뺀다. 구간이 없으면 빈 목록이다.
     */
    public static List<Long> tiers(List<CalculationRule> rules) {
        return rules.stream().filter(CalculationRule::ruleEngineReady)
            .map(CalculationRule::previousMonthMinKrw)
            .filter(min -> min != null && min > 0)
            .distinct().sorted().toList();
    }

    /**
     * 전월 사용 금액({@code previousMonthSpend})을 채운 규칙들의 월 혜택 한도 합(원).
     *
     * <ul>
     *   <li>전월실적 조건(이상, 미만)을 채운 규칙만 센다.</li>
     *   <li>규칙마다 월 한도를 더하고, 같은 한도 묶음({@code capGroupId})은 공유 한도를 한 번만 더한다.</li>
     *   <li>센 규칙 중 월 한도도 공유 한도도 없는 규칙이 하나라도 있으면 한도를 알 수 없어서(없거나 미확인) 비어 있다.</li>
     *   <li>센 규칙이 하나도 없어도 비어 있다.</li>
     * </ul>
     */
    public static OptionalLong monthlyLimit(List<CalculationRule> rules, long previousMonthSpend) {
        List<CalculationRule> applicable = new ArrayList<>();
        for (CalculationRule rule : rules) {
            if (rule.ruleEngineReady() && meetsPerformance(rule, previousMonthSpend)) {
                applicable.add(rule);
            }
        }
        if (applicable.isEmpty()) {
            return OptionalLong.empty();
        }
        long total = 0;
        // 같은 묶음의 공유 한도는 묶음당 한 번만 더한다. 묶음 안에서 가장 작은 공유 한도를 쓴다.
        Map<String, Long> sharedByGroup = new HashMap<>();
        for (CalculationRule rule : applicable) {
            if (rule.capGroupId() != null && rule.sharedMonthlyLimit() != null) {
                sharedByGroup.merge(rule.capGroupId(), rule.sharedMonthlyLimit(), Math::min);
            } else if (rule.monthlyLimit() != null) {
                total = Math.addExact(total, rule.monthlyLimit());
            } else {
                return OptionalLong.empty();
            }
        }
        total = Math.addExact(total, sharedByGroup.values().stream().mapToLong(Long::longValue).sum());
        return OptionalLong.of(total);
    }

    /** 전월 사용 금액이 이 규칙의 전월실적 조건(하한 이상, 상한 미만)을 채우는지. 조건이 없으면 채운다. */
    private static boolean meetsPerformance(CalculationRule rule, long previousMonthSpend) {
        if (rule.previousMonthMinKrw() != null && previousMonthSpend < rule.previousMonthMinKrw()) {
            return false;
        }
        return rule.previousMonthMaxExclusiveKrw() == null || previousMonthSpend < rule.previousMonthMaxExclusiveKrw();
    }

    /** 전월 사용 금액이 채운 가장 높은 구간 하한. 하나도 못 채웠거나 구간이 없으면 비어 있다. */
    public static OptionalLong achievedTier(List<Long> tiers, long amount) {
        return tiers.stream().mapToLong(Long::longValue).filter(tier -> tier <= amount).max();
    }

    /** 아직 채우지 못한 가장 낮은 구간 하한. 모두 채웠거나 구간이 없으면 비어 있다. */
    public static OptionalLong nextTier(List<Long> tiers, long amount) {
        return tiers.stream().mapToLong(Long::longValue).filter(tier -> tier > amount).min();
    }

}
