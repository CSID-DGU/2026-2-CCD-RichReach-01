package com.richreach.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.richreach.TestcontainersConfiguration;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 공용 서버용 {@code dev} 프로파일로 앱이 실제로 끝까지 시작되는지 확인한다. 필수 환경변수(DB 비밀번호, 암호화 키, JWT 서명 키,
 * CORS)만 넘기고 나머지는 이 프로파일의 설정 그대로 쓴다. DB 연결 정보는 테스트 컨테이너가 덮어쓰고, Flyway는 빈 DB에 V1부터
 * 끝까지 적용한다.
 */
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"DB_HOST=unused", "MYSQL_PASSWORD=unused",
    "CORS_ALLOWED_ORIGINS=https://app.example.com"})
@AutoConfigureMockMvc
class DevProfileBootTest {

    /** 키와 서명 비밀은 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void secrets(DynamicPropertyRegistry registry) {
        SecureRandom random = new SecureRandom();
        byte[] encryptionKey = new byte[32];
        random.nextBytes(encryptionKey);
        byte[] jwtSecret = new byte[48];
        random.nextBytes(jwtSecret);
        registry.add("DATA_ENCRYPTION_KEY", () -> Base64.getEncoder().encodeToString(encryptionKey));
        registry.add("JWT_SECRET", () -> Base64.getEncoder().encodeToString(jwtSecret));
    }

    @Autowired
    private ApplicationContext context;
    @Autowired
    private Environment environment;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("dev 프로파일로 앱이 시작되고 Flyway가 빈 DB에 마이그레이션을 끝까지 적용한다")
    void startsAndMigrates() {
        Integer failed = jdbc.queryForObject("select count(*) from flyway_schema_history where success = 0",
            Integer.class);
        Integer applied = jdbc.queryForObject("select count(*) from flyway_schema_history where success = 1",
            Integer.class);

        assertThat(failed).isZero();
        assertThat(applied).isGreaterThanOrEqualTo(27);
    }

    @Test
    @DisplayName("dev 프로파일에서는 시연 데이터 생성기가 만들어지지 않고 인증번호도 응답에 담지 않는다")
    void devOnlyFeaturesAreOff() {
        assertThat(context.containsBean("demoSeedRunner")).isFalse();
        assertThat(environment.getProperty("app.auth.password-reset.expose-code", Boolean.class)).isFalse();
        assertThat(environment.getProperty("app.jwt.require-secret", Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("헬스체크는 로그인 없이 열리고 API 문서도 열려 있다")
    void healthAndDocsAreReachable() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인이 필요한 API는 토큰 없이 401이다")
    void protectedApisRequireLogin() throws Exception {
        mockMvc.perform(get("/api/plans")).andExpect(status().isUnauthorized());
    }

}
