package com.richreach.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.benefit.dto.BenefitQuery;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.benefit.service.BenefitCalculator;
import com.richreach.card.dto.CalculationRule;
import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.service.BenefitRuleQueryService;
import com.richreach.card.service.CardPerformanceQueryService;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.plan.dto.PlanCardTargetsResponse;
import com.richreach.plan.dto.PlanCardTargetsResponse.CardDetail;
import com.richreach.plan.entity.MonthlyPlan;
import com.richreach.plan.entity.PlanCardAllocation;
import com.richreach.plan.entity.PlanStrategy;
import com.richreach.plan.repository.MonthlyPlanRepository;
import com.richreach.plan.repository.PlanCardAllocationRepository;
import com.richreach.transaction.service.TransactionSummaryService;
import com.richreach.transaction.service.TransactionBenefitService;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** 카드별 상세가 플랜 목표, 이번 달 사용 금액, 혜택 규칙을 합치는 방식을 DB 없이 확인한다. */
class PlanCardTargetsServiceTest {

    private static final long USER = 1L;
    private static final long PLAN = 50L;
    private static final YearMonth MONTH = YearMonth.of(2026, 11);

    private MonthlyPlanRepository plans;
    private PlanCardAllocationRepository allocations;
    private OwnedCardQueryService ownedCards;
    private TransactionSummaryService transactions;
    private BenefitRuleQueryService rules;
    private CardPerformanceQueryService notes;
    private TransactionBenefitService transactionBenefits;
    private PlanCardTargetsService service;

    @BeforeEach
    void setUp() {
        plans = mock(MonthlyPlanRepository.class);
        allocations = mock(PlanCardAllocationRepository.class);
        ownedCards = mock(OwnedCardQueryService.class);
        transactions = mock(TransactionSummaryService.class);
        rules = mock(BenefitRuleQueryService.class);
        notes = mock(CardPerformanceQueryService.class);
        transactionBenefits = mock(TransactionBenefitService.class);
        service = new PlanCardTargetsService(plans, allocations, ownedCards, transactions, rules, notes,
            transactionBenefits);

        MonthlyPlan plan = MonthlyPlan.create(USER, 1L, MONTH, PlanStrategy.CP_SAT, 1, 500_000, 9_000);
        ReflectionTestUtils.setField(plan, "id", PLAN);
        when(plans.findById(PLAN)).thenReturn(Optional.of(plan));
        when(transactions.cardCategorySpend(USER, MONTH)).thenReturn(Map.of());
        when(transactions.cardSpend(USER, MONTH.minusMonths(1))).thenReturn(Map.of());
        when(rules.findForCalculation(anyLong())).thenReturn(List.of());
        when(notes.performanceExclusionNotes(anyLong())).thenReturn(List.of());
        when(transactionBenefits.monthTotal(anyLong(), any(), any())).thenReturn(0L);
    }

    private static PlanCardAllocation allocation(long card, SpendingCategory category, long target, long benefit) {
        return PlanCardAllocation.create(PLAN, card, category, target, benefit);
    }

    private static OwnedCardResponse card(long id, Long productId, String name) {
        return new OwnedCardResponse(id, productId, null, name, "1111", null,
            OwnedCardResponse.AnalysisStatus.UNAVAILABLE, true, null);
    }

    private static CalculationRule rule(Long monthlyLimit, Long min) {
        return new CalculationRule(1L, Set.of(), true, false, BigDecimal.ONE, "KRW", monthlyLimit, null, null, null,
            null, null, null, null, "BASE", min, null, true);
    }

    private CardDetail onlyCard() {
        PlanCardTargetsResponse response = service.cardTargets(USER, PLAN);
        assertThat(response.cards()).hasSize(1);
        return response.cards().get(0);
    }

    @Test
    @DisplayName("플랜의 목표, 이번 달 사용 금액, 남은 목표, 예상 혜택, 업종별 금액을 카드 한 장으로 합친다")
    void combinesPlanAndSpend() {
        when(allocations.findByPlanId(PLAN)).thenReturn(List.of(
            allocation(10, SpendingCategory.DINING, 300_000, 6_000), allocation(10, SpendingCategory.CAFE, 50_000, 1_500)));
        when(ownedCards.list(USER)).thenReturn(List.of(card(10, 100L, "카드A")));
        when(transactions.cardCategorySpend(USER, MONTH)).thenReturn(Map.of(10L,
            Map.of(SpendingCategory.DINING, 120_000L, SpendingCategory.TRANSPORT, 30_000L)));

        CardDetail card = onlyCard();

        assertThat(card.cardName()).isEqualTo("카드A");
        assertThat(card.targetAmount()).isEqualTo(350_000);
        assertThat(card.spentAmount()).isEqualTo(150_000);
        assertThat(card.remainingTarget()).isEqualTo(200_000);
        assertThat(card.expectedBenefit()).isEqualTo(7_500);
        // 목표가 있거나 쓴 업종을 업종 고정 순서로 모은다. 목표가 없는 업종은 목표와 혜택이 0이다.
        assertThat(card.categories()).extracting(PlanCardTargetsResponse.CardCategoryAmount::category)
            .containsExactlyInAnyOrder(SpendingCategory.DINING, SpendingCategory.CAFE, SpendingCategory.TRANSPORT);
        PlanCardTargetsResponse.CardCategoryAmount transport = card.categories().stream()
            .filter(c -> c.category() == SpendingCategory.TRANSPORT).findFirst().orElseThrow();
        assertThat(transport.targetAmount()).isZero();
        assertThat(transport.spentAmount()).isEqualTo(30_000);
        assertThat(transport.expectedBenefit()).isZero();
    }

