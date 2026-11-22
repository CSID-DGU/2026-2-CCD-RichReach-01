package com.richreach.plan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.richreach.TestcontainersConfiguration;
import com.richreach.auth.repository.RefreshTokenRepository;
import com.richreach.auth.service.AuthTokenService;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 새 달 예산 초안 API({@code GET /api/plans/month-draft})를 보안 필터와 실제 MySQL로 확인한다. 로그인이 필요한지, 새 달(12월
 * 1일)에 근거가 없을 때 오류 없이 {@code NONE}을 주는지, 해를 넘기는 달(1월)의 지난달 표기를 본다. 지난달 예산을 이어 쓰는 경우와
 * 최근 소비로 추천하는 경우는 {@code PlanMonthDraftServiceTest}가 본다.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PlanMonthDraftApiTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    /** 2027-01-01 00:10 KST. 새 해의 첫 달이라 지난달이 전년 12월이 된다. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-12-31T15:10:00Z"), ZoneId.of("Asia/Seoul"));

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return CLOCK;
        }

    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private RefreshTokenRepository refreshTokens;
    @Autowired
    private AuthTokenService authTokenService;

    @AfterEach
    void cleanUp() {
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    private String token() {
        int n = SEQUENCE.incrementAndGet();
        User user = users.save(new User("monthdraft" + n + "@example.com", "hash", "김리치",
            "010%08d".formatted(8000000 + n), "REAL", LocalDateTime.now()));
        return authTokenService.issue(user.getId()).accessToken();
    }

    @Test
    @DisplayName("로그인 없이는 401이다")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/plans/month-draft")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("지난달 예산도 소비도 없으면 오류 없이 NONE이고 예산은 없다. 1월이면 지난달은 전년 12월이다")
    void noBasisAcrossTheYearBoundary() throws Exception {
        mockMvc.perform(get("/api/plans/month-draft").header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.targetMonth").value("2027-01"))
            .andExpect(jsonPath("$.data.previousMonth").value("2026-12"))
            .andExpect(jsonPath("$.data.planExists").value(false))
            .andExpect(jsonPath("$.data.source").value("NONE"))
            .andExpect(jsonPath("$.data.budget").doesNotExist())
            .andExpect(jsonPath("$.data.previousMonthSpent").value(0));
    }

}
