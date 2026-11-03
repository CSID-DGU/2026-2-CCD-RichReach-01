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

    private static final String URL = "https://api.test/v1/kr/card/p/account/card-list";
    private static final String PATH = "/v1/kr/card/p/account/card-list";
    private static final String SUCCESS_JSON = "{\"result\":{\"code\":\"CF-00000\",\"message\":\"성공\"},\"data\":{}}";
    private static final String INVALID_TOKEN_JSON = "{\"result\":{\"code\":\"CF-00401\",\"message\":\"토큰 오류\"}}";

    private MockRestServiceServer server;
    private CodefTokenProvider tokenProvider;
    private CodefApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.test");
        server = MockRestServiceServer.bindTo(builder).build();
        tokenProvider = mock(CodefTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("test-token-1", "test-token-2");
        client = new CodefApiClient(builder.build(), tokenProvider);
    }

    @Test
    @DisplayName("JSON 본문을 URL 인코딩해서 Bearer 토큰과 함께 보내고, 응답을 돌려준다")
    void sendsEncodedBodyWithBearerToken() {
        server.expect(requestTo(URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-token-1"))
            .andExpect(content().string("%7B%22a%22%3A%22b%22%7D"))
            .andRespond(withSuccess(SUCCESS_JSON, MediaType.APPLICATION_JSON));

        JsonNode response = client.post(PATH, Map.of("a", "b"));

        assertThat(response.path("result").path("code").asString("")).isEqualTo("CF-00000");
        server.verify();
    }

    @Test
    @DisplayName("URL 인코딩되어 온 응답도 디코딩해서 읽는다")
    void decodesUrlEncodedResponse() {
        String encoded = URLEncoder.encode(SUCCESS_JSON, StandardCharsets.UTF_8);
        server.expect(requestTo(URL)).andRespond(withSuccess(encoded, MediaType.TEXT_PLAIN));

        JsonNode response = client.post(PATH, Map.of());

        assertThat(response.path("result").path("message").asString("")).isEqualTo("성공");
    }

    @Test
    @DisplayName("토큰이 거부되면(CF-00401) 새 토큰으로 한 번만 다시 호출한다")
    void retriesOnceWhenTokenRejectedInBody() {
        server.expect(requestTo(URL))
            .andExpect(header("Authorization", "Bearer test-token-1"))
            .andRespond(withSuccess(INVALID_TOKEN_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL))
            .andExpect(header("Authorization", "Bearer test-token-2"))
            .andRespond(withSuccess(SUCCESS_JSON, MediaType.APPLICATION_JSON));

        JsonNode response = client.post(PATH, Map.of());

        assertThat(response.path("result").path("code").asString("")).isEqualTo("CF-00000");
        verify(tokenProvider, times(1)).invalidate("test-token-1");
        server.verify();
    }

    @Test
    @DisplayName("HTTP 401이어도 새 토큰으로 한 번만 다시 호출한다")
    void retriesOnceOnHttp401() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(URL))
            .andExpect(header("Authorization", "Bearer test-token-2"))
            .andRespond(withSuccess(SUCCESS_JSON, MediaType.APPLICATION_JSON));

        client.post(PATH, Map.of());

        verify(tokenProvider, times(1)).invalidate("test-token-1");
        server.verify();
    }

    @Test
    @DisplayName("재시도해도 401이면 더 이상 반복하지 않고 외부 API 오류로 끝낸다")
    void givesUpAfterOneRetry() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.post(PATH, Map.of()))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR));
        server.verify();
    }

    @Test
    @DisplayName("서버 오류는 외부 API 오류(COMMON-502)로 바뀌고, 응답 본문과 요청 내용은 메시지에 나오지 않는다")
    void mapsServerErrorWithoutLeakingBody() {
        server.expect(requestTo(URL))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("upstream-detail-should-not-leak").contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.post(PATH, Map.of("password", "secret-card-password")))
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
                assertThat(e.getMessage())
                    .doesNotContain("upstream-detail-should-not-leak")
                    .doesNotContain("secret-card-password");
            });
    }

    @Test
    @DisplayName("응답이 비어 있거나 JSON이 아니면 외부 API 오류다")
    void rejectsMalformedResponse() {
        server.expect(requestTo(URL)).andRespond(withSuccess("%7Bbroken", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.post(PATH, Map.of()))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR));
    }

    @Test
    @DisplayName("퍼센트 인코딩이 깨진 응답은 500이 아니라 외부 API 오류(502)다")
    void rejectsBrokenPercentEncoding() {
        server.expect(requestTo(URL)).andRespond(withSuccess("%7", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.post(PATH, Map.of()))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR));
    }

}