    @Test
    @DisplayName("목표보다 많이 쓰면 남은 목표는 음수가 아니라 0이다")
    void remainingTargetNeverNegative() {
        when(allocations.findByPlanId(PLAN)).thenReturn(List.of(allocation(10, SpendingCategory.DINING, 100_000, 0)));
        when(ownedCards.list(USER)).thenReturn(List.of(card(10, 100L, "카드A")));
        when(transactions.cardCategorySpend(USER, MONTH)).thenReturn(Map.of(10L, Map.of(SpendingCategory.DINING, 180_000L)));

        assertThat(onlyCard().remainingTarget()).isZero();
    }

    @Test
    @DisplayName("지금까지 쓴 금액과 전월 사용액으로 혜택 계산기를 불러 예상 혜택을 구한다")
    void currentBenefitUsesSpendAndPreviousMonth() {
        when(allocations.findByPlanId(PLAN)).thenReturn(List.of(allocation(10, SpendingCategory.DINING, 300_000, 0)));
        when(ownedCards.list(USER)).thenReturn(List.of(card(10, 100L, "카드A")));
        when(transactions.cardCategorySpend(USER, MONTH)).thenReturn(Map.of(10L, Map.of(SpendingCategory.DINING, 120_000L)));
        when(transactions.cardSpend(USER, MONTH.minusMonths(1))).thenReturn(Map.of(10L, 700_000L));
        when(rules.findForCalculation(100L)).thenReturn(List.of(rule(5_000L, null)));
        when(transactionBenefits.monthTotal(USER, MONTH, List.of(10L))).thenReturn(2_400L);

        CardDetail card = onlyCard();

        verify(transactionBenefits).monthTotal(USER, MONTH, List.of(10L));
        assertThat(card.currentBenefit()).isEqualTo(2_400L);
        assertThat(card.benefitLimit().usedAmount()).isEqualTo(2_400L);
    }

    @Test
    @DisplayName("전월실적 구간과 이번 달 실적: 이번 달 혜택 구간, 달성한 구간, 다음 구간까지 남은 금액, 정확하지 않음 표시")
    void performanceTiers() {
        when(allocations.findByPlanId(PLAN)).thenReturn(List.of(allocation(10, SpendingCategory.DINING, 900_000, 0)));
        when(ownedCards.list(USER)).thenReturn(List.of(card(10, 100L, "카드A")));
        when(transactions.cardCategorySpend(USER, MONTH)).thenReturn(Map.of(10L, Map.of(SpendingCategory.DINING, 350_000L)));
        when(transactions.cardSpend(USER, MONTH.minusMonths(1))).thenReturn(Map.of(10L, 310_000L));
        when(rules.findForCalculation(100L)).thenReturn(List.of(rule(5_000L, 300_000L), rule(10_000L, 700_000L)));
        when(notes.performanceExclusionNotes(100L)).thenReturn(List.of("상품권 구입액은 실적에서 제외"));

        PlanCardTargetsResponse.Performance performance = onlyCard().performance();

        assertThat(performance.tiers()).containsExactly(300_000L, 700_000L);
        assertThat(performance.previousMonthSpend()).isEqualTo(310_000L);
        assertThat(performance.appliedTier()).isEqualTo(300_000L);
        assertThat(performance.currentMonthAmount()).isEqualTo(350_000L);
        assertThat(performance.currentMonthAmountExact()).isFalse();
        assertThat(performance.achievedTier()).isEqualTo(300_000L);
        assertThat(performance.nextTier()).isEqualTo(700_000L);
        assertThat(performance.shortfallToNextTier()).isEqualTo(350_000L);
        assertThat(performance.exclusionNotes()).containsExactly("상품권 구입액은 실적에서 제외");
    }

    @Test
    @DisplayName("월 혜택 한도는 전월실적을 채운 규칙의 합이고, 남은 한도는 한도에서 지금까지 받는 혜택을 뺀 값이다")
    void benefitLimit() {
        when(allocations.findByPlanId(PLAN)).thenReturn(List.of(allocation(10, SpendingCategory.DINING, 300_000, 0)));
        when(ownedCards.list(USER)).thenReturn(List.of(card(10, 100L, "카드A")));
        when(transactions.cardSpend(USER, MONTH.minusMonths(1))).thenReturn(Map.of(10L, 310_000L));
        when(rules.findForCalculation(100L)).thenReturn(List.of(rule(5_000L, null), rule(10_000L, 300_000L),
            rule(20_000L, 700_000L)));
        when(transactionBenefits.monthTotal(USER, MONTH, List.of(10L))).thenReturn(4_000L);

        PlanCardTargetsResponse.BenefitLimit limit = onlyCard().benefitLimit();

        assertThat(limit.monthlyLimit()).isEqualTo(15_000L);
        assertThat(limit.usedAmount()).isEqualTo(4_000L);
        assertThat(limit.remainingAmount()).isEqualTo(11_000L);
    }

