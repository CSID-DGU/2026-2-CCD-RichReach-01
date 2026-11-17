package com.richreach.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * 공용 서버용 설정({@code application-dev.yml})이 안전한지 DB 없이 확인한다. 비밀값에 기본값이 없어야 설정을 빠뜨린 채 떠
 * 있는 일이 없고, 시연 데이터와 인증번호 노출이 켜져 있지 않아야 한다.
 */
class DevProfileConfigTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}:]+)(:([^}]*))?}");

    private static Map<String, Object> dev() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("dev",
            new ClassPathResource("application-dev.yml"));
        assertThat(sources).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) sources.get(0).getSource();
        return map;
    }

    private static String value(String key) throws IOException {
        Object value = dev().get(key);
        assertThat(value).as(key).isNotNull();
        return value.toString();
    }

    /** {@code ${NAME}} 형태(기본값 없음)이고 이름이 기대와 같은지 확인한다. */
    private static void assertRequiredEnv(String key, String envName) throws IOException {
        Matcher matcher = PLACEHOLDER.matcher(value(key));
        assertThat(matcher.find()).as(key + " 는 환경변수 자리표시자여야 한다").isTrue();
        assertThat(matcher.group(1)).isEqualTo(envName);
        assertThat(matcher.group(2)).as(key + " 에는 기본값을 두지 않는다").isNull();
    }

    @Test
    @DisplayName("DB 비밀번호, 암호화 키, CORS 허용 origin에는 기본값이 없어 설정하지 않으면 앱이 시작되지 않는다")
    void secretsHaveNoDefaults() throws IOException {
        assertRequiredEnv("spring.datasource.password", "MYSQL_PASSWORD");
        assertRequiredEnv("app.encryption.key", "DATA_ENCRYPTION_KEY");
        assertRequiredEnv("richreach.cors.allowed-origins", "CORS_ALLOWED_ORIGINS");
        assertThat(value("spring.datasource.url")).contains("${DB_HOST}");
        assertThat(value("spring.datasource.url")).doesNotContain("localhost").doesNotContain("127.0.0.1");
    }

    @Test
    @DisplayName("JWT 서명 키는 필수여서 임시 키로 시작하지 않는다")
    void jwtSecretIsRequired() throws IOException {
        assertThat(value("app.jwt.require-secret")).isEqualTo("true");
        // secret은 이 프로파일에서 덮어쓰지 않고 application.yml의 JWT_SECRET 환경변수를 그대로 쓴다.
        assertThat(dev()).doesNotContainKey("app.jwt.secret");
    }

    @Test
    @DisplayName("비밀번호 재설정 인증번호 노출과 시연 데이터 생성은 켜지 않는다")
    void noDevOnlyFeatures() throws IOException {
        assertThat(dev()).doesNotContainKey("app.auth.password-reset.expose-code");
        assertThat(dev().keySet().stream().map(Object::toString).noneMatch(key -> key.contains("demo-seed")))
            .isTrue();
    }

    @Test
    @DisplayName("DB 연결은 기본으로 암호화하고, 운영 로그에 SQL을 남기지 않는다")
    void safeDefaults() throws IOException {
        assertThat(value("spring.datasource.url")).contains("sslMode=${DB_SSL_MODE:REQUIRED}");
        assertThat(value("logging.level.org.hibernate.SQL")).isEqualTo("warn");
    }

}
