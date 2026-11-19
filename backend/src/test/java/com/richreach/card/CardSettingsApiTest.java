package com.richreach.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.richreach.TestcontainersConfiguration;
import com.richreach.auth.repository.RefreshTokenRepository;
import com.richreach.auth.service.AuthTokenService;
import com.richreach.card.dto.CollectedCard;
import com.richreach.card.entity.OwnedCard;
import com.richreach.card.repository.OwnedCardRepository;
import com.richreach.card.service.OwnedCardService;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 카드 정보와 연결 관리(SETTINGS-03) API를 보안 필터와 실제 MySQL로 확인한다. 카드 정보 조회
 * ({@code GET /api/cards/{cardId}/settings}), 자동 동기화 설정({@code PUT /api/connections/{id}/auto-sync}), 카드사 연결 해제
 * ({@code DELETE /api/connections/{id}})를 본다. CODEF 호출은 모킹하고, 실제 키는 쓰지 않는다.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CardSettingsApiTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final String KB = "0301";
    private static final String SHINHAN = "0306";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    /** 서비스와 테스트가 같은 시각을 보도록 고정한 시계. 달이 바뀌는 시점에 기대 기간이 어긋나지 않게 한다. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return CLOCK;
        }

    }

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
    private OwnedCardService ownedCardService;
    @Autowired
    private OwnedCardRepository ownedCards;
    @Autowired
    private RefreshTokenRepository refreshTokens;
    @Autowired
    private AuthTokenService authTokenService;
    @Autowired
    private JdbcTemplate jdbc;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        user = newUser();
        token = authTokenService.issue(user.getId()).accessToken();
    }

    @AfterEach
    void cleanUp() {
        syncJobs.deleteAll();
        jdbc.update("delete from user_cards");
        orgAccounts.deleteAll();
        connections.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    // ---- 준비 ----

    private User newUser() {
        int n = SEQUENCE.incrementAndGet();
        return users.save(new User("cardsettings" + n + "@example.com", "hash", "김리치",
            "010%08d".formatted(6000000 + n), "REAL", LocalDateTime.now()));
    }

    /** 사용자의 카드사 연결에 카드사 계정을 하나 더한다. 연결은 사용자당 하나라서 이미 있으면 그 연결을 쓴다. */
    private CodefOrgAccount connect(User owner, String organization) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        CodefConnection connection = connections.findByUserId(owner.getId()).orElseGet(() -> connections.save(
            new CodefConnection(owner, "test-connected-id-" + owner.getId(), "DEMO", now, now, now, now)));
        CodefOrgAccount account = CodefOrgAccount.connected(connection, organization, now);
        account.rememberLoginId("ab***");
        return orgAccounts.save(account);
    }

    /** 계정에 카드 한 장을 저장하고 그 카드를 돌려준다. */
    private OwnedCard card(User owner, CodefOrgAccount account, CardCompany company, String name, String number) {
        ownedCardService.saveSynced(owner.getId(), account.getId(), company,
            List.of(new CollectedCard(name, number, "check")));
        return ownedCards.findByOrgAccountId(account.getId()).stream()
            .filter(found -> found.getCardName().equals(name)).findFirst().orElseThrow();
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String jwt) throws Exception {
        return mockMvc.perform(jwt == null ? request : request.header("Authorization", "Bearer " + jwt));
    }

    private ResultActions autoSync(String jwt, Object accountId, String json) throws Exception {
        return call(put("/api/connections/" + accountId + "/auto-sync").contentType(MediaType.APPLICATION_JSON)
            .content(json), jwt);
    }

    private ResultActions disconnect(String jwt, Object accountId) throws Exception {
        return call(delete("/api/connections/" + accountId), jwt);
    }

    private String statusOf(CodefOrgAccount account) {
        return orgAccounts.findById(account.getId()).orElseThrow().getStatus();
    }

    // ---- 카드 정보 조회 ----

    @Test
    @DisplayName("카드 정보는 연결된 계정의 가린 아이디·자동 동기화·전월 기간을 주고, 알 수 없는 결제일은 null이다")
    void cardSettings() throws Exception {
        CodefOrgAccount account = connect(user, KB);
        OwnedCard card = card(user, account, CardCompany.KB, "테스트 카드", "1111");
        YearMonth previous = YearMonth.now(CLOCK).minusMonths(1);

        call(get("/api/cards/" + card.getId() + "/settings"), token).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.cardId").value(card.getId()))
            .andExpect(jsonPath("$.data.cardCompany").value("KB"))
            .andExpect(jsonPath("$.data.cardName").value("테스트 카드"))
            .andExpect(jsonPath("$.data.maskedNumber").value("1111"))
            .andExpect(jsonPath("$.data.paymentDay").doesNotExist())
            .andExpect(jsonPath("$.data.performancePeriod.from").value(previous.atDay(1).toString()))
            .andExpect(jsonPath("$.data.performancePeriod.to").value(previous.atEndOfMonth().toString()))
            .andExpect(jsonPath("$.data.account.connectionId").value(account.getId()))
            .andExpect(jsonPath("$.data.account.maskedLoginId").value("ab***"))
            .andExpect(jsonPath("$.data.account.autoSyncEnabled").value(true))
            .andExpect(jsonPath("$.data.account.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("가린 아이디를 저장하기 전에 연결한 계정은 maskedLoginId가 null이다")
    void legacyAccountHasNoMaskedId() throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        CodefConnection connection = connections.save(
            new CodefConnection(user, "test-connected-id-legacy", "DEMO", now, now, now, now));
        CodefOrgAccount account = orgAccounts.save(CodefOrgAccount.connected(connection, KB, now));
        OwnedCard card = card(user, account, CardCompany.KB, "예전 카드", "2222");

        call(get("/api/cards/" + card.getId() + "/settings"), token).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.account.maskedLoginId").doesNotExist());
    }

    @Test
    @DisplayName("카드 정보는 로그인이 필요하고, 남의 카드나 없는 카드는 404 CARD-001이다")
    void cardSettingsAccess() throws Exception {
        CodefOrgAccount account = connect(user, KB);
        OwnedCard card = card(user, account, CardCompany.KB, "테스트 카드", "1111");
        User other = newUser();
        String otherToken = authTokenService.issue(other.getId()).accessToken();

        call(get("/api/cards/" + card.getId() + "/settings"), null).andExpect(status().isUnauthorized());
        call(get("/api/cards/" + card.getId() + "/settings"), otherToken).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CARD-001"));
        call(get("/api/cards/999999999/settings"), token).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CARD-001"));
    }

    // ---- 자동 동기화 설정 ----

    @Test
    @DisplayName("자동 동기화는 기본으로 켜져 있고, 끄면 연결 목록과 카드 정보에 반영된다")
    void autoSyncToggle() throws Exception {
        CodefOrgAccount account = connect(user, KB);
        OwnedCard card = card(user, account, CardCompany.KB, "테스트 카드", "1111");

        call(get("/api/connections"), token).andExpect(jsonPath("$.data[0].autoSyncEnabled").value(true))
            .andExpect(jsonPath("$.data[0].maskedLoginId").value("ab***"));

        autoSync(token, account.getId(), "{\"enabled\":false}").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.connectionId").value(account.getId()))
            .andExpect(jsonPath("$.data.autoSyncEnabled").value(false));

        call(get("/api/connections"), token).andExpect(jsonPath("$.data[0].autoSyncEnabled").value(false));
        call(get("/api/cards/" + card.getId() + "/settings"), token)
            .andExpect(jsonPath("$.data.account.autoSyncEnabled").value(false));

        // 같은 값으로 다시 바꿔도 오류가 아니다.
        autoSync(token, account.getId(), "{\"enabled\":false}").andExpect(status().isOk());
        autoSync(token, account.getId(), "{\"enabled\":true}").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.autoSyncEnabled").value(true));
    }

    @Test
    @DisplayName("자동 동기화 설정은 enabled가 없으면 400, 로그인이 없으면 401, 남의 연결은 404 CONN-001이다")
    void autoSyncValidationAndAccess() throws Exception {
        CodefOrgAccount account = connect(user, KB);
        User other = newUser();
        String otherToken = authTokenService.issue(other.getId()).accessToken();

        autoSync(token, account.getId(), "{}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-400"));
        autoSync(null, account.getId(), "{\"enabled\":false}").andExpect(status().isUnauthorized());
        autoSync(otherToken, account.getId(), "{\"enabled\":false}").andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CONN-001"));
        assertThat(orgAccounts.findById(account.getId()).orElseThrow().isAutoSyncEnabled()).isTrue();
    }

    // ---- 카드사 연결 해제 ----

    @Test
    @DisplayName("연결을 해제하면 CODEF에서 그 카드사만 지우고, 그 카드사의 카드는 모두 지우며 다른 카드사는 그대로 둔다")
    void disconnectDeletesOnlyThatCompany() throws Exception {
        CodefOrgAccount kb = connect(user, KB);
        CodefOrgAccount shinhan = connect(user, SHINHAN);
        card(user, kb, CardCompany.KB, "KB 카드", "1111");
        card(user, kb, CardCompany.KB, "KB 카드2", "3333");
        card(user, shinhan, CardCompany.SHINHAN, "신한 카드", "2222");
        when(codef.delete(anyString(), eq(CardCompany.KB))).thenReturn(new AccountResult(true, "", null));

        disconnect(token, kb.getId()).andExpect(status().isOk());

        verify(codef).delete("test-connected-id-" + user.getId(), CardCompany.KB);
        assertThat(statusOf(kb)).isEqualTo(CodefOrgAccount.STATUS_DELETED);
        assertThat(orgAccounts.findById(kb.getId()).orElseThrow().getLoginIdMasked()).isNull();
        assertThat(ownedCards.findByOrgAccountId(kb.getId())).isEmpty();
        assertThat(ownedCards.findByOrgAccountId(shinhan.getId())).hasSize(1);
        assertThat(statusOf(shinhan)).isEqualTo(CodefOrgAccount.STATUS_ACTIVE);

        // 연결 목록과 카드 목록에서도 사라지고, 다른 카드사는 남는다.
        call(get("/api/connections"), token).andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].cardCompany").value("SHINHAN"));
        call(get("/api/cards"), token).andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].cardName").value("신한 카드"));
    }

    @Test
    @DisplayName("해제한 카드사는 다시 해제할 수 없고(404 CONN-001), 자동 동기화도 바꿀 수 없다")
    void cannotDisconnectTwice() throws Exception {
        CodefOrgAccount kb = connect(user, KB);
        when(codef.delete(anyString(), any())).thenReturn(new AccountResult(true, "", null));

        disconnect(token, kb.getId()).andExpect(status().isOk());
        disconnect(token, kb.getId()).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CONN-001"));
        autoSync(token, kb.getId(), "{\"enabled\":false}").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CODEF에서 지우지 못하면 502 CONN-005이고 연결과 카드는 그대로 남아 다시 시도할 수 있다")
    void codefFailureKeepsEverything() throws Exception {
        CodefOrgAccount kb = connect(user, KB);
        card(user, kb, CardCompany.KB, "KB 카드", "1111");
        when(codef.delete(anyString(), any())).thenReturn(new AccountResult(false, null, "CF-09999"));

        disconnect(token, kb.getId()).andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("CONN-005"));

        assertThat(statusOf(kb)).isEqualTo(CodefOrgAccount.STATUS_ACTIVE);
        assertThat(ownedCards.findByOrgAccountId(kb.getId())).hasSize(1);
        assertThat(orgAccounts.findById(kb.getId()).orElseThrow().getLoginIdMasked()).isEqualTo("ab***");

        // 다시 시도하면 성공한다.
        when(codef.delete(anyString(), any())).thenReturn(new AccountResult(true, "", null));
        disconnect(token, kb.getId()).andExpect(status().isOk());
        assertThat(statusOf(kb)).isEqualTo(CodefOrgAccount.STATUS_DELETED);
    }

    @Test
    @DisplayName("CODEF 호출이 예외로 끝나도 데이터는 그대로이고 CODEF 서버 오류로 응답한다")
    void codefExceptionKeepsEverything() throws Exception {
        CodefOrgAccount kb = connect(user, KB);
        card(user, kb, CardCompany.KB, "KB 카드", "1111");
        when(codef.delete(anyString(), any())).thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_ERROR));

        disconnect(token, kb.getId()).andExpect(status().isBadGateway());

        assertThat(statusOf(kb)).isEqualTo(CodefOrgAccount.STATUS_ACTIVE);
        assertThat(ownedCards.findByOrgAccountId(kb.getId())).hasSize(1);
    }

    @Test
    @DisplayName("동기화가 진행 중이면 409 CONN-007이고 CODEF를 부르지 않는다")
    void blockedWhileSyncing() throws Exception {
        CodefOrgAccount kb = connect(user, KB);
        card(user, kb, CardCompany.KB, "KB 카드", "1111");
        syncJobs.save(CodefSyncJob.startCardList(kb, "batch", LocalDateTime.now(CLOCK)));

        disconnect(token, kb.getId()).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONN-007"));

        verify(codef, never()).delete(any(), any());
        assertThat(ownedCards.findByOrgAccountId(kb.getId())).hasSize(1);
    }

    @Test
    @DisplayName("로그인이 필요하고, 남의 연결이나 없는 연결은 404 CONN-001이며 CODEF를 부르지 않는다")
    void disconnectAccess() throws Exception {
        CodefOrgAccount kb = connect(user, KB);
        card(user, kb, CardCompany.KB, "KB 카드", "1111");
        User other = newUser();
        String otherToken = authTokenService.issue(other.getId()).accessToken();

        disconnect(null, kb.getId()).andExpect(status().isUnauthorized());
        disconnect(otherToken, kb.getId()).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CONN-001"));
        disconnect(token, 999999999L).andExpect(status().isNotFound());
        disconnect(token, "abc").andExpect(status().isBadRequest());

        verify(codef, never()).delete(any(), any());
        assertThat(statusOf(kb)).isEqualTo(CodefOrgAccount.STATUS_ACTIVE);
        assertThat(ownedCards.findByOrgAccountId(kb.getId())).hasSize(1);
    }

    @Test
    @DisplayName("해제한 뒤 같은 카드사를 다시 연결할 수 있도록 새 계정을 만들 수 있다")
    void canReconnectAfterDisconnect() throws Exception {
        CodefOrgAccount kb = connect(user, KB);
        when(codef.delete(anyString(), any())).thenReturn(new AccountResult(true, "", null));
        disconnect(token, kb.getId()).andExpect(status().isOk());

        // 연결 생성이 쓰는 조회: 해제한 계정은 없는 것으로 본다.
        assertThat(orgAccounts.findByConnectionIdAndOrganizationAndStatusNot(kb.getConnection().getId(), KB,
            CodefOrgAccount.STATUS_DELETED)).isEmpty();
        CodefOrgAccount again = connect(user, KB);
        assertThat(again.getId()).isNotEqualTo(kb.getId());
        call(get("/api/connections"), token).andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].connectionId").value(again.getId()));
    }

}
