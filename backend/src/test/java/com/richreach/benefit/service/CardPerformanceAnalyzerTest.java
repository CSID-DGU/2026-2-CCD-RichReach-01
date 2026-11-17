package com.richreach.benefit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.card.dto.CalculationRule;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 카드 혜택 규칙에서 전월실적 구간과 월 혜택 한도를 정리하는 계산을 DB 없이 확인한다. */
class CardPerformanceAnalyzerTest {

    private static CalculationRule rule(Long monthlyLimit, String capGroupId, Long sharedLimit, Long min, Long maxExclusive,
                                        boolean ready) {
        return new CalculationRule(1L, Set.of(), true, false, BigDecimal.ONE, "KRW", monthlyLimit, capGroupId,
            sharedLimit, null, null, null, null, null, "BASE", min, maxExclusive, ready);
    }

    private static CalculationRule limited(long monthlyLimit, Long min) {
        return rule(monthlyLimit, null, null, min, null, true);
    }

    @Test
    @DisplayName("전월실적 구간은 확인된 규칙의 하한을 작은 것부터, 같은 값은 한 번만 모은다")
    void tiersAreDistinctAndSorted() {
        List<CalculationRule> rules = List.of(limited(1_000, 700_000L), limited(2_000, 300_000L),
            limited(3_000, 300_000L), limited(4_000, null));

        assertThat(CardPerformanceAnalyzer.tiers(rules)).containsExactly(300_000L, 700_000L);
    }

    @Test
    @DisplayName("확인 전 규칙과 0원 이하 하한은 구간에 넣지 않고, 구간이 없으면 빈 목록이다")
    void unconfirmedRulesAndZeroMinimumsAreIgnored() {
        List<CalculationRule> rules = List.of(rule(1_000L, null, null, 500_000L, null, false), limited(1_000, 0L));

        assertThat(CardPerformanceAnalyzer.tiers(rules)).isEmpty();
        assertThat(CardPerformanceAnalyzer.tiers(List.of())).isEmpty();
    }

    @Test
    @DisplayName("월 한도는 전월실적 조건을 채운 규칙의 한도를 더한다")
    void monthlyLimitSumsApplicableRules() {
        List<CalculationRule> rules = List.of(limited(5_000, null), limited(10_000, 300_000L), limited(20_000, 700_000L));

        assertThat(CardPerformanceAnalyzer.monthlyLimit(rules, 100_000)).hasValue(5_000);
        assertThat(CardPerformanceAnalyzer.monthlyLimit(rules, 300_000)).hasValue(15_000);
        assertThat(CardPerformanceAnalyzer.monthlyLimit(rules, 800_000)).hasValue(35_000);
    }

    @Test
    @DisplayName("전월실적 상한(미만)을 넘은 규칙은 센 한도에서 뺀다")
    void ruleAboveTheUpperBoundIsNotApplicable() {
        List<CalculationRule> rules = List.of(rule(10_000L, null, null, null, 300_000L, true), limited(5_000, null));

        assertThat(CardPerformanceAnalyzer.monthlyLimit(rules, 100_000)).hasValue(15_000);
        assertThat(CardPerformanceAnalyzer.monthlyLimit(rules, 300_000)).hasValue(5_000);
    }

    @Test
    @DisplayName("같은 한도 묶음은 공유 한도를 한 번만 더하고, 묶음 안에서는 가장 작은 공유 한도를 쓴다")
    void sharedLimitIsCountedOncePerGroup() {
        List<CalculationRule> rules = List.of(rule(null, "G1", 20_000L, null, null, true),
            rule(null, "G1", 15_000L, null, null, true), rule(null, "G2", 8_000L, null, null, true),
            limited(1_000, null));

        assertThat(CardPerformanceAnalyzer.monthlyLimit(rules, 0)).hasValue(15_000 + 8_000 + 1_000);
    }

    @Test
    @DisplayName("묶음의 규칙마다 월 한도가 있으면 규칙별 한도의 합과 공유 한도 중 작은 값을 쓴다")
    void groupLimitIsTheSmallerOfRuleLimitsAndTheSharedLimit() {
        // 규칙별 5천원 2개(합 1만원)와 공유 한도 2만원 -> 1만원
        List<CalculationRule> sumBelowShared = List.of(rule(5_000L, "G1", 20_000L, null, null, true),
            rule(5_000L, "G1", 20_000L, null, null, true));
        assertThat(CardPerformanceAnalyzer.monthlyLimit(sumBelowShared, 0)).hasValue(10_000);

        // 규칙별 15,000원 2개(합 3만원)와 공유 한도 2만원 -> 2만원
        List<CalculationRule> sumAboveShared = List.of(rule(15_000L, "G1", 20_000L, null, null, true),
            rule(15_000L, "G1", 20_000L, null, null, true));
        assertThat(CardPerformanceAnalyzer.monthlyLimit(sumAboveShared, 0)).hasValue(20_000);
    }

    @Test
    @DisplayName("묶음 안에 월 한도가 없는 규칙이 있으면 규칙별 상한이 없어 공유 한도만 쓴다")
    void groupWithARuleWithoutALimitUsesTheSharedLimit() {
        List<CalculationRule> rules = List.of(rule(5_000L, "G1", 20_000L, null, null, true),
            rule(null, "G1", 20_000L, null, null, true));

        assertThat(CardPerformanceAnalyzer.monthlyLimit(rules, 0)).hasValue(20_000);
    }

    @Test
    @DisplayName("한도가 없거나 미확인인 규칙이 하나라도 있으면 한도를 알 수 없어 비어 있다")
    void unknownLimitMakesTheTotalUnknown() {
        List<CalculationRule> rules = List.of(limited(5_000, null), rule(null, null, null, null, null, true));

        assertThat(CardPerformanceAnalyzer.monthlyLimit(rules, 0)).isEmpty();
    }

    @Test
    @DisplayName("센 규칙이 없거나 확인된 규칙이 없으면 비어 있다")
    void noApplicableRules() {
        assertThat(CardPerformanceAnalyzer.monthlyLimit(List.of(), 0)).isEmpty();
        assertThat(CardPerformanceAnalyzer.monthlyLimit(List.of(rule(5_000L, null, null, null, null, false)), 0)).isEmpty();
        assertThat(CardPerformanceAnalyzer.monthlyLimit(List.of(limited(5_000, 300_000L)), 100_000)).isEmpty();
    }

    @Test
    @DisplayName("채운 가장 높은 구간과 아직 못 채운 가장 낮은 구간을 찾는다")
    void achievedAndNextTier() {
        List<Long> tiers = List.of(300_000L, 700_000L);

        assertThat(CardPerformanceAnalyzer.achievedTier(tiers, 299_999)).isEmpty();
        assertThat(CardPerformanceAnalyzer.achievedTier(tiers, 300_000)).hasValue(300_000);
        assertThat(CardPerformanceAnalyzer.achievedTier(tiers, 900_000)).hasValue(700_000);
        assertThat(CardPerformanceAnalyzer.nextTier(tiers, 100_000)).hasValue(300_000);
        assertThat(CardPerformanceAnalyzer.nextTier(tiers, 300_000)).hasValue(700_000);
        assertThat(CardPerformanceAnalyzer.nextTier(tiers, 700_000)).isEmpty();
        assertThat(CardPerformanceAnalyzer.achievedTier(List.of(), 100)).isEmpty();
    }

}
