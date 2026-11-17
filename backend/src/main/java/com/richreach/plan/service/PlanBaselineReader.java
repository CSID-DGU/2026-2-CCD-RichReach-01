package com.richreach.plan.service;

import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.entity.MonthlyPlan;
import com.richreach.plan.entity.PlanCardAllocation;
import com.richreach.plan.entity.PlanStrategy;
import com.richreach.plan.entity.SpendingProfileItem;
import com.richreach.plan.repository.MonthlyPlanRepository;
import com.richreach.plan.repository.PlanCardAllocationRepository;
import com.richreach.plan.repository.SpendingProfileItemRepository;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재계산의 출발점이 되는 지금 보여주는 플랜을 읽는다. 사용자에게 보이는 플랜은 {@link PlanQueryService}와 같은 규칙(전략
 * 우선순위, 가장 최신 version)으로 고르고, 확정 예산은 그 플랜의 소비 프로필 항목에서 읽는다.
 * 금액은 소비 정보라서 로그에 남기지 않는다.
 */
@Component
@RequiredArgsConstructor
class PlanBaselineReader {

    private final MonthlyPlanRepository plans;
    private final SpendingProfileItemRepository profileItems;
    private final PlanCardAllocationRepository allocations;

    /**
     * 재계산의 출발점.
     *
     * @param plan        지금 보여주는 플랜
     * @param budget      그 플랜의 확정 예산(업종별, 0원인 업종은 없음)
     * @param allocations 그 플랜의 보유카드별·업종별 목표 금액
     */
    record Baseline(MonthlyPlan plan, PlanBudget budget, Map<Long, Map<SpendingCategory, Long>> allocations) {

    }

    /** 그 달에 보여줄 플랜이 없으면 비어 있다. */
    @Transactional(readOnly = true)
    Optional<Baseline> read(long userId, YearMonth month) {
        Map<PlanStrategy, MonthlyPlan> latest = PlanQueryService.latestByStrategy(
            plans.findByUserIdAndPlanMonth(userId, month.toString()));
        Optional<PlanStrategy> primary = PlanPrimarySelector.select(latest.keySet());
        if (primary.isEmpty()) {
            return Optional.empty();
        }
        MonthlyPlan plan = latest.get(primary.get());

        Map<SpendingCategory, Long> byCategory = new EnumMap<>(SpendingCategory.class);
        long total = 0;
        for (SpendingProfileItem item : profileItems.findByProfileId(plan.getProfileId())) {
            if (item.getExpectedAmount() > 0) {
                byCategory.put(item.getCategory(), item.getExpectedAmount());
                total += item.getExpectedAmount();
            }
        }
        Map<Long, Map<SpendingCategory, Long>> byCard = new HashMap<>();
        for (PlanCardAllocation allocation : allocations.findByPlanId(plan.getId())) {
            byCard.computeIfAbsent(allocation.getUserCardId(), id -> new EnumMap<>(SpendingCategory.class))
                .merge(allocation.getCategory(), allocation.getTargetAmount(), Long::sum);
        }
        return Optional.of(new Baseline(plan, new PlanBudget(month, total, byCategory), byCard));
    }

}
