package com.richreach.plan.optimizer.cpsat;

import com.google.ortools.Loader;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearArgument;
import com.google.ortools.sat.LinearExpr;
import com.richreach.benefit.config.BenefitProperties;
import com.richreach.benefit.dto.BenefitQuery;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.benefit.service.BenefitCalculator;
import com.richreach.card.dto.CalculationRule;
import com.richreach.card.service.BenefitRuleQueryService;
import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.entity.PlanStrategy;
import com.richreach.plan.entity.SolverStatus;
import com.richreach.plan.optimizer.PlanStrategyInput;
import com.richreach.plan.optimizer.PlanStrategyInput.CardInput;
import com.richreach.plan.optimizer.PlanStrategyResult;
import com.richreach.plan.optimizer.PlanStrategyResult.Allocation;
import com.richreach.plan.optimizer.PlanStrategyRunner;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 월간 업종 예산을 사용자가 보유한 카드에 배분한다. 비율 혜택, 전월실적 구간,
 * 규칙별 월 한도와 공유 월 한도를 CP-SAT으로 모델링한다. 반환 금액은 기존
 * BenefitCalculator로 다시 계산한다. 가맹점·횟수·정액 혜택과 다음 달 실적 가치는
 * 현재 입력 계약에 없으므로 모델 밖이다.
 */
@Component
@RequiredArgsConstructor
public class CpSatPlanStrategyRunner implements PlanStrategyRunner {

    // 혜택률은 최대 소수 넷째 자리(%)까지 정수 계수로 표현한다.
    private static final long RATE_DENOMINATOR = 1_000_000;
    private final BenefitRuleQueryService ruleQuery;
    private final BenefitCalculator benefitCalculator;
    private final BenefitProperties benefitProperties;

    @Override
    public PlanStrategy strategy() {
        return PlanStrategy.CP_SAT;
    }

