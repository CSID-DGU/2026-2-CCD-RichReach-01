package com.richreach.plan.service;

import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.entity.MonthlyPlan;
import com.richreach.plan.entity.PlanCardAllocation;
import com.richreach.plan.entity.PlanChange;
import com.richreach.plan.entity.PlanChangeItem;
import com.richreach.plan.entity.PlanChangeReason;
import com.richreach.plan.entity.PlanStrategy;
import com.richreach.plan.entity.SpendingProfile;
import com.richreach.plan.entity.SpendingProfileItem;
import com.richreach.plan.repository.MonthlyPlanRepository;
import com.richreach.plan.repository.PlanCardAllocationRepository;
import com.richreach.plan.repository.PlanChangeItemRepository;
import com.richreach.plan.repository.PlanChangeRepository;
import com.richreach.plan.repository.SpendingProfileItemRepository;
import com.richreach.plan.repository.SpendingProfileRepository;
import com.richreach.plan.service.PlanChangeBuilder.Draft;
import com.richreach.plan.service.PlanRecalculationComputer.Computation;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재계산 결과를 새 플랜 버전과 변경 기록으로 저장한다. 호출하는 쪽의 트랜잭션에 참여하므로(트랜잭션이 없으면 오류) 플랜, 배분,
 * 변경 기록이 작업 완료 기록과 함께 저장되거나 함께 되돌려진다.
 *
 * <ul>
 *   <li>배분이 이전과 완전히 같으면 새 버전을 만들지 않고 지금 플랜을 그대로 돌려준다. (거래가 들어올 때마다 버전이 쌓이지 않는다)</li>
 *   <li>새 버전은 CP_SAT 전략의 다음 version이다. 조회는 전략 우선순위와 최신 version으로 보여줄 플랜을 고르므로 이 플랜이
 *       바로 보인다. 이전 version은 지우지 않는다.</li>
 *   <li>플랜의 소비 프로필(확정 예산)은 예산이 그대로면 이전 플랜의 것을 쓰고, 사용자가 예산을 바꿨으면 새 프로필을 만든다.
 *       예산만 바뀌고 배분이 같아도 새 버전을 만든다.</li>
 * </ul>
 * 금액은 소비 정보라서 로그에 남기지 않는다.
 */
@Component
@RequiredArgsConstructor
class PlanRecalculationSaver {

    private final MonthlyPlanRepository plans;
    private final PlanCardAllocationRepository allocations;
    private final PlanChangeRepository changes;
    private final PlanChangeItemRepository changeItems;
    private final SpendingProfileRepository profiles;
    private final SpendingProfileItemRepository profileItems;

    /** 지금 보여줄 플랜의 ID. 바뀐 것이 없으면 이전 플랜의 ID이고, 있으면 새로 저장한 플랜의 ID다. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    long save(long userId, YearMonth month, Computation computation, PlanChangeReason reason) {
        MonthlyPlan base = computation.baseline().plan();
        List<Draft> drafts = PlanChangeBuilder.diff(computation.baseline().allocations(), computation.targets());
        // 예산이 바뀌었으면 배분이 같아도 새 버전을 만들어 새 예산을 확정 예산으로 남긴다.
        boolean budgetChanged = !computation.budget().byCategory().equals(computation.baseline().budget().byCategory());
        if (drafts.isEmpty() && !budgetChanged) {
            return base.getId();
        }

        int version = plans.findFirstByUserIdAndPlanMonthAndStrategyOrderByVersionDesc(userId, month.toString(),
            PlanStrategy.CP_SAT).map(plan -> plan.getVersion() + 1).orElse(1);
        long profileId = budgetChanged ? saveProfile(userId, month, computation.budget()) : base.getProfileId();
        MonthlyPlan plan = MonthlyPlan.create(userId, profileId, month, PlanStrategy.CP_SAT, version,
            computation.totalSpend(), computation.totalBenefit());
        plan.recordSolve(computation.solverStatus(), null);
        plan = plans.save(plan);

        List<PlanCardAllocation> rows = new ArrayList<>();
        for (Map.Entry<Long, Map<SpendingCategory, Long>> card : computation.targets().entrySet()) {
            for (Map.Entry<SpendingCategory, Long> target : card.getValue().entrySet()) {
                long benefit = computation.benefits().getOrDefault(card.getKey(), Map.of())
                    .getOrDefault(target.getKey(), 0L);
                rows.add(PlanCardAllocation.create(plan.getId(), card.getKey(), target.getKey(), target.getValue(),
                    benefit));
            }
        }
        allocations.saveAll(rows);

        PlanChange change = changes.save(PlanChange.create(userId, month, plan.getId(), base.getId(), reason,
            base.getExpectedBenefit(), computation.totalBenefit()));
        changeItems.saveAll(drafts.stream().map(draft -> PlanChangeItem.create(change.getId(), draft.type(),
            draft.category(), draft.userCardId(), draft.previousCardId(), draft.previousAmount(),
            draft.currentAmount())).toList());
        return plan.getId();
    }

    /** 사용자가 새로 정한 예산으로 소비 프로필을 저장한다. 플랜 생성과 같은 방식이다. */
    private long saveProfile(long userId, YearMonth month, PlanBudget budget) {
        SpendingProfile profile = profiles.save(SpendingProfile.create(userId, month, month.minusMonths(2).atDay(1),
            month.minusMonths(1).atEndOfMonth()));
        budget.byCategory().forEach((category, amount) -> profileItems.save(
            SpendingProfileItem.create(profile.getId(), category, amount, 0, BigDecimal.ONE, BigDecimal.ONE)));
        return profile.getId();
    }

}
