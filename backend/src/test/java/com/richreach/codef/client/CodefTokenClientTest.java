package com.richreach.codef.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.richreach.codef.config.CodefProperties;
import com.richreach.codef.dto.CodefTokenResponse;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CodefTokenClientTest {

    private static final String TOKEN_URL = "https://oauth.test/oauth/token";

    private static CodefProperties properties(String clientId, String clientSecret) {
        return new CodefProperties(clientId, clientSecret, null, null, null, null, null, null, null);
    }

    private static CodefTokenClient clientFor(MockRestServiceServer[] serverHolder, CodefProperties properties) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://oauth.test");
        serverHolder[0] = MockRestServiceServer.bindTo(builder).build();
        return new CodefTokenClient(builder.build(), properties);
    }

    @Test
    @DisplayName("클라이언트 ID/시크릿을 Basic 인증으로 보내 토큰을 받는다")
    void issuesTokenWithBasicAuth() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        CodefTokenClient client = clientFor(holder, properties("test-client-id", "test-client-secret"));
        String expectedAuth = "Basic " + Base64.getEncoder()
            .encodeToString("test-client-id:test-client-secret".getBytes(StandardCharsets.UTF_8));
        holder[0].expect(requestTo(TOKEN_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", expectedAuth))
            .andRespond(withSuccess(
                "{\"access_token\":\"test-token\",\"token_type\":\"bearer\",\"expires_in\":604799,\"scope\":\"read\"}",
                MediaType.APPLICATION_JSON));

        CodefTokenResponse response = client.issueToken();

        assertThat(response.accessToken()).isEqualTo("test-token");
        assertThat(response.expiresIn()).isEqualTo(604799L);
        assertThat(response.toString()).doesNotContain("test-token");
        holder[0].verify();
    }

    @Test
    @DisplayName("클라이언트 정보가 없으면 호출하지 않고 500으로 끝내며, 메시지에 설정 이름이 나오지 않는다")
    void failsWithoutCredentials() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        CodefTokenClient client = clientFor(holder, properties("", ""));

        assertThatThrownBy(client::issueToken)
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
                assertThat(e.getMessage()).doesNotContain("CODEF_CLIENT").doesNotContain(".env");
            });
        holder[0].verify();
    }

    @Test
    @DisplayName("토큰 발급이 거절되면 외부 API 오류로 바뀌고, 메시지에 시크릿이 나오지 않는다")
    void mapsRejectedIssuanceWithoutLeakingSecret() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        CodefTokenClient client = clientFor(holder, properties("test-client-id", "test-client-secret"));
        holder[0].expect(requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(client::issueToken)
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
                assertThat(e.getMessage()).doesNotContain("test-client-secret").doesNotContain("test-client-id");
            });
    }

    @Test
    @DisplayName("퍼센트 인코딩이 깨진 응답은 500이 아니라 외부 API 오류(502)다")
    void rejectsBrokenPercentEncoding() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        CodefTokenClient client = clientFor(holder, properties("test-client-id", "test-client-secret"));
        holder[0].expect(requestTo(TOKEN_URL)).andRespond(withSuccess("%7", MediaType.TEXT_PLAIN));

        assertThatThrownBy(client::issueToken)
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR));
    }

    @Test
    @DisplayName("응답에 토큰이 없으면 외부 API 오류다")
    void rejectsResponseWithoutToken() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        CodefTokenClient client = clientFor(holder, properties("test-client-id", "test-client-secret"));
        holder[0].expect(requestTo(TOKEN_URL))
            .andRespond(withSuccess("{\"token_type\":\"bearer\",\"expires_in\":10}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(client::issueToken)
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR));
    }

}
