package com.richreach.codef.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.richreach.codef.dto.CodefTokenResponse;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CodefJsonCodecTest {

    private final CodefJsonCodec codec = new CodefJsonCodec(JsonMapper.builder().build());

    private static void assertExternalApiError(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR));
    }

    @Test
    @DisplayName("요청 본문은 JSON으로 직렬화한 뒤 URL 인코딩한다")
    void encodesBody() {
        assertThat(codec.encodeBody(Map.of("a", "b"))).isEqualTo("%7B%22a%22%3A%22b%22%7D");
    }

    @Test
    @DisplayName("JSON 객체와 배열로 시작하는 응답은 그대로 읽는다")
    void readsPlainJson() {
        assertThat(codec.readTree("{\"a\":1}").path("a").asInt()).isEqualTo(1);
        assertThat(codec.readTree("  [1,2,3]  ").size()).isEqualTo(3);
    }

    @Test
    @DisplayName("URL 인코딩되어 온 응답은 디코딩해서 읽는다")
    void readsUrlEncodedJson() {
        String encoded = URLEncoder.encode("{\"name\":\"성공\"}", StandardCharsets.UTF_8);

        assertThat(codec.readTree(encoded).path("name").asString("")).isEqualTo("성공");
    }

    @Test
    @DisplayName("지정한 타입으로도 같은 규칙으로 읽는다")
    void readsTypedValue() {
        String encoded = URLEncoder.encode("{\"access_token\":\"test-token\",\"expires_in\":10}",
            StandardCharsets.UTF_8);

        CodefTokenResponse response = codec.read(encoded, CodefTokenResponse.class);

        assertThat(response.accessToken()).isEqualTo("test-token");
        assertThat(response.expiresIn()).isEqualTo(10L);
    }

    @Test
    @DisplayName("비어 있는 응답은 외부 API 오류다")
    void rejectsBlank() {
        assertExternalApiError(() -> codec.readTree(null));
        assertExternalApiError(() -> codec.readTree("   "));
    }

    @Test
    @DisplayName("퍼센트 인코딩이 깨졌거나 JSON이 아니면 500이 아니라 외부 API 오류다")
    void rejectsMalformed() {
        assertExternalApiError(() -> codec.readTree("%7"));
        assertExternalApiError(() -> codec.readTree("{broken"));
        assertExternalApiError(() -> codec.read("not-json-at-all", CodefTokenResponse.class));
    }

    @Test
    @DisplayName("오류 메시지에 응답 본문이 나오지 않는다")
    void errorMessageDoesNotLeakBody() {
        assertThatThrownBy(() -> codec.readTree("{secret-body-content"))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getMessage()).doesNotContain("secret-body-content"));
    }

}