    @Test
    @DisplayName("이미 한도를 넘게 받는 것으로 계산되면 남은 한도는 0이다")
    void remainingLimitNeverNegative() {
        when(allocations.findByPlanId(PLAN)).thenReturn(List.of(allocation(10, SpendingCategory.DINING, 300_000, 0)));
        when(ownedCards.list(USER)).thenReturn(List.of(card(10, 100L, "카드A")));
        when(rules.findForCalculation(100L)).thenReturn(List.of(rule(1_000L, null)));
        when(transactionBenefits.monthTotal(USER, MONTH, List.of(10L))).thenReturn(3_000L);

        assertThat(onlyCard().benefitLimit().remainingAmount()).isZero();
    }

    @Test
    @DisplayName("카드 상품에는 매칭됐지만 혜택 규칙이 없는 카드는 혜택을 0이 아니라 null로 내려주고 계산기를 부르지 않는다")
    void matchedCardWithoutRulesHasUnknownBenefit() {
        when(allocations.findByPlanId(PLAN)).thenReturn(List.of(allocation(10, SpendingCategory.DINING, 300_000, 0)));
        when(ownedCards.list(USER)).thenReturn(List.of(card(10, 100L, "카드A")));
        when(transactions.cardCategorySpend(USER, MONTH)).thenReturn(Map.of(10L, Map.of(SpendingCategory.DINING, 120_000L)));
        when(rules.findForCalculation(100L)).thenReturn(List.of());

        CardDetail card = onlyCard();

        assertThat(card.currentBenefit()).isNull();
        assertThat(card.benefitLimit().usedAmount()).isNull();
        assertThat(card.benefitLimit().monthlyLimit()).isNull();
        assertThat(card.benefitLimit().remainingAmount()).isNull();
        verify(transactionBenefits, never()).monthTotal(anyLong(), any(), any());
    }

    @Test
    @DisplayName("카드 상품과 매칭되지 않은 카드는 혜택, 구간, 한도를 계산하지 않고 null 또는 빈 값으로 내려준다")
    void unmatchedCardHasUnknownValues() {
        when(allocations.findByPlanId(PLAN)).thenReturn(List.of(allocation(10, SpendingCategory.DINING, 300_000, 0)));
        when(ownedCards.list(USER)).thenReturn(List.of(card(10, null, "카드A")));

        CardDetail card = onlyCard();

        assertThat(card.currentBenefit()).isNull();
        assertThat(card.benefitLimit().monthlyLimit()).isNull();
        assertThat(card.benefitLimit().remainingAmount()).isNull();
        assertThat(card.performance().tiers()).isEmpty();
        assertThat(card.performance().exclusionNotes()).isEmpty();
        verify(transactionBenefits, never()).monthTotal(anyLong(), any(), any());
        verify(rules, never()).findForCalculation(anyLong());
    }

    @Test
    @DisplayName("보유카드를 찾을 수 없으면 카드 이름만 null이고 나머지는 플랜과 거래로 채운다")
    void deletedCardKeepsThePlanValues() {
        when(allocations.findByPlanId(PLAN)).thenReturn(List.of(allocation(10, SpendingCategory.DINING, 300_000, 0)));
        when(ownedCards.list(USER)).thenReturn(List.of());

        CardDetail card = onlyCard();

        assertThat(card.cardName()).isNull();
        assertThat(card.targetAmount()).isEqualTo(300_000);
        assertThat(card.currentBenefit()).isNull();
    }

    @Test
    @DisplayName("카드는 목표 금액이 큰 순서이고 같으면 카드 ID가 작은 쪽이 먼저다")
    void cardsAreSortedByTarget() {
        when(allocations.findByPlanId(PLAN)).thenReturn(List.of(allocation(30, SpendingCategory.DINING, 100_000, 0),
            allocation(20, SpendingCategory.DINING, 300_000, 0), allocation(10, SpendingCategory.DINING, 100_000, 0)));
        when(ownedCards.list(USER)).thenReturn(List.of());

        assertThat(service.cardTargets(USER, PLAN).cards()).extracting(CardDetail::cardId).containsExactly(20L, 10L, 30L);
    }

    @Test
    @DisplayName("없거나 다른 사용자의 플랜이면 PLAN-001이다")
    void foreignOrMissingPlan() {
        assertThatThrownBy(() -> service.cardTargets(2L, PLAN)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        assertThatThrownBy(() -> service.cardTargets(USER, 999L)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
    }

}
