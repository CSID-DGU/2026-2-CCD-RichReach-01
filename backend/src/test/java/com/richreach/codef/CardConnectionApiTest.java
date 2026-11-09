package com.richreach.codef;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.richreach.TestcontainersConfiguration;
import com.richreach.auth.repository.RefreshTokenRepository;
import com.richreach.auth.service.AuthTokenService;
import com.richreach.user.entity.TermType;
import com.richreach.user.entity.User;
import com.richreach.user.entity.UserTermAgreement;
import com.richreach.user.repository.UserRepository;
import com.richreach.user.repository.UserTermAgreementRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 보안 필터를 켠 채로 실제 MySQL에서 카드사 목록과 카드 연동 동의를 확인한다.
 * 두 API 모두 로그인이 필요하므로, 실제로 발급한 access 토큰으로 호출한다.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CardConnectionApiTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final String ALL_THREE = "{\"types\":[\"CARD_INFO\",\"TRANSACTION\",\"PRIVACY\"]}";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private UserTermAgreementRepository agreements;
    @Autowired
    private RefreshTokenRepository refreshTokens;
    @Autowired
    private AuthTokenService authTokenService;

    private User user;
    private String accessToken;

    @BeforeEach
    void createUserAndLogin() {
        user = newUser();
        accessToken = authTokenService.issue(user.getId()).accessToken();
    }

    @AfterEach
    void cleanUp() {
        refreshTokens.deleteAll();
        users.deleteAll(); // 약관 동의 이력은 DB의 ON DELETE CASCADE로 함께 지워진다.
    }

    private User newUser() {
        int n = SEQUENCE.incrementAndGet();
        return users.save(new User("user" + n + "@example.com", "hash", "김리치", "010%08d".formatted(n), "REAL",
            LocalDateTime.now()));
    }

    private ResultActions agree(String token, String json) throws Exception {
        var request = post("/api/connections/agreements").contentType(MediaType.APPLICATION_JSON).content(json);
        return mockMvc.perform(token == null ? request : request.header("Authorization", "Bearer " + token));
    }

    private List<UserTermAgreement> cardAgreementsOf(User owner) {
        return agreements.findByUserId(owner.getId()).stream()
            .filter(a -> !a.getTermType().equals(TermType.SERVICE_AND_PRIVACY.name()))
            .toList();
    }

    // ---- 카드사 목록 ----

    @Test
    @DisplayName("카드사 목록은 로그인이 필요하다 (토큰이 없으면 401 AUTH-001)")
    void cardCompaniesRequireLogin() throws Exception {
        mockMvc.perform(get("/api/card-companies"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-001"));
    }

    @Test
    @DisplayName("카드사 목록은 7개를 화면 순서대로 코드·이름·지원 여부·연결 여부와 함께 내려준다")
    void cardCompaniesList() throws Exception {
        mockMvc.perform(get("/api/card-companies").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(7))
            .andExpect(jsonPath("$.data[0].code").value("KB"))
            .andExpect(jsonPath("$.data[0].name").value("KB국민카드"))
            .andExpect(jsonPath("$.data[0].supported").value(true))
            .andExpect(jsonPath("$.data[0].connected").value(false))
            .andExpect(jsonPath("$.data[1].code").value("HYUNDAI"))
            .andExpect(jsonPath("$.data[2].code").value("NH"))
            .andExpect(jsonPath("$.data[3].code").value("CITI"))
            .andExpect(jsonPath("$.data[4].code").value("WOORI"))
            .andExpect(jsonPath("$.data[5].code").value("BC"))
            .andExpect(jsonPath("$.data[6].code").value("SHINHAN"))
            .andExpect(jsonPath("$.data[6].name").value("신한카드"))
            .andExpect(jsonPath("$.data[?(@.supported == false)]").isEmpty())
            .andExpect(jsonPath("$.data[?(@.connected == true)]").isEmpty());
    }

    @Test
    @DisplayName("만료되었거나 잘못된 토큰이면 카드사 목록도 401이다 (AUTH-005)")
    void cardCompaniesRejectInvalidToken() throws Exception {
        mockMvc.perform(get("/api/card-companies").header("Authorization", "Bearer not-a-jwt"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-005"));
    }

    // ---- 연결 동의 ----

    @Test
    @DisplayName("연결 동의는 로그인이 필요하고, 토큰이 없으면 아무것도 저장하지 않는다")
    void agreementsRequireLogin() throws Exception {
        agree(null, ALL_THREE).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH-001"));

        assertThat(cardAgreementsOf(user)).isEmpty();
    }

    @Test
    @DisplayName("3개에 동의하면 현재 약관 버전과 시각과 함께 저장되고 응답으로 돌려준다")
    void agreementsAreSaved() throws Exception {
        String body = agree(accessToken, ALL_THREE).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.types[0]").value("CARD_INFO"))
            .andExpect(jsonPath("$.data.types[1]").value("TRANSACTION"))
            .andExpect(jsonPath("$.data.types[2]").value("PRIVACY"))
            .andExpect(jsonPath("$.data.termsVersion").value("2026-10-01"))
            .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(body, "$.data.agreedAt"))
            .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([+-]\\d{2}:\\d{2}|Z)");
        List<UserTermAgreement> saved = cardAgreementsOf(user);
        assertThat(saved).hasSize(3)
            .extracting(UserTermAgreement::getTermType)
            .containsExactlyInAnyOrder("CARD_INFO", "TRANSACTION", "PRIVACY");
        assertThat(saved).allSatisfy(a -> {
            assertThat(a.getTermVersion()).isEqualTo("2026-10-01");
            assertThat(a.getAgreedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("같은 버전에 다시 동의해도 오류 없이 같은 결과이고 행이 늘지 않는다")
    void repeatedAgreementIsIdempotent() throws Exception {
        String first = agree(accessToken, ALL_THREE).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String second = agree(accessToken, ALL_THREE).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(cardAgreementsOf(user)).hasSize(3);
        assertThat(JsonPath.<String>read(second, "$.data.agreedAt"))
            .isEqualTo(JsonPath.<String>read(first, "$.data.agreedAt"));
    }

    @Test
    @DisplayName("요청에 같은 항목이 여러 번 있어도 3개만 저장된다")
    void duplicatedTypesInRequest() throws Exception {
        agree(accessToken, "{\"types\":[\"CARD_INFO\",\"CARD_INFO\",\"TRANSACTION\",\"PRIVACY\",\"PRIVACY\"]}")
            .andExpect(status().isOk());

        assertThat(cardAgreementsOf(user)).hasSize(3);
    }

    @Test
    @DisplayName("필수 항목이 하나라도 빠지면 400(AUTH-008)이고 아무것도 저장하지 않는다")
    void missingTypeIsRejected() throws Exception {
        agree(accessToken, "{\"types\":[\"CARD_INFO\",\"TRANSACTION\"]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("AUTH-008"));

        assertThat(cardAgreementsOf(user)).isEmpty();
    }

    @Test
    @DisplayName("항목이 비었거나 없으면 400(COMMON-400)이고 types 필드 오류를 알려준다")
    void emptyTypesAreRejected() throws Exception {
        agree(accessToken, "{\"types\":[]}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-400"))
            .andExpect(jsonPath("$.data[?(@.field=='types')]").exists());
        agree(accessToken, "{}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.data[?(@.field=='types')]").exists());

        assertThat(cardAgreementsOf(user)).isEmpty();
    }

    @Test
    @DisplayName("모르는 항목 이름이거나 JSON이 아니면 400이다")
    void unknownTypeIsRejected() throws Exception {
        agree(accessToken, "{\"types\":[\"CARD_INFO\",\"TRANSACTION\",\"UNKNOWN\"]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-400"));
        agree(accessToken, "not json").andExpect(status().isBadRequest());

        assertThat(cardAgreementsOf(user)).isEmpty();
    }

    @Test
    @DisplayName("가입 약관 종류를 섞어서 보내면 400이고 아무것도 저장하지 않는다")
    void signupTermTypeIsRejected() throws Exception {
        agree(accessToken, "{\"types\":[\"CARD_INFO\",\"TRANSACTION\",\"PRIVACY\",\"SERVICE_AND_PRIVACY\"]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-400"));

        assertThat(cardAgreementsOf(user)).isEmpty();
    }

    @Test
    @DisplayName("프론트가 termsVersion을 보내도 무시하고 서버의 현재 버전을 기록한다")
    void clientSuppliedVersionIsIgnored() throws Exception {
        agree(accessToken, "{\"types\":[\"CARD_INFO\",\"TRANSACTION\",\"PRIVACY\"],\"termsVersion\":\"1999-01-01\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.termsVersion").value("2026-10-01"));

        assertThat(cardAgreementsOf(user)).allSatisfy(a -> assertThat(a.getTermVersion()).isEqualTo("2026-10-01"));
    }

    @Test
    @DisplayName("동의는 사용자별로 따로 기록되고, 가입 약관 동의 기록과 섞이지 않는다")
    void agreementsArePerUserAndSeparateFromSignup() throws Exception {
        User other = newUser();
        agreements.save(new UserTermAgreement(user.getId(), TermType.SERVICE_AND_PRIVACY, "2026-10-01",
            LocalDateTime.now()));

        agree(accessToken, ALL_THREE).andExpect(status().isOk());

        assertThat(cardAgreementsOf(user)).hasSize(3);
        assertThat(agreements.findByUserId(user.getId())).hasSize(4);
        assertThat(agreements.findByUserId(other.getId())).isEmpty();
    }

    @Test
    @DisplayName("토큰은 유효하지만 사용자가 삭제된 경우 401(AUTH-005)이다")
    void deletedUserIsRejected() throws Exception {
        users.deleteById(user.getId());

        agree(accessToken, ALL_THREE).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-005"));
    }

}
