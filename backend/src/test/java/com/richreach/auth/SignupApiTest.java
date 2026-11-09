package com.richreach.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.richreach.TestcontainersConfiguration;
import com.richreach.auth.repository.RefreshTokenRepository;
import com.richreach.auth.service.SignupService;
import com.richreach.global.exception.ErrorCode;
import com.richreach.global.security.JwtTokenProvider;
import com.richreach.global.security.TokenType;
import com.richreach.user.entity.User;
import com.richreach.user.entity.UserTermAgreement;
import com.richreach.user.repository.UserRepository;
import com.richreach.user.repository.UserTermAgreementRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 보안 필터를 켠 채로 실제 MySQL(V13까지 적용)에 가입한다. 토큰 없이 호출하므로 /api/auth/**가 공개 경로인지도 함께 확인한다.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SignupApiTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final String PASSWORD = "abcd1234";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private UserTermAgreementRepository agreements;
    @Autowired
    private RefreshTokenRepository refreshTokens;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenProvider tokenProvider;

    @AfterEach
    void cleanUp() {
        refreshTokens.deleteAll();
        users.deleteAll(); // 약관 동의 이력은 DB의 ON DELETE CASCADE로 함께 지워진다.
    }

    private ResultActions signup(String name, String phone, String loginId, boolean terms) throws Exception {
        String json = """
            {"name":"%s","phoneNumber":"%s","loginId":"%s","password":"%s","termsAgreed":%s}"""
            .formatted(name, phone, loginId, PASSWORD, terms);
        return mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions checkId(String loginId) throws Exception {
        return mockMvc.perform(post("/api/auth/check-id").contentType(MediaType.APPLICATION_JSON)
            .content("{\"loginId\":\"%s\"}".formatted(loginId)));
    }

    @Test
    @DisplayName("가입하면 계정과 약관 동의 이력이 저장되고 바로 쓸 수 있는 토큰을 받는다")
    void signupCreatesAccountAgreementAndTokens() throws Exception {
        String body = signup(" 김리치 ", "010-1234-5678", "Rich@Example.com", true)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

        User user = users.findByEmail("rich@example.com").orElseThrow();
        assertThat(user.getName()).isEqualTo("김리치");
        assertThat(user.getPhone()).isEqualTo("01012345678");
        assertThat(user.getUserType()).isEqualTo("REAL");
        assertThat(user.getTermsAgreedAt()).isNotNull();
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();

        List<UserTermAgreement> saved = agreements.findByUserId(user.getId());
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getTermType()).isEqualTo("SERVICE_AND_PRIVACY");
        assertThat(saved.get(0).getTermVersion()).isEqualTo("2026-10-01");

        String accessToken = JsonPath.read(body, "$.data.accessToken");
        assertThat(tokenProvider.parse(accessToken, TokenType.ACCESS).userId()).isEqualTo(user.getId());
        assertThat(refreshTokens.findAll()).hasSize(1)
            .allMatch(token -> token.getUserId().equals(user.getId()));
    }

    @Test
    @DisplayName("약관에 동의하지 않으면 400(AUTH-008)이고 계정은 만들어지지 않는다")
    void signupRequiresTerms() throws Exception {
        signup("김리치", "01012345678", "rich@example.com", false)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("AUTH-008"));

        assertThat(users.count()).isZero();
        assertThat(agreements.count()).isZero();
        assertThat(refreshTokens.count()).isZero();
    }

    @Test
    @DisplayName("이미 가입된 이메일은 대소문자가 달라도 409(AUTH-007)로 거부한다")
    void duplicateEmailIsRejected() throws Exception {
        signup("김리치", "01012345678", "rich@example.com", true).andExpect(status().isCreated());

        signup("박다른", "01099998888", "RICH@example.com", true)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("AUTH-007"));

        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 가입된 전화번호는 하이픈 유무가 달라도 409(AUTH-012)로 거부한다")
    void duplicatePhoneIsRejected() throws Exception {
        signup("김리치", "01012345678", "rich@example.com", true).andExpect(status().isCreated());

        signup("박다른", "010-1234-5678", "other@example.com", true)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("AUTH-012"));

        assertThat(users.count()).isEqualTo(1);
        assertThat(agreements.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("입력값 형식이 틀리면 400이고 아무것도 저장하지 않는다")
    void invalidInputSavesNothing() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"phoneNumber\":\"1\",\"loginId\":\"x\",\"password\":\"1\",\"termsAgreed\":true}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-400"));

        assertThat(users.count()).isZero();
    }

    @Test
    @DisplayName("아이디 중복 확인: 가입 전에는 사용 가능, 가입 후에는 대소문자와 무관하게 사용 불가")
    void checkIdReflectsSignups() throws Exception {
        checkId("rich@example.com").andExpect(status().isOk()).andExpect(jsonPath("$.data.available").value(true));

        signup("김리치", "01012345678", "rich@example.com", true).andExpect(status().isCreated());

        checkId("rich@example.com").andExpect(jsonPath("$.data.available").value(false));
        checkId("RICH@Example.com").andExpect(jsonPath("$.data.available").value(false));
        checkId("other@example.com").andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    @DisplayName("잘못된 토큰을 함께 보내도 가입/중복 확인은 막지 않는다")
    void publicPathsIgnoreBadToken() throws Exception {
        mockMvc.perform(post("/api/auth/check-id").header("Authorization", "Bearer not-a-jwt")
                .contentType(MediaType.APPLICATION_JSON).content("{\"loginId\":\"rich@example.com\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DB의 유일 제약이 같은 전화번호/이메일을 막고, 어느 쪽 때문인지 구분할 수 있다")
    void databaseEnforcesUniqueness() {
        LocalDateTime now = LocalDateTime.now();
        users.saveAndFlush(new User("first@example.com", "hash", "첫째", "01011112222", "REAL", now));

        Throwable samePhone = catchThrowable(() ->
            users.saveAndFlush(new User("second@example.com", "hash", "둘째", "01011112222", "REAL", now)));
        Throwable sameEmail = catchThrowable(() ->
            users.saveAndFlush(new User("first@example.com", "hash", "셋째", "01033334444", "REAL", now)));

        assertThat(samePhone).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(SignupService.duplicateErrorOf(samePhone)).isEqualTo(ErrorCode.DUPLICATE_PHONE);
        assertThat(sameEmail).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(SignupService.duplicateErrorOf(sameEmail)).isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

}
