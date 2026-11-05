package com.richreach.codef;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.richreach.TestcontainersConfiguration;
import com.richreach.auth.repository.RefreshTokenRepository;
import com.richreach.auth.service.AuthTokenService;
import com.richreach.codef.client.CodefAccountClient;
import com.richreach.codef.client.CodefAccountClient.AccountResult;
import com.richreach.codef.entity.CardCompany;
import com.richreach.codef.entity.CodefConnection;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.entity.CodefSyncJob;
import com.richreach.codef.repository.CodefConnectionRepository;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.codef.repository.CodefSyncJobRepository;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
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
 * 실패한 카드사 재연결 API를 보안 필터와 실제 MySQL로 확인한다. CODEF 호출은 모킹하고, 실제 키는 쓰지 않는다.
 */
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ConnectionRetryApiTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final String KB = "0301";
    private static final String LOGIN_ID = "test-card-login-id";
    private static final String PASSWORD = "test-card-secret-pw-9876";
    private static final String BODY = "{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(LOGIN_ID, PASSWORD);
    private static final String CONNECTED_ID = "test-connected-id-retry";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @MockitoBean
    private CodefAccountClient codef;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private CodefConnectionRepository connections;
    @Autowired
    private CodefOrgAccountRepository orgAccounts;
    @Autowired
    private CodefSyncJobRepository syncJobs;
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
    }

    @AfterEach
    void cleanUp() {
        syncJobs.deleteAll();
        orgAccounts.deleteAll();
        connections.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    private User newUser() {
        int n = SEQUENCE.incrementAndGet();
        return users.save(new User("retry" + n + "@example.com", "hash", "김리치", "010%08d".formatted(9000000 + n),
            "REAL", LocalDateTime.now()));
    }

    private CodefOrgAccount connect(User owner, String organization, String status) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        CodefConnection connection = connections.findByUserId(owner.getId()).orElseGet(() -> connections.save(
            new CodefConnection(owner, CONNECTED_ID + owner.getId(), "DEMO", now, now, now, now)));
        return orgAccounts.save(new CodefOrgAccount(connection, null, organization, "CD", "P", "1", status, now));
    }

    /** 가장 최근 동기화가 실패한 연결된 카드사. */
    private CodefOrgAccount accountWithFailedSync(boolean retryable) {
        CodefOrgAccount account = connect(user, KB, "ACTIVE");
        CodefSyncJob job = CodefSyncJob.startCardList(account, "batch", LocalDateTime.now().minusMinutes(2));
        job.markFailed("CF-12345", retryable, LocalDateTime.now().minusMinutes(1));
        syncJobs.save(job);
        return account;
    }

    private ResultActions retry(String token, Object accountId, String json) throws Exception {
        var request = post("/api/connections/" + accountId + "/retry")
            .contentType(MediaType.APPLICATION_JSON).content(json);
        return mockMvc.perform(token == null ? request : request.header("Authorization", "Bearer " + token));
    }

    private ResultActions retry(Object accountId) throws Exception {
        return retry(accessToken, accountId, BODY);
    }

    private void stubUpdateSuccess() {
        when(codef.update(anyString(), eq(CardCompany.KB), eq(LOGIN_ID), eq(PASSWORD)))
            .thenReturn(new AccountResult(true, CONNECTED_ID, null));
    }

    private String statusOf(CodefOrgAccount account) {
        return orgAccounts.findById(account.getId()).orElseThrow().getStatus();
    }

    // ---- 로그인·입력 ----

    @Test
    @DisplayName("로그인이 필요하다 (토큰이 없으면 401 AUTH-001이고 CODEF를 부르지 않는다)")
    void requiresLogin() throws Exception {
        CodefOrgAccount account = accountWithFailedSync(true);

        retry(null, account.getId(), BODY).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-001"));

        verify(codef, never()).update(any(), any(), any(), any());
    }

    @Test
    @DisplayName("아이디나 비밀번호가 비어 있으면 400이고, 응답에 입력값이 나오지 않으며 CODEF를 부르지 않는다")
    void validatesCredentials() throws Exception {
        CodefOrgAccount account = accountWithFailedSync(true);

        retry(accessToken, account.getId(), "{\"loginId\":\" \",\"password\":\"" + PASSWORD + "\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON-400"));
        String response = retry(accessToken, account.getId(), "{\"loginId\":\"" + LOGIN_ID + "\",\"password\":\"\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON-400"))
            .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(LOGIN_ID).doesNotContain(PASSWORD);
        verify(codef, never()).update(any(), any(), any(), any());
    }

    @Test
    @DisplayName("계정 ID가 숫자가 아니면 400이다")
    void rejectsNonNumericId() throws Exception {
        retry("abc").andExpect(status().isBadRequest());

        verify(codef, never()).update(any(), any(), any(), any());
    }

    // ---- 재시도할 수 있는 경우 ----

    @Test
    @DisplayName("동기화가 실패한 카드사는 update로 다시 연결하고, 같은 connectedId를 쓰며 응답에 비밀이 나오지 않는다")
    void retriesAfterFailedSync() throws Exception {
        CodefOrgAccount account = accountWithFailedSync(true);
        stubUpdateSuccess();

        String response = retry(account.getId()).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.connectionId").value(account.getId()))
            .andExpect(jsonPath("$.data.cardCompany").value("KB"))
            .andExpect(jsonPath("$.data.status").value("CONNECTED"))
            .andReturn().getResponse().getContentAsString();

        verify(codef).update(CONNECTED_ID + user.getId(), CardCompany.KB, LOGIN_ID, PASSWORD);
        assertThat(response).doesNotContain(CONNECTED_ID).doesNotContain(LOGIN_ID).doesNotContain(PASSWORD);
        assertThat(connections.count()).isEqualTo(1);
        assertThat(orgAccounts.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("연결 실패(ERROR) 상태의 카드사는 다시 연결하면 ACTIVE로 돌아오고 오류 코드가 지워진다")
    void retriesErrorAccountAndResetsStatus() throws Exception {
        CodefOrgAccount account = connect(user, KB, "ERROR");
        jdbc.update("update codef_org_accounts set last_error_code = 'CF-12345' where id = ?", account.getId());
        stubUpdateSuccess();

        retry(account.getId()).andExpect(status().isOk());

        CodefOrgAccount reloaded = orgAccounts.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo("ACTIVE");
        assertThat(reloaded.getLastErrorCode()).isNull();
        mockMvc.perform(get("/api/card-companies").header("Authorization", "Bearer " + accessToken))
            .andExpect(jsonPath("$.data[0].connected").value(true));
    }

    @Test
    @DisplayName("시간이 초과된 채 멈춘 동기화도 실패로 보고 다시 연결할 수 있다")
    void retriesAfterTimedOutSync() throws Exception {
        CodefOrgAccount account = connect(user, KB, "ACTIVE");
        syncJobs.save(CodefSyncJob.startCardList(account, "stale", LocalDateTime.now().minusMinutes(30)));
        stubUpdateSuccess();

        retry(account.getId()).andExpect(status().isOk());
    }

    // ---- 거부 ----

    @Test
    @DisplayName("이미 동기화에 성공한 카드사는 404 CONN-001이고 CODEF를 부르지 않는다")
    void rejectsSucceededAccount() throws Exception {
        CodefOrgAccount account = connect(user, KB, "ACTIVE");
        CodefSyncJob job = CodefSyncJob.startCardList(account, "batch", LocalDateTime.now().minusMinutes(2));
        job.markFetched(2, "CF-00000", LocalDateTime.now().minusMinutes(1));
        syncJobs.save(job);

        retry(account.getId()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONN-001"));

        verify(codef, never()).update(any(), any(), any(), any());
    }

    @Test
    @DisplayName("한 번도 실패한 적 없는 연결된 카드사는 404 CONN-001이다")
    void rejectsNeverFailedAccount() throws Exception {
        CodefOrgAccount account = connect(user, KB, "ACTIVE");

        retry(account.getId()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONN-001"));

        verify(codef, never()).update(any(), any(), any(), any());
    }

    @Test
    @DisplayName("없는 카드사 계정은 404 CONN-001이다")
    void rejectsUnknownAccount() throws Exception {
        retry(999999999L).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONN-001"));

        verify(codef, never()).update(any(), any(), any(), any());
    }

    @Test
    @DisplayName("다른 사용자의 카드사 계정은 없는 계정과 같은 404 CONN-001이고 CODEF를 부르지 않는다")
    void rejectsOtherUsersAccount() throws Exception {
        User other = newUser();
        CodefOrgAccount theirs = connect(other, KB, "ERROR");

        retry(theirs.getId()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONN-001"));

        verify(codef, never()).update(any(), any(), any(), any());
        assertThat(statusOf(theirs)).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("해제(DELETED)한 카드사 계정은 404 CONN-001이다")
    void rejectsDeletedAccount() throws Exception {
        CodefOrgAccount account = connect(user, KB, "DELETED");

        retry(account.getId()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONN-001"));
    }

    @Test
    @DisplayName("동기화가 진행 중이면 409 CONN-007이고 CODEF를 부르지 않는다")
    void rejectsWhileSyncRunning() throws Exception {
        CodefOrgAccount account = connect(user, KB, "ERROR");
        syncJobs.save(CodefSyncJob.startCardList(account, "running", LocalDateTime.now()));

        retry(account.getId()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONN-007"));

        verify(codef, never()).update(any(), any(), any(), any());
    }

    // ---- 잠금 위험 ----

    @Test
    @DisplayName("계정이 잠긴(LOCKED) 카드사는 409 CONN-008로 막고 CODEF를 부르지 않는다")
    void blocksLockedAccount() throws Exception {
        CodefOrgAccount account = connect(user, KB, "LOCKED");

        retry(account.getId()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONN-008"));

        verify(codef, never()).update(any(), any(), any(), any());
        assertThat(statusOf(account)).isEqualTo("LOCKED");
    }

    @Test
    @DisplayName("최근 실패가 다시 시도하면 안 되는 오류(retryable=false)면 409 CONN-008로 막는다")
    void blocksNonRetryableFailure() throws Exception {
        CodefOrgAccount account = accountWithFailedSync(false);

        retry(account.getId()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONN-008"));

        verify(codef, never()).update(any(), any(), any(), any());
    }

    // ---- CODEF 오류 ----

    @Test
    @DisplayName("카드사 인증에 실패하면 400 CONN-003이고 계정 상태를 바꾸지 않는다")
    void authFailureKeepsState() throws Exception {
        CodefOrgAccount account = connect(user, KB, "ERROR");
        when(codef.update(anyString(), eq(CardCompany.KB), anyString(), anyString()))
            .thenReturn(new AccountResult(false, null, "CF-12345"));

        retry(account.getId()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CONN-003"));

        assertThat(statusOf(account)).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("CODEF 서버 오류 코드는 502 CONN-005다")
    void codefServerErrorCode() throws Exception {
        CodefOrgAccount account = connect(user, KB, "ERROR");
        when(codef.update(anyString(), eq(CardCompany.KB), anyString(), anyString()))
            .thenReturn(new AccountResult(false, null, "CF-09999"));

        retry(account.getId()).andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("CONN-005"));
    }

    @Test
    @DisplayName("CODEF에 연결하지 못하면 502 CONN-005이고, 응답에 외부 오류 내용이 나오지 않는다")
    void codefUnreachable() throws Exception {
        CodefOrgAccount account = connect(user, KB, "ERROR");
        when(codef.update(anyString(), eq(CardCompany.KB), anyString(), anyString()))
            .thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "upstream-detail-should-not-leak"));

        String response = retry(account.getId()).andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("CONN-005"))
            .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("upstream-detail-should-not-leak");
        assertThat(statusOf(account)).isEqualTo("ERROR");
    }

    // ---- 보안 ----

    @Test
    @DisplayName("카드사 아이디와 비밀번호는 로그에 남지 않는다")
    void credentialsAreNotLogged(CapturedOutput output) throws Exception {
        CodefOrgAccount account = connect(user, KB, "ERROR");
        when(codef.update(anyString(), eq(CardCompany.KB), anyString(), anyString()))
            .thenReturn(new AccountResult(false, null, "CF-12345"));

        retry(account.getId()).andExpect(status().isBadRequest());

        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain(LOGIN_ID).doesNotContain(CONNECTED_ID);
    }

}
