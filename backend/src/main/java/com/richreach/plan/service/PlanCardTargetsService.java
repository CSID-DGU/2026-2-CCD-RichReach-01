package com.richreach.plan.service;

import com.richreach.benefit.service.CardPerformanceAnalyzer;
import com.richreach.card.dto.CalculationRule;
import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.service.BenefitRuleQueryService;
import com.richreach.card.service.CardPerformanceQueryService;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.plan.dto.PlanCardTargetsResponse;
import com.richreach.plan.dto.PlanCardTargetsResponse.BenefitLimit;
import com.richreach.plan.dto.PlanCardTargetsResponse.CardDetail;
import com.richreach.plan.dto.PlanCardTargetsResponse.CardCategoryAmount;
import com.richreach.plan.dto.PlanCardTargetsResponse.Performance;
import com.richreach.plan.entity.MonthlyPlan;
import com.richreach.plan.entity.PlanCardAllocation;
import com.richreach.plan.repository.MonthlyPlanRepository;
import com.richreach.plan.repository.PlanCardAllocationRepository;
import com.richreach.transaction.service.TransactionSummaryService;
import com.richreach.transaction.service.TransactionBenefitService;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월간 플랜의 카드별 상세(PLAN-02). 플랜의 목표, 이번 달에 실제로 쓴 금액, 카드의 혜택 규칙으로 구한 전월실적 구간과 월 혜택
 * 한도를 카드마다 합쳐서 보여준다. 읽기만 하고 아무것도 저장하지 않는다.
 *
 * <ul>
 *   <li>플랜에 배분이 있는 카드만 나온다. 목표 금액이 큰 카드부터이고, 같으면 카드 ID가 작은 쪽이 먼저다.</li>
 *   <li>이번 달 사용 금액은 승인일 기준으로 취소·환불을 뺀 값이고, 업종은 사용자가 고친 값이 먼저다.</li>
 *   <li>혜택, 구간, 한도는 카드의 혜택 규칙으로 계산한 <b>추정</b>이다. 카드 상품과 매칭되지 않은 카드나 규칙이 없는 카드는
 *       {@code null} 또는 빈 목록으로 내려가며 0이나 "한도 없음"이 아니다. (혜택 규칙이 없는 카드의 혜택도 0이 아니라 null이다)</li>
 *   <li>실적 제외 조건은 문장으로만 있어서 금액에서 뺄 수 없다. 그래서 이번 달 실적은 사용 금액 그대로이고 제외 조건은 안내
 *       문구로만 내려준다.</li>
 * </ul>
 * 다른 사용자의 플랜은 없는 플랜과 똑같이 {@code PLAN-001}이다. 금액은 소비 정보라서 로그에 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PlanCardTargetsService {

    private final MonthlyPlanRepository plans;
    private final PlanCardAllocationRepository allocations;
    private final OwnedCardQueryService ownedCards;
    private final TransactionSummaryService transactions;
    private final BenefitRuleQueryService rules;
    private final CardPerformanceQueryService performanceNotes;
    private final TransactionBenefitService transactionBenefits;

    @Transactional(readOnly = true)
    public PlanCardTargetsResponse cardTargets(long userId, long planId) {
        MonthlyPlan plan = plans.findById(planId).filter(found -> found.getUserId().equals(userId))
            .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        YearMonth month = YearMonth.parse(plan.getPlanMonth());

        Map<Long, List<PlanCardAllocation>> byCard = new LinkedHashMap<>();
        for (PlanCardAllocation allocation : allocations.findByPlanId(plan.getId())) {
            byCard.computeIfAbsent(allocation.getUserCardId(), id -> new ArrayList<>()).add(allocation);
        }

        Map<Long, OwnedCardResponse> cards = new HashMap<>();
        ownedCards.list(userId).forEach(card -> cards.put(card.cardId(), card));
        Map<Long, Map<SpendingCategory, Long>> spent = transactions.cardCategorySpend(userId, month);
        Map<Long, Long> previousSpend = transactions.cardSpend(userId, month.minusMonths(1));

        List<CardDetail> details = new ArrayList<>();
        for (Map.Entry<Long, List<PlanCardAllocation>> entry : byCard.entrySet()) {
            long cardId = entry.getKey();
            OwnedCardResponse card = cards.get(cardId);
            details.add(detail(userId, month, cardId, card, entry.getValue(), spent.getOrDefault(cardId, Map.of()),
                previousSpend.getOrDefault(cardId, 0L)));
        }
        details.sort(Comparator.comparingLong(CardDetail::targetAmount).reversed()
            .thenComparingLong(CardDetail::cardId));
        return new PlanCardTargetsResponse(plan.getId(), plan.getPlanMonth(), details);
    }

    private CardDetail detail(long userId, YearMonth month, long cardId, OwnedCardResponse card,
                              List<PlanCardAllocation> planned,
                              Map<SpendingCategory, Long> spentByCategory, long previousMonthSpend) {
        long target = planned.stream().mapToLong(PlanCardAllocation::getTargetAmount).sum();
        long expectedBenefit = planned.stream().mapToLong(PlanCardAllocation::getExpectedBenefit).sum();
        long spent = spentByCategory.values().stream().mapToLong(Long::longValue).sum();
        Long productId = card == null ? null : card.cardProductId();

        List<CalculationRule> cardRules = productId == null ? List.of() : rules.findForCalculation(productId);
        // 카드 상품과 매칭되지 않았거나 혜택 규칙이 없으면 혜택을 알 수 없다. 계산기는 이때 0을 돌려주므로 부르지 않는다.
        Long currentBenefit = cardRules.isEmpty() ? null
            : transactionBenefits.monthTotal(userId, month, List.of(cardId));

        return new CardDetail(cardId, card == null ? null : card.cardName(), target, spent,
            Math.max(0, target - spent), expectedBenefit, currentBenefit,
            performance(productId, cardRules, previousMonthSpend, spent), benefitLimit(cardRules, previousMonthSpend,
            currentBenefit), categories(planned, spentByCategory));
    }

    private Performance performance(Long productId, List<CalculationRule> cardRules, long previousMonthSpend,
                                    long currentMonthAmount) {
        List<Long> tiers = CardPerformanceAnalyzer.tiers(cardRules);
        Long applied = boxed(CardPerformanceAnalyzer.achievedTier(tiers, previousMonthSpend));
        Long achieved = boxed(CardPerformanceAnalyzer.achievedTier(tiers, currentMonthAmount));
        Long next = boxed(CardPerformanceAnalyzer.nextTier(tiers, currentMonthAmount));
        List<String> notes = productId == null ? List.of() : performanceNotes.performanceExclusionNotes(productId);
        // 실적 제외 조건은 문장이라 금액에서 뺄 수 없다. 이번 달 실적은 사용 금액 그대로이고 정확하다고 말할 수 없다.
        return new Performance(tiers, previousMonthSpend, applied, currentMonthAmount, false, achieved, next,
            next == null ? null : next - currentMonthAmount, notes);
    }

    private static BenefitLimit benefitLimit(List<CalculationRule> cardRules, long previousMonthSpend,
                                             Long currentBenefit) {
        OptionalLong limit = CardPerformanceAnalyzer.monthlyLimit(cardRules, previousMonthSpend);
        Long monthlyLimit = boxed(limit);
        Long remaining = monthlyLimit == null || currentBenefit == null ? null
            : Math.max(0, monthlyLimit - currentBenefit);
        return new BenefitLimit(monthlyLimit, currentBenefit, remaining);
    }

    /** 목표나 사용이 있는 업종을 업종 고정 순서로 모은다. */
    private static List<CardCategoryAmount> categories(List<PlanCardAllocation> planned,
                                                   Map<SpendingCategory, Long> spentByCategory) {
        Map<SpendingCategory, long[]> merged = new EnumMap<>(SpendingCategory.class);
        for (PlanCardAllocation allocation : planned) {
            long[] values = merged.computeIfAbsent(allocation.getCategory(), key -> new long[3]);
            values[0] += allocation.getTargetAmount();
            values[2] += allocation.getExpectedBenefit();
        }
        spentByCategory.forEach((category, amount) -> merged.computeIfAbsent(category, key -> new long[3])[1] += amount);
        return merged.entrySet().stream().map(entry -> new CardCategoryAmount(entry.getKey(), entry.getValue()[0],
            entry.getValue()[1], entry.getValue()[2])).toList();
    }

    private static Long boxed(OptionalLong value) {
        return value.isPresent() ? value.getAsLong() : null;
    }

}
