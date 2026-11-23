package com.richreach.recommendation.dto;

/** AI가 질문을 해석하되 추천 카드와 혜택 금액은 기존 서버 계산 결과를 그대로 담는다. */
public record RecommendationChatResponse(String reply, boolean needsClarification, String answerMode,
                                         String merchantName, ManualRecommendationResponse recommendation) {
}
