package com.richreach.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SensitiveDataMaskerTest {

    @Test
    @DisplayName("JSON 본문에서 비밀번호와 토큰 값은 가리고 나머지는 그대로 둔다")
    void masksSensitiveJsonValues() {
        String body = "{\"email\":\"a@b.com\",\"password\":\"p@ss w0rd!\",\"refreshToken\":\"abc.def\"}";

        String masked = SensitiveDataMasker.maskJson(body);

        assertThat(masked).contains("\"email\":\"a@b.com\"");
        assertThat(masked).contains("\"password\":\"****\"").contains("\"refreshToken\":\"****\"");
        assertThat(masked).doesNotContain("p@ss w0rd!").doesNotContain("abc.def");
    }

    @Test
    @DisplayName("중첩된 객체와 배열 안의 카드사 자격증명도 가린다")
    void masksNestedValues() {
        String body = "{\"connections\":[{\"cardCompany\":\"WOORI\",\"loginId\":\"user1\","
            + "\"login_password\":\"secret1\",\"card-password\":\"1234\"}],\"pin\":123456}";

        String masked = SensitiveDataMasker.maskJson(body);

        assertThat(masked).contains("\"cardCompany\":\"WOORI\"").contains("\"loginId\":\"user1\"");
        assertThat(masked).doesNotContain("secret1").doesNotContain("1234").doesNotContain("123456");
    }

    @Test
    @DisplayName("값에 따옴표나 쉼표가 있어도 뒤에 오는 값이 새지 않는다")
    void masksValuesWithSpecialCharacters() {
        String body = "{\"password\":\"a\\\"b,c}d\",\"name\":\"홍길동\"}";

        String masked = SensitiveDataMasker.maskJson(body);

        assertThat(masked).isEqualTo("{\"password\":\"****\",\"name\":\"홍길동\"}");
    }

    @Test
    @DisplayName("JSON이 아니거나 잘린 본문은 통째로 가린다")
    void masksUnreadableBodyEntirely() {
        assertThat(SensitiveDataMasker.maskJson("password=secret")).doesNotContain("secret");
        assertThat(SensitiveDataMasker.maskJson("{\"password\":\"secret")).doesNotContain("secret");
    }

    @Test
    @DisplayName("본문이 없으면 그대로 돌려준다")
    void keepsEmptyBody() {
        assertThat(SensitiveDataMasker.maskJson(null)).isNull();
        assertThat(SensitiveDataMasker.maskJson("  ")).isEqualTo("  ");
    }

    @Test
    @DisplayName("쿼리 문자열에서 민감한 파라미터만 가린다")
    void masksQuery() {
        String masked = SensitiveDataMasker.maskQuery("month=2026-10&token=xyz&access_token=abc&flag");

        assertThat(masked).isEqualTo("month=2026-10&token=****&access_token=****&flag");
        assertThat(SensitiveDataMasker.maskQuery(null)).isNull();
        assertThat(SensitiveDataMasker.maskQuery("")).isEmpty();
    }

    @Test
    @DisplayName("Authorization, Cookie 헤더 값은 가리고 나머지 헤더는 그대로 둔다")
    void masksHeaders() {
        assertThat(SensitiveDataMasker.maskHeader("Authorization", "Bearer abc")).isEqualTo("****");
        assertThat(SensitiveDataMasker.maskHeader("Set-Cookie", "sid=1")).isEqualTo("****");
        assertThat(SensitiveDataMasker.maskHeader("Content-Type", "application/json")).isEqualTo("application/json");
    }

}
