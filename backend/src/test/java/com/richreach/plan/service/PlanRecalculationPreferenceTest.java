package com.richreach.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.entity.MonthlyPlan;
import com.richreach.plan.entity.PlanChange;
import com.richreach.plan.entity.PlanChangeReason;
import com.richreach.plan.entity.PlanStrategy;
import com.richreach.plan.entity.SolverStatus;
import com.richreach.plan.repository.MonthlyPlanRepository;
import com.richreach.plan.repository.PlanCardAllocationRepository;
import com.richreach.plan.repository.PlanChangeItemRepository;
import com.richreach.plan.repository.PlanChangeRepository;
import com.richreach.plan.repository.SpendingProfileItemRepository;
import com.richreach.plan.repository.SpendingProfileRepository;
import com.richreach.plan.service.PlanBaselineReader.Baseline;
import com.richreach.plan.service.PlanRecalculationComputer.Computation;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PlanRecalculationPreferenceTest {
    private final MonthlyPlanRepository plans = mock(MonthlyPlanRepository.class);
    private final PlanChangeRepository changes = mock(PlanChangeRepository.class);
    private final PlanRecalculationSaver saver = new PlanRecalculationSaver(plans,
        mock(PlanCardAllocationRepository.class), changes, mock(PlanChangeItemRepository.class),
        mock(SpendingProfileRepository.class), mock(SpendingProfileItemRepository.class));

    @Test
    void preferenceOnlyChangeCreatesNewPlanVersion() {
        Computation computation = unchangedComputation();
        when(plans.findFirstByUserIdAndPlanMonthAndStrategyOrderByVersionDesc(7L, "2026-11",
            PlanStrategy.CP_SAT)).thenReturn(Optional.of(computation.baseline().plan()));
        when(plans.save(any(MonthlyPlan.class))).thenAnswer(call -> {
            MonthlyPlan plan = call.getArgument(0);
            ReflectionTestUtils.setField(plan, "id", 52L);
            return plan;
        });
        when(changes.save(any(PlanChange.class))).thenAnswer(call -> {
            PlanChange change = call.getArgument(0);
            ReflectionTestUtils.setField(change, "id", 11L);
            return change;
        });

        long id = saver.save(7L, YearMonth.of(2026, 11), computation,
            PlanChangeReason.CHAT_CONFIRMED, "IMMEDIATE_DISCOUNT");

        ArgumentCaptor<MonthlyPlan> saved = ArgumentCaptor.forClass(MonthlyPlan.class);
        verify(plans).save(saved.capture());
        assertThat(id).isEqualTo(52L);
        assertThat(saved.getValue().effectiveRecommendationPreference()).isEqualTo("IMMEDIATE_DISCOUNT");
        assertThat(saved.getValue().getVersion()).isEqualTo(2);
        verify(changes).save(any(PlanChange.class));
    }

    @Test
    void unchangedPreferenceAndPlanDoNotCreateVersion() {
        long id = saver.save(7L, YearMonth.of(2026, 11), unchangedComputation(),
            PlanChangeReason.CHAT_CONFIRMED, "MONTHLY_PLAN");
        assertThat(id).isEqualTo(51L);
        verify(plans, never()).save(any());
    }

    private static Computation unchangedComputation() {
        YearMonth month = YearMonth.of(2026, 11);
        PlanBudget budget = new PlanBudget(month, 100_000L, Map.of(SpendingCategory.DINING, 100_000L));
        MonthlyPlan plan = MonthlyPlan.create(7L, 1L, month, PlanStrategy.CP_SAT, 1, 100_000L, 0L);
        ReflectionTestUtils.setField(plan, "id", 51L);
        Baseline baseline = new Baseline(plan, budget, Map.of());
        return new Computation(true, null, null, baseline, budget, Map.of(), Map.of(), 100_000L,
            0L, SolverStatus.OPTIMAL);
    }
}
