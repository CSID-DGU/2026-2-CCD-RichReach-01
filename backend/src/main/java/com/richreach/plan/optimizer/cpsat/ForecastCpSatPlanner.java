package com.richreach.plan.optimizer.cpsat;

import com.google.ortools.Loader;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearArgument;
import com.google.ortools.sat.LinearExpr;
import com.richreach.benefit.config.BenefitProperties;
import com.richreach.benefit.dto.BenefitPurchase;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.benefit.service.BenefitCalculator;
import com.richreach.card.dto.CalculationRule;
import com.richreach.card.service.BenefitRuleQueryService;
import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.entity.SolverStatus;
import com.richreach.plan.optimizer.PlanStrategyInput;
import com.richreach.plan.optimizer.PlanStrategyInput.CardInput;
import com.richreach.plan.optimizer.PlanStrategyResult;
import com.richreach.plan.optimizer.PlanStrategyResult.Allocation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Assigns forecast purchase units without splitting a payment across cards. */
final class ForecastCpSatPlanner {

    private ForecastCpSatPlanner() {
    }

    static PlanStrategyResult run(PlanStrategyInput input, Map<Long, Map<SpendingCategory, Long>> committed,
                                  Set<Long> futureEligible, BenefitRuleQueryService ruleQuery,
                                  BenefitCalculator calculator, BenefitProperties properties) {
        long started = System.nanoTime();
        List<CardInput> cards = input.cards().stream().sorted(Comparator.comparingLong(CardInput::userCardId)).toList();
        if (cards.isEmpty() || futureEligible == null || futureEligible.isEmpty()) {
            return PlanStrategyResult.failure(null, elapsed(started), "배분할 보유카드가 없습니다.");
        }
        Map<SpendingCategory, Long> actualTotals = new EnumMap<>(SpendingCategory.class);
        for (CardInput card : cards) {
            Map<SpendingCategory, Long> fixed = committed.getOrDefault(card.userCardId(), Map.of());
            Map<SpendingCategory, Long> fromPurchases = new EnumMap<>(SpendingCategory.class);
            input.approvedPurchasesByCard().getOrDefault(card.userCardId(), List.of()).stream()
                .filter(purchase -> purchase.amount() > 0).forEach(purchase ->
                    fromPurchases.merge(purchase.category(), purchase.amount(), Math::addExact));
            if (!fixed.equals(fromPurchases)) {
                return PlanStrategyResult.failure(null, elapsed(started), "확정 거래와 카드별 소비 합계가 다릅니다.");
            }
            fixed.forEach((category, amount) -> actualTotals.merge(category, amount, Math::addExact));
        }
        Map<SpendingCategory, Long> forecastTotals = new EnumMap<>(SpendingCategory.class);
        input.forecastPurchases().forEach(purchase ->
            forecastTotals.merge(purchase.category(), purchase.amount(), Math::addExact));
        for (SpendingCategory category : SpendingCategory.values()) {
            if (actualTotals.getOrDefault(category, 0L) + forecastTotals.getOrDefault(category, 0L)
                != input.budgetByCategory().getOrDefault(category, 0L)) {
                return PlanStrategyResult.failure(null, elapsed(started), "예상 거래와 확정 예산이 다릅니다.");
            }
        }

        try {
            Loader.loadNativeLibraries();
            CpModel model = new CpModel();
            Map<Long, Map<Long, BoolVar>> assignments = new HashMap<>();
            List<CardInput> eligible = cards.stream().filter(card -> futureEligible.contains(card.userCardId())).toList();
            for (BenefitPurchase purchase : input.forecastPurchases()) {
                Map<Long, BoolVar> byCard = new HashMap<>();
                for (CardInput card : eligible) {
                    byCard.put(card.userCardId(), model.newBoolVar("purchase_" + purchase.id() + "_" + card.userCardId()));
                }
                model.addEquality(LinearExpr.sum(byCard.values().toArray(IntVar[]::new)), 1);
                assignments.put(purchase.id(), byCard);
            }

            boolean readyOnly = properties.ruleSelection() == BenefitProperties.RuleSelection.READY_ONLY;
            List<IntVar> rewards = new ArrayList<>();
            Set<String> notes = new LinkedHashSet<>();
            notes.add("예상 거래의 금액은 지난 소비를 확정 예산에 맞춰 조정한 값입니다.");
            for (CardInput card : eligible) {
                List<CalculationRule> allRules = ruleQuery.findForCalculation(card.cardProductId());
                Map<String, Long> usedByRule = new HashMap<>();
                calculator.calculatePurchases(card.cardProductId(), input.approvedPurchasesByCard()
                    .getOrDefault(card.userCardId(), List.of()), card.previousMonthSpend()).values()
                    .forEach(result -> result.benefitByRule().forEach((id, amount) ->
                        usedByRule.merge(id, amount, Math::addExact)));
                Map<String, Long> usedByGroup = new HashMap<>();
                for (CalculationRule rule : allRules) {
                    if (rule.capGroupId() != null) {
                        usedByGroup.merge(rule.capGroupId(), usedByRule.getOrDefault(String.valueOf(rule.id()), 0L),
                            Math::addExact);
                    }
                }
                Map<String, List<IntVar>> groups = new HashMap<>();
                Map<String, Long> groupMaxima = new HashMap<>();
                Map<String, Long> groupCaps = new HashMap<>();
                for (CalculationRule rule : allRules) {
                    if (readyOnly && !rule.ruleEngineReady()) continue;
                    if (!supported(rule, card.previousMonthSpend())) {
                        notes.add("건별 금액으로 확정할 수 없는 혜택은 월간 최적화에서 제외했습니다.");
                        continue;
                    }
                    List<LinearArgument> terms = new ArrayList<>();
                    long maximum = 0;
                    for (BenefitPurchase purchase : input.forecastPurchases()) {
                        if (!purchase.merchantIdentified() || purchase.amount() == 0
                            || (!rule.allMerchants() && !rule.categories().contains(purchase.category()))
                            || (rule.perTransactionMinKrw() != null
                                && purchase.amount() < rule.perTransactionMinKrw())) continue;
                        long value = BigDecimal.valueOf(purchase.amount()).multiply(rule.ratePercent())
                            .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR).longValueExact();
                        if (rule.perTransactionMaxKrw() != null) {
                            value = Math.min(value, rule.perTransactionMaxKrw());
                        }
                        if (value == 0) continue;
                        terms.add(LinearExpr.term(assignments.get(purchase.id()).get(card.userCardId()), value));
                        maximum = Math.addExact(maximum, value);
                    }
                    if (terms.isEmpty()) continue;
                    IntVar raw = model.newIntVar(0, maximum,
                        "raw_" + card.userCardId() + "_" + rule.id());
                    model.addEquality(raw, LinearExpr.sum(terms.toArray(LinearArgument[]::new)));
                    IntVar reward = raw;
                    if (rule.monthlyLimit() != null) {
                        long remaining = Math.max(0, rule.monthlyLimit()
                            - usedByRule.getOrDefault(String.valueOf(rule.id()), 0L));
                        reward = model.newIntVar(0, Math.min(maximum, remaining),
                            "rule_cap_" + card.userCardId() + "_" + rule.id());
                        model.addMinEquality(reward, new LinearArgument[] {raw, model.newConstant(remaining)});
                        maximum = Math.min(maximum, remaining);
                    }
                    String group = rule.capGroupId();
                    if (group == null || group.isBlank()) {
                        rewards.add(reward);
                    } else {
                        groups.computeIfAbsent(group, ignored -> new ArrayList<>()).add(reward);
                        groupMaxima.merge(group, maximum, Math::addExact);
                        if (rule.sharedMonthlyLimit() != null) {
                            groupCaps.merge(group, rule.sharedMonthlyLimit(), Math::min);
                        }
                    }
                }
                for (Map.Entry<String, List<IntVar>> group : groups.entrySet()) {
                    long maximum = groupMaxima.get(group.getKey());
                    IntVar raw = model.newIntVar(0, maximum,
                        "shared_raw_" + card.userCardId() + "_" + group.getKey());
                    model.addEquality(raw, LinearExpr.sum(group.getValue().toArray(IntVar[]::new)));
                    Long cap = groupCaps.get(group.getKey());
                    if (cap == null) {
                        rewards.add(raw);
                    } else {
                        long remaining = Math.max(0, cap - usedByGroup.getOrDefault(group.getKey(), 0L));
                        IntVar limited = model.newIntVar(0, Math.min(maximum, remaining),
                            "shared_cap_" + card.userCardId() + "_" + group.getKey());
                        model.addMinEquality(limited, new LinearArgument[] {raw, model.newConstant(remaining)});
                        rewards.add(limited);
                    }
                }
            }
            model.maximize(rewards.isEmpty() ? model.newConstant(0)
                : LinearExpr.sum(rewards.toArray(IntVar[]::new)));
            double remaining = input.timeLimit().toNanos() / 1_000_000_000.0
                - (System.nanoTime() - started) / 1_000_000_000.0;
            if (remaining <= 0) {
                return PlanStrategyResult.failure(null, elapsed(started), "계산 시간 제한을 넘었습니다.");
            }
            CpSolver solver = new CpSolver();
            solver.getParameters().setMaxTimeInSeconds(remaining);
            solver.getParameters().setNumSearchWorkers(1);
            solver.getParameters().setRandomSeed(1);
            CpSolverStatus status = solver.solve(model);
            if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
                return PlanStrategyResult.failure(status == CpSolverStatus.INFEASIBLE ? SolverStatus.INFEASIBLE : null,
                    elapsed(started), "시간 안에 카드 배분 결과를 찾지 못했습니다: " + status);
            }

