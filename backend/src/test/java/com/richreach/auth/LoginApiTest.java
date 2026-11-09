package com.richreach.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.richreach.TestcontainersConfiguration;
import com.richreach.auth.repository.RefreshTokenRepository;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 보안 필터를 켠 채로 실제 MySQL(V14까지 적용)에서 로그인, 실패 잠금, 재발급, 로그아웃을 확인한다.
 * 토큰 없이 호출하므로 /api/auth/**가 공개 경로인지도 함께 확인한다.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class LoginApiTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final String EMAIL = "rich@example.com";
    private static final String PASSWORD = "abcd1234";
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private RefreshTokenRepository refreshTokens;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user;

    @BeforeEach
    void createUser() {
        user = users.save(new User(EMAIL, passwordEncoder.encode(PASSWORD), "김리치",
            "010%08d".formatted(PHONE_SEQUENCE.incrementAndGet()), "REAL", LocalDateTime.now()));
    }

    @AfterEach
    void cleanUp() {
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    private ResultActions login(String loginId, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(loginId, password)));
    }

    private ResultActions postRefresh(String path, String refreshToken) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    private User reload() {
        return users.findById(user.getId()).orElseThrow();
    }

    private void failTimes(int count) throws Exception {
        for (int i = 0; i < count; i++) {
            login(EMAIL, "wrong-password1").andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("올바른 이메일과 비밀번호로 로그인하면 토큰을 받고 refresh 토큰이 저장된다")
    void loginSucceeds() throws Exception {
        login(EMAIL, PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());

        assertThat(refreshTokens.findAll()).hasSize(1)
            .allMatch(token -> token.getUserId().equals(user.getId()));
    }

    @Test
    @DisplayName("이메일은 대소문자와 앞뒤 공백을 무시한다")
    void loginIgnoresEmailCaseAndSpaces() throws Exception {
        login("  RICH@Example.com ", PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("비밀번호가 틀리거나 계정이 없을 때 같은 401(AUTH-002)과 같은 메시지를 준다")
    void wrongPasswordAndUnknownUserLookTheSame() throws Exception {
        String wrongPassword = login(EMAIL, "wrong-password1").andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-002")).andReturn().getResponse().getContentAsString();
        String unknownUser = login("nobody@example.com", PASSWORD).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-002")).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(wrongPassword, "$.message"))
            .isEqualTo(JsonPath.<String>read(unknownUser, "$.message"));
        assertThat(refreshTokens.count()).isZero();
    }

    @Test
    @DisplayName("실패할 때마다 횟수가 늘고, 로그인에 성공하면 0으로 돌아간다")
    void failureCountIsCountedAndReset() throws Exception {
        failTimes(4);
        assertThat(reload().getFailedLoginCount()).isEqualTo(4);

        login(EMAIL, PASSWORD).andExpect(status().isOk());

        assertThat(reload().getFailedLoginCount()).isZero();
        assertThat(reload().getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("5번 연속 실패하면 잠기고, 잠긴 동안에는 비밀번호가 맞아도 423(AUTH-003)이다")
    void accountIsLockedAfterRepeatedFailures() throws Exception {
        failTimes(5);

        assertThat(reload().isLocked(LocalDateTime.now())).isTrue();
        login(EMAIL, PASSWORD).andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("AUTH-003"));
        login(EMAIL, "wrong-password1").andExpect(status().isLocked());
        assertThat(refreshTokens.count()).isZero();
    }

    @Test
    @DisplayName("잠금이 풀린 뒤에는 다시 로그인할 수 있고 실패 횟수가 초기화된다")
    void lockExpires() throws Exception {
        failTimes(5);
        User locked = reload();
        locked.resetLoginFailures();
        // 잠금 시간이 지난 상태를 만든다: 횟수는 남아 있고 해제 시각만 과거다.
        locked.recordLoginFailure(LocalDateTime.now().minusHours(1), 1, java.time.Duration.ofMinutes(10));
        users.saveAndFlush(locked);

        login(EMAIL, PASSWORD).andExpect(status().isOk());

        assertThat(reload().getFailedLoginCount()).isZero();
    }

    @Test
    @DisplayName("잠금이 풀린 뒤 처음 틀린 것은 새로 세기 시작한다 (이전 실패가 이어지지 않는다)")
    void countRestartsAfterLockExpired() throws Exception {
        User locked = reload();
        locked.recordLoginFailure(LocalDateTime.now().minusHours(1), 1, java.time.Duration.ofMinutes(10));
        users.saveAndFlush(locked);

        login(EMAIL, "wrong-password1").andExpect(status().isUnauthorized());

        assertThat(reload().getFailedLoginCount()).isEqualTo(1);
        assertThat(reload().isLocked(LocalDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("한 계정이 잠겨도 다른 계정은 영향이 없다")
    void lockIsPerAccount() throws Exception {
        User other = users.save(new User("other@example.com", passwordEncoder.encode(PASSWORD), "박다른",
            "010%08d".formatted(PHONE_SEQUENCE.incrementAndGet()), "REAL", LocalDateTime.now()));

        failTimes(5);

        login("other@example.com", PASSWORD).andExpect(status().isOk());
        assertThat(users.findById(other.getId()).orElseThrow().getFailedLoginCount()).isZero();
    }

    @Test
    @DisplayName("아이디나 비밀번호가 비어 있으면 400이고 실패 횟수에 세지 않는다")
    void blankInputIsNotCounted() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + EMAIL + "\",\"password\":\"\"}"))
            .andExpect(status().isBadRequest());

        assertThat(reload().getFailedLoginCount()).isZero();
    }

    @Test
    @DisplayName("재발급하면 새 토큰을 받고 쓴 refresh 토큰은 다시 쓸 수 없다")
    void reissueRotatesTokens() throws Exception {
        String body = login(EMAIL, PASSWORD).andReturn().getResponse().getContentAsString();
        String oldRefresh = JsonPath.read(body, "$.data.refreshToken");

        String reissued = postRefresh("/api/auth/reissue", oldRefresh)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        String newRefresh = JsonPath.read(reissued, "$.data.refreshToken");
        assertThat(newRefresh).isNotEqualTo(oldRefresh);

        // 쓴 토큰을 다시 보내면 탈취로 보고 거부하며, 새 토큰도 함께 폐기된다.
        postRefresh("/api/auth/reissue", oldRefresh).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-005"));
        postRefresh("/api/auth/reissue", newRefresh).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("위조된 토큰이나 access 토큰으로는 재발급할 수 없다")
    void reissueRejectsInvalidTokens() throws Exception {
        String body = login(EMAIL, PASSWORD).andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(body, "$.data.accessToken");

        postRefresh("/api/auth/reissue", "not-a-jwt").andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-005"));
        postRefresh("/api/auth/reissue", accessToken).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-005"));
    }

    @Test
    @DisplayName("로그아웃하면 그 refresh 토큰으로 더는 재발급할 수 없다")
    void logoutRevokesRefreshToken() throws Exception {
        String body = login(EMAIL, PASSWORD).andReturn().getResponse().getContentAsString();
        String refresh = JsonPath.read(body, "$.data.refreshToken");

        postRefresh("/api/auth/logout", refresh).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        postRefresh("/api/auth/reissue", refresh).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("이미 로그아웃했거나 알 수 없는 토큰으로 로그아웃해도 성공으로 답한다")
    void logoutIsIdempotent() throws Exception {
        String body = login(EMAIL, PASSWORD).andReturn().getResponse().getContentAsString();
        String refresh = JsonPath.read(body, "$.data.refreshToken");

        postRefresh("/api/auth/logout", refresh).andExpect(status().isOk());
        postRefresh("/api/auth/logout", refresh).andExpect(status().isOk());
        postRefresh("/api/auth/logout", "not-a-jwt").andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인, 재발급, 로그아웃은 토큰이 없어도 호출할 수 있고, 잘못된 토큰이 함께 와도 막지 않는다")
    void authPathsArePublic() throws Exception {
        mockMvc.perform(post("/api/auth/login").header("Authorization", "Bearer not-a-jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk());
    }

}
