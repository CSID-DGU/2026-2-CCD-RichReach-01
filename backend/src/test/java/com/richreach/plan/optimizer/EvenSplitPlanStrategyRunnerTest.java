package com.richreach.plan.optimizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.benefit.dto.BenefitResult;
import com.richreach.benefit.service.BenefitCalculator;
import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.entity.PlanStrategy;
import com.richreach.plan.optimizer.PlanStrategyInput.CardInput;
import com.richreach.plan.optimizer.PlanStrategyResult.Allocation;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 임시 균등 배분 전략을 DB 없이 확인한다. 업종마다 배분 합이 예산과 같아야 하고(총소비 불변), 같은 입력이면 같은 결과여야 한다. */
class EvenSplitPlanStrategyRunnerTest {

    private static final YearMonth MONTH = YearMonth.of(2026, 12);

    /** 소비의 1%를 혜택으로 주는 가짜 계산기. */
    private static final BenefitCalculator ONE_PERCENT = query -> {
        Map<SpendingCategory, Long> benefit = new java.util.EnumMap<>(SpendingCategory.class);
        query.monthlySpend().forEach((category, amount) -> benefit.put(category, amount / 100));
        long total = benefit.values().stream().mapToLong(Long::longValue).sum();
        return new BenefitResult(total, benefit, true, List.of());
    };

    private static PlanStrategyInput input(Map<SpendingCategory, Long> budget, CardInput... cards) {
        return new PlanStrategyInput(1L, MONTH, budget, List.of(cards), Duration.ofSeconds(5));
    }

    private final EvenSplitPlanStrategyRunner runner = new EvenSplitPlanStrategyRunner(ONE_PERCENT);

    @Test
    @DisplayName("전략 이름은 EVEN_SPLIT이다")
    void strategyName() {
        assertThat(runner.strategy()).isEqualTo(PlanStrategy.EVEN_SPLIT);
    }

    @Test
    @DisplayName("업종 예산을 카드들에 똑같이 나누고, 배분 합이 예산과 같다")
    void splitsBudgetEvenlyAcrossCards() {
        PlanStrategyInput input = input(Map.of(SpendingCategory.DINING, 300_000L, SpendingCategory.CAFE, 100_000L),
            new CardInput(10, 100, 0), new CardInput(20, 200, 0));

        PlanStrategyResult result = runner.run(input);

        assertThat(result.success()).isTrue();
        assertThat(result.violations(input)).isEmpty();
        assertThat(result.allocations()).contains(
            new Allocation(10, SpendingCategory.DINING, 150_000, 1_500),
            new Allocation(20, SpendingCategory.DINING, 150_000, 1_500),
            new Allocation(10, SpendingCategory.CAFE, 50_000, 500),
            new Allocation(20, SpendingCategory.CAFE, 50_000, 500));
        assertThat(result.expectedBenefit()).isEqualTo(4_000);
        assertThat(result.solverStatus()).isNull();
        assertThat(result.confirmedOnly()).isTrue();
    }

    @Test
    @DisplayName("나누어떨어지지 않으면 남는 금액을 카드 순서대로 1원씩 더 줘서 합이 정확히 맞는다")
    void distributesRemainderByCardOrder() {
        PlanStrategyInput input = input(Map.of(SpendingCategory.SHOPPING, 100_001L),
            new CardInput(10, 100, 0), new CardInput(20, 200, 0), new CardInput(30, 300, 0));

        PlanStrategyResult result = runner.run(input);

        assertThat(result.violations(input)).isEmpty();
        assertThat(result.allocations()).extracting(Allocation::userCardId, Allocation::targetAmount)
            .containsExactly(org.assertj.core.groups.Tuple.tuple(10L, 33_334L), org.assertj.core.groups.Tuple.tuple(20L, 33_334L),
                org.assertj.core.groups.Tuple.tuple(30L, 33_333L));
    }

    @Test
    @DisplayName("예산이 카드 수보다 작으면 금액이 0인 배분은 만들지 않는다")
    void skipsZeroAllocations() {
        PlanStrategyInput input = input(Map.of(SpendingCategory.ETC, 2L),
            new CardInput(10, 100, 0), new CardInput(20, 200, 0), new CardInput(30, 300, 0));

        PlanStrategyResult result = runner.run(input);

        assertThat(result.allocations()).hasSize(2).allSatisfy(a -> assertThat(a.targetAmount()).isEqualTo(1));
        assertThat(result.violations(input)).isEmpty();
    }

    @Test
    @DisplayName("카드가 한 장이면 모든 예산이 그 카드에 간다")
    void singleCardGetsEverything() {
        PlanStrategyInput input = input(Map.of(SpendingCategory.DINING, 70_000L, SpendingCategory.EDUCATION, 30_000L),
            new CardInput(10, 100, 0));

        PlanStrategyResult result = runner.run(input);

        assertThat(result.allocations()).extracting(Allocation::userCardId).containsOnly(10L);
        assertThat(result.violations(input)).isEmpty();
    }

    @Test
    @DisplayName("카드가 없으면 예외 없이 실패 결과를 돌려준다")
    void failsWithoutCards() {
        PlanStrategyResult result = runner.run(input(Map.of(SpendingCategory.DINING, 1_000L)));

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).isNotBlank();
    }

    @Test
    @DisplayName("계산기에는 카드 상품 ID와 그 카드의 전월 실적, 그 카드가 쓸 금액을 넘긴다")
    void passesCardProductAndPreviousSpendToCalculator() {
        java.util.List<com.richreach.benefit.dto.BenefitQuery> seen = new java.util.ArrayList<>();
        EvenSplitPlanStrategyRunner spying = new EvenSplitPlanStrategyRunner(query -> {
            seen.add(query);
            return ONE_PERCENT.calculate(query);
        });

        spying.run(input(Map.of(SpendingCategory.DINING, 100_000L),
            new CardInput(10, 111, 500_000), new CardInput(20, 222, 0)));

        assertThat(seen).hasSize(2);
        assertThat(seen.get(0).cardProductId()).isEqualTo(111);
        assertThat(seen.get(0).previousMonthSpend()).isEqualTo(500_000);
        assertThat(seen.get(1).cardProductId()).isEqualTo(222);
        assertThat(seen.get(0).monthlySpend()).containsEntry(SpendingCategory.DINING, 50_000L);
    }

    @Test
    @DisplayName("확인 전 규칙이 섞인 계산 결과가 있으면 결과도 확인 전(confirmedOnly=false)으로 표시하고 안내를 합친다")
    void propagatesUnconfirmedFlagAndNotes() {
        EvenSplitPlanStrategyRunner unconfirmed = new EvenSplitPlanStrategyRunner(
            query -> new BenefitResult(0, Map.of(), false, List.of("확인 전 규칙을 썼습니다.")));

        PlanStrategyResult result = unconfirmed.run(input(Map.of(SpendingCategory.DINING, 10_000L),
            new CardInput(10, 100, 0), new CardInput(20, 200, 0)));

        assertThat(result.confirmedOnly()).isFalse();
        assertThat(result.notes()).containsExactly("확인 전 규칙을 썼습니다.");
    }

    @Test
    @DisplayName("같은 입력이면 항상 같은 결과다")
    void isDeterministic() {
        PlanStrategyInput input = input(Map.of(SpendingCategory.DINING, 123_457L, SpendingCategory.CAFE, 8_001L),
            new CardInput(10, 100, 0), new CardInput(20, 200, 0), new CardInput(30, 300, 0));

        assertThat(runner.run(input).allocations()).isEqualTo(runner.run(input).allocations());
    }

}
