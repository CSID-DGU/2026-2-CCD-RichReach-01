package com.richreach.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.richreach.TestcontainersConfiguration;
import com.richreach.auth.repository.RefreshTokenRepository;
import com.richreach.auth.service.AuthTokenService;
import com.richreach.card.dto.CollectedCard;
import com.richreach.card.repository.OwnedCardRepository;
import com.richreach.card.service.OwnedCardService;
import com.richreach.codef.entity.CardCompany;
import com.richreach.codef.entity.CodefConnection;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.repository.CodefConnectionRepository;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.transaction.SelectedCards;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 플랜 생성 요청과 상태 조회 API를 보안 필터와 실제 MySQL로 확인한다. 접수(검증, 분석 대상 카드, 이미 있는 달), 멱등성(같은 요청은 같은
 * 작업, 다른 요청은 충돌), 시간 초과, 사용자 격리, 실행 결과(저장된 프로필·플랜·배분)를 본다.
 *
 * <p>실행기는 가짜로 바꿔서, 작업을 바로 실행하지 않고 쌓아 두었다가 테스트가 원하는 때 실행한다. 그래서 "진행 중인 작업"이 있는
 * 상태를 만들 수 있다.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PlanGenerationApiTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String MONTH = "2026-12";

    @MockitoBean(name = "planJobExecutor")
    private ThreadPoolTaskExecutor executor;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OwnedCardService ownedCardService;
    @Autowired
    private OwnedCardRepository ownedCards;
    @Autowired
    private UserRepository users;
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

    private final List<Runnable> queued = new ArrayList<>();
    private User user;
    private String accessToken;
    private long card;

    @BeforeEach
    void setUp() {
        queued.clear();
        doAnswer(invocation -> {
            queued.add(invocation.getArgument(0));
            return null;
        }).when(executor).execute(any(Runnable.class));
        user = newUser();
        accessToken = authTokenService.issue(user.getId()).accessToken();
        card = newCard(user, "테스트 카드A");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from plan_jobs");
        jdbc.update("delete from plan_card_allocations");
        jdbc.update("delete from monthly_plans");
        jdbc.update("delete from spending_profile_items");
        jdbc.update("delete from spending_profiles");
        jdbc.update("delete from card_transactions");
        jdbc.update("delete from user_cards");
        SelectedCards.cleanUp(jdbc);
        orgAccounts.deleteAll();
        connections.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    private User newUser() {
        int n = SEQUENCE.incrementAndGet();
        return users.save(new User("planjob" + n + "@example.com", "hash", "김리치", "010%08d".formatted(5000000 + n),
            "REAL", LocalDateTime.now()));
    }

    /** 분석 대상으로 고른 보유카드. 연결은 사용자당 하나라서 이미 있으면 그 연결에 카드사 계정을 더한다. */
    private long newCard(User owner, String cardName) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Optional<CodefConnection> existing = connections.findByUserId(owner.getId());
        boolean first = existing.isEmpty();
        CodefConnection connection = existing.orElseGet(() -> connections.save(
            new CodefConnection(owner, "test-connected-id-" + owner.getId(), "DEMO", now, now, now, now)));
        CodefOrgAccount account = orgAccounts.save(CodefOrgAccount.connected(connection, first ? "0301" : "0306", now));
        ownedCardService.saveSynced(owner.getId(), account.getId(), first ? CardCompany.KB : CardCompany.SHINHAN,
            List.of(new CollectedCard(cardName, first ? "1111" : "2222", "check")));
        long cardId = ownedCards.findByOrgAccountId(account.getId()).get(0).getId();
        SelectedCards.select(jdbc, cardId);
        return cardId;
    }

    private static String body(String month, long total, String... categoryAmounts) {
        StringBuilder items = new StringBuilder();
        for (String item : categoryAmounts) {
            String[] pair = item.split(":");
            if (items.length() > 0) {
                items.append(',');
            }
            items.append("{\"category\":\"").append(pair[0]).append("\",\"amount\":").append(pair[1]).append('}');
        }
        return "{\"month\":\"" + month + "\",\"budget\":{\"total\":" + total + ",\"byCategory\":[" + items + "]}}";
    }

    private static String validBody() {
        return body(MONTH, 400_000, "DINING:300000", "CAFE:100000");
    }

    private ResultActions create(String token, String json) throws Exception {
        var request = post("/api/plans").contentType(MediaType.APPLICATION_JSON).content(json);
        return mockMvc.perform(token == null ? request : request.header("Authorization", "Bearer " + token));
    }

    private ResultActions create(String json) throws Exception {
        return create(accessToken, json);
    }

    private ResultActions jobStatus(String token, String jobId) throws Exception {
        var request = get("/api/plans/jobs/" + jobId);
        return mockMvc.perform(token == null ? request : request.header("Authorization", "Bearer " + token));
    }

    /** 요청을 보내고 jobId를 꺼낸다. */
    private String start(String json) throws Exception {
        String response = create(json).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.data.jobId");
    }

    /** 쌓아 둔 작업을 모두 실행한다. */
    private void runQueued() {
        List<Runnable> toRun = new ArrayList<>(queued);
        queued.clear();
        toRun.forEach(Runnable::run);
    }

    // ---- 로그인, 요청 검증 ----

    @Test
    @DisplayName("로그인이 필요하다 (토큰이 없으면 401 AUTH-001)")
    void requiresLogin() throws Exception {
        create(null, validBody()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH-001"));
        jobStatus(null, "any").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("월 형식이 틀리면 400 COMMON-400이다")
    void invalidMonth() throws Exception {
        create(body("2026/12", 400_000, "DINING:400000")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    @DisplayName("예산이 올바르지 않으면 400 PLAN-003이다 (합이 다름, 알 수 없는 업종, 음수, 중복, 총예산 0)")
    void invalidBudget() throws Exception {
        for (String json : new String[] {
            body(MONTH, 400_000, "DINING:300000", "CAFE:99999"),
            body(MONTH, 100_000, "NOPE:100000"),
            body(MONTH, 100_000, "DINING:200000", "CAFE:-100000"),
            body(MONTH, 200_000, "DINING:100000", "DINING:100000"),
            body(MONTH, 0, "DINING:0")}) {
            create(json).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLAN-003"));
        }
        assertThat(queued).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from plan_jobs", Long.class)).isZero();
    }

    @Test
    @DisplayName("분석 대상 카드가 없으면 400 PLAN-006이다")
    void noAnalysisCard() throws Exception {
        jdbc.update("update user_cards set selected = false where id = ?", card);

        create(validBody()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLAN-006"));
        assertThat(queued).isEmpty();
    }

    // ---- 시작과 진행 상태 ----

    @Test
    @DisplayName("요청하면 작업을 만들고 jobId를 돌려주며, 실행 전에는 진행 중이고 진행률은 0이다")
    void startsAJobAndReportsInProgress() throws Exception {
        String jobId = start(validBody());

        assertThat(queued).hasSize(1);
        jobStatus(accessToken, jobId).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.progress").value(0))
            .andExpect(jsonPath("$.data.planId").value(nullValue()))
            .andExpect(jsonPath("$.data.failureCode").value(nullValue()));
    }

    @Test
    @DisplayName("없는 jobId와 다른 사용자의 jobId는 똑같이 404 PLAN-002다")
    void unknownOrForeignJobIsNotFound() throws Exception {
        String jobId = start(validBody());
        User other = newUser();
        String otherToken = authTokenService.issue(other.getId()).accessToken();

        jobStatus(accessToken, "no-such-job").andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PLAN-002"));
        jobStatus(otherToken, jobId).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PLAN-002"));
    }

    // ---- 실행 결과 ----

    @Test
    @DisplayName("실행하면 완료되고, 프로필·플랜·카드별 배분이 저장되며 업종마다 배분 합이 예산과 같다")
    void runsAndStoresThePlan() throws Exception {
        long secondCard = newCard(user, "테스트 카드B");
        String jobId = start(validBody());

        runQueued();

        jobStatus(accessToken, jobId).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.progress").value(100))
            .andExpect(jsonPath("$.data.planId").isNumber())
            .andExpect(jsonPath("$.data.failureCode").value(nullValue()));

        // 확정 예산으로 만든 소비 프로필
        assertThat(jdbc.queryForObject("select count(*) from spending_profiles where user_id = ?", Long.class,
            user.getId())).isEqualTo(1);
        Map<String, Long> profileAmounts = new java.util.HashMap<>();
        jdbc.query("select category, expected_amount from spending_profile_items", rs -> {
            profileAmounts.put(rs.getString(1), rs.getLong(2));
        });
        assertThat(profileAmounts).containsOnly(
            org.assertj.core.api.Assertions.entry("DINING", 300_000L), org.assertj.core.api.Assertions.entry("CAFE", 100_000L));

        // 성공한 전략(임시 균등 배분)의 플랜 하나. 구현되지 않은 CP_SAT는 저장되지 않는다.
        List<Map<String, Object>> plans = jdbc.queryForList(
            "select id, strategy, version, total_spend, plan_month from monthly_plans where user_id = ?", user.getId());
        assertThat(plans).hasSize(1);
        assertThat(plans.get(0)).containsEntry("strategy", "EVEN_SPLIT").containsEntry("version", 1)
            .containsEntry("total_spend", 400_000L).containsEntry("plan_month", MONTH);
        long planId = ((Number) plans.get(0).get("id")).longValue();

        // 완료된 작업은 그 플랜을 가리킨다.
        assertThat(jdbc.queryForObject("select plan_id from plan_jobs where user_id = ?", Long.class, user.getId()))
            .isEqualTo(planId);

        // 총소비 불변: 업종마다 카드별 배분 합이 예산과 같다. 카드 두 장이 똑같이 나눠 갖는다.
        Map<String, Long> allocated = new java.util.HashMap<>();
        jdbc.query("select category, sum(target_amount) from plan_card_allocations where plan_id = ? group by category",
            rs -> {
                allocated.put(rs.getString(1), rs.getLong(2));
            }, planId);
        assertThat(allocated).containsOnly(
            org.assertj.core.api.Assertions.entry("DINING", 300_000L), org.assertj.core.api.Assertions.entry("CAFE", 100_000L));
        assertThat(jdbc.queryForList("select distinct user_card_id from plan_card_allocations where plan_id = ?",
            Long.class, planId)).containsExactlyInAnyOrder(card, secondCard);
    }

    @Test
    @DisplayName("플랜이 만들어진 달에 다시 요청하면 409 PLAN-004다")
    void rejectsAMonthThatAlreadyHasAPlan() throws Exception {
        start(validBody());
        runQueued();

        create(validBody()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN-004"));
        assertThat(queued).isEmpty();
    }

    @Test
    @DisplayName("분석 대상 카드가 실행 전에 없어지면 작업은 NO_ANALYSIS_CARD로 실패하고, 그 뒤의 새 요청은 막히지 않는다")
    void failsWhenCardsDisappearBeforeRunning() throws Exception {
        String jobId = start(validBody());
        jdbc.update("update user_cards set selected = false where id = ?", card);

        runQueued();

        jobStatus(accessToken, jobId).andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.failureCode").value("NO_ANALYSIS_CARD"))
            .andExpect(jsonPath("$.data.planId").value(nullValue()));
        assertThat(jdbc.queryForObject("select count(*) from monthly_plans", Long.class)).isZero();

        jdbc.update("update user_cards set selected = true where id = ?", card);
        String retry = start(validBody());
        assertThat(retry).isNotEqualTo(jobId);
        runQueued();
        jobStatus(accessToken, retry).andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    // ---- 멱등성, 충돌 ----

    @Test
    @DisplayName("진행 중인 같은 요청이 다시 오면 새 작업을 만들지 않고 같은 jobId를 돌려준다")
    void sameRequestWhileRunningReturnsTheSameJob() throws Exception {
        String first = start(validBody());
        String again = start(body(MONTH, 400_000, "CAFE:100000", "DINING:300000"));   // 업종 순서가 달라도 같은 요청

        assertThat(again).isEqualTo(first);
        assertThat(queued).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from plan_jobs", Long.class)).isEqualTo(1);

        runQueued();
        assertThat(jdbc.queryForObject("select count(*) from monthly_plans", Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("진행 중인 작업이 있을 때 다른 요청(다른 예산, 다른 월)은 409 PLAN-005다")
    void differentRequestWhileRunningConflicts() throws Exception {
        start(validBody());

        create(body(MONTH, 400_000, "DINING:200000", "CAFE:200000")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PLAN-005"));
        create(body("2027-01", 400_000, "DINING:300000", "CAFE:100000")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PLAN-005"));
        assertThat(queued).hasSize(1);
    }

    @Test
    @DisplayName("다른 사용자의 진행 중인 작업은 내 요청을 막지 않는다")
    void otherUsersJobDoesNotBlock() throws Exception {
        User other = newUser();
        long otherCard = newCard(other, "남의 카드");
        String otherToken = authTokenService.issue(other.getId()).accessToken();
        String otherJob = com.jayway.jsonpath.JsonPath.read(create(otherToken, validBody()).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), "$.data.jobId");

        String mine = start(validBody());

        assertThat(mine).isNotEqualTo(otherJob);
        assertThat(queued).hasSize(2);
        assertThat(otherCard).isNotEqualTo(card);
    }

    // ---- 시간 초과 ----

    @Test
    @DisplayName("제한 시간이 지난 진행 중 작업은 TIMEOUT 실패로 보고되고, 새 요청을 막지 않으며, 늦게 끝난 결과는 반영하지 않는다")
    void timedOutJobIsReportedAsFailedAndDoesNotBlock() throws Exception {
        String stale = start(validBody());
        jdbc.update("update plan_jobs set started_at = ? where job_id = ?", LocalDateTime.now().minusDays(1), stale);

        jobStatus(accessToken, stale).andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.failureCode").value("TIMEOUT"));

        String fresh = start(validBody());
        assertThat(fresh).isNotEqualTo(stale);

        // 늦게 시작된 첫 작업을 실행해도 이미 실패로 보고된 작업이라 아무것도 저장하지 않고, 실패 상태가 뒤집히지 않는다.
        List<Runnable> toRun = new ArrayList<>(queued);
        queued.clear();
        toRun.get(0).run();
        assertThat(jdbc.queryForObject("select count(*) from monthly_plans", Long.class)).isZero();
        jobStatus(accessToken, stale).andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.failureCode").value("TIMEOUT"));

        toRun.get(1).run();
        jobStatus(accessToken, fresh).andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

}