            List<Allocation> allocations = new ArrayList<>();
            boolean confirmedOnly = readyOnly;
            for (CardInput card : cards) {
                List<BenefitPurchase> assigned = new ArrayList<>(input.approvedPurchasesByCard()
                    .getOrDefault(card.userCardId(), List.of()));
                for (BenefitPurchase purchase : input.forecastPurchases()) {
                    BoolVar chosen = assignments.get(purchase.id()).get(card.userCardId());
                    if (chosen != null && solver.value(chosen) == 1) assigned.add(purchase);
                }
                Map<SpendingCategory, Long> spend = new EnumMap<>(SpendingCategory.class);
                assigned.forEach(purchase -> spend.merge(purchase.category(), purchase.amount(), Math::addExact));
                if (spend.isEmpty()) continue;
                Map<SpendingCategory, Long> benefit = new EnumMap<>(SpendingCategory.class);
                calculator.calculatePurchases(card.cardProductId(), assigned, card.previousMonthSpend()).values()
                    .forEach(value -> {
                        value.benefitByCategory().forEach((category, amount) ->
                            benefit.merge(category, amount, Math::addExact));
                        notes.addAll(value.notes());
                    });
                for (Map.Entry<SpendingCategory, Long> entry : spend.entrySet()) {
                    allocations.add(new Allocation(card.userCardId(), entry.getKey(), entry.getValue(),
                        benefit.getOrDefault(entry.getKey(), 0L)));
                }
            }
            notes.add("최적성 상태는 건별 금액·비율·월 한도·공유 한도로 만든 모델에만 적용됩니다.");
            PlanStrategyResult result = PlanStrategyResult.success(allocations,
                status == CpSolverStatus.OPTIMAL ? SolverStatus.OPTIMAL : SolverStatus.FEASIBLE,
                elapsed(started), confirmedOnly, new ArrayList<>(notes));
            return result.violations(input).isEmpty() ? result
                : PlanStrategyResult.failure(null, elapsed(started), "배분 결과가 확정 예산과 맞지 않습니다.");
        } catch (ArithmeticException | IllegalArgumentException e) {
            return PlanStrategyResult.failure(null, elapsed(started), "혜택 수치로 모델을 만들 수 없습니다.");
        } catch (LinkageError e) {
            return PlanStrategyResult.failure(null, elapsed(started), "CP-SAT 실행 라이브러리를 불러오지 못했습니다.");
        }
    }

    private static boolean supported(CalculationRule rule, long previousMonthSpend) {
        String mode = rule.ruleMode();
        return rule.id() != null && !rule.merchantSpecific() && rule.ratePercent() != null
            && rule.ratePercent().signum() >= 0 && !"MILE".equals(rule.rewardUnit())
            && rule.dailyLimit() == null && rule.dailyCountLimit() == null
            && rule.monthlyCountLimit() == null && rule.annualCountLimit() == null
            && rule.eligibleSpendMonthlyLimit() == null
            && !("REPLACES_BASE".equals(mode) || "PREFER_HIGHER".equals(mode) || "ANNUAL_BONUS".equals(mode))
            && (rule.previousMonthMinKrw() == null || previousMonthSpend >= rule.previousMonthMinKrw())
            && (rule.previousMonthMaxExclusiveKrw() == null
                || previousMonthSpend < rule.previousMonthMaxExclusiveKrw());
    }

    private static int elapsed(long started) {
        return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - started) / 1_000_000);
    }
}
