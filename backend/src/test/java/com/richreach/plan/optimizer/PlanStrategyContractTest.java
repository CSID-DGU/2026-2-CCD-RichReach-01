package com.richreach.plan.optimizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.entity.PlanStrategy;
import com.richreach.plan.entity.SolverStatus;
import com.richreach.plan.optimizer.PlanStrategyInput.CardInput;
import com.richreach.plan.optimizer.PlanStrategyResult.Allocation;
import com.richreach.plan.optimizer.cpsat.CpSatPlanStrategyRunner;
import com.richreach.benefit.config.BenefitProperties;
import com.richreach.card.service.BenefitRuleQueryService;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 플랜 전략의 약속(입력, 결과, 전략 등록)을 DB 없이 확인한다. 다른 전략을 만드는 사람이 같은 도구로 자기 결과를 확인할 수 있어야 한다. */
class PlanStrategyContractTest {

    private static final YearMonth MONTH = YearMonth.of(2026, 12);

    private static PlanStrategyInput input() {
        return new PlanStrategyInput(1L, MONTH,
            Map.of(SpendingCategory.DINING, 300_000L, SpendingCategory.CAFE, 100_000L),
            List.of(new CardInput(10, 100, 500_000), new CardInput(20, 200, 0)), Duration.ofSeconds(5));
    }

    /** 테스트용 가짜 전략. */
    private record FakeRunner(PlanStrategy strategy) implements PlanStrategyRunner {

        @Override
        public PlanStrategyResult run(PlanStrategyInput input) {
            return PlanStrategyResult.failure(null, null, "가짜");
        }
    }

    // ---- 입력 ----

