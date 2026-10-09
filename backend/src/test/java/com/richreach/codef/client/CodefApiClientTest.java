package com.richreach.codef.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

class CodefApiClientTest {

    private static final String BASE_URL = "https://development.codef.io";
    private static final String PATH = "/v1/kr/card/p/account/card-list";
    private static final String OK_JSON =
        "{\"result\":{\"code\":\"CF-00000\",\"message\":\"성공\"},\"data\":[{\"resCardName\":\"테스트카드\"}]}";

    private MockRestServiceServer server;
    private CodefTokenProvider tokenProvider;
    private CodefApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        tokenProvider = mock(CodefTokenProvider.class);
        client = new CodefApiClient(builder.build(), tokenProvider);
    }

    @Test
    @DisplayName("JSON 본문을 URL 인코딩해서 Bearer 토큰과 함께 POST하고 응답을 파싱한다")
    void post() {
        when(tokenProvider.getAccessToken()).thenReturn("token-1");
        String expectedBody = URLEncoder.encode("{\"connectedId\":\"cid\",\"organization\":\"0301\"}",
            StandardCharsets.UTF_8);

        server.expect(requestTo(BASE_URL + PATH))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer token-1"))
            .andExpect(content().string(expectedBody))
            .andRespond(withSuccess(OK_JSON, MediaType.APPLICATION_JSON));

        // Map.of는 실행마다 순서가 달라질 수 있어서, JSON 키 순서가 고정되는 LinkedHashMap을 쓴다.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("connectedId", "cid");
        body.put("organization", "0301");
        JsonNode response = client.post(PATH, body);

        assertThat(response.path("result").path("code").asString("")).isEqualTo("CF-00000");
        assertThat(response.path("data").get(0).path("resCardName").asString("")).isEqualTo("테스트카드");
        server.verify();
    }

    @Test
    @DisplayName("URL 인코딩된 응답도 디코딩해서 파싱한다")
    void urlEncodedResponse() {
        when(tokenProvider.getAccessToken()).thenReturn("token-1");
        server.expect(requestTo(BASE_URL + PATH))
            .andRespond(withSuccess(URLEncoder.encode(OK_JSON, StandardCharsets.UTF_8), MediaType.TEXT_PLAIN));

        JsonNode response = client.post(PATH, Map.of("k", "v"));

        assertThat(response.path("data").get(0).path("resCardName").asString("")).isEqualTo("테스트카드");
    }

    @Test
    @DisplayName("401이면 토큰을 무효화하고 새 토큰으로 한 번만 재시도한다")
    void retryOnUnauthorized() {
        when(tokenProvider.getAccessToken()).thenReturn("old-token", "new-token");

        server.expect(requestTo(BASE_URL + PATH))
            .andExpect(header("Authorization", "Bearer old-token"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(BASE_URL + PATH))
            .andExpect(header("Authorization", "Bearer new-token"))
            .andRespond(withSuccess(OK_JSON, MediaType.APPLICATION_JSON));

        JsonNode response = client.post(PATH, Map.of("k", "v"));

        assertThat(response.path("result").path("code").asString("")).isEqualTo("CF-00000");
        verify(tokenProvider, times(1)).invalidate();
        server.verify();
    }

    @Test
    @DisplayName("본문의 result.code가 CF-00401이어도 토큰을 새로 발급해서 재시도한다")
    void retryOnInvalidTokenResultCode() {
        when(tokenProvider.getAccessToken()).thenReturn("old-token", "new-token");

        server.expect(requestTo(BASE_URL + PATH))
            .andRespond(withSuccess("{\"result\":{\"code\":\"CF-00401\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + PATH))
            .andExpect(header("Authorization", "Bearer new-token"))
            .andRespond(withSuccess(OK_JSON, MediaType.APPLICATION_JSON));

        JsonNode response = client.post(PATH, Map.of("k", "v"));

        assertThat(response.path("result").path("code").asString("")).isEqualTo("CF-00000");
        verify(tokenProvider, times(1)).invalidate();
    }

    @Test
    @DisplayName("재시도해도 401이면 외부 API 오류로 변환한다 (무한 재시도 없음)")
    void unauthorizedTwice() {
        when(tokenProvider.getAccessToken()).thenReturn("t1", "t2");
        server.expect(requestTo(BASE_URL + PATH)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(BASE_URL + PATH)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.post(PATH, Map.of("k", "v")))
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
                assertThat(e.getMessage()).contains("401");
            });
        verify(tokenProvider, times(1)).invalidate();
    }

    @Test
    @DisplayName("CODEF 서버 오류는 외부 API 오류로 변환하고 상태 코드만 알려준다")
    void serverError() {
        when(tokenProvider.getAccessToken()).thenReturn("token-1");
        server.expect(requestTo(BASE_URL + PATH))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("개인정보가 담긴 오류 본문"));

        assertThatThrownBy(() -> client.post(PATH, Map.of("k", "v")))
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
                assertThat(e.getMessage()).contains("500").doesNotContain("개인정보");
            });
    }

    @Test
    @DisplayName("응답 형식이 올바르지 않으면 외부 API 오류로 변환한다")
    void malformedResponse() {
        when(tokenProvider.getAccessToken()).thenReturn("token-1");
        server.expect(requestTo(BASE_URL + PATH))
            .andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.post(PATH, Map.of("k", "v")))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR));
    }

}
