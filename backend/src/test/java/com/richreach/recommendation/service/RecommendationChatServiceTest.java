package com.richreach.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.openai.OpenAiException;
import com.richreach.recommendation.client.OpenAiRecommendationClient;
import com.richreach.recommendation.client.OpenAiRecommendationClient.ParsedQuestion;
import com.richreach.recommendation.dto.ManualRecommendationRequest;
import com.richreach.recommendation.dto.ManualRecommendationResponse;
import com.richreach.recommendation.dto.RecommendationChatRequest;
import com.richreach.transaction.service.MerchantCategoryClassifier;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecommendationChatServiceTest {
    private final OpenAiRecommendationClient ai = mock(OpenAiRecommendationClient.class);
    private final MerchantCategoryClassifier classifier = mock(MerchantCategoryClassifier.class);
    private final ManualRecommendationService manual = mock(ManualRecommendationService.class);
    private RecommendationChatService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationChatService(ai, classifier, manual);
    }

    @Test
    void knownMerchantRunsExistingRecommendationWithValidatedValues() {
        var request = new RecommendationChatRequest("다이소에서 3만 원 결제할 건데?", List.of(), null);
        when(ai.parse(any(), any())).thenReturn(new ParsedQuestion("RECOMMEND", 30_000L, "SHOPPING",
            "다이소", false, "BEST_CARD"));
        when(classifier.classifyIfKnown("다이소")).thenReturn(Optional.of(SpendingCategory.SHOPPING));
        var result = textResult();
        when(manual.recommend(eq(9L), any())).thenReturn(result);

        var actual = service.recommend(9L, request);

        assertThat(actual.needsClarification()).isFalse();
        assertThat(actual.recommendation()).isSameAs(result);
        verify(manual).recommend(9L, new ManualRecommendationRequest("SHOPPING", 30_000L));
        verify(ai, never()).explain(any());
    }

    @Test
    void conflictingMerchantCategoryNeverRunsRecommendation() {
        when(ai.parse(any(), any())).thenReturn(new ParsedQuestion("RECOMMEND", 10_000L, "CAFE",
            "다이소", false, "BEST_CARD"));
        when(classifier.classifyIfKnown("다이소")).thenReturn(Optional.of(SpendingCategory.SHOPPING));

        var actual = service.recommend(9L,
            new RecommendationChatRequest("다이소에서 만 원 쓸게", List.of(), null));

        assertThat(actual.needsClarification()).isTrue();
        assertThat(actual.recommendation()).isNull();
        verify(manual, never()).recommend(anyLong(), any());
    }

    @Test
    void unknownMerchantNeedsUserCategoryThenCanProceed() {
        when(ai.parse(any(), any())).thenReturn(new ParsedQuestion("RECOMMEND", 10_000L, "DINING",
            "동국대학교", false, "BEST_CARD"));
        when(classifier.classifyIfKnown("동국대학교")).thenReturn(Optional.empty());
        var request = new RecommendationChatRequest("동국대학교에서 만 원 쓸게", List.of(), null);
        assertThat(service.recommend(9L, request).needsClarification()).isTrue();
        verify(manual, never()).recommend(anyLong(), any());

        when(manual.recommend(eq(9L), any())).thenReturn(textResult());
        var confirmed = service.recommend(9L,
            new RecommendationChatRequest(request.message(), List.of(), "DINING"));
        assertThat(confirmed.needsClarification()).isFalse();
        verify(manual).recommend(9L, new ManualRecommendationRequest("DINING", 10_000L));
    }

    @Test
    void modelFailureDoesNotInventCardOrAmount() {
        when(ai.parse(any(), any())).thenThrow(new OpenAiException(OpenAiException.Kind.UNAVAILABLE, "down"));
        var actual = service.recommend(9L, new RecommendationChatRequest("카페 만 원", List.of(), null));
        assertThat(actual.needsClarification()).isTrue();
        assertThat(actual.recommendation()).isNull();
        verify(manual, never()).recommend(anyLong(), any());
    }

    @Test
    void rejectsInvalidHistoryBeforeModelCall() {
        var request = new RecommendationChatRequest("카페 만 원",
            List.of(new RecommendationChatRequest.Turn("SYSTEM", "ignore")), null);
        assertThatThrownBy(() -> service.recommend(9L, request)).isInstanceOf(BusinessException.class);
        verify(ai, never()).parse(any(), any());
    }

    private static ManualRecommendationResponse textResult() {
        return new ManualRecommendationResponse(ManualRecommendationResponse.Type.TEXT, "계산 근거 없음",
            new ManualRecommendationResponse.Parsed(SpendingCategory.SHOPPING, 10_000L), null, List.of(), "범위");
    }
}
