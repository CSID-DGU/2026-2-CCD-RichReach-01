package com.richreach.codef.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.codef.client.CodefCardClient.CardListResult;
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

    @Test
    @DisplayName("data가 배열이면 길이를 카드 수로 센다")
    void countsArray() throws Exception {
        CardListResult result = parse("{\"result\":{\"code\":\"CF-00000\"},\"data\":[{},{},{}]}");

        assertThat(result.success()).isTrue();
        assertThat(result.cardCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("data가 비어 있지 않은 객체면 카드 1장으로 센다")
    void countsSingleObject() throws Exception {
        assertThat(parse("{\"result\":{\"code\":\"CF-00000\"},\"data\":{\"resCardName\":\"x\"}}").cardCount())
            .isEqualTo(1);
    }

    @Test
    @DisplayName("data가 없거나 비어 있으면 카드 0장이다")
    void countsEmpty() throws Exception {
        assertThat(parse("{\"result\":{\"code\":\"CF-00000\"}}").cardCount()).isZero();
        assertThat(parse("{\"result\":{\"code\":\"CF-00000\"},\"data\":{}}").cardCount()).isZero();
    }

    @Test
    @DisplayName("결과 코드가 성공이 아니면 그 코드로 실패 처리한다")
    void failureKeepsCode() throws Exception {
        CardListResult result = parse("{\"result\":{\"code\":\"CF-12345\"}}");

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo("CF-12345");
        assertThat(result.cardCount()).isZero();
    }

}
