package com.richreach.recommendation.service;

import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.plan.optimizer.ImmediateDiscountSelector;
import com.richreach.plan.optimizer.ImmediateDiscountSelector.Candidate;
import com.richreach.recommendation.dto.ManualRecommendationRequest;
import com.richreach.recommendation.dto.ManualRecommendationResponse;
import com.richreach.recommendation.dto.ManualRecommendationResponse.Card;
import com.richreach.recommendation.dto.ManualRecommendationResponse.Parsed;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 결제 한 건의 현재 혜택을 비교한다. 월간 플랜을 변경하거나 결제를 기록하지 않는다. */
@Service
@RequiredArgsConstructor
public class ManualRecommendationService {

    private static final long MAX_PAYMENT = 100_000_000L;
    private static final String SCOPE = "확인된 카드 혜택 규칙 중 업종과 월간 사용액으로 계산 가능한 비율 혜택만 비교했습니다. 전월실적은 수집 거래 기준 추정값입니다.";

    private final ManualRecommendationInputs inputs;
    private final ImmediateDiscountSelector selector;
    private final RecommendationExplanationClient explanation;

    public ManualRecommendationResponse recommend(long userId, ManualRecommendationRequest request) {
        if (request == null || request.amount() == null || request.amount() <= 0
            || request.amount() > MAX_PAYMENT) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_RECOMMENDATION_INPUT);
        }
        SpendingCategory category = SpendingCategory.fromCode(request.category())
            .orElseThrow(() -> new BusinessException(ErrorCode.INSUFFICIENT_RECOMMENDATION_INPUT));
        var snapshot = inputs.load(userId);
        if (snapshot.cards().isEmpty()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_RECOMMENDATION_INPUT,
                "분석 대상으로 선택하고 카드 상품에 연결된 보유카드가 필요합니다.");
        }
        var choice = selector.choose(category, request.amount(), snapshot.states());
        Map<Long, OwnedCardResponse> byId = snapshot.cards().stream()
            .collect(Collectors.toMap(OwnedCardResponse::cardId, Function.identity()));
        Parsed parsed = new Parsed(category.name(), request.amount());
        if (choice.selected().incrementalBenefit() <= 0 || !choice.selected().confirmedOnly()) {
            return new ManualRecommendationResponse("TEXT",
                "현재 확인된 혜택 규칙으로는 이 결제의 카드별 추가 혜택을 확인할 수 없어요.",
                parsed, null, List.of(), SCOPE);
        }
        Card selected = toCard(choice.selected(), byId);
        List<Card> alternatives = choice.candidates().stream().skip(1)
            .map(candidate -> toCard(candidate, byId)).toList();
        long nextBenefit = alternatives.isEmpty() ? 0 : alternatives.get(0).expectedBenefit();
        String message = selected.cardName() + "의 계산 가능한 예상 혜택은 "
            + String.format("%,d", selected.expectedBenefit()) + "원이에요.";
        String reason = explanation.explain(category, selected.expectedBenefit(), nextBenefit).orElse(null);
        if (reason != null) message += " " + reason;
        return new ManualRecommendationResponse("CARD", message, parsed, selected, alternatives, SCOPE);
    }

    private static Card toCard(Candidate candidate, Map<Long, OwnedCardResponse> byId) {
        OwnedCardResponse owned = byId.get(candidate.userCardId());
        return new Card(owned.cardId(), owned.cardProductId(), owned.cardCompany(), owned.cardName(),
            owned.imageUrl(), candidate.incrementalBenefit(), candidate.confirmedOnly(), candidate.notes());
    }
}
