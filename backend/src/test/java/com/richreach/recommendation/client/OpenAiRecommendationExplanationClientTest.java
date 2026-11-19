package com.richreach.recommendation.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenAiRecommendationExplanationClientTest {

    @Test
    void acceptsShortKoreanComparisonButRejectsInventedConditionsOrBrokenText() {
        assertThat(OpenAiRecommendationExplanationClient.valid("계산 가능한 혜택을 비교하면 이 카드가 유리해요."))
            .isTrue();
        assertThat(OpenAiRecommendationExplanationClient.valid("실적을 달성해 10% 할인이 확정돼요."))
            .isFalse();
        assertThat(OpenAiRecommendationExplanationClient.valid("혜택이 높�아요."))
            .isFalse();
    }
}
