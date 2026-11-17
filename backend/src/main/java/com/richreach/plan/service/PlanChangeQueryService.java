package com.richreach.plan.service;

import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.plan.dto.PlanChangesResponse;
import com.richreach.plan.dto.PlanChangesResponse.Change;
import com.richreach.plan.dto.PlanChangesResponse.Item;
import com.richreach.plan.entity.MonthlyPlan;
import com.richreach.plan.entity.PlanChange;
import com.richreach.plan.entity.PlanChangeItem;
import com.richreach.plan.repository.MonthlyPlanRepository;
import com.richreach.plan.repository.PlanChangeItemRepository;
import com.richreach.plan.repository.PlanChangeRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월간 플랜의 변경 기록을 조회하고, 사용자가 변경 내용을 확인했다고 표시한다. 다른 사용자의 플랜이나 기록은 없는 것과 똑같이
 * {@code PLAN-001}, {@code PLAN-009}로 응답해서 존재 여부를 알려주지 않는다. 카드 이름은 카드 서비스를 거쳐서 읽는다.
 * 금액은 소비 정보라서 로그에 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PlanChangeQueryService {

    private final MonthlyPlanRepository plans;
    private final PlanChangeRepository changes;
    private final PlanChangeItemRepository changeItems;
    private final OwnedCardQueryService ownedCards;
    private final Clock clock;

    /** 플랜이 속한 달의 변경 기록. 없거나 다른 사용자의 플랜이면 {@code PLAN-001}이다. */
    @Transactional(readOnly = true)
    public PlanChangesResponse list(long userId, long planId) {
        MonthlyPlan plan = plans.findById(planId).filter(found -> found.getUserId().equals(userId))
            .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        List<PlanChange> found = changes.findByUserIdAndPlanMonthOrderByIdDesc(userId, plan.getPlanMonth());
        return new PlanChangesResponse(plan.getId(), plan.getPlanMonth(), toResponses(userId, found));
    }

    /**
     * 변경 내용을 확인한 것으로 표시하고 그 기록을 돌려준다. 이미 확인했으면 처음 확인한 상태 그대로다.
     * 없거나 다른 사용자의 기록이면 {@code PLAN-009}이다.
     */
    @Transactional
    public Change confirm(long userId, long changeId) {
        PlanChange change = changes.findByIdAndUserId(changeId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_CHANGE_NOT_FOUND));
        change.confirm(LocalDateTime.now(clock));
        return toResponses(userId, List.of(change)).get(0);
    }

    private List<Change> toResponses(long userId, List<PlanChange> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, String> cardNames = new HashMap<>();
        for (OwnedCardResponse card : ownedCards.list(userId)) {
            cardNames.put(card.cardId(), card.cardName());
        }
        Map<Long, List<PlanChangeItem>> itemsByChange = changeItems
            .findByChangeIdInOrderById(found.stream().map(PlanChange::getId).toList()).stream()
            .collect(Collectors.groupingBy(PlanChangeItem::getChangeId));
        return found.stream().map(change -> new Change(change.getId(), change.getPlanId(), change.getPreviousPlanId(),
            change.getReason().name(), change.getReasonText(), change.getPreviousBenefit(), change.getCurrentBenefit(),
            change.getCurrentBenefit() - change.getPreviousBenefit(), change.isConfirmed(), change.getCreatedAt(),
            itemsByChange.getOrDefault(change.getId(), List.of()).stream().map(item -> new Item(item.getType().name(),
                item.getCategory(), item.getUserCardId(), nameOf(cardNames, item.getUserCardId()),
                item.getPreviousCardId(), nameOf(cardNames, item.getPreviousCardId()), item.getPreviousAmount(),
                item.getCurrentAmount())).toList())).toList();
    }

    private static String nameOf(Map<Long, String> names, Long cardId) {
        return cardId == null ? null : names.get(cardId);
    }

}
