package com.richreach.recommendation.service;

import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.global.openai.OpenAiException;
import com.richreach.recommendation.client.OpenAiRecommendationClient;
import com.richreach.recommendation.client.OpenAiRecommendationClient.ParsedQuestion;
import com.richreach.recommendation.dto.ManualRecommendationRequest;
import com.richreach.recommendation.dto.ManualRecommendationResponse;
import com.richreach.recommendation.dto.RecommendationChatRequest;
import com.richreach.recommendation.dto.RecommendationChatRequest.Turn;
import com.richreach.recommendation.dto.RecommendationChatResponse;
import com.richreach.transaction.service.MerchantCategoryClassifier;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 질문 해석과 검증 뒤 기존 수동 추천을 호출한다. 모델은 금액과 카드를 결정하지 않는다. */
@Service
@RequiredArgsConstructor
public class RecommendationChatService {
    private static final String ASK_AMOUNT = "결제할 정확한 금액을 알려주세요.";
    private static final String ASK_CATEGORY = "어떤 업종 또는 용도로 결제하시나요?";
    private static final String ASK_BOTH = "결제할 금액과 업종을 알려주세요.";
    private static final String OUT_OF_SCOPE = "결제 전 카드 추천을 도와드릴게요. 결제할 금액과 업종을 알려주세요.";
    private static final String AI_UNAVAILABLE = "질문을 해석하지 못했어요. 금액과 업종을 직접 선택해 주세요.";

    private final OpenAiRecommendationClient ai;
    private final MerchantCategoryClassifier classifier;
    private final ManualRecommendationService manual;

    public RecommendationChatResponse recommend(long userId, RecommendationChatRequest request) {
        List<Turn> history = validate(request);
        ParsedQuestion parsed;
        try {
            parsed = ai.parse(request.message().strip(), history);
        } catch (OpenAiException | IllegalArgumentException e) {
            return clarify(AI_UNAVAILABLE, null, "BEST_CARD");
        }
        if ("OUT_OF_SCOPE".equals(parsed.intent())) {
            return new RecommendationChatResponse(OUT_OF_SCOPE, false, parsed.answerMode(), null, null);
        }
        Long amount = parsed.amount();
        SpendingCategory category = SpendingCategory.fromCode(parsed.category()).orElse(null);
        if (request.confirmedCategory() != null) {
            category = SpendingCategory.fromCode(request.confirmedCategory())
                .orElseThrow(() -> new BusinessException(ErrorCode.INSUFFICIENT_RECOMMENDATION_INPUT,
                    "확인한 업종 코드가 올바르지 않습니다."));
        }
        String merchant = parsed.merchantName();
        if (merchant != null && !presentInConversation(merchant, request.message(), history)) {
            return clarify(ASK_CATEGORY, null, parsed.answerMode());
        }
        if (merchant != null) {
            Optional<SpendingCategory> known = classifier.classifyIfKnown(merchant);
            if (known.isPresent() && category != null && known.get() != category) {
                return clarify("가맹점의 분류와 선택한 업종이 달라요. 결제 용도를 확인해 주세요.", merchant,
                    parsed.answerMode());
            }
            if (known.isEmpty() && request.confirmedCategory() == null) {
                return clarify("가맹점의 업종을 확인할 수 없어요. 결제 용도를 선택해 주세요.", merchant,
                    parsed.answerMode());
            }
            if (category == null) category = known.orElse(null);
        }
        if (amount == null || amount < 1 || amount > ManualRecommendationService.MAX_AMOUNT || category == null) {
            String question = amount == null && category == null ? ASK_BOTH : amount == null ? ASK_AMOUNT : ASK_CATEGORY;
            return clarify(question, merchant, parsed.answerMode());
        }
        if (parsed.needsClarification() && request.confirmedCategory() == null) {
            return clarify(ASK_CATEGORY, merchant, parsed.answerMode());
        }
        ManualRecommendationResponse result = manual.recommend(userId,
            new ManualRecommendationRequest(category.name(), amount));
        String reply = result.message();
        if ("ALTERNATIVES".equals(parsed.answerMode()) && result.type() == ManualRecommendationResponse.Type.CARD) {
            reply = result.alternatives().isEmpty()
                ? "현재 확인된 규칙으로 계산 가능한 다른 카드는 없어요."
                : "다른 카드도 아래에서 비교해 보세요.";
        } else if (result.type() == ManualRecommendationResponse.Type.CARD && result.card().confirmedOnly()
            && "BEST_CARD".equals(parsed.answerMode())) {
            try {
                reply = ai.explain(result);
            } catch (OpenAiException | IllegalArgumentException e) {
                // 설명은 선택 기능이다. 계산된 카드·금액·순위와 고정 안내는 그대로 돌려준다.
            }
        }
        return new RecommendationChatResponse(reply, false, parsed.answerMode(), merchant, result);
    }

    private static RecommendationChatResponse clarify(String message, String merchant, String mode) {
        return new RecommendationChatResponse(message, true, mode, merchant, null);
    }

    private static List<Turn> validate(RecommendationChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()
            || request.message().length() > 500) throw new BusinessException(ErrorCode.INSUFFICIENT_RECOMMENDATION_INPUT);
        List<Turn> history = request.history() == null ? List.of() : request.history();
        if (history.size() > 10) throw new BusinessException(ErrorCode.INSUFFICIENT_RECOMMENDATION_INPUT);
        for (Turn turn : history) {
            if (turn == null || !("USER".equals(turn.role()) || "ASSISTANT".equals(turn.role()))
                || turn.content() == null || turn.content().isBlank() || turn.content().length() > 500) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_RECOMMENDATION_INPUT);
            }
        }
        return List.copyOf(history);
    }

    private static boolean presentInConversation(String merchant, String message, List<Turn> history) {
        if (message.contains(merchant)) return true;
        return history.stream().anyMatch(turn -> "USER".equals(turn.role()) && turn.content().contains(merchant));
    }
}
