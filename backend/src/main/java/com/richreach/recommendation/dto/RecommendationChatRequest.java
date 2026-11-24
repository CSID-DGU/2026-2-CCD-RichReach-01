package com.richreach.recommendation.dto;

import java.util.List;

/** 결제 전 자연어 추천 질문. 이력은 최근 10개만 보내며 서버에 저장하지 않는다. */
public record RecommendationChatRequest(String message, List<Turn> history, String confirmedCategory) {
    public record Turn(String role, String content) {
    }
}
