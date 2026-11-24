package com.richreach.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.card.dto.CardEvidenceResponse.Status;
import com.richreach.card.entity.CardProduct;
import com.richreach.card.repository.CardProductRepository;
import com.richreach.global.openai.OpenAiResponsesClient;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.json.JsonMapper;

class CardEvidenceServiceTest {
    @Test
    void searchesOnlyTheRequestedCardsSourceScopes() {
        var cards = mock(CardProductRepository.class);
        var jdbc = mock(JdbcTemplate.class);
        var ai = mock(OpenAiResponsesClient.class);
        var card = mock(CardProduct.class);
        when(cards.findById(5L)).thenReturn(Optional.of(card));
        when(card.getIssuerCode()).thenReturn("bc");
        when(card.getCardId()).thenReturn("123");
        when(card.getSourceSnapshotDate()).thenReturn(LocalDate.of(2026, 10, 20));
        doReturn(List.of()).when(jdbc).query(anyString(), any(RowMapper.class), eq("bc"), eq("123"));

        var response = new CardEvidenceService(cards, jdbc, ai, JsonMapper.builder().build())
            .explain(5, "점심 캐시백");

        assertThat(response.status()).isEqualTo(Status.INSUFFICIENT_EVIDENCE);
        assertThat(response.citations()).isEmpty();
        verify(jdbc).query(anyString(), any(RowMapper.class), eq("bc"), eq("123"));
    }

    @Test
    void ranksRelevantChunksWithoutMixingUnrelatedText() {
        var ranked = CardEvidenceService.rank(List.of(
            new CardEvidenceService.Source("s1", "https://issuer.example/1", "점심 시간 캐시백 0.6%"),
            new CardEvidenceService.Source("s2", "https://issuer.example/2", "주유 할인 안내")),
            "점심 캐시백");
        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).citation().citationId()).isEqualTo("s1#0001");
        assertThat(ranked.get(0).citation().sourceType()).isEqualTo("PRODUCT_PAGE_EXCERPT");
    }

    @Test
    void ranksDetailedMovieTermsBeforeNavigationSummary() {
        var ranked = CardEvidenceService.rank(List.of(
            new CardEvidenceService.Source("navigation", "https://issuer.example/card",
                "주요 혜택: 대중교통, 이동통신, 영화, 편의점"),
            new CardEvidenceService.Source("terms", "https://issuer.example/card",
                "CGV 영화 예매 5천원 캐시백. 영화 예매는 건당 2만원 이상 결제 시 제공. "
                    + "영화관 매점 결제는 제외됩니다.")), "영화 할인");

        assertThat(ranked).hasSize(2);
        assertThat(ranked.get(0).citation().citationId()).isEqualTo("terms#0001");
    }

    @Test
    void genericQuestionDoesNotSelectArbitraryNavigationText() {
        var ranked = CardEvidenceService.rank(List.of(
            new CardEvidenceService.Source("navigation", "https://issuer.example/card",
                "카드 혜택 추천 할인 안내")), "이 카드 혜택 알려줘");

        assertThat(ranked).isEmpty();
    }

    @Test
    void rejectsModelCitationNotPresentInRetrievedSources() {
        var cards = mock(CardProductRepository.class);
        var jdbc = mock(JdbcTemplate.class);
        var ai = mock(OpenAiResponsesClient.class);
        var card = mock(CardProduct.class);
        when(cards.findById(5L)).thenReturn(Optional.of(card));
        when(card.getIssuerCode()).thenReturn("bc");
        when(card.getCardId()).thenReturn("123");
        when(card.getCardName()).thenReturn("예시카드");
        when(card.getSourceSnapshotDate()).thenReturn(LocalDate.of(2026, 10, 20));
        doReturn(List.of(new CardEvidenceService.Source("s1", "https://issuer.example/1",
            "점심 시간 캐시백 0.6%"))).when(jdbc)
            .query(anyString(), any(RowMapper.class), eq("bc"), eq("123"));
        when(ai.generateJson(anyString(), anyString(), any(), anyString(), any(), anyInt()))
            .thenReturn(JsonMapper.builder().build().readTree("""
                {"status":"ANSWERED","answer":"[2026-10-20] 점심 시간에는 캐시백 0.6%입니다.",
                 "citationIds":["other-card#0001"]}
                """));

        var response = new CardEvidenceService(cards, jdbc, ai, JsonMapper.builder().build())
            .explain(5, "점심 캐시백");

        assertThat(response.status()).isEqualTo(Status.SOURCE_FOUND);
        assertThat(response.citations()).extracting("citationId").containsExactly("s1#0001");
    }
}
