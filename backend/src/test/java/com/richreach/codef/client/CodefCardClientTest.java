package com.richreach.codef.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.codef.client.CodefCardClient.CardListResult;
import com.richreach.codef.client.CodefCardClient.CodefCard;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class CodefCardClientTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static CardListResult parse(String json) throws Exception {
        return CodefCardClient.parse(JSON.readTree(json));
    }

    @Test
    @DisplayName("카드 목록 조회는 connectedId와 기관 코드를 담아 카드 목록 경로로 보낸다")
    @SuppressWarnings("unchecked")
    void sendsConnectedIdAndOrganization() throws Exception {
        CodefApiClient apiClient = mock(CodefApiClient.class);
        when(apiClient.post(any(), any())).thenReturn(JSON.readTree("{\"result\":{\"code\":\"CF-00000\"},\"data\":[]}"));

        new CodefCardClient(apiClient).fetchCardList("test-connected-id", "0301");

        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(apiClient).post(eq("/v1/kr/card/p/account/card-list"), body.capture());
        assertThat((Map<String, Object>) body.getValue())
            .containsEntry("connectedId", "test-connected-id").containsEntry("organization", "0301");
    }

    // ---- 카드 읽기 ----

    @Test
    @DisplayName("data가 배열이면 원소마다 카드로 읽는다")
    void readsArrayOfCards() throws Exception {
        CardListResult result = parse("""
            {"result":{"code":"CF-00000"},"data":[
              {"resCardName":"테스트 카드A","resCardNo":"1234-****-****-5678","resCardType":"신용"},
              {"resCardName":"테스트 카드B","resCardNo":"9999-****-****-0000","resCardType":"체크"}]}
            """);

        assertThat(result.success()).isTrue();
        assertThat(result.cardCount()).isEqualTo(2);
        assertThat(result.cards()).containsExactly(
            new CodefCard("테스트 카드A", "1234-****-****-5678", "신용"),
            new CodefCard("테스트 카드B", "9999-****-****-0000", "체크"));
    }

    @Test
    @DisplayName("data가 비어 있지 않은 객체면 카드 한 장으로 읽는다")
    void readsSingleObjectAsOneCard() throws Exception {
        CardListResult result = parse(
            "{\"result\":{\"code\":\"CF-00000\"},\"data\":{\"resCardName\":\"테스트 카드A\",\"resCardNo\":\"1111\"}}");

        assertThat(result.cardCount()).isEqualTo(1);
        assertThat(result.cards()).containsExactly(new CodefCard("테스트 카드A", "1111", ""));
    }

    @Test
    @DisplayName("카드번호나 종류가 없어도 카드명이 있으면 카드로 읽는다 (없는 값은 빈 문자열)")
    void missingOptionalFieldsAreEmpty() throws Exception {
        CardListResult result = parse("{\"result\":{\"code\":\"CF-00000\"},\"data\":[{\"resCardName\":\"테스트 카드A\"}]}");

        assertThat(result.cards()).containsExactly(new CodefCard("테스트 카드A", "", ""));
    }

    @Test
    @DisplayName("필드 이름이 예상과 다르면 카드로 읽지 못하고, 응답의 카드 수와 읽어낸 카드 수가 어긋나서 드러난다")
    void unexpectedFieldNamesShowUpAsCountMismatch() throws Exception {
        CardListResult result = parse(
            "{\"result\":{\"code\":\"CF-00000\"},\"data\":[{\"cardName\":\"테스트 카드A\"},{\"cardName\":\"테스트 카드B\"}]}");

        assertThat(result.success()).isTrue();
        assertThat(result.cardCount()).isEqualTo(2);
        assertThat(result.cards()).isEmpty();
    }

    @Test
    @DisplayName("일부 항목만 카드명이 없으면 그 항목만 빠지고 어긋남이 드러난다")
    void partialUnreadableEntriesShowUp() throws Exception {
        CardListResult result = parse(
            "{\"result\":{\"code\":\"CF-00000\"},\"data\":[{\"resCardName\":\"테스트 카드A\"},{\"resCardName\":\"  \"},{}]}");

        assertThat(result.cardCount()).isEqualTo(3);
        assertThat(result.cards()).hasSize(1);
    }

    @Test
    @DisplayName("data가 없거나 비어 있으면 카드 0장이다")
    void countsEmpty() throws Exception {
        for (String json : new String[] {
            "{\"result\":{\"code\":\"CF-00000\"}}",
            "{\"result\":{\"code\":\"CF-00000\"},\"data\":{}}",
            "{\"result\":{\"code\":\"CF-00000\"},\"data\":[]}"}) {
            CardListResult result = parse(json);

            assertThat(result.success()).isTrue();
            assertThat(result.cardCount()).isZero();
            assertThat(result.cards()).isEmpty();
        }
    }

    // ---- 실패와 보안 ----

    @Test
    @DisplayName("결과 코드가 성공이 아니면 그 코드로 실패 처리하고 카드는 비어 있다")
    void failureKeepsCode() throws Exception {
        CardListResult result = parse(
            "{\"result\":{\"code\":\"CF-12345\"},\"data\":[{\"resCardName\":\"테스트 카드A\"}]}");

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo("CF-12345");
        assertThat(result.cardCount()).isZero();
        assertThat(result.cards()).isEmpty();
    }

    @Test
    @DisplayName("카드 객체를 로그에 찍어도 카드명과 번호가 나오지 않는다")
    void cardToStringIsMasked() {
        CodefCard card = new CodefCard("테스트 카드A", "1234-5678-9012-3456", "신용");

        assertThat(card.toString()).doesNotContain("테스트 카드A").doesNotContain("1234");
        assertThat("card=" + card + ", result=" + new CardListResult(true, 1, "CF-00000", java.util.List.of(card)))
            .doesNotContain("테스트 카드A").doesNotContain("3456");
    }

}