    @Test
    @DisplayName("총예산은 업종 예산의 합이고, 입력은 바꿀 수 없다")
    void inputTotalAndImmutability() {
        PlanStrategyInput input = input();

        assertThat(input.totalBudget()).isEqualTo(400_000);
        assertThatThrownBy(() -> input.cards().add(new CardInput(30, 300, 0)))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> input.budgetByCategory().put(SpendingCategory.ETC, 1L))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("음수 예산, 음수 전월 실적, 0 이하의 시간 제한은 거부한다")
    void inputRejectsInvalidValues() {
        assertThatThrownBy(() -> new PlanStrategyInput(1L, MONTH, Map.of(SpendingCategory.DINING, -1L), List.of(),
            Duration.ofSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CardInput(1, 1, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlanStrategyInput(1L, MONTH, Map.of(), List.of(), Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlanStrategyInput(1L, MONTH, Map.of(), List.of(), Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- 결과 ----

    @Test
    @DisplayName("성공 결과의 예상 혜택 합계는 배분의 예상 혜택을 더한 값이다")
    void successSumsAllocationBenefits() {
        PlanStrategyResult result = PlanStrategyResult.success(List.of(
            new Allocation(10, SpendingCategory.DINING, 300_000, 15_000),
            new Allocation(20, SpendingCategory.CAFE, 100_000, 3_000)), SolverStatus.OPTIMAL, 120, true, List.of());

        assertThat(result.success()).isTrue();
        assertThat(result.expectedBenefit()).isEqualTo(18_000);
        assertThat(result.solverStatus()).isEqualTo(SolverStatus.OPTIMAL);
        assertThat(result.failureReason()).isNull();
    }

    @Test
    @DisplayName("실패 결과는 이유가 필요하고, 배분과 혜택은 없다")
    void failureNeedsReason() {
        PlanStrategyResult failure = PlanStrategyResult.failure(SolverStatus.INFEASIBLE, 30, "조건이 충돌합니다.");

        assertThat(failure.success()).isFalse();
        assertThat(failure.allocations()).isEmpty();
        assertThat(failure.expectedBenefit()).isZero();
        assertThatThrownBy(() -> PlanStrategyResult.failure(null, null, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlanStrategyResult.failure(null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("같은 카드와 업종의 배분이 두 번 있거나 금액이 음수이면 거부한다")
    void successRejectsDuplicateOrNegative() {
        assertThatThrownBy(() -> PlanStrategyResult.success(List.of(
            new Allocation(10, SpendingCategory.DINING, 1, 0), new Allocation(10, SpendingCategory.DINING, 2, 0)),
            null, null, true, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Allocation(10, SpendingCategory.DINING, -1, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Allocation(10, SpendingCategory.DINING, 0, -1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- 약속 확인 ----

    @Test
    @DisplayName("업종마다 배분 합이 예산과 같으면 어긴 것이 없다")
    void respectingTotalSpendHasNoViolations() {
        PlanStrategyResult result = PlanStrategyResult.success(List.of(
            new Allocation(10, SpendingCategory.DINING, 200_000, 0), new Allocation(20, SpendingCategory.DINING, 100_000, 0),
            new Allocation(20, SpendingCategory.CAFE, 100_000, 0)), null, null, true, List.of());

        assertThat(result.violations(input())).isEmpty();
    }

    @Test
    @DisplayName("배분 합이 예산과 다르면 총소비가 달라졌다고 알린다 (모자란 경우, 넘친 경우, 예산 없는 업종에 배분한 경우)")
    void violationsReportChangedTotalSpend() {
        PlanStrategyResult less = PlanStrategyResult.success(List.of(
            new Allocation(10, SpendingCategory.DINING, 299_000, 0), new Allocation(20, SpendingCategory.CAFE, 100_000, 0)),
            null, null, true, List.of());
        PlanStrategyResult more = PlanStrategyResult.success(List.of(
            new Allocation(10, SpendingCategory.DINING, 300_000, 0), new Allocation(20, SpendingCategory.CAFE, 100_001, 0)),
            null, null, true, List.of());
        PlanStrategyResult unplanned = PlanStrategyResult.success(List.of(
            new Allocation(10, SpendingCategory.DINING, 300_000, 0), new Allocation(20, SpendingCategory.CAFE, 100_000, 0),
            new Allocation(10, SpendingCategory.ETC, 1_000, 0)), null, null, true, List.of());

        assertThat(less.violations(input())).singleElement().asString().contains("DINING");
        assertThat(more.violations(input())).singleElement().asString().contains("CAFE");
        assertThat(unplanned.violations(input())).singleElement().asString().contains("ETC");
    }

    @Test
    @DisplayName("입력에 없는 카드를 쓰면 알린다")
    void violationsReportUnknownCards() {
        PlanStrategyResult result = PlanStrategyResult.success(List.of(
            new Allocation(999, SpendingCategory.DINING, 300_000, 0), new Allocation(20, SpendingCategory.CAFE, 100_000, 0)),
            null, null, true, List.of());

        assertThat(result.violations(input())).singleElement().asString().contains("999");
    }

    @Test
    @DisplayName("실패 결과는 어긴 것이 없는 것으로 본다")
    void failureHasNoViolations() {
        assertThat(PlanStrategyResult.failure(null, null, "실패").violations(input())).isEmpty();
    }

    // ---- 전략 등록 ----

    @Test
    @DisplayName("전략 이름으로 구현체를 찾고, 없는 전략은 비어 있다")
    void registryFindsRunnersByStrategy() {
        FakeRunner greedy = new FakeRunner(PlanStrategy.GREEDY);
        PlanStrategyRegistry registry = new PlanStrategyRegistry(List.of(greedy));

        assertThat(registry.find(PlanStrategy.GREEDY)).containsSame(greedy);
        assertThat(registry.find(PlanStrategy.CP_SAT)).isEmpty();
    }

    @Test
    @DisplayName("같은 전략의 구현체가 둘이면 시작할 때 오류로 알린다")
    void registryRejectsDuplicateStrategies() {
        assertThatThrownBy(() -> new PlanStrategyRegistry(
            List.of(new FakeRunner(PlanStrategy.GREEDY), new FakeRunner(PlanStrategy.GREEDY))))
            .isInstanceOf(IllegalStateException.class);
    }

    // ---- CP-SAT 전략 등록 ----

    @Test
    @DisplayName("CP-SAT 실행기가 CP_SAT 전략으로 등록된다")
    void cpSatRunnerIsRegistered() {
        CpSatPlanStrategyRunner runner = new CpSatPlanStrategyRunner(new BenefitRuleQueryService(null, null, null),
            query -> null,
            new BenefitProperties("simple", BenefitProperties.RuleSelection.READY_ONLY));
        PlanStrategyRegistry registry = new PlanStrategyRegistry(List.of(runner));

        assertThat(registry.find(PlanStrategy.CP_SAT)).containsSame(runner);
    }

}
