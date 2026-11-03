package com.richreach.codef;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.richreach.TestcontainersConfiguration;
import com.richreach.auth.repository.RefreshTokenRepository;
import com.richreach.auth.service.AuthTokenService;
import com.richreach.codef.client.CodefAccountClient;
import com.richreach.codef.client.CodefAccountClient.AccountResult;
import com.richreach.codef.entity.CardCompany;
import com.richreach.codef.entity.CodefConnection;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.repository.CodefConnectionRepository;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 카드사 연결 생성 API를 보안 필터와 실제 MySQL로 확인한다. CODEF 호출은 모킹하고, 실제 키는 쓰지 않는다.
 */
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ConnectionCreateApiTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final String LOGIN_ID = "test-card-login-id";
    private static final String PASSWORD = "test-card-secret-pw-9876";
    private static final String CONNECTED_ID = "test-connected-id-1";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @MockitoBean
    private CodefAccountClient codef;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private UserTermAgreementRepository agreements;
    @Autowired
    private CodefConnectionRepository connections;
    @Autowired
    private CodefOrgAccountRepository orgAccounts;
    @Autowired
    private RefreshTokenRepository refreshTokens;
    @Autowired
    private AuthTokenService authTokenService;
    @Autowired
    private JdbcTemplate jdbc;

    private User user;
    private String accessToken;

    @BeforeEach
    void createUserAndLogin() {
        user = newUser();
        accessToken = authTokenService.issue(user.getId()).accessToken();
        agree(user);
    }

    @AfterEach
    void cleanUp() {
        orgAccounts.deleteAll();
        connections.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    private User newUser() {
        int n = SEQUENCE.incrementAndGet();
        return users.save(new User("conn" + n + "@example.com", "hash", "김리치", "010%08d".formatted(7000000 + n),
            "REAL", LocalDateTime.now()));
    }

    private void agree(User owner) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        agreements.saveAll(List.of(TermType.CARD_INFO, TermType.TRANSACTION, TermType.PRIVACY).stream()
            .map(type -> new UserTermAgreement(owner.getId(), type, "2026-10-01", now))
            .toList());
    }

    private static String body(CardCompany company, String loginId, String password) {
        return "{\"cardCompany\":\"%s\",\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(company, loginId, password);
    }

    private ResultActions connect(String token, String json) throws Exception {
        var request = post("/api/connections").contentType(MediaType.APPLICATION_JSON).content(json);
        return mockMvc.perform(token == null ? request : request.header("Authorization", "Bearer " + token));
    }

    private ResultActions connect(CardCompany company) throws Exception {
        return connect(accessToken, body(company, LOGIN_ID, PASSWORD));
    }

    private static AccountResult connectedWith(String connectedId) {
        return new AccountResult(true, connectedId, null);
    }

    private static AccountResult failedWith(String errorCode) {
        return new AccountResult(false, null, errorCode);
    }

    private String rawConnectedIdOf(User owner) {
        return jdbc.queryForObject("select connected_id from codef_connections where user_id = ?", String.class,
            owner.getId());
    }

    // ---- 요청 검증 ----

    @Test
    @DisplayName("로그인이 필요하다 (토큰이 없으면 401 AUTH-001이고 CODEF를 부르지 않는다)")
    void requiresLogin() throws Exception {
        connect(null, body(CardCompany.KB, LOGIN_ID, PASSWORD))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-001"));

        verify(codef, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("카드 연동 동의가 없으면 400 AUTH-008이고 CODEF를 부르지 않는다")
    void requiresAgreement() throws Exception {
        User other = newUser();
        String otherToken = authTokenService.issue(other.getId()).accessToken();

        connect(otherToken, body(CardCompany.KB, LOGIN_ID, PASSWORD))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("AUTH-008"));

        verify(codef, never()).create(any(), any(), any());
        assertThat(connections.findByUserId(other.getId())).isEmpty();
    }

    @Test
    @DisplayName("카드사, 아이디, 비밀번호 중 하나라도 비어 있으면 400이고, 응답에 입력값이 나오지 않는다")
    void validatesRequiredFields() throws Exception {
        connect(accessToken, "{\"loginId\":\"" + LOGIN_ID + "\",\"password\":\"" + PASSWORD + "\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON-400"));
        connect(accessToken, body(CardCompany.KB, " ", PASSWORD))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON-400"));
        String response = connect(accessToken, body(CardCompany.KB, LOGIN_ID, ""))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON-400"))
            .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(LOGIN_ID).doesNotContain(PASSWORD);
        verify(codef, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("목록에 없는 카드사 코드는 400이다")
    void rejectsUnknownCardCompany() throws Exception {
        connect(accessToken, "{\"cardCompany\":\"NOPE\",\"loginId\":\"a\",\"password\":\"b\"}")
            .andExpect(status().isBadRequest());

        verify(codef, never()).create(any(), any(), any());
    }

    // ---- 연결 ----

    @Test
    @DisplayName("처음 연결하면 connectedId를 암호화해서 저장하고, 카드사 계정을 등록하며, 동의 시각을 옮긴다")
    void firstConnectionCreatesEncryptedConnection() throws Exception {
        when(codef.create(CardCompany.KB, LOGIN_ID, PASSWORD)).thenReturn(connectedWith(CONNECTED_ID));

        String response = connect(CardCompany.KB).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.cardCompany").value("KB"))
            .andExpect(jsonPath("$.data.status").value("CONNECTED"))
            .andReturn().getResponse().getContentAsString();

        CodefConnection connection = connections.findByUserId(user.getId()).orElseThrow();
        assertThat(connection.getConnectedId()).isEqualTo(CONNECTED_ID);
        assertThat(rawConnectedIdOf(user)).isNotEqualTo(CONNECTED_ID).doesNotContain(CONNECTED_ID);
        assertThat(connection.getEnv()).isEqualTo("DEMO");
        assertThat(connection.getCardInfoAgreedAt()).isNotNull();
        assertThat(connection.getTransactionHistoryAgreedAt()).isNotNull();
        assertThat(connection.getPrivacyAgreedAt()).isNotNull();

        List<CodefOrgAccount> accounts = orgAccounts.findByConnectionId(connection.getId());
        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).getOrganization()).isEqualTo("0301");
        assertThat(accounts.get(0).getStatus()).isEqualTo("ACTIVE");
        assertThat(accounts.get(0).getLoginType()).isEqualTo("1");
        assertThat(JsonPath.<Number>read(response, "$.data.connectionId").longValue())
            .isEqualTo(accounts.get(0).getId());
        assertThat(response).doesNotContain(CONNECTED_ID).doesNotContain(LOGIN_ID).doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("다른 카드사를 더하면 기존 connectedId에 add로 추가하고, 연동은 하나만 유지한다")
    void secondCompanyIsAddedToExistingConnection() throws Exception {
        when(codef.create(CardCompany.KB, LOGIN_ID, PASSWORD)).thenReturn(connectedWith(CONNECTED_ID));
        when(codef.add(CONNECTED_ID, CardCompany.SHINHAN, LOGIN_ID, PASSWORD)).thenReturn(connectedWith(CONNECTED_ID));

        connect(CardCompany.KB).andExpect(status().isOk());
        connect(CardCompany.SHINHAN).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.cardCompany").value("SHINHAN"));

        CodefConnection connection = connections.findByUserId(user.getId()).orElseThrow();
        assertThat(connections.count()).isEqualTo(1);
        assertThat(orgAccounts.findByConnectionId(connection.getId()))
            .extracting(CodefOrgAccount::getOrganization).containsExactlyInAnyOrder("0301", "0306");
        verify(codef, times(1)).create(any(), any(), any());
    }

    @Test
    @DisplayName("이미 연결한 카드사를 다시 보내면 update로 재연결하고, 계정을 새로 만들지 않으며 상태를 되돌린다")
    void reconnectUsesUpdateWithoutDuplicate() throws Exception {
        when(codef.create(CardCompany.KB, LOGIN_ID, PASSWORD)).thenReturn(connectedWith(CONNECTED_ID));
        when(codef.update(CONNECTED_ID, CardCompany.KB, LOGIN_ID, PASSWORD)).thenReturn(connectedWith(CONNECTED_ID));
        connect(CardCompany.KB).andExpect(status().isOk());
        jdbc.update("update codef_org_accounts set status = 'ERROR', last_error_code = 'CF-12345'");

        connect(CardCompany.KB).andExpect(status().isOk());

        CodefConnection connection = connections.findByUserId(user.getId()).orElseThrow();
        List<CodefOrgAccount> accounts = orgAccounts.findByConnectionId(connection.getId());
        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).getStatus()).isEqualTo("ACTIVE");
        assertThat(accounts.get(0).getLastErrorCode()).isNull();
        verify(codef, times(1)).update(eq(CONNECTED_ID), eq(CardCompany.KB), any(), any());
    }

    // ---- 실패 ----

    @Test
    @DisplayName("카드사 인증에 실패하면 400 CONN-003이고 아무것도 저장하지 않는다")
    void authFailureSavesNothing() throws Exception {
        when(codef.create(CardCompany.KB, LOGIN_ID, PASSWORD)).thenReturn(failedWith("CF-12345"));

        connect(CardCompany.KB).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CONN-003"));

        assertThat(connections.findByUserId(user.getId())).isEmpty();
        assertThat(orgAccounts.count()).isZero();
    }

    @Test
    @DisplayName("CODEF 서버 오류 코드는 502 CONN-005이고 아무것도 저장하지 않는다")
    void codefServerErrorCode() throws Exception {
        when(codef.create(CardCompany.KB, LOGIN_ID, PASSWORD)).thenReturn(failedWith("CF-09999"));

        connect(CardCompany.KB).andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("CONN-005"));

        assertThat(connections.findByUserId(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("CODEF에 연결하지 못하면 502 CONN-005이고, 응답에 외부 오류 내용이 나오지 않는다")
    void codefUnreachable() throws Exception {
        when(codef.create(any(), any(), any()))
            .thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "upstream-detail-should-not-leak"));

        String response = connect(CardCompany.KB).andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("CONN-005"))
            .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("upstream-detail-should-not-leak");
        assertThat(connections.findByUserId(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("성공했는데 connectedId가 비어 있으면 저장하지 않고 502 CONN-005다")
    void missingConnectedIdIsRejected() throws Exception {
        when(codef.create(CardCompany.KB, LOGIN_ID, PASSWORD)).thenReturn(connectedWith(""));

        connect(CardCompany.KB).andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("CONN-005"));

        assertThat(connections.findByUserId(user.getId())).isEmpty();
    }

    // ---- 보안 ----

    @Test
    @DisplayName("카드사 아이디와 비밀번호는 로그에 남지 않는다")
    void credentialsAreNotLogged(CapturedOutput output) throws Exception {
        when(codef.create(CardCompany.KB, LOGIN_ID, PASSWORD)).thenReturn(failedWith("CF-12345"));

        connect(CardCompany.KB).andExpect(status().isBadRequest());

        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain(LOGIN_ID);
    }

    // ---- 카드사 목록의 연결 여부 ----

    @Test
    @DisplayName("카드사 목록은 연결한 카드사만 connected=true로 보여준다")
    void cardCompaniesShowConnected() throws Exception {
        when(codef.create(CardCompany.KB, LOGIN_ID, PASSWORD)).thenReturn(connectedWith(CONNECTED_ID));
        connect(CardCompany.KB).andExpect(status().isOk());

        mockMvc.perform(get("/api/card-companies").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].code").value("KB"))
            .andExpect(jsonPath("$.data[0].connected").value(true))
            .andExpect(jsonPath("$.data[?(@.connected == true)]", hasSize(1)));
    }

    @Test
    @DisplayName("실패(ERROR)한 카드사는 connected=false다")
    void failedCompanyIsNotConnected() throws Exception {
        when(codef.create(CardCompany.KB, LOGIN_ID, PASSWORD)).thenReturn(connectedWith(CONNECTED_ID));
        connect(CardCompany.KB).andExpect(status().isOk());
        jdbc.update("update codef_org_accounts set status = 'ERROR'");

        mockMvc.perform(get("/api/card-companies").header("Authorization", "Bearer " + accessToken))
            .andExpect(jsonPath("$.data[0].connected").value(false));
    }

    @Test
    @DisplayName("다른 사용자의 연결은 내 카드사 목록에 나오지 않는다")
    void otherUsersConnectionIsNotShown() throws Exception {
        User other = newUser();
        agree(other);
        String otherToken = authTokenService.issue(other.getId()).accessToken();
        when(codef.create(CardCompany.KB, LOGIN_ID, PASSWORD)).thenReturn(connectedWith(CONNECTED_ID));
        connect(otherToken, body(CardCompany.KB, LOGIN_ID, PASSWORD)).andExpect(status().isOk());

        mockMvc.perform(get("/api/card-companies").header("Authorization", "Bearer " + accessToken))
            .andExpect(jsonPath("$.data[?(@.connected == true)]").isEmpty());
    }

}
