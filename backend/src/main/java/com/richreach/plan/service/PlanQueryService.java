package com.richreach.plan.service;

import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.plan.dto.PlanCategoriesResponse;
import com.richreach.plan.dto.PlanCategoriesResponse.CardAmount;
import com.richreach.plan.dto.PlanCategoriesResponse.CategoryPlan;
import com.richreach.plan.dto.PlanResponse;
import com.richreach.plan.dto.PlanResponse.CardTarget;
import com.richreach.plan.entity.MonthlyPlan;
import com.richreach.plan.entity.PlanCardAllocation;
import com.richreach.plan.entity.PlanStrategy;
import com.richreach.plan.repository.MonthlyPlanRepository;
import com.richreach.plan.repository.PlanCardAllocationRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장된 월간 플랜을 조회한다. 읽기만 하고 아무것도 바꾸지 않는다.
 *
 * <p>한 달에는 전략마다 플랜이 저장되어 있다. 사용자에게는 {@link PlanPrimarySelector}의 우선순위(CP_SAT, GREEDY,
 * EVEN_SPLIT, HABIT)로 고른 한 개만 보여 준다. 같은 전략의 플랜이 여럿이면 가장 최신 version을 쓴다. 카드 이름은 카드 서비스를
 * 거쳐서 읽는다. (카드의 엔티티와 리포지토리를 직접 읽지 않는다.) 금액은 소비 정보라서 로그에 남기지 않는다.
 *
 * <p>{@code baselineBenefit}은 HABIT(지금처럼 쓸 때) 플랜의 예상 혜택이다. HABIT 플랜이 없으면 비교할 기준이 없으므로
 * {@code baselineBenefit}과 {@code additionalBenefit}은 모두 {@code null}이다. (0으로 내리면 "추가 혜택 전부"로 보인다.)
 *
 * <p>다른 사용자의 플랜은 없는 플랜과 똑같이 {@code PLAN-001}이다. 존재 여부를 알려주지 않기 위해서다.
 */
@Service
@RequiredArgsConstructor
public class PlanQueryService {

    private final MonthlyPlanRepository plans;
    private final PlanCardAllocationRepository allocations;
    private final OwnedCardQueryService ownedCards;

    /** 그 달에 보여줄 플랜. 월 형식이 틀리면 {@code COMMON-400}, 플랜이 없으면 {@code PLAN-001}이다. */
    @Transactional(readOnly = true)
    public PlanResponse getMonthlyPlan(long userId, String month) {
        String planMonth = PlanBudget.parseMonth(month).toString();
        Map<PlanStrategy, MonthlyPlan> latest = latestByStrategy(plans.findByUserIdAndPlanMonth(userId, planMonth));
        PlanStrategy primary = PlanPrimarySelector.select(latest.keySet())
            .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        MonthlyPlan plan = latest.get(primary);

        MonthlyPlan habit = latest.get(PlanStrategy.HABIT);
        Long baseline = habit == null ? null : habit.getExpectedBenefit();

        Map<Long, String> cardNames = cardNames(userId);
        Map<Long, Long> targetByCard = new LinkedHashMap<>();
        for (PlanCardAllocation allocation : allocations.findByPlanId(plan.getId())) {
            targetByCard.merge(allocation.getUserCardId(), allocation.getTargetAmount(), Long::sum);
        }
        List<CardTarget> cardTargets = targetByCard.entrySet().stream()
            .sorted(Map.Entry.<Long, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .map(entry -> new CardTarget(entry.getKey(), cardNames.get(entry.getKey()), entry.getValue())).toList();

        return new PlanResponse(plan.getId(), plan.getPlanMonth(), plan.getExpectedBenefit(), baseline,
            additionalBenefit(plan.getExpectedBenefit(), baseline), cardTargets);
    }

    /** 플랜의 업종별 사용계획. 없거나 다른 사용자의 플랜이면 {@code PLAN-001}이다. */
    @Transactional(readOnly = true)
    public PlanCategoriesResponse getCategories(long userId, long planId) {
        MonthlyPlan plan = plans.findById(planId).filter(found -> found.getUserId().equals(userId))
            .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));

        Map<Long, String> cardNames = cardNames(userId);
        Map<SpendingCategory, List<PlanCardAllocation>> byCategory = new EnumMap<>(SpendingCategory.class);
        for (PlanCardAllocation allocation : allocations.findByPlanId(plan.getId())) {
            byCategory.computeIfAbsent(allocation.getCategory(), category -> new ArrayList<>()).add(allocation);
        }
        List<CategoryPlan> categories = byCategory.entrySet().stream().map(entry -> {
            List<CardAmount> cards = entry.getValue().stream()
                .sorted(Comparator.comparingLong(PlanCardAllocation::getTargetAmount).reversed()
                    .thenComparing(PlanCardAllocation::getUserCardId))
                .map(a -> new CardAmount(a.getUserCardId(), cardNames.get(a.getUserCardId()), a.getTargetAmount(),
                    a.getExpectedBenefit())).toList();
            return new CategoryPlan(entry.getKey(), cards.stream().mapToLong(CardAmount::targetAmount).sum(),
                cards.stream().mapToLong(CardAmount::expectedBenefit).sum(), cards);
        }).toList();
        return new PlanCategoriesResponse(plan.getId(), plan.getPlanMonth(), categories);
    }

    /** 추가 혜택. 비교할 기준(기존 예상)이 없으면 null이다. */
    static Long additionalBenefit(long expectedBenefit, Long baselineBenefit) {
        return baselineBenefit == null ? null : expectedBenefit - baselineBenefit;
    }

    /** 전략마다 가장 최신 version의 플랜만 남긴다. */
    static Map<PlanStrategy, MonthlyPlan> latestByStrategy(List<MonthlyPlan> found) {
        Map<PlanStrategy, MonthlyPlan> latest = new EnumMap<>(PlanStrategy.class);
        for (MonthlyPlan plan : found) {
            latest.merge(plan.getStrategy(), plan, (a, b) -> a.getVersion() >= b.getVersion() ? a : b);
        }
        return latest;
    }

    private Map<Long, String> cardNames(long userId) {
        Map<Long, String> names = new LinkedHashMap<>();
        for (OwnedCardResponse card : ownedCards.list(userId)) {
            names.put(card.cardId(), card.cardName());
        }
        return names;
    }

}
