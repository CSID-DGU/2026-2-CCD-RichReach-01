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
    @DisplayName("민감한 키의 값이 배열이나 객체여도 안에 든 값이 하나도 남지 않는다")
    void masksArrayAndObjectValues() {
        String masked = SensitiveDataMasker.maskJson(
            "{\"token\":[\"aaa\",\"bbb\"],\"secret\":{\"v\":\"xxx\",\"w\":\"yyy\"},\"name\":\"kim\"}");

        assertThat(masked).isEqualTo("{\"token\":\"****\",\"secret\":\"****\",\"name\":\"kim\"}");
    }

    @Test
    @DisplayName("키를 유니코드 이스케이프로 써도 가려진다")
    void masksEscapedKeys() {
        String masked = SensitiveDataMasker.maskJson("{\"pass\\u0077ord\":\"secret1\",\"name\":\"kim\"}");

        assertThat(masked).doesNotContain("secret1").contains("\"name\":\"kim\"");
    }

    @Test
    @DisplayName("최상위가 배열이어도 그 안의 객체에서 민감한 값을 가린다")
    void masksInsideTopLevelArray() {
        String masked = SensitiveDataMasker.maskJson("[{\"password\":\"a1\"},{\"nested\":[{\"otp\":\"654321\"}]}]");

        assertThat(masked).doesNotContain("a1").doesNotContain("654321");
    }

    @Test
    @DisplayName("값이 하나뿐인 JSON(문자열, 숫자)은 통째로 가린다")
    void masksScalarBody() {
        assertThat(SensitiveDataMasker.maskJson("\"eyJhbGciOi.token\"")).doesNotContain("eyJhbGciOi");
        assertThat(SensitiveDataMasker.maskJson("123456")).doesNotContain("123456");
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
    @DisplayName("본문에 줄바꿈이 있어도 로그에는 한 줄로 남아 가짜 로그 줄을 만들 수 없다")
    void oneLineRemovesLineBreaks() {
        String lineSeparator = String.valueOf((char) 0x2028);
        // 토큰 사이의 줄바꿈과 문자열 안의 줄 구분 문자는 JSON으로 유효하다.
        String body = "{\"name\":\"kim\",\r\n\"memo\":\"x" + lineSeparator + "2026-10-28 ERROR forged\"}";

        String line = SensitiveDataMasker.oneLine(SensitiveDataMasker.maskJson(body));

        assertThat(line).doesNotContain("\r").doesNotContain("\n").doesNotContain(lineSeparator);
        assertThat(line).contains("\"name\":\"kim\"");
        assertThat(SensitiveDataMasker.oneLine(null)).isNull();
    }

    @Test
    @DisplayName("깨진 JSON에 줄바꿈과 가짜 로그 줄이 있어도 본문은 한 줄 문구로만 남는다")
    void brokenBodyWithLineBreaksIsOneLine() {
        String line = SensitiveDataMasker.oneLine(
            SensitiveDataMasker.maskJson("{\"memo\":\"x\"\n2026-10-28 ERROR forged\"}"));

        assertThat(line).doesNotContain("\n").doesNotContain("forged");
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
