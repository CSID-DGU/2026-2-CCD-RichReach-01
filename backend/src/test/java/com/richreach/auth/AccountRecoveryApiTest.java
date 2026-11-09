package com.richreach.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.richreach.TestcontainersConfiguration;
import com.richreach.auth.entity.PasswordResetCode;
import com.richreach.auth.repository.PasswordResetCodeRepository;
import com.richreach.auth.repository.RefreshTokenRepository;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 보안 필터를 켠 채로 실제 MySQL(V15까지 적용)에서 이메일 찾기와 비밀번호 재설정 전체 흐름을 확인한다.
 * 토큰 없이 호출하므로 /api/auth/**가 공개 경로인지도 함께 확인한다.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AccountRecoveryApiTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final String EMAIL = "rich@example.com";
    private static final String OLD_PASSWORD = "oldPass123";
    private static final String NEW_PASSWORD = "newPass456";
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordResetCodeRepository codes;
    @Autowired
    private RefreshTokenRepository refreshTokens;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user;
    private String phone;

    @BeforeEach
    void createUser() {
        phone = "010%08d".formatted(PHONE_SEQUENCE.incrementAndGet());
        user = users.save(new User(EMAIL, passwordEncoder.encode(OLD_PASSWORD), "김리치", phone, "REAL",
            LocalDateTime.now()));
    }

    @AfterEach
    void cleanUp() {
        codes.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions findEmail(String name, String phoneNumber) throws Exception {
        return send(post("/api/auth/find-email"), "{\"name\":\"%s\",\"phoneNumber\":\"%s\"}".formatted(name, phoneNumber));
    }

    private ResultActions sendCode(String loginId) throws Exception {
        return send(post("/api/auth/password/code"), "{\"loginId\":\"%s\"}".formatted(loginId));
    }

    private ResultActions verify(String loginId, String code) throws Exception {
        return send(post("/api/auth/password/verify"),
            "{\"loginId\":\"%s\",\"code\":\"%s\"}".formatted(loginId, code));
    }

    private ResultActions resetPassword(String resetToken, String newPassword) throws Exception {
        return send(patch("/api/auth/password"),
            "{\"resetToken\":\"%s\",\"newPassword\":\"%s\"}".formatted(resetToken, newPassword));
    }

    private ResultActions login(String password) throws Exception {
        return send(post("/api/auth/login"), "{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(EMAIL, password));
    }

    /** 인증번호를 발송하고 개발용으로 응답에 담긴 인증번호를 돌려준다. */
    private String requestCode() throws Exception {
        String body = sendCode(EMAIL).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.devCode");
    }

    private String verifiedResetToken() throws Exception {
        String code = requestCode();
        String body = verify(EMAIL, code).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.resetToken");
    }

    /** 재전송 대기 시간이 지난 것처럼 마지막 발송 시각을 과거로 돌린다. */
    private void ageCode(Duration age) {
        PasswordResetCode stored = codes.findByUserId(user.getId()).orElseThrow();
        stored.renew(stored.getCodeHash(), LocalDateTime.now().minus(age), Duration.ofMinutes(3));
        codes.saveAndFlush(stored);
    }

    // ---- 이메일 찾기 ----

    @Test
    @DisplayName("이름과 전화번호가 맞으면 가려진 이메일을 알려준다 (전화번호 하이픈은 무시)")
    void findEmailSucceeds() throws Exception {
        String hyphenated = phone.substring(0, 3) + "-" + phone.substring(3, 7) + "-" + phone.substring(7);

        findEmail("김리치", phone).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.maskedEmail").value("ri***@example.com"));
        findEmail(" 김리치 ", hyphenated).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.maskedEmail").value("ri***@example.com"));
    }

    @Test
    @DisplayName("이름이 다르거나 전화번호가 없으면 404(AUTH-013)이다")
    void findEmailNotFound() throws Exception {
        findEmail("박다른", phone).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("AUTH-013"));
        findEmail("김리치", "01099998888").andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("AUTH-013"));
    }

    // ---- 인증번호 발송 ----

    @Test
    @DisplayName("인증번호를 발송하면 6자리 번호가 해시로만 저장되고, 개발용으로 응답에도 담긴다")
    void sendCodeStoresOnlyTheHash() throws Exception {
        String body = sendCode(EMAIL).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.expiresIn").value(180))
            .andExpect(jsonPath("$.data.resendIn").value(60))
            .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(body, "$.data.devCode");

        assertThat(code).matches("\\d{6}");
        PasswordResetCode stored = codes.findByUserId(user.getId()).orElseThrow();
        assertThat(stored.getCodeHash()).isNotEqualTo(code).doesNotContain(code);
        assertThat(passwordEncoder.matches(code, stored.getCodeHash())).isTrue();
    }

    @Test
    @DisplayName("가입되지 않은 이메일도 같은 성공 응답이지만 인증번호는 없고 아무것도 저장하지 않는다")
    void sendCodeForUnknownEmailLooksTheSame() throws Exception {
        sendCode("nobody@example.com").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.expiresIn").value(180))
            .andExpect(jsonPath("$.data.resendIn").value(60))
            .andExpect(jsonPath("$.data.devCode").doesNotExist());

        assertThat(codes.count()).isZero();
        verify("nobody@example.com", "123456").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("AUTH-009"));
    }

    @Test
    @DisplayName("60초 안에 다시 요청하면 429(AUTH-011)이고, 지나면 새 인증번호가 발급되어 이전 번호는 쓸 수 없다")
    void resendIsRateLimited() throws Exception {
        String first = requestCode();

        sendCode(EMAIL).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("AUTH-011"));

        ageCode(Duration.ofSeconds(61));
        String second = requestCode();
        assertThat(codes.count()).isEqualTo(1);
        if (!first.equals(second)) {
            verify(EMAIL, first).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUTH-009"));
        }
        verify(EMAIL, second).andExpect(status().isOk());
    }

    // ---- 인증번호 확인 ----

    @Test
    @DisplayName("맞는 인증번호는 재설정 토큰을 주고, 같은 인증번호를 다시 쓸 수 없다")
    void verifySucceedsOnce() throws Exception {
        String code = requestCode();

        verify(EMAIL, code).andExpect(status().isOk()).andExpect(jsonPath("$.data.resetToken").isNotEmpty());

        assertThat(codes.count()).isZero();
        verify(EMAIL, code).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUTH-009"));
    }

    @Test
    @DisplayName("틀린 인증번호는 400(AUTH-009)이고, 이메일은 대소문자를 구분하지 않는다")
    void wrongCodeIsRejected() throws Exception {
        String code = requestCode();
        String wrong = code.equals("000000") ? "000001" : "000000";

        verify(EMAIL, wrong).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUTH-009"));
        verify("RICH@Example.com", code).andExpect(status().isOk());
    }

    @Test
    @DisplayName("5번 틀리면 그 인증번호는 소진되어 맞는 번호도 429(AUTH-011)로 거부된다")
    void tooManyWrongAttemptsExhaustTheCode() throws Exception {
        String code = requestCode();
        String wrong = code.equals("000000") ? "000001" : "000000";

        for (int i = 0; i < 4; i++) {
            verify(EMAIL, wrong).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUTH-009"));
        }
        verify(EMAIL, wrong).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("AUTH-011"));

        // 행은 남아 있고(재전송 대기 시간이 계속 적용되도록), 맞는 번호도 더는 받아주지 않는다.
        assertThat(codes.count()).isEqualTo(1);
        assertThat(codes.findByUserId(user.getId()).orElseThrow().getFailedAttempts()).isEqualTo(5);
        verify(EMAIL, code).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("AUTH-011"));
    }

    @Test
    @DisplayName("소진된 뒤 바로 다시 발송할 수 없다 (발송→추측→즉시 재발송으로 대기 시간을 우회할 수 없다)")
    void exhaustedCodeCannotBeReplacedImmediately() throws Exception {
        String code = requestCode();
        String wrong = code.equals("000000") ? "000001" : "000000";
        for (int i = 0; i < 5; i++) {
            verify(EMAIL, wrong);
        }

        sendCode(EMAIL).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("AUTH-011"));
    }

    @Test
    @DisplayName("소진된 인증번호도 마지막 발송 후 60초가 지나면 새로 발급되고 틀린 횟수가 초기화된다")
    void exhaustedCodeIsReplacedAfterTheWaitingTime() throws Exception {
        String code = requestCode();
        String wrong = code.equals("000000") ? "000001" : "000000";
        for (int i = 0; i < 5; i++) {
            verify(EMAIL, wrong);
        }

        ageCode(Duration.ofSeconds(61));
        String fresh = requestCode();

        assertThat(codes.findByUserId(user.getId()).orElseThrow().getFailedAttempts()).isZero();
        verify(EMAIL, fresh).andExpect(status().isOk()).andExpect(jsonPath("$.data.resetToken").isNotEmpty());
    }

    @Test
    @DisplayName("틀린 횟수는 오류를 내려가도 저장된다 (4번 틀린 뒤 맞으면 통과)")
    void failedAttemptsArePersisted() throws Exception {
        String code = requestCode();
        String wrong = code.equals("000000") ? "000001" : "000000";

        verify(EMAIL, wrong).andExpect(status().isBadRequest());
        verify(EMAIL, wrong).andExpect(status().isBadRequest());

        assertThat(codes.findByUserId(user.getId()).orElseThrow().getFailedAttempts()).isEqualTo(2);
        verify(EMAIL, code).andExpect(status().isOk());
    }

    @Test
    @DisplayName("유효 시간이 지난 인증번호는 400(AUTH-010)이고 행은 남는다")
    void expiredCodeIsRejected() throws Exception {
        String code = requestCode();
        PasswordResetCode stored = codes.findByUserId(user.getId()).orElseThrow();
        stored.renew(stored.getCodeHash(), LocalDateTime.now().minusMinutes(4), Duration.ofMinutes(3));
        codes.saveAndFlush(stored);

        verify(EMAIL, code).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUTH-010"));
        assertThat(codes.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("인증번호를 요청한 적 없으면 확인할 수 없다 (AUTH-009)")
    void verifyWithoutRequest() throws Exception {
        verify(EMAIL, "123456").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUTH-009"));
    }

    // ---- 새 비밀번호 설정 ----

    @Test
    @DisplayName("재설정하면 새 비밀번호로 로그인되고 옛 비밀번호는 거부된다")
    void resetChangesPassword() throws Exception {
        String resetToken = verifiedResetToken();

        resetPassword(resetToken, NEW_PASSWORD).andExpect(status().isOk());

        login(OLD_PASSWORD).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH-002"));
        login(NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("재설정 토큰은 한 번만 쓸 수 있다 (비밀번호가 바뀌면 같은 토큰은 무효)")
    void resetTokenIsSingleUse() throws Exception {
        String resetToken = verifiedResetToken();

        resetPassword(resetToken, NEW_PASSWORD).andExpect(status().isOk());

        resetPassword(resetToken, "another789Pass").andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-005"));
        login(NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("재설정하면 기존 로그인(refresh 토큰)이 모두 끊긴다")
    void resetRevokesExistingSessions() throws Exception {
        String loginBody = login(OLD_PASSWORD).andReturn().getResponse().getContentAsString();
        String refresh = JsonPath.read(loginBody, "$.data.refreshToken");
        String resetToken = verifiedResetToken();

        resetPassword(resetToken, NEW_PASSWORD).andExpect(status().isOk());

        send(post("/api/auth/reissue"), "{\"refreshToken\":\"%s\"}".formatted(refresh))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("재설정하면 로그인 실패로 잠긴 계정도 풀린다")
    void resetUnlocksTheAccount() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("wrong-password1").andExpect(status().isUnauthorized());
        }
        login(OLD_PASSWORD).andExpect(status().isLocked());
        String resetToken = verifiedResetToken();

        resetPassword(resetToken, NEW_PASSWORD).andExpect(status().isOk());

        login(NEW_PASSWORD).andExpect(status().isOk());
        assertThat(users.findById(user.getId()).orElseThrow().getFailedLoginCount()).isZero();
    }

    @Test
    @DisplayName("새 비밀번호가 규칙에 맞지 않으면 400이고 비밀번호는 바뀌지 않으며 토큰도 그대로 쓸 수 있다")
    void weakNewPasswordIsRejected() throws Exception {
        String resetToken = verifiedResetToken();

        resetPassword(resetToken, "short1").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.data[?(@.field=='newPassword')]").exists());

        login(OLD_PASSWORD).andExpect(status().isOk());
        resetPassword(resetToken, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("위조된 토큰이나 다른 종류의 토큰(access)으로는 재설정할 수 없다")
    void invalidResetTokensAreRejected() throws Exception {
        String loginBody = login(OLD_PASSWORD).andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(loginBody, "$.data.accessToken");

        resetPassword("not-a-jwt", NEW_PASSWORD).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-005"));
        resetPassword(accessToken, NEW_PASSWORD).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-005"));
        login(OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("재설정 토큰은 로그인이 필요한 API의 access 토큰으로 쓸 수 없다")
    void resetTokenIsNotAnAccessToken() throws Exception {
        String resetToken = verifiedResetToken();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/anything")
                .header("Authorization", "Bearer " + resetToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-005"));
    }

    @Test
    @DisplayName("잘못된 토큰을 함께 보내도 공개 경로(계정 복구 API)는 막지 않는다")
    void publicPathsIgnoreBadToken() throws Exception {
        mockMvc.perform(post("/api/auth/find-email").header("Authorization", "Bearer not-a-jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"김리치\",\"phoneNumber\":\"%s\"}".formatted(phone)))
            .andExpect(status().isOk());
    }

}
