package com.richreach.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.richreach.TestcontainersConfiguration;
import com.richreach.auth.dto.TokenPair;
import com.richreach.auth.repository.RefreshTokenRepository;
import com.richreach.auth.service.AuthTokenService;
import com.richreach.card.dto.CollectedCard;
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
import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.entity.MonthlyPlan;
import com.richreach.plan.entity.PlanCardAllocation;
import com.richreach.plan.entity.PlanStrategy;
import com.richreach.plan.entity.SpendingProfile;
import com.richreach.plan.repository.MonthlyPlanRepository;
import com.richreach.plan.repository.PlanCardAllocationRepository;
import com.richreach.plan.repository.SpendingProfileRepository;
import com.richreach.transaction.SelectedCards;
import com.richreach.transaction.dto.CollectedTransaction;
import com.richreach.transaction.service.TransactionStoreService;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
 * 회원 탈퇴({@code DELETE /api/users/me})를 보안 필터와 실제 MySQL로 확인한다. 사용자와 그 데이터가 모두 지워지는지(카드 연동,
 * 보유카드, 거래, 월간 플랜, 토큰), 다른 사용자의 데이터는 그대로인지, CODEF 삭제가 실패하거나 동기화가 진행 중이면 아무것도 지워지지
 * 않는지, 같은 이메일로 다시 가입할 수 있는지를 본다. CODEF 호출은 모킹하고, 실제 키는 쓰지 않는다.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class UserWithdrawalApiTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final YearMonth MONTH = YearMonth.of(2026, 12);
    /** 서비스와 테스트가 같은 시각을 보도록 고정한 시계. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T03:00:00Z"), ZoneId.of("Asia/Seoul"));

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
    private TransactionStoreService store;
    @Autowired
    private SpendingProfileRepository profiles;
    @Autowired
    private MonthlyPlanRepository plans;
    @Autowired
    private PlanCardAllocationRepository allocations;
    @Autowired
    private RefreshTokenRepository refreshTokens;
    @Autowired
    private AuthTokenService authTokenService;
    @Autowired
    private JdbcTemplate jdbc;

    private User user;
    private TokenPair tokens;

    @BeforeEach
    void setUp() {
        user = newUser();
        tokens = authTokenService.issue(user.getId());
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from plan_card_allocations");
        jdbc.update("delete from monthly_plans");
        jdbc.update("delete from card_transactions");
        jdbc.update("delete from user_cards");
        syncJobs.deleteAll();
        orgAccounts.deleteAll();
        connections.deleteAll();
        jdbc.update("delete from spending_profiles");
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    // ---- 준비 ----

    private User newUser() {
        int n = SEQUENCE.incrementAndGet();
        return users.save(new User("withdraw" + n + "@example.com", "hash", "김리치",
            "010%08d".formatted(7000000 + n), "REAL", LocalDateTime.now()));
    }

    private CodefOrgAccount connect(User owner, String organization) {
        LocalDateTime now = LocalDateTime.now(CLOCK).withNano(0);
        CodefConnection connection = connections.findByUserId(owner.getId()).orElseGet(() -> connections.save(
            new CodefConnection(owner, "test-connected-id-" + owner.getId(), "DEMO", now, now, now, now)));
        return orgAccounts.save(CodefOrgAccount.connected(connection, organization, now));
    }

    /** 사용자에게 카드 연동, 보유카드, 거래, 월간 플랜, 동기화 작업 기록을 만든다. 만든 계정 두 개(정상, 해제)를 돌려준다. */
    private CodefOrgAccount[] fill(User owner) {
        CodefOrgAccount kb = connect(owner, "0301");
        CodefOrgAccount shinhan = connect(owner, "0306");
        ownedCardService.saveSynced(owner.getId(), kb.getId(), CardCompany.KB,
            List.of(new CollectedCard("테스트 카드", "1111", "check")));
        long cardId = ownedCards.findByOrgAccountId(kb.getId()).get(0).getId();
        SelectedCards.select(jdbc, cardId);
        store.saveCollected(owner.getId(), cardId, List.of(new CollectedTransaction("AP-" + SEQUENCE.incrementAndGet(),
            LocalDateTime.now(CLOCK).minusDays(1), "가맹점", 10_000, 0, 0, false, SpendingCategory.CAFE.name())));

        SpendingProfile profile = profiles.save(SpendingProfile.create(owner.getId(), MONTH,
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30)));
        MonthlyPlan plan = plans.save(MonthlyPlan.create(owner.getId(), profile.getId(), MONTH, PlanStrategy.CP_SAT, 1,
            450_000, 10_500));
        allocations.save(PlanCardAllocation.create(plan.getId(), cardId, SpendingCategory.CAFE, 30_000, 900));

        // 이미 해제한 계정. CODEF에서는 이미 지운 계정이라 탈퇴 때 다시 부르지 않는다.
        shinhan.disconnect();
        orgAccounts.save(shinhan);
        CodefSyncJob job = syncJobs.save(CodefSyncJob.startCardList(kb, "batch-" + owner.getId(),
            LocalDateTime.now(CLOCK).minusHours(3)));
        job.markFailed("CF-00000", true, LocalDateTime.now(CLOCK).minusHours(3).plusMinutes(1));
        syncJobs.save(job);
        return new CodefOrgAccount[] {kb, shinhan};
    }

    private ResultActions withdraw(String jwt, String body) throws Exception {
        MockHttpServletRequestBuilder request = delete("/api/users/me").contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request = request.content(body);
        }
        return mockMvc.perform(jwt == null ? request : request.header("Authorization", "Bearer " + jwt));
    }

    private int count(String table, String column, long value) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + column + " = ?", Integer.class, value);
    }

    /** user_id 컬럼이 있는 모든 테이블에서 이 사용자의 행 수를 센다. 새 테이블이 생겨도 함께 확인한다. */
    private int rowsOf(long userId) {
        List<String> tables = jdbc.queryForList("select table_name from information_schema.columns "
            + "where table_schema = database() and column_name = 'user_id'", String.class);
        int total = 0;
        for (String table : tables) {
            total += count(table, "user_id", userId);
        }
        return total;
    }

    // ---- 테스트 ----

    @Test
    @DisplayName("탈퇴하면 사용자와 카드 연동·보유카드·거래·플랜·토큰이 모두 지워지고, CODEF에서는 해제하지 않은 계정만 지운다")
    void withdrawsEverything() throws Exception {
        CodefOrgAccount[] accounts = fill(user);
        long planId = jdbc.queryForObject("select id from monthly_plans where user_id = ?", Long.class, user.getId());
        assertThat(rowsOf(user.getId())).isGreaterThan(5);
        when(codef.delete(anyString(), eq(CardCompany.KB))).thenReturn(new AccountResult(true, "", null));

        withdraw(tokens.accessToken(), "{\"agreed\":true}").andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        assertThat(users.findById(user.getId())).isEmpty();
        assertThat(rowsOf(user.getId())).isZero();
        assertThat(count("codef_org_accounts", "id", accounts[0].getId())).isZero();
        assertThat(count("codef_org_accounts", "id", accounts[1].getId())).isZero();
        assertThat(count("plan_card_allocations", "plan_id", planId)).isZero();
        verify(codef, times(1)).delete(anyString(), eq(CardCompany.KB));
        verify(codef, never()).delete(anyString(), eq(CardCompany.SHINHAN));
    }

    @Test
    @DisplayName("탈퇴하면 refresh 토큰으로 더는 갱신할 수 없다")
    void refreshTokenStopsWorking() throws Exception {
        withdraw(tokens.accessToken(), "{\"agreed\":true}").andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/reissue").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("카드를 연결한 적 없는 사용자도 탈퇴할 수 있고, CODEF는 부르지 않는다")
    void withdrawsWithoutAnyConnection() throws Exception {
        withdraw(tokens.accessToken(), "{\"agreed\":true}").andExpect(status().isOk());

        assertThat(users.findById(user.getId())).isEmpty();
        verify(codef, never()).delete(anyString(), any());
    }

    @Test
    @DisplayName("다른 사용자의 데이터는 그대로이고, 탈퇴한 이메일과 휴대폰으로 다시 가입할 수 있다")
    void otherUsersAreUntouchedAndTheEmailCanBeReused() throws Exception {
        User other = newUser();
        fill(other);
        int otherRows = rowsOf(other.getId());
        String email = user.getEmail();
        String phone = user.getPhone();

        withdraw(tokens.accessToken(), "{\"agreed\":true}").andExpect(status().isOk());

        assertThat(users.findById(other.getId())).isPresent();
        assertThat(rowsOf(other.getId())).isEqualTo(otherRows);
        User again = users.save(new User(email, "hash2", "김리치", phone, "REAL", LocalDateTime.now()));
        assertThat(again.getId()).isNotEqualTo(user.getId());
    }

    @Test
    @DisplayName("CODEF에서 카드사 계정을 지우지 못하면 아무것도 지워지지 않고, 다시 시도할 수 있다")
    void keepsEverythingWhenCodefFails() throws Exception {
        CodefOrgAccount[] accounts = fill(user);
        int before = rowsOf(user.getId());
        when(codef.delete(anyString(), any())).thenReturn(new AccountResult(false, null, "CF-09999"));

        withdraw(tokens.accessToken(), "{\"agreed\":true}").andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("CONN-005"));

        assertThat(users.findById(user.getId())).isPresent();
        assertThat(rowsOf(user.getId())).isEqualTo(before);
        assertThat(count("codef_org_accounts", "id", accounts[0].getId())).isEqualTo(1);

        when(codef.delete(anyString(), any())).thenReturn(new AccountResult(true, "", null));
        withdraw(tokens.accessToken(), "{\"agreed\":true}").andExpect(status().isOk());
        assertThat(users.findById(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("카드 동기화가 진행 중이면 409 CONN-007이고 아무것도 지우지 않는다")
    void blockedWhileSyncing() throws Exception {
        CodefOrgAccount[] accounts = fill(user);
        syncJobs.save(CodefSyncJob.startCardList(accounts[0], "running", LocalDateTime.now(CLOCK).minusMinutes(1)));
        int before = rowsOf(user.getId());

        withdraw(tokens.accessToken(), "{\"agreed\":true}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONN-007"));

        assertThat(users.findById(user.getId())).isPresent();
        assertThat(rowsOf(user.getId())).isEqualTo(before);
        verify(codef, never()).delete(anyString(), any());
    }

    @Test
    @DisplayName("동의하지 않았거나 본문이 없으면 400이고 아무것도 지우지 않는다")
    void requiresAgreement() throws Exception {
        fill(user);
        int before = rowsOf(user.getId());

        withdraw(tokens.accessToken(), "{\"agreed\":false}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-400"));
        withdraw(tokens.accessToken(), "{}").andExpect(status().isBadRequest());
        withdraw(tokens.accessToken(), null).andExpect(status().isBadRequest());

        assertThat(users.findById(user.getId())).isPresent();
        assertThat(rowsOf(user.getId())).isEqualTo(before);
        verify(codef, never()).delete(anyString(), any());
    }

    @Test
    @DisplayName("로그인 없이는 401이다")
    void requiresLogin() throws Exception {
        withdraw(null, "{\"agreed\":true}").andExpect(status().isUnauthorized());

        assertThat(users.findById(user.getId())).isPresent();
    }

}
