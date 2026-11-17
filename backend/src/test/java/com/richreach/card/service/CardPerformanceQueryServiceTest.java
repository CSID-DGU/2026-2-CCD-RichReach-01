package com.richreach.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.richreach.card.entity.PerformanceExclusion;
import com.richreach.card.repository.PerformanceExclusionRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 전월실적 산정 제외 조건 문장을 안내 문구로 다듬는 규칙을 DB 없이 확인한다. */
class CardPerformanceQueryServiceTest {

    private PerformanceExclusionRepository repository;
    private CardPerformanceQueryService service;

    @BeforeEach
    void setUp() {
        repository = mock(PerformanceExclusionRepository.class);
        service = new CardPerformanceQueryService(repository);
    }

    private static PerformanceExclusion exclusion(String appliesTo, String text) {
        PerformanceExclusion exclusion = mock(PerformanceExclusion.class);
        when(exclusion.getAppliesTo()).thenReturn(appliesTo);
        when(exclusion.getConditionText()).thenReturn(text);
        return exclusion;
    }

    /** 목을 모두 만든 뒤에 저장소 응답을 정한다. (when 안에서 목을 만들면 Mockito가 stubbing을 헷갈린다) */
    private void givenExclusions(PerformanceExclusion... exclusions) {
        List<PerformanceExclusion> list = List.of(exclusions);
        when(repository.findByCardProductId(100L)).thenReturn(list);
    }

    @Test
    @DisplayName("전월실적 산정 제외 조건만 문장으로 돌려주고 혜택 제외 조건은 뺀다")
    void onlyPerformanceExclusions() {
        givenExclusions(exclusion("PERFORMANCE", "상품권 구입액은 실적에서 제외"),
            exclusion("BENEFIT", "해외 가맹점은 혜택 제외"));

        assertThat(service.performanceExclusionNotes(100L)).containsExactly("상품권 구입액은 실적에서 제외");
    }

    @Test
    @DisplayName("앞뒤 공백을 없애고, 빈 문장과 같은 문장은 한 번만 돌려준다")
    void trimsAndDeduplicates() {
        givenExclusions(exclusion("PERFORMANCE", "  세금 납부액 제외  "), exclusion("PERFORMANCE", "세금 납부액 제외"),
            exclusion("PERFORMANCE", "   "), exclusion("PERFORMANCE", null));

        assertThat(service.performanceExclusionNotes(100L)).containsExactly("세금 납부액 제외");
    }

    @Test
    @DisplayName("긴 문장은 200자에서 줄이고, 한 카드에는 최대 5개만 담는다")
    void limitsLengthAndCount() {
        String longText = "가".repeat(300);
        givenExclusions(exclusion("PERFORMANCE", longText), exclusion("PERFORMANCE", "2"),
            exclusion("PERFORMANCE", "3"), exclusion("PERFORMANCE", "4"), exclusion("PERFORMANCE", "5"),
            exclusion("PERFORMANCE", "6"));

        List<String> notes = service.performanceExclusionNotes(100L);

        assertThat(notes).hasSize(5);
        assertThat(notes.get(0)).hasSize(CardPerformanceQueryService.MAX_NOTE_LENGTH + 1).endsWith("…");
        assertThat(notes).doesNotContain("6");
    }

    @Test
    @DisplayName("제외 조건이 없으면 빈 목록이다")
    void noExclusions() {
        when(repository.findByCardProductId(100L)).thenReturn(List.of());

        assertThat(service.performanceExclusionNotes(100L)).isEmpty();
    }

}
