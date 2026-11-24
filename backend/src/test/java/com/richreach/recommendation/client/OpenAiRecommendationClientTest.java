package com.richreach.recommendation.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.richreach.codef.entity.CardCompany;
import com.richreach.global.category.SpendingCategory;
import com.richreach.global.openai.OpenAiResponsesClient;
import com.richreach.recommendation.dto.ManualRecommendationResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class OpenAiRecommendationClientTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final OpenAiResponsesClient openAi = mock(OpenAiResponsesClient.class);
    private final OpenAiRecommendationClient client = new OpenAiRecommendationClient(openAi);

    @Test
    void parsesStructuredQuestionWithoutChoosingCard() {
        when(openAi.generateJson(anyString(), anyString(), any(), anyString(), any(), anyInt()))
            .thenReturn(mapper.readTree("""
                {"intent":"RECOMMEND","amount":30000,"category":"SHOPPING","merchantName":"다이소",
                 "needsClarification":false,"answerMode":"BEST_CARD"}
                """));
        var parsed = client.parse("다이소에서 3만 원 결제할 건데?", List.of());
        assertThat(parsed.amount()).isEqualTo(30_000L);
        assertThat(parsed.category()).isEqualTo("SHOPPING");
        assertThat(parsed.merchantName()).isEqualTo("다이소");
    }

    @Test
    void rejectsInventedNumbersAndOtherCardsInExplanation() {
        var result = recommendation();
        when(openAi.generateJson(anyString(), anyString(), any(), anyString(), any(), anyInt()))
            .thenReturn(mapper.readTree("""
                {"explanation":"추천카드가 좋아요. 예상 혜택은 1,200원이고 실적은 30만 원이에요."}
                """));
        assertThatThrownBy(() -> client.explain(result)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsOnlyGroundedShortExplanation() {
        when(openAi.generateJson(anyString(), anyString(), any(), anyString(), any(), anyInt()))
            .thenReturn(mapper.readTree("""
                {"explanation":"이번 결제에는 추천카드가 유리해요. 예상 혜택은 1,200원이에요."}
                """));
        assertThat(client.explain(recommendation())).contains("추천카드", "1,200원");
    }

    private static ManualRecommendationResponse recommendation() {
        var card = new ManualRecommendationResponse.Card(1, 2, CardCompany.KB, "추천카드", null,
            1200, 4.0, null, List.of(), true, List.of());
        var other = new ManualRecommendationResponse.Comparison(3, 4, CardCompany.KB, "대안카드", null, 500);
        return new ManualRecommendationResponse(ManualRecommendationResponse.Type.CARD, "서버 고정 설명",
            new ManualRecommendationResponse.Parsed(SpendingCategory.SHOPPING, 30000L), card, List.of(other),
            List.of(), null, "범위");
    }
}
