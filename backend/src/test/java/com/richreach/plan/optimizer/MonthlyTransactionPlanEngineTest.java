package com.richreach.plan.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richreach.benefit.dto.BenefitTransaction;
import com.richreach.benefit.dto.TransactionBenefitRule;
import com.richreach.benefit.service.TransactionBenefitCalculator;
import com.richreach.global.category.SpendingCategory;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MonthlyTransactionPlanEngineTest {
    private final MonthlyTransactionPlanEngine engine =
        new MonthlyTransactionPlanEngine(new TransactionBenefitCalculator());

    private static TransactionBenefitRule rule(String id, String rate, String category) {
        return new TransactionBenefitRule(id, id, new BigDecimal(rate), null, null, null,
            null, null, null, null, null, null, "KRW", true,
            TransactionBenefitRule.parse("{\"schema\":\"card-benefit-v1\",\"any\":[{\"category\":\""
                + category + "\"}],\"performance_exclusions\":[]}"));
    }

    private static BenefitTransaction transaction(String id, String timestamp, long amount,
                                                   String category) {
        return new BenefitTransaction(id, LocalDateTime.parse(timestamp), null, amount,
            "EXAMPLE", category, "PURCHASE", Map.of("exclusion_class", "NONE"));
    }

    private MonthlyTransactionPlanEngine.Input input(LocalDateTime asOf, long budget,
                                                      List<MonthlyTransactionPlanEngine.Observed> history,
                                                      List<MonthlyTransactionPlanEngine.Observed> committed) {
        return new MonthlyTransactionPlanEngine.Input(YearMonth.of(2026, 9), asOf, history, committed,
            List.of(
                new MonthlyTransactionPlanEngine.Card(101, 301, List.of(rule("a", "1", "CAFE")), 0L, Map.of()),
                new MonthlyTransactionPlanEngine.Card(102, 302, List.of(rule("b", "2", "CAFE")), 0L, Map.of())),
            Map.of(SpendingCategory.CAFE, budget), Duration.ofSeconds(2), false);
    }

    @Test
    void forecastsOnlyPreviousMonthAndRespectsConfirmedBudget() {
        var history = List.of(
            new MonthlyTransactionPlanEngine.Observed(transaction("aug1", "2026-08-02T10:00:00", 10_000, "CAFE"), 101),
            new MonthlyTransactionPlanEngine.Observed(transaction("aug2", "2026-08-20T10:00:00", 30_000, "CAFE"), 101));
        var committed = List.of(
            new MonthlyTransactionPlanEngine.Observed(transaction("sep1", "2026-09-03T10:00:00", 5_000, "CAFE"), 101));
        var result = engine.create(input(LocalDateTime.parse("2026-09-10T00:00:00"), 25_000, history, committed));

        assertEquals(YearMonth.of(2026, 8), result.sourceMonth());
        assertEquals(1, result.forecast().size());
        assertEquals("aug2", result.forecast().getFirst().sourceId());
        assertEquals(20_000, result.forecast().getFirst().transaction().amount());
        assertEquals(YearMonth.of(2026, 9), YearMonth.from(result.forecast().getFirst().transaction().approvedAt()));
        for (var plan : result.strategies().values()) {
            assertEquals(25_000, plan.allocations().stream().mapToLong(MonthlyTransactionPlanEngine.Allocation::amount).sum());
            assertEquals(1, plan.forecastAssignments().size());
            assertFalse(plan.forecastAssignments().stream().anyMatch(a -> a.transactionId().equals("sep1")));
        }
        assertEquals(250, result.strategies().get("HABIT").totalBenefit());
        assertEquals(450, result.strategies().get("IMMEDIATE").totalBenefit());
        assertEquals("CP_SAT", result.selectedStrategy());
        assertEquals(102, result.strategies().get("CP_SAT").forecastAssignments().getFirst().userCardId());
    }

    @Test
    void committedPaymentCannotBeReallocatedEvenWhenItExceedsBudget() {
        var history = List.of(new MonthlyTransactionPlanEngine.Observed(
            transaction("aug", "2026-08-20T10:00:00", 10_000, "CAFE"), 101));
        var committed = List.of(new MonthlyTransactionPlanEngine.Observed(
            transaction("sep", "2026-09-03T10:00:00", 15_000, "CAFE"), 101));
        var result = engine.create(input(LocalDateTime.parse("2026-09-10T00:00:00"), 10_000, history, committed));
        assertTrue(result.forecast().isEmpty());
        assertEquals(15_000, result.effectiveBudget().get(SpendingCategory.CAFE));
        assertEquals(15_000, result.strategies().get("CP_SAT").allocations().getFirst().amount());
        assertEquals(150, result.strategies().get("CP_SAT").totalBenefit());
    }

    @Test
    void rejectsFutureOrUnrelatedHistoryAndCardsWithoutRules() {
        var future = List.of(new MonthlyTransactionPlanEngine.Observed(
            transaction("oct", "2026-10-01T10:00:00", 10_000, "CAFE"), 101));
        assertThrows(IllegalArgumentException.class, () -> engine.create(input(
            LocalDateTime.parse("2026-09-01T00:00:00"), 10_000, future, List.of())));
        var history = List.of(new MonthlyTransactionPlanEngine.Observed(
            transaction("aug", "2026-08-01T10:00:00", 10_000, "CAFE"), 101));
        var invalid = new MonthlyTransactionPlanEngine.Input(YearMonth.of(2026, 9),
            LocalDateTime.parse("2026-09-01T00:00:00"), history, List.of(),
            List.of(new MonthlyTransactionPlanEngine.Card(101, 301, List.of(), null, Map.of())),
            Map.of(SpendingCategory.CAFE, 10_000L), Duration.ofSeconds(1), false);
        assertThrows(IllegalArgumentException.class, () -> engine.create(invalid));
    }

    @Test
    void processesMoreThanOneHundredPaymentsWithinTheDecisionBudget() {
        List<MonthlyTransactionPlanEngine.Observed> history = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            String time = LocalDateTime.of(2026, 8, 1 + i / 4, i % 4, 0).toString();
            history.add(new MonthlyTransactionPlanEngine.Observed(
                transaction("h" + i, time, 1_000, "CAFE"), 101));
        }
        var base = input(LocalDateTime.parse("2026-09-01T00:00:00"), 120_000, history, List.of());
        var bounded = new MonthlyTransactionPlanEngine.Input(base.month(), base.asOf(), base.history(),
            base.committed(), base.cards(), base.confirmedBudget(), Duration.ofMillis(200), false);
        var result = engine.create(bounded);
        assertEquals(120, result.forecast().size());
        assertEquals(120, result.strategies().get("CP_SAT").forecastAssignments().size());
        assertEquals(120_000, result.strategies().get("CP_SAT").allocations().stream()
            .mapToLong(MonthlyTransactionPlanEngine.Allocation::amount).sum());
        assertTrue(result.strategies().get("CP_SAT").fallback() > 0);
    }
}
