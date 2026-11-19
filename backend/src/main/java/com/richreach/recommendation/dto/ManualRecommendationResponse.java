package com.richreach.recommendation.dto;

import com.richreach.codef.entity.CardCompany;
import java.util.List;

/** 확인 가능한 혜택만 계산한 결제 전 추천. CARD가 아니면 card는 null이다. */
public record ManualRecommendationResponse(String type, String message, Parsed parsed, Card card,
                                           List<Card> alternatives, String calculationScope) {

    public record Parsed(String category, long amount) {
    }

    public record Card(long cardId, long cardProductId, CardCompany cardCompany, String cardName,
                       String imageUrl, long expectedBenefit, boolean confirmedOnly, List<String> notes) {
        public Card {
            notes = List.copyOf(notes);
        }
    }
}
