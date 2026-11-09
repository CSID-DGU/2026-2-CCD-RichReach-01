package com.richreach.codef.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.richreach.codef.config.CodefDebugProperties;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** 응답 구조 로그를 켜고 끌 때 실제로 무엇이 로그에 남는지 확인한다. */
@ExtendWith(OutputCaptureExtension.class)
class CodefApiClientShapeLogTest {

    private static final String RESPONSE = """
        {"result":{"code":"CF-00000","message":"secret-message"},
         "data":{"connectedId":"secret-connected-id","cards":[{"resCardName":"secret-card-name","resCardNo":"1234-9999"}]}}
        """;

    private static CodefApiClient clientWith(boolean logShape) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.test/v1/x")).andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));
        CodefTokenProvider tokenProvider = mock(CodefTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("test-token");
        return new CodefApiClient(builder.build(), tokenProvider, new CodefJsonCodec(JsonMapper.builder().build()),
            new CodefDebugProperties(logShape));
    }

    @Test
    @DisplayName("켜면 응답 구조가 로그에 남고, 결과 코드 외의 값은 나오지 않는다")
    void logsShapeWithoutValues(CapturedOutput output) {
        clientWith(true).post("/v1/x", Map.of());

        assertThat(output.getAll())
            .contains("CODEF 응답 구조: path=/v1/x")
            .contains("result.code : string = 'CF-00000'")
            .contains("data.connectedId : string(len=19)")
            .contains("data.cards : array[1]")
            .contains("data.cards[].resCardName : string(len=16)");
        assertThat(output.getAll())
            .doesNotContain("secret-message")
            .doesNotContain("secret-connected-id")
            .doesNotContain("secret-card-name")
            .doesNotContain("1234-9999")
            .doesNotContain("test-token");
    }

    @Test
    @DisplayName("기본(꺼짐)에서는 응답 구조를 로그에 남기지 않는다")
    void doesNotLogShapeByDefault(CapturedOutput output) {
        clientWith(false).post("/v1/x", Map.of());

        assertThat(output.getAll()).doesNotContain("CODEF 응답 구조").doesNotContain("data.connectedId");
    }

}
