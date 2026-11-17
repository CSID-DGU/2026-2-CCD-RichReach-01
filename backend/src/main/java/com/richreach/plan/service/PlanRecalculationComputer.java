package com.richreach.plan.service;

import com.richreach.benefit.dto.BenefitQuery;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.benefit.service.BenefitCalculator;
import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.config.PlanProperties;
import com.richreach.plan.entity.SolverStatus;
import com.richreach.plan.optimizer.RollingPlanInput;
import com.richreach.plan.optimizer.RollingPlanInput.CardState;
import com.richreach.plan.optimizer.RollingPlanReplanner;
import com.richreach.plan.optimizer.RollingPlanResult;
import com.richreach.plan.service.PlanBaselineReader.Baseline;
import com.richreach.transaction.service.TransactionSummaryService;
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
 *   <li>이미 쓴 금액(취소·환불을 뺀 카드×업종 금액)은 그 카드에 고정하고 남은 예산만 다시 배분한다. 이 스냅샷은 이번 달 거래
 *       전체라서 새 거래를 더하는 것이 아니라 통째로 다시 읽은 값이다.</li>
 *   <li>분석 대상으로 고른 카드는 앞으로의 배분에 쓰고, 분석 대상이 아니지만 이미 쓴 카드는 쓴 금액만 고정한다.</li>
 *   <li>결과의 카드별 목표 금액은 <b>이번 달 전체 목표</b>(이미 쓴 금액 + 남은 목표)라서 기존 플랜 조회와 같은 의미다.</li>
 *   <li>예상 혜택은 카드마다 그 목표로 혜택 계산기를 다시 불러 구한다. 업종별 배분의 혜택 합이 플랜의 예상 혜택과 같다.</li>
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

    private final PlanBaselineReader baselines;
    private final OwnedCardQueryService ownedCards;
    private final TransactionSummaryService transactions;
    private final RollingPlanReplanner replanner;
    private final BenefitCalculator benefitCalculator;
    private final PlanProperties properties;

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

        RollingPlanResult result;
        try {
            result = replanner.replan(new RollingPlanInput(userId, month, budget.byCategory(), cards,
                properties.strategyTimeLimit()));
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
            BenefitResult benefit = benefitCalculator.calculate(new BenefitQuery(productByCard.get(entry.getKey()),
                entry.getValue(), previousSpend.getOrDefault(entry.getKey(), 0L)));
            Map<SpendingCategory, Long> perCategory = new EnumMap<>(SpendingCategory.class);
            perCategory.putAll(benefit.benefitByCategory());
            benefits.put(entry.getKey(), perCategory);
            totalBenefit += benefit.totalBenefit();
        }
        return new Computation(true, null, null, baseline, budget, targets, benefits, totalSpend, totalBenefit,
            result.solverStatus());
    }

}
