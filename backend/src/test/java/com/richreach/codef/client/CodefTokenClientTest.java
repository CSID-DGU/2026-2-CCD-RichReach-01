package com.richreach.codef.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.richreach.codef.config.CodefProperties;
import com.richreach.codef.dto.CodefTokenResponse;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CodefTokenClientTest {

    private static final String BASE_URL = "https://oauth.codef.io";
    private static final String CLIENT_ID = "test-client-id";
    private static final String CLIENT_SECRET = "test-client-secret";
    private static final String TOKEN_JSON =
        "{\"access_token\":\"abc.def.ghi\",\"token_type\":\"bearer\",\"expires_in\":604799,\"scope\":\"read\"}";

    private MockRestServiceServer server;
    private CodefTokenClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CodefTokenClient(builder.build(), properties(CLIENT_ID, CLIENT_SECRET));
    }

    private CodefProperties properties(String clientId, String clientSecret) {
        return new CodefProperties(clientId, clientSecret, null, BASE_URL, "https://development.codef.io",
            Duration.ofSeconds(3), Duration.ofSeconds(10), Duration.ofSeconds(120), Duration.ofHours(1));
    }

    @Test
    @DisplayName("Basic 인증과 client_credentials 폼으로 토큰을 요청하고 응답을 파싱한다")
    void issueToken() {
        String expectedAuth = "Basic " + Base64.getEncoder()
            .encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));

        server.expect(requestTo(BASE_URL + "/oauth/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", expectedAuth))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().string("grant_type=client_credentials&scope=read"))
            .andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));

        CodefTokenResponse response = client.issueToken();

        assertThat(response.accessToken()).isEqualTo("abc.def.ghi");
        assertThat(response.tokenType()).isEqualTo("bearer");
        assertThat(response.expiresIn()).isEqualTo(604799);
        assertThat(response.scope()).isEqualTo("read");
        server.verify();
    }

    @Test
    @DisplayName("URL 인코딩된 응답도 디코딩해서 파싱한다")
    void issueToken_urlEncodedResponse() {
        String encoded = URLEncoder.encode(TOKEN_JSON, StandardCharsets.UTF_8);
        server.expect(requestTo(BASE_URL + "/oauth/token"))
            .andRespond(withSuccess(encoded, MediaType.TEXT_PLAIN));

        CodefTokenResponse response = client.issueToken();

        assertThat(response.accessToken()).isEqualTo("abc.def.ghi");
    }

    @Test
    @DisplayName("토큰 값과 시크릿은 toString으로 출력되어도 노출되지 않는다")
    void secretsAreMaskedInToString() {
        CodefTokenResponse response = new CodefTokenResponse("abc.def.ghi", "bearer", 604799, "read");

        assertThat(response.toString()).doesNotContain("abc.def.ghi");
        assertThat(properties(CLIENT_ID, CLIENT_SECRET).toString())
            .doesNotContain(CLIENT_ID)
            .doesNotContain(CLIENT_SECRET);
    }

    @Test
    @DisplayName("401 응답이면 외부 API 오류로 변환하고 메시지에 시크릿을 담지 않는다")
    void issueToken_unauthorized() {
        server.expect(requestTo(BASE_URL + "/oauth/token"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("<html>401</html>"));

        assertThatThrownBy(() -> client.issueToken())
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
                assertThat(e.getMessage()).contains("401");
                assertThat(e.getMessage()).doesNotContain(CLIENT_ID).doesNotContain(CLIENT_SECRET);
            });
    }

    @Test
    @DisplayName("응답 형식이 올바르지 않으면 외부 API 오류로 변환한다")
    void issueToken_malformedResponse() {
        server.expect(requestTo(BASE_URL + "/oauth/token"))
            .andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.issueToken())
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR));
    }

    @Test
    @DisplayName("응답에 토큰이 없으면 외부 API 오류로 변환한다")
    void issueToken_missingToken() {
        server.expect(requestTo(BASE_URL + "/oauth/token"))
            .andRespond(withSuccess("{\"token_type\":\"bearer\",\"expires_in\":10}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.issueToken())
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR));
    }

    @Test
    @DisplayName("클라이언트 정보가 없으면 CODEF를 호출하지 않고 안내 오류를 던진다")
    void issueToken_notConfigured() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer emptyServer = MockRestServiceServer.bindTo(builder).build();
        CodefTokenClient notConfigured = new CodefTokenClient(builder.build(), properties(null, ""));

        assertThatThrownBy(notConfigured::issueToken)
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
                assertThat(e.getMessage()).contains("CODEF_CLIENT_ID");
            });
        emptyServer.verify(); // 요청이 하나도 발생하지 않았음을 확인
    }

}
