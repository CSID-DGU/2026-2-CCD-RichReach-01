package com.richreach.plan.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.plan.entity.MonthlyPlan;
import com.richreach.plan.entity.PlanStrategy;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 플랜 조회의 추가 혜택 계산과 최신 version 선택을 DB 없이 확인한다. */
class PlanQueryServiceTest {

    private static MonthlyPlan plan(PlanStrategy strategy, int version, long benefit) {
        return MonthlyPlan.create(1L, 1L, YearMonth.of(2026, 12), strategy, version, 1_000_000, benefit);
    }

    @Test
    @DisplayName("추가 혜택은 예상 혜택에서 기존 예상(HABIT)을 뺀 값이다")
    void additionalBenefitIsDifference() {
        assertThat(PlanQueryService.additionalBenefit(32_400, 21_600L)).isEqualTo(10_800L);
        assertThat(PlanQueryService.additionalBenefit(10_000, 10_000L)).isZero();
    }

    @Test
    @DisplayName("기존 예상이 없으면 추가 혜택도 null이다 (0으로 내리지 않는다)")
    void additionalBenefitIsNullWithoutBaseline() {
        assertThat(PlanQueryService.additionalBenefit(32_400, null)).isNull();
    }

    @Test
    @DisplayName("기존 예상보다 혜택이 작으면 추가 혜택은 음수 그대로다")
    void additionalBenefitCanBeNegative() {
        assertThat(PlanQueryService.additionalBenefit(5_000, 8_000L)).isEqualTo(-3_000L);
    }

    @Test
    @DisplayName("전략마다 가장 최신 version의 플랜만 남긴다")
    void keepsLatestVersionPerStrategy() {
        MonthlyPlan v1 = plan(PlanStrategy.GREEDY, 1, 100);
        MonthlyPlan v3 = plan(PlanStrategy.GREEDY, 3, 300);
        MonthlyPlan v2 = plan(PlanStrategy.GREEDY, 2, 200);
        MonthlyPlan habit = plan(PlanStrategy.HABIT, 1, 50);

        Map<PlanStrategy, MonthlyPlan> latest = PlanQueryService.latestByStrategy(List.of(v1, v3, v2, habit));

        assertThat(latest).hasSize(2);
        assertThat(latest.get(PlanStrategy.GREEDY)).isSameAs(v3);
        assertThat(latest.get(PlanStrategy.HABIT)).isSameAs(habit);
    }

    @Test
    @DisplayName("플랜이 없으면 비어 있다")
    void emptyWhenNoPlans() {
        assertThat(PlanQueryService.latestByStrategy(List.of())).isEmpty();
    }

}
