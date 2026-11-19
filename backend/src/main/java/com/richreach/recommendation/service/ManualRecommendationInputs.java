package com.richreach.recommendation.service;

import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.plan.optimizer.ImmediateDiscountSelector.CardState;
import com.richreach.plan.service.PlanInputAssembler;
import com.richreach.transaction.service.TransactionSummaryService;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 보유카드와 전월·당월 거래를 같은 읽기 트랜잭션에서 모은다. */
@Component
@RequiredArgsConstructor
public class ManualRecommendationInputs {

    private final PlanInputAssembler planInputs;
    private final TransactionSummaryService transactions;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Snapshot load(long userId) {
        YearMonth month = YearMonth.now(clock);
        List<OwnedCardResponse> cards = planInputs.analysisTargets(userId);
        if (cards.isEmpty()) return new Snapshot(List.of(), List.of());
        Map<Long, Long> previous = transactions.cardSpend(userId, month.minusMonths(1));
        var current = transactions.cardCategorySpend(userId, month);
        List<CardState> states = cards.stream().map(card -> new CardState(card.cardId(), card.cardProductId(),
            previous.getOrDefault(card.cardId(), 0L), current.getOrDefault(card.cardId(), Map.of()))).toList();
        return new Snapshot(cards, states);
    }

    public record Snapshot(List<OwnedCardResponse> cards, List<CardState> states) {
    }
}
