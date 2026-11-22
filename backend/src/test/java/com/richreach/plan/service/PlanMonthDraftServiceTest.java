package com.richreach.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.dto.PlanAnalysisResponse;
import com.richreach.plan.dto.PlanAnalysisResponse.CategoryAmount;
import com.richreach.plan.dto.PlanAnalysisResponse.SuggestedBudget;
import com.richreach.plan.dto.PlanCreateRequest;
import com.richreach.plan.dto.PlanCreateRequest.CategoryBudget;
import com.richreach.plan.dto.PlanMonthDraftResponse;
import com.richreach.plan.entity.MonthlyPlan;
import com.richreach.plan.repository.MonthlyPlanRepository;
import com.richreach.transaction.dto.CardScope;
import com.richreach.transaction.dto.SummaryPeriod;
import com.richreach.transaction.dto.TransactionSummaryResponse;
import com.richreach.transaction.service.TransactionSummaryService;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 새 달 예산 초안이 지난달 확정 예산, 최근 소비 추천, 없음 순서로 나오고 월 경계에서 어긋나지 않는지 확인한다. */
class PlanMonthDraftServiceTest {

    private static final long USER = 1L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PlanQueryService planQuery = mock(PlanQueryService.class);
    private final SpendingAnalysisService analysis = mock(SpendingAnalysisService.class);
    private final TransactionSummaryService transactions = mock(TransactionSummaryService.class);
    private final MonthlyPlanRepository plans = mock(MonthlyPlanRepository.class);

    private PlanMonthDraftService service(String instant) {
        return new PlanMonthDraftService(planQuery, analysis, transactions, plans,
            Clock.fixed(Instant.parse(instant), KST));
    }

    private static PlanCreateRequest.Budget budget(long total) {
        return new PlanCreateRequest.Budget(total, List.of(new CategoryBudget("DINING", total)));
    }

    private void previousSpent(YearMonth month, long amount) {
        TransactionSummaryResponse summary = mock(TransactionSummaryResponse.class);
        when(summary.totalAmount()).thenReturn(amount);
        when(transactions.summarize(USER, month, CardScope.SELECTED)).thenReturn(summary);
    }

    @Test
    @DisplayName("지난달에 확정한 예산이 있으면 그것을 이번 달 초안으로 준다")
    void usesThePreviousConfirmedBudget() {
        previousSpent(YearMonth.of(2026, 11), 450_000);
        when(planQuery.findConfirmedBudget(USER, YearMonth.of(2026, 11))).thenReturn(Optional.of(budget(500_000)));

        PlanMonthDraftResponse draft = service("2026-12-01T00:30:00Z").draft(USER);

        assertThat(draft.targetMonth()).isEqualTo("2026-12");
        assertThat(draft.previousMonth()).isEqualTo("2026-11");
        assertThat(draft.source()).isEqualTo(PlanMonthDraftResponse.SOURCE_PREVIOUS_BUDGET);
        assertThat(draft.budget()).isEqualTo(budget(500_000));
        assertThat(draft.previousMonthSpent()).isEqualTo(450_000);
        assertThat(draft.planExists()).isFalse();
        verify(analysis, never()).analyze(eq(USER), any());
    }

    @Test
    @DisplayName("지난달 플랜이 없으면 최근 소비로 추천한 예산을 준다")
    void fallsBackToTheAnalysis() {
        previousSpent(YearMonth.of(2026, 11), 300_000);
        when(planQuery.findConfirmedBudget(USER, YearMonth.of(2026, 11))).thenReturn(Optional.empty());
        when(analysis.analyze(USER, SummaryPeriod.THREE_MONTHS)).thenReturn(new PlanAnalysisResponse(3, 320_000,
            SpendingCategory.DINING, true, false, List.of(),
            new SuggestedBudget(320_000, List.of(new CategoryAmount(SpendingCategory.DINING, 320_000)))));

        PlanMonthDraftResponse draft = service("2026-12-01T00:30:00Z").draft(USER);

        assertThat(draft.source()).isEqualTo(PlanMonthDraftResponse.SOURCE_ANALYSIS);
        assertThat(draft.budget()).isEqualTo(budget(320_000));
    }

    @Test
    @DisplayName("지난달 예산도 소비도 없으면 NONE이고 예산은 null이다")
    void noBasisMeansNone() {
        previousSpent(YearMonth.of(2026, 11), 0);
        when(planQuery.findConfirmedBudget(USER, YearMonth.of(2026, 11))).thenReturn(Optional.empty());
        when(analysis.analyze(USER, SummaryPeriod.THREE_MONTHS)).thenReturn(new PlanAnalysisResponse(1, 0, null,
            false, false, List.of(), new SuggestedBudget(0, List.of())));

        PlanMonthDraftResponse draft = service("2026-12-01T00:30:00Z").draft(USER);

        assertThat(draft.source()).isEqualTo(PlanMonthDraftResponse.SOURCE_NONE);
        assertThat(draft.budget()).isNull();
        assertThat(draft.previousMonthSpent()).isZero();
    }

    @Test
    @DisplayName("12월에서 1월로 넘어가면 지난달은 해를 넘겨 12월이다")
    void crossesTheYearBoundary() {
        previousSpent(YearMonth.of(2026, 12), 100_000);
        when(planQuery.findConfirmedBudget(USER, YearMonth.of(2026, 12))).thenReturn(Optional.of(budget(200_000)));

        // 2027-01-01 00:10 KST
        PlanMonthDraftResponse draft = service("2026-12-31T15:10:00Z").draft(USER);

        assertThat(draft.targetMonth()).isEqualTo("2027-01");
        assertThat(draft.previousMonth()).isEqualTo("2026-12");
        assertThat(draft.budget()).isEqualTo(budget(200_000));
    }

    @Test
    @DisplayName("월의 마지막 날 밤에는 아직 이번 달이다 (한국 시간 기준)")
    void usesTheServerClockZone() {
        previousSpent(YearMonth.of(2026, 10), 0);
        when(planQuery.findConfirmedBudget(USER, YearMonth.of(2026, 10))).thenReturn(Optional.empty());
        when(analysis.analyze(USER, SummaryPeriod.THREE_MONTHS)).thenReturn(new PlanAnalysisResponse(1, 0, null,
            false, false, List.of(), new SuggestedBudget(0, List.of())));

        // 2026-11-30 23:50 KST
        PlanMonthDraftResponse draft = service("2026-11-30T14:50:00Z").draft(USER);

        assertThat(draft.targetMonth()).isEqualTo("2026-11");
    }

    @Test
    @DisplayName("이번 달 플랜이 이미 있으면 planExists로 알린다")
    void reportsAnExistingPlan() {
        previousSpent(YearMonth.of(2026, 11), 0);
        when(planQuery.findConfirmedBudget(USER, YearMonth.of(2026, 11))).thenReturn(Optional.of(budget(500_000)));
        when(plans.findByUserIdAndPlanMonth(USER, "2026-12")).thenReturn(List.of(mock(MonthlyPlan.class)));

        assertThat(service("2026-12-01T00:30:00Z").draft(USER).planExists()).isTrue();
    }

}
