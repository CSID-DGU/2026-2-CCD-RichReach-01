package com.richreach.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.config.PlanProperties;
import com.richreach.plan.entity.MonthlyPlan;
import com.richreach.plan.entity.PlanCardAllocation;
import com.richreach.plan.entity.PlanStrategy;
import com.richreach.plan.entity.SpendingProfileItem;
import com.richreach.plan.repository.MonthlyPlanRepository;
import com.richreach.plan.repository.PlanCardAllocationRepository;
import com.richreach.plan.repository.PlanJobRepository;
import com.richreach.plan.repository.SpendingProfileItemRepository;
import com.richreach.transaction.service.TransactionSummaryService;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 플랜 조회의 추가 혜택 계산과 최신 version 선택을 DB 없이 확인한다. */
class PlanQueryServiceTest {

    @Test
    @DisplayName("챗봇에 전달할 확정 예산은 예정지출이 포함된 카드 목표액이 아닌 소비 프로필에서 읽는다")
    void confirmedBudgetExcludesPlannedExpenseOverlay() {
        MonthlyPlanRepository plans = mock(MonthlyPlanRepository.class);
        PlanCardAllocationRepository allocations = mock(PlanCardAllocationRepository.class);
        SpendingProfileItemRepository profileItems = mock(SpendingProfileItemRepository.class);
        OwnedCardQueryService cards = mock(OwnedCardQueryService.class);
        TransactionSummaryService transactions = mock(TransactionSummaryService.class);
        PlanJobRepository jobs = mock(PlanJobRepository.class);
        PlanProperties properties = mock(PlanProperties.class);
        MonthlyPlan plan = mock(MonthlyPlan.class);
        SpendingProfileItem cafe = mock(SpendingProfileItem.class);
        PlanCardAllocation target = mock(PlanCardAllocation.class);

        when(plan.getStrategy()).thenReturn(PlanStrategy.CP_SAT);
        when(plan.getVersion()).thenReturn(1);
        when(plan.getId()).thenReturn(51L);
        when(plan.getProfileId()).thenReturn(17L);
        when(plan.getPlanMonth()).thenReturn("2026-11");
        when(plan.effectiveRecommendationPreference()).thenReturn("MONTHLY_PLAN");
        when(plans.findByUserIdAndPlanMonth(1L, "2026-11")).thenReturn(List.of(plan));
        when(cafe.getCategory()).thenReturn(SpendingCategory.CAFE);
        when(cafe.getExpectedAmount()).thenReturn(100_000L);
        when(profileItems.findByProfileId(17L)).thenReturn(List.of(cafe));
        when(target.getUserCardId()).thenReturn(3L);
        when(target.getTargetAmount()).thenReturn(130_000L);
        when(allocations.findByPlanId(51L)).thenReturn(List.of(target));

        PlanQueryService service = new PlanQueryService(plans, allocations, cards, transactions, jobs,
            profileItems, properties, Clock.systemUTC());
        var response = service.getMonthlyPlan(1L, "2026-11");

        assertThat(response.cardTargets().getFirst().targetAmount()).isEqualTo(130_000L);
        assertThat(response.confirmedBudget().total()).isEqualTo(100_000L);
        assertThat(response.confirmedBudget().byCategory()).hasSize(SpendingCategory.values().length)
            .anySatisfy(item -> {
                assertThat(item.category()).isEqualTo("CAFE");
                assertThat(item.amount()).isEqualTo(100_000L);
            });
    }

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
