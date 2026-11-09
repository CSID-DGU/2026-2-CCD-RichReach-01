package com.richreach.plan.service;

import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.plan.optimizer.PlanStrategyInput;
import com.richreach.plan.optimizer.PlanStrategyInput.CardInput;
import com.richreach.transaction.service.TransactionSummaryService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 플랜 전략에 줄 입력을 만든다. 쓸 수 있는 카드는 사용자가 분석 대상으로 고른 카드뿐이고, 카드 정보와 전월 소비는 각 도메인의
 * 서비스를 거쳐서 읽는다. (카드·거래의 엔티티와 리포지토리를 직접 읽지 않는다.)
 *
 * <p>카드의 전월 실적은 플랜 대상 월의 직전 달에 그 카드로 쓴 금액이다. 카드사가 알려주는 실적이 아니라 수집한 거래로 계산한
 * 추정값이다.
 */
@Component
@RequiredArgsConstructor
class PlanInputAssembler {

    private final OwnedCardQueryService ownedCards;
    private final TransactionSummaryService transactions;

    /** 분석 대상 카드가 하나도 없으면 비어 있다. */
    Optional<PlanStrategyInput> assemble(long userId, PlanBudget budget, Duration timeLimit) {
        List<OwnedCardResponse> targets = analysisTargets(userId);
        if (targets.isEmpty()) {
            return Optional.empty();
        }
        Map<Long, Long> previousSpend = transactions.cardSpend(userId, budget.month().minusMonths(1));
        List<CardInput> cards = targets.stream()
            .map(card -> new CardInput(card.cardId(), card.cardProductId(), previousSpend.getOrDefault(card.cardId(), 0L)))
            .toList();
        return Optional.of(new PlanStrategyInput(userId, budget.month(), budget.byCategory(), cards, timeLimit));
    }

    /** 사용자가 분석 대상으로 고른 카드. 카드 상품에 매칭된 카드만 분석 대상이 될 수 있다. */
    List<OwnedCardResponse> analysisTargets(long userId) {
        return ownedCards.list(userId).stream().filter(card -> card.selected() && card.cardProductId() != null).toList();
    }

}
