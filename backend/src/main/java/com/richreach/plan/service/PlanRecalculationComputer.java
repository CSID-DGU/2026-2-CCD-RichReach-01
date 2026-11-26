package com.richreach.plan.service;

import com.richreach.benefit.dto.BenefitPurchase;
import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.config.PlanProperties;
import com.richreach.plan.entity.SolverStatus;
import com.richreach.plan.optimizer.RollingPlanInput;
import com.richreach.plan.optimizer.ForecastPurchaseBuilder;
import com.richreach.plan.optimizer.RollingPlanInput.CardState;
import com.richreach.plan.optimizer.RollingPlanReplanner;
import com.richreach.plan.optimizer.RollingPlanResult;
import com.richreach.plan.service.PlanBaselineReader.Baseline;
import com.richreach.plannedexpense.dto.PlannedExpenseResponse;
import com.richreach.plannedexpense.service.PlannedExpenseService;
import com.richreach.transaction.service.TransactionSummaryService;
import com.richreach.transaction.service.TransactionBenefitService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 이번 달 승인 거래 전체의 현재 스냅샷으로 남은 기간의 카드 배분을 다시 계산한다. DB에 아무것도 쓰지 않고, 오래 걸릴 수 있어서
 * 호출하는 쪽이 DB 트랜잭션을 열지 않은 상태에서 불러야 한다.
 *
 * <ul>
 *   <li>확정 예산은 지금 보여주는 플랜의 소비 프로필에서 읽는다. 사용자가 예산을 새로 정했으면(예산 수정) 그 예산을 쓴다.
 *       어느 쪽이든 예산을 혜택을 위해 늘리지 않는다.</li>
 *   <li>이번 달 예정지출은 그 예산에 얹어서({@link PlannedExpenseBudgetOverlay}) 엔진에 넣는다. 결과의 {@code budget}은 얹기 전의
 *       확정 예산 그대로라서, 예정지출을 고치거나 지워도 같은 예정지출이 두 번 더해지지 않는다.</li>
 *   <li>이미 쓴 금액(취소·환불을 뺀 카드×업종 금액)은 그 카드에 고정하고 남은 예산만 다시 배분한다. 이 스냅샷은 이번 달 거래
 *       전체라서 새 거래를 더하는 것이 아니라 통째로 다시 읽은 값이다.</li>
 *   <li>분석 대상으로 고른 카드는 앞으로의 배분에 쓰고, 분석 대상이 아니지만 이미 쓴 카드는 쓴 금액만 고정한다.</li>
 *   <li>결과의 카드별 목표 금액은 <b>이번 달 전체 목표</b>(이미 쓴 금액 + 남은 목표)라서 기존 플랜 조회와 같은 의미다.</li>
 *   <li>이미 승인된 거래의 혜택은 거래별로 재생하고, 남은 예산의 혜택은 예상 거래를 배분해 계산한다.
 *       업종별 혜택 합은 플랜의 예상 혜택과 같다.</li>
 * </ul>
 * <b>한계</b>: 카드 상품과 매칭되지 않은 카드의 승인액은 재계산 엔진이 카드 상품 ID를 요구해서 입력에서 빠진다.
 * 금액은 소비 정보라서 로그에 남기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PlanRecalculationComputer {

    static final String CODE_NO_PLAN = "NO_PLAN";
    static final String CODE_NO_BUDGET = "NO_BUDGET";
    static final String CODE_NO_ANALYSIS_CARD = "NO_ANALYSIS_CARD";
    static final String CODE_SOLVER_FAILED = "SOLVER_FAILED";
    static final String CODE_SOLVER_ERROR = "SOLVER_ERROR";

    /** 예산 밖 추가 지출의 반영 방식 코드. (예정지출 도메인의 열거형을 직접 참조하지 않는다) */
    private static final String EXTRA = "EXTRA";

    private final PlanBaselineReader baselines;
    private final OwnedCardQueryService ownedCards;
    private final TransactionSummaryService transactions;
    private final RollingPlanReplanner replanner;
    private final PlanProperties properties;
    private final PlannedExpenseService plannedExpenses;
    private final TransactionBenefitService transactionBenefits;
    private final Clock clock;

    /**
     * 계산 결과. 성공이면 출발점 플랜과 바뀐 뒤의 목표·혜택을, 실패면 코드와 이유를 가진다.
     *
     * @param targets  보유카드별·업종별 이번 달 전체 목표 금액(0원인 칸은 없음)
     * @param benefits 보유카드별·업종별 예상 혜택(원). {@code targets}의 칸마다 있다.
     */
    record Computation(boolean success, String failureCode, String failureReason, Baseline baseline,
                       PlanBudget budget, Map<Long, Map<SpendingCategory, Long>> targets,
                       Map<Long, Map<SpendingCategory, Long>> benefits, long totalSpend, long totalBenefit,
                       SolverStatus solverStatus) {

        static Computation failure(String code, String reason) {
            return new Computation(false, code, reason, null, null, Map.of(), Map.of(), 0, 0, null);
        }

    }

    Computation compute(long userId, YearMonth month) {
        return compute(userId, month, null);
    }

    /**
     * @param budgetOverride 사용자가 새로 정한 예산. 있으면 지금 플랜의 예산 대신 이 예산으로 계산한다. 없으면 지금 플랜의 확정 예산을 쓴다.
     */
    Computation compute(long userId, YearMonth month, PlanBudget budgetOverride) {
        Optional<Baseline> found = baselines.read(userId, month);
        if (found.isEmpty()) {
            return Computation.failure(CODE_NO_PLAN, "다시 계산할 플랜이 없습니다.");
        }
        Baseline baseline = found.get();
        PlanBudget budget = budgetOverride != null ? budgetOverride : baseline.budget();
        if (budget.byCategory().isEmpty()) {
            return Computation.failure(CODE_NO_BUDGET, "플랜의 확정 예산을 읽을 수 없습니다.");
        }

        List<OwnedCardResponse> owned = ownedCards.list(userId);
        Map<Long, Map<SpendingCategory, Long>> approved = transactions.cardCategorySpend(userId, month);
        Map<Long, Long> previousSpend = transactions.cardSpend(userId, month.minusMonths(1));

        List<CardState> cards = new ArrayList<>();
        Map<Long, Long> productByCard = new HashMap<>();
        boolean anyEligible = false;
        for (OwnedCardResponse card : owned) {
            if (card.cardProductId() == null) {
                continue;
            }
            boolean eligible = card.selected();
            Map<SpendingCategory, Long> spend = approved.getOrDefault(card.cardId(), Map.of());
            // 분석 대상이 아니고 이번 달에 쓴 것도 없는 카드는 재계산과 상관이 없다.
            if (!eligible && spend.isEmpty()) {
                continue;
            }
            anyEligible |= eligible;
            productByCard.put(card.cardId(), card.cardProductId());
            cards.add(new CardState(card.cardId(), card.cardProductId(), previousSpend.getOrDefault(card.cardId(), 0L),
                spend, eligible));
        }
        if (!anyEligible) {
            return Computation.failure(CODE_NO_ANALYSIS_CARD, "분석 대상으로 고른 카드가 없습니다.");
        }

        // 예정지출을 얹은 예산으로 계산한다. 저장되는 확정 예산(budget)은 그대로 둔다.
        // 이미 쓴 금액은 엔진이 그 카드에 고정하므로, 조정으로 줄일 수 있는 것은 업종별 남은 예산뿐이다.
        Map<SpendingCategory, Long> spentByCategory = new EnumMap<>(SpendingCategory.class);
        for (CardState card : cards) {
            card.approvedSpendByCategory().forEach((category, amount) -> {
                if (amount > 0) {
                    spentByCategory.merge(category, amount, Long::sum);
                }
            });
        }
        PlanBudget planned = PlannedExpenseBudgetOverlay.apply(budget, plannedItems(userId, month), spentByCategory);

        Map<Long, List<BenefitPurchase>> actualPurchases = new HashMap<>();
        List<BenefitPurchase> history = new ArrayList<>();
        for (CardState card : cards) {
            actualPurchases.put(card.userCardId(), transactionBenefits.cardPurchases(userId, card.userCardId(), month));
            if (card.eligibleForFuture()) {
                history.addAll(transactionBenefits.cardPurchases(userId, card.userCardId(), month.minusMonths(1)));
            }
        }
        Map<SpendingCategory, Long> futureBudget = new EnumMap<>(SpendingCategory.class);
        planned.byCategory().forEach((category, amount) -> {
            long remaining = amount - spentByCategory.getOrDefault(category, 0L);
            if (remaining > 0) futureBudget.put(category, remaining);
        });
        LocalDateTime earliestFuture = month.equals(YearMonth.now(clock)) ? LocalDateTime.now(clock)
            : month.atDay(1).atStartOfDay();
        List<BenefitPurchase> forecast = ForecastPurchaseBuilder.build(month, futureBudget, history, earliestFuture);

        RollingPlanResult result;
        try {
            result = replanner.replan(new RollingPlanInput(userId, month, planned.byCategory(), cards,
                properties.strategyTimeLimit(), forecast, actualPurchases));
        } catch (RuntimeException e) {
            // 메시지에 금액이 섞일 수 있어서 종류만 남긴다.
            log.error("플랜 재계산 중 오류: type={}", e.getClass().getSimpleName());
            return Computation.failure(CODE_SOLVER_ERROR, "플랜을 다시 계산하는 중 오류가 발생했습니다.");
        }
        if (!result.success()) {
            return Computation.failure(CODE_SOLVER_FAILED, result.failureReason() == null
                ? "플랜을 다시 계산하지 못했습니다." : result.failureReason());
        }

        // 이번 달 전체 목표 = 이미 쓴 금액 + 남은 목표
        Map<Long, Map<SpendingCategory, Long>> targets = new HashMap<>();
        for (CardState card : cards) {
            card.approvedSpendByCategory().forEach((category, amount) -> {
                if (amount > 0) {
                    targets.computeIfAbsent(card.userCardId(), id -> new EnumMap<>(SpendingCategory.class))
                        .merge(category, amount, Long::sum);
                }
            });
        }
        for (RollingPlanResult.RemainingAllocation allocation : result.remainingAllocations()) {
            if (allocation.targetAmount() > 0) {
                targets.computeIfAbsent(allocation.userCardId(), id -> new EnumMap<>(SpendingCategory.class))
                    .merge(allocation.category(), allocation.targetAmount(), Long::sum);
            }
        }

        Map<Long, Map<SpendingCategory, Long>> benefits = new HashMap<>();
        long totalSpend = 0;
        long totalBenefit = 0;
        for (Map.Entry<Long, Map<SpendingCategory, Long>> entry : targets.entrySet()) {
            totalSpend += entry.getValue().values().stream().mapToLong(Long::longValue).sum();
            Map<SpendingCategory, Long> perCategory = new EnumMap<>(SpendingCategory.class);
            perCategory.putAll(transactionBenefits.monthByCategory(userId, entry.getKey(),
                productByCard.get(entry.getKey()), month, previousSpend.getOrDefault(entry.getKey(), 0L)));
            benefits.put(entry.getKey(), perCategory);
        }
        for (RollingPlanResult.RemainingAllocation allocation : result.remainingAllocations()) {
            benefits.computeIfAbsent(allocation.userCardId(), id -> new EnumMap<>(SpendingCategory.class))
                .merge(allocation.category(), allocation.expectedBenefit(), Math::addExact);
        }
        for (Map<SpendingCategory, Long> byCategory : benefits.values()) {
            totalBenefit = Math.addExact(totalBenefit, byCategory.values().stream().mapToLong(Long::longValue).sum());
        }
        if (totalBenefit != result.estimatedApprovedBenefit() + result.estimatedRemainingBenefit()) {
            return Computation.failure(CODE_SOLVER_ERROR, "혜택 합계를 일관되게 계산하지 못했습니다.");
        }
        return new Computation(true, null, null, baseline, budget, targets, benefits, totalSpend, totalBenefit,
            result.solverStatus());
    }

    /** 그 달의 예정지출을 예산에 얹을 항목으로 바꾼다. */
    private List<PlannedExpenseBudgetOverlay.Item> plannedItems(long userId, YearMonth month) {
        List<PlannedExpenseBudgetOverlay.Item> items = new ArrayList<>();
        for (PlannedExpenseResponse expense : plannedExpenses.listForBudget(userId, month)) {
            SpendingCategory category = SpendingCategory.fromCode(expense.category()).orElse(null);
            if (category != null && expense.monthlyAmount() > 0) {
                items.add(new PlannedExpenseBudgetOverlay.Item(category, expense.monthlyAmount(),
                    EXTRA.equals(expense.budgetMethod())));
            }
        }
        return items;
    }

}