    @Override
    public PlanStrategyResult run(PlanStrategyInput input) {
        long started = System.nanoTime();
        if (input.cards().isEmpty()) {
            return PlanStrategyResult.failure(null, elapsedMs(started), "분석할 보유카드가 없습니다.");
        }
        List<CardInput> cards = input.cards().stream()
            .sorted(Comparator.comparingLong(CardInput::userCardId)).toList();
        if (cards.stream().map(CardInput::userCardId).distinct().count() != cards.size()) {
            return PlanStrategyResult.failure(null, elapsedMs(started), "보유카드 ID가 중복되었습니다.");
        }
        List<SpendingCategory> categories = input.budgetByCategory().entrySet().stream()
            .filter(entry -> entry.getValue() > 0).map(Map.Entry::getKey).sorted().toList();
        if (categories.isEmpty()) {
            return PlanStrategyResult.success(List.of(), SolverStatus.OPTIMAL, elapsedMs(started), true, List.of());
        }

        try {
            Loader.loadNativeLibraries();
            CpModel model = new CpModel();
            Map<Long, Map<SpendingCategory, IntVar>> spendVars = new HashMap<>();
            for (CardInput card : cards) {
                Map<SpendingCategory, IntVar> byCategory = new EnumMap<>(SpendingCategory.class);
                for (SpendingCategory category : categories) {
                    byCategory.put(category, model.newIntVar(0, input.budgetByCategory().get(category),
                        "spend_" + card.userCardId() + "_" + category));
                }
                spendVars.put(card.userCardId(), byCategory);
            }
            for (SpendingCategory category : categories) {
                IntVar[] variables = cards.stream()
                    .map(card -> spendVars.get(card.userCardId()).get(category)).toArray(IntVar[]::new);
                model.addEquality(LinearExpr.sum(variables), input.budgetByCategory().get(category));
            }

            boolean readyOnly = benefitProperties.ruleSelection() == BenefitProperties.RuleSelection.READY_ONLY;
            List<IntVar> rewards = new ArrayList<>();
            Set<String> notes = new LinkedHashSet<>();
            for (CardInput card : cards) {
                Map<String, List<IntVar>> groups = new HashMap<>();
                Map<String, Long> groupBounds = new HashMap<>();
                Map<String, Long> groupCaps = new HashMap<>();
                for (CalculationRule rule : ruleQuery.findForCalculation(card.cardProductId())) {
                    if (readyOnly && !rule.ruleEngineReady()) {
                        continue;
                    }
                    if (!supported(rule, card.previousMonthSpend())) {
                        notes.add("가맹점 지정·정액·특수 조건 등의 규칙은 월간 업종 배분에서 제외했습니다.");
                        continue;
                    }
                    List<IntVar> targets = categories.stream()
                        .filter(category -> rule.allMerchants() || rule.categories().contains(category))
                        .map(category -> spendVars.get(card.userCardId()).get(category)).toList();
                    if (targets.isEmpty()) {
                        continue;
                    }
                    long scaledRate;
                    try {
                        scaledRate = rule.ratePercent().multiply(BigDecimal.valueOf(10_000)).longValueExact();
                    } catch (ArithmeticException e) {
                        notes.add("정수 계수로 표현할 수 없는 혜택률은 월간 배분에서 제외했습니다.");
                        continue;
                    }
                    long maxSpend = 0;
                    for (SpendingCategory category : categories) {
                        if (rule.allMerchants() || rule.categories().contains(category)) {
                            maxSpend = Math.addExact(maxSpend, input.budgetByCategory().get(category));
                        }
                    }
                    long maximum = Math.multiplyExact(maxSpend, scaledRate) / RATE_DENOMINATOR;
                    IntVar eligible = model.newIntVar(0, maxSpend,
                        "eligible_" + card.userCardId() + "_" + rule.id());
                    model.addEquality(eligible, LinearExpr.sum(targets.toArray(IntVar[]::new)));
                    IntVar raw = model.newIntVar(0, maximum,
                        "raw_" + card.userCardId() + "_" + rule.id());
                    model.addDivisionEquality(raw, LinearExpr.term(eligible, scaledRate),
                        model.newConstant(RATE_DENOMINATOR));
                    IntVar reward = raw;
                    if (rule.monthlyLimit() != null) {
                        long cap = Math.max(0, rule.monthlyLimit());
                        maximum = Math.min(maximum, cap);
                        reward = model.newIntVar(0, maximum,
                            "limit_" + card.userCardId() + "_" + rule.id());
                        model.addMinEquality(reward, new LinearArgument[] {raw, model.newConstant(cap)});
                    }
                    if (rule.capGroupId() == null || rule.capGroupId().isBlank()) {
                        rewards.add(reward);
                    } else {
                        String group = rule.capGroupId();
                        groups.computeIfAbsent(group, ignored -> new ArrayList<>()).add(reward);
                        groupBounds.merge(group, maximum, Math::addExact);
                        if (rule.sharedMonthlyLimit() != null) {
                            groupCaps.merge(group, Math.max(0, rule.sharedMonthlyLimit()), Math::min);
                        }
                    }
                }
                for (Map.Entry<String, List<IntVar>> group : groups.entrySet()) {
                    long maximum = groupBounds.get(group.getKey());
                    IntVar sum = model.newIntVar(0, maximum,
                        "shared_sum_" + card.userCardId() + "_" + group.getKey());
                    model.addEquality(sum, LinearExpr.sum(group.getValue().toArray(IntVar[]::new)));
                    Long cap = groupCaps.get(group.getKey());
                    if (cap == null) {
                        rewards.add(sum);
                    } else {
                        IntVar limited = model.newIntVar(0, Math.min(maximum, cap),
                            "shared_cap_" + card.userCardId() + "_" + group.getKey());
                        model.addMinEquality(limited, new LinearArgument[] {sum, model.newConstant(cap)});
                        rewards.add(limited);
                    }
                }
            }
            if (rewards.isEmpty()) {
                return PlanStrategyResult.failure(null, elapsedMs(started), "계산 가능한 카드 혜택 규칙이 없습니다.");
            }
            model.maximize(LinearExpr.sum(rewards.toArray(IntVar[]::new)));
            double remaining = input.timeLimit().toNanos() / 1_000_000_000.0
                - (System.nanoTime() - started) / 1_000_000_000.0;
            if (remaining <= 0) {
                return PlanStrategyResult.failure(null, elapsedMs(started), "계산 시간 제한을 넘었습니다.");
            }
            CpSolver solver = new CpSolver();
            solver.getParameters().setMaxTimeInSeconds(remaining);
            solver.getParameters().setNumSearchWorkers(1);
            solver.getParameters().setRandomSeed(1);
            CpSolverStatus status = solver.solve(model);
            if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
                SolverStatus failed = status == CpSolverStatus.INFEASIBLE ? SolverStatus.INFEASIBLE : null;
                return PlanStrategyResult.failure(failed, elapsedMs(started),
                    "시간 안에 카드 배분 결과를 찾지 못했습니다: " + status);
            }

            List<Allocation> allocations = new ArrayList<>();
            boolean confirmedOnly = readyOnly;
            for (CardInput card : cards) {
                Map<SpendingCategory, Long> spend = new EnumMap<>(SpendingCategory.class);
                for (SpendingCategory category : categories) {
                    long amount = solver.value(spendVars.get(card.userCardId()).get(category));
                    if (amount > 0) {
                        spend.put(category, amount);
                    }
                }
                if (spend.isEmpty()) {
                    continue;
                }
                BenefitResult checked = benefitCalculator.calculate(
                    new BenefitQuery(card.cardProductId(), spend, card.previousMonthSpend()));
                confirmedOnly &= checked.confirmedOnly();
                notes.addAll(checked.notes());
                long allocatedBenefit = 0;
                for (Map.Entry<SpendingCategory, Long> entry : spend.entrySet()) {
                    long benefit = checked.benefitByCategory().getOrDefault(entry.getKey(), 0L);
                    allocatedBenefit = Math.addExact(allocatedBenefit, benefit);
                    allocations.add(new Allocation(card.userCardId(), entry.getKey(), entry.getValue(), benefit));
                }
                if (allocatedBenefit != checked.totalBenefit()) {
                    return PlanStrategyResult.failure(null, elapsedMs(started),
                        "혜택 계산 결과가 업종별 합계와 맞지 않습니다.");
                }
            }
            // The solver's proof applies to the supported rate/cap model.
            // Category rounding and omitted special rules can change the full result.
            notes.add("풀이 상태의 최적성은 모델에 포함된 비율·월 한도에만 적용되며, 표시 혜택은 업종별 원 단위로 다시 계산했습니다.");
            SolverStatus reported = status == CpSolverStatus.OPTIMAL ? SolverStatus.OPTIMAL : SolverStatus.FEASIBLE;
            PlanStrategyResult result = PlanStrategyResult.success(allocations, reported,
                elapsedMs(started), confirmedOnly, new ArrayList<>(notes));
            if (!result.violations(input).isEmpty()) {
                return PlanStrategyResult.failure(null, elapsedMs(started), "배분 결과가 입력 예산과 맞지 않습니다.");
            }
            return result;
        } catch (ArithmeticException | IllegalArgumentException e) {
            return PlanStrategyResult.failure(null, elapsedMs(started), "혜택 수치로 모델을 만들 수 없습니다.");
        } catch (LinkageError e) {
            return PlanStrategyResult.failure(null, elapsedMs(started), "CP-SAT 실행 라이브러리를 불러오지 못했습니다.");
        }
    }

    private static boolean supported(CalculationRule rule, long previousMonthSpend) {
        if (rule.merchantSpecific() || rule.ratePercent() == null || rule.ratePercent().signum() < 0
            || "MILE".equals(rule.rewardUnit())) {
            return false;
        }
        if ("REPLACES_BASE".equals(rule.ruleMode()) || "PREFER_HIGHER".equals(rule.ruleMode())
            || "ANNUAL_BONUS".equals(rule.ruleMode())) {
            return false;
        }
        return (rule.previousMonthMinKrw() == null || previousMonthSpend >= rule.previousMonthMinKrw())
            && (rule.previousMonthMaxExclusiveKrw() == null
                || previousMonthSpend < rule.previousMonthMaxExclusiveKrw());
    }

    private static int elapsedMs(long started) {
        return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - started) / 1_000_000);
    }
}
