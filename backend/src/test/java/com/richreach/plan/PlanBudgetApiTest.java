package com.richreach.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.entity.MonthlyPlan;
import com.richreach.plan.entity.PlanCardAllocation;
import com.richreach.plan.entity.PlanChangeReason;
import com.richreach.plan.entity.PlanJob;
import com.richreach.plan.entity.PlanStrategy;
import com.richreach.plan.entity.SolverStatus;
import com.richreach.plan.entity.SpendingProfile;
import com.richreach.plan.entity.SpendingProfileItem;
import com.richreach.plan.optimizer.RollingPlanInput;
import com.richreach.plan.optimizer.RollingPlanReplanner;
import com.richreach.plan.optimizer.RollingPlanResult;
import com.richreach.plan.optimizer.RollingPlanResult.RemainingAllocation;
import com.richreach.plan.repository.MonthlyPlanRepository;
import com.richreach.plan.repository.PlanCardAllocationRepository;
import com.richreach.plan.repository.PlanJobRepository;
import com.richreach.plan.repository.SpendingProfileItemRepository;
import com.richreach.plan.repository.SpendingProfileRepository;
import com.richreach.plan.service.PlanRecalculationService;
import com.richreach.transaction.SelectedCards;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

/**
 * 소비예산 수정({@code PUT /api/plans/budget})을 보안 필터와 실제 MySQL로 확인한다. 재계산 엔진(CP-SAT)은 가짜로 바꿔서 정해진
 * 결과를 돌려주게 하고, 이 테스트는 요청 검증, 새 예산이 확정 예산으로 저장되는 시점, 새 플랜 버전과 변경 기록, 중복 요청 처리를 본다.
 * 계산은 별도 스레드에서 실행되므로 작업 상태가 끝날 때까지 기다린다.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PlanBudgetApiTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    /** 서비스와 테스트가 같은 시각을 보도록 고정한 시계. */
    private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneId.systemDefault());
    private static final YearMonth MONTH = YearMonth.now(CLOCK);

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return CLOCK;
        }

    }

    @MockitoBean
    private RollingPlanReplanner replanner;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PlanRecalculationService recalculation;
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
    private SpendingProfileRepository profiles;
    @Autowired
    private SpendingProfileItemRepository profileItems;
    @Autowired
    private MonthlyPlanRepository plans;
    @Autowired
    private PlanCardAllocationRepository allocations;
    @Autowired
    private PlanJobRepository jobs;
    @Autowired
    private JdbcTemplate jdbc;

    private User user;
    private String accessToken;
    private long cardA;
    private long cardB;
    private long basePlanId;

    @BeforeEach
    void setUp() {
        user = newUser();
        accessToken = authTokenService.issue(user.getId()).accessToken();
        cardA = newCard(user, "테스트 카드A");
        cardB = newCard(user, "테스트 카드B");
        // 확정 예산: 외식 30만원, 카페 10만원. 플랜: 카드A가 외식, 카드B가 카페.
        basePlanId = savePlan(user, 1, 5_000, Map.of(SpendingCategory.DINING, 300_000L, SpendingCategory.CAFE, 100_000L),
            new Object[] {SpendingCategory.DINING, cardA, 300_000L}, new Object[] {SpendingCategory.CAFE, cardB, 100_000L});
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from plan_card_allocations");
        jdbc.update("delete from monthly_plans");
        jdbc.update("delete from spending_profile_items");
        jdbc.update("delete from spending_profiles");
        jdbc.update("delete from plan_jobs");
        jdbc.update("delete from user_cards");
        SelectedCards.cleanUp(jdbc);
        orgAccounts.deleteAll();
        connections.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    // ---- 준비 ----

    private User newUser() {
        int n = SEQUENCE.incrementAndGet();
        return users.save(new User("planbudget" + n + "@example.com", "hash", "김리치", "010%08d".formatted(7000000 + n),
            "REAL", LocalDateTime.now(CLOCK)));
    }

    /** 카드 상품에 매칭되고 분석 대상으로 선택된 보유카드. 연결은 사용자당 하나라서 이미 있으면 그 연결에 카드사 계정을 더한다. */
    private long newCard(User owner, String cardName) {
        LocalDateTime now = LocalDateTime.now(CLOCK).withNano(0);
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

    /** 이번 달 플랜 하나(CP_SAT)를 저장한다. 배분은 {업종, 카드, 목표 금액} 순서의 값을 쓴다. */
    private long savePlan(User owner, int version, long benefit, Map<SpendingCategory, Long> budget, Object[]... rows) {
        SpendingProfile profile = profiles.save(SpendingProfile.create(owner.getId(), MONTH,
            MONTH.minusMonths(2).atDay(1), MONTH.minusMonths(1).atEndOfMonth()));
        budget.forEach((category, amount) -> profileItems.save(
            SpendingProfileItem.create(profile.getId(), category, amount, 0, BigDecimal.ONE, BigDecimal.ONE)));
        long total = budget.values().stream().mapToLong(Long::longValue).sum();
        MonthlyPlan plan = plans.save(MonthlyPlan.create(owner.getId(), profile.getId(), MONTH, PlanStrategy.CP_SAT,
            version, total, benefit));
        for (Object[] row : rows) {
            allocations.save(PlanCardAllocation.create(plan.getId(), (Long) row[1], (SpendingCategory) row[0],
                ((Number) row[2]).longValue(), 0));
        }
        return plan.getId();
    }

    private static RollingPlanResult success(RemainingAllocation... remaining) {
        return new RollingPlanResult(true, Map.of(), Map.of(), List.of(remaining), 0, 0, SolverStatus.OPTIMAL,
            List.of(), null);
    }

    /** 요청한 예산(외식 20만원, 카페 15만원)에 맞춰 카드A가 외식, 카드B가 카페를 쓰는 결과. */
    private void engineFollowsTheNewBudget() {
        when(replanner.replan(any())).thenReturn(success(
            new RemainingAllocation(cardA, SpendingCategory.DINING, 200_000),
            new RemainingAllocation(cardB, SpendingCategory.CAFE, 150_000)));
    }

    private static final String NEW_BUDGET = """
        {"month": "%s", "budget": {"total": 350000, "byCategory": [
          {"category": "DINING", "amount": 200000}, {"category": "CAFE", "amount": 150000}]}}
        """.formatted(MONTH);

    private ResultActions putBudget(String token, String body) throws Exception {
        var request = put("/api/plans/budget").contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(token == null ? request : request.header("Authorization", "Bearer " + token));
    }

    private ResultActions api(String url) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", "Bearer " + accessToken));
    }

    /** 작업이 진행 중이 아니게 될 때까지 최대 10초 기다리고 마지막 상태를 돌려준다. */
    private String awaitFinished(String jobId) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        String last = null;
        while (System.currentTimeMillis() < deadline) {
            String body = api("/api/plans/jobs/" + jobId).andReturn().getResponse().getContentAsString();
            last = JsonPath.read(body, "$.data.status");
            if (!"IN_PROGRESS".equals(last)) {
                return last;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("작업이 끝나지 않았습니다. 마지막 상태: " + last);
    }

    private String startAndGetJobId(String body) throws Exception {
        String response = putBudget(accessToken, body).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.jobId");
    }

    private MonthlyPlan latestPlan() {
        return plans.findFirstByUserIdAndPlanMonthAndStrategyOrderByVersionDesc(user.getId(), MONTH.toString(),
            PlanStrategy.CP_SAT).orElseThrow();
    }

    // ---- 예산 수정 ----

    @Test
    @DisplayName("예산을 고치면 새 예산으로 계산해서 새 플랜 버전이 보이고, 새 예산이 확정 예산(소비 프로필)으로 저장된다")
    void updatesTheBudgetAndCreatesANewVersion() throws Exception {
        engineFollowsTheNewBudget();

        String jobId = startAndGetJobId(NEW_BUDGET);

        assertThat(awaitFinished(jobId)).isEqualTo("COMPLETED");
        // 엔진에는 새 예산이 넘어갔다.
        ArgumentCaptor<RollingPlanInput> input = ArgumentCaptor.forClass(RollingPlanInput.class);
        verify(replanner, atLeastOnce()).replan(input.capture());
        assertThat(input.getValue().confirmedBudgetByCategory()).containsEntry(SpendingCategory.DINING, 200_000L)
            .containsEntry(SpendingCategory.CAFE, 150_000L);

        MonthlyPlan latest = latestPlan();
        assertThat(latest.getVersion()).isEqualTo(2);
        assertThat(latest.getId()).isNotEqualTo(basePlanId);
        assertThat(profileItems.findByProfileId(latest.getProfileId())).extracting(SpendingProfileItem::getCategory,
                SpendingProfileItem::getExpectedAmount)
            .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(SpendingCategory.DINING, 200_000L),
                org.assertj.core.groups.Tuple.tuple(SpendingCategory.CAFE, 150_000L));
        api("/api/plans?month=" + MONTH).andExpect(jsonPath("$.data.planId").value(latest.getId()))
            .andExpect(jsonPath("$.data.cardTargets[0].cardId").value(cardA))
            .andExpect(jsonPath("$.data.cardTargets[0].targetAmount").value(200_000))
            .andExpect(jsonPath("$.data.cardTargets[1].cardId").value(cardB))
            .andExpect(jsonPath("$.data.cardTargets[1].targetAmount").value(150_000));
        // 이전 플랜은 지우지 않는다.
        assertThat(plans.findById(basePlanId)).isPresent();
    }

    @Test
    @DisplayName("예산 수정은 변경 기록에 BUDGET_CHANGED와 고정 문구, 바뀐 금액을 남긴다")
    void recordsTheBudgetChange() throws Exception {
        engineFollowsTheNewBudget();

        assertThat(awaitFinished(startAndGetJobId(NEW_BUDGET))).isEqualTo("COMPLETED");

        api("/api/plans/" + latestPlan().getId() + "/changes").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.changes.length()").value(1))
            .andExpect(jsonPath("$.data.changes[0].reasonCode").value("BUDGET_CHANGED"))
            .andExpect(jsonPath("$.data.changes[0].reasonText").value(PlanChangeReason.BUDGET_CHANGED.text()))
            .andExpect(jsonPath("$.data.changes[0].previousPlanId").value(basePlanId))
            .andExpect(jsonPath("$.data.changes[0].items[?(@.type=='AMOUNT' && @.cardId==" + cardA
                + ")].previousAmount").value(300_000))
            .andExpect(jsonPath("$.data.changes[0].items[?(@.type=='AMOUNT' && @.cardId==" + cardA
                + ")].currentAmount").value(200_000))
            .andExpect(jsonPath("$.data.changes[0].items[?(@.type=='AMOUNT' && @.cardId==" + cardB
                + ")].currentAmount").value(150_000));
    }

    @Test
    @DisplayName("이후 거래 반영 재계산도 수정한 새 예산을 기준으로 한다")
    void laterRecalculationsUseTheNewBudget() throws Exception {
        engineFollowsTheNewBudget();
        assertThat(awaitFinished(startAndGetJobId(NEW_BUDGET))).isEqualTo("COMPLETED");

        recalculation.requestAndRun(user.getId(), MONTH, PlanChangeReason.TRANSACTION_SYNCED);

        ArgumentCaptor<RollingPlanInput> input = ArgumentCaptor.forClass(RollingPlanInput.class);
        verify(replanner, times(2)).replan(input.capture());
        assertThat(input.getAllValues().get(1).confirmedBudgetByCategory())
            .containsEntry(SpendingCategory.DINING, 200_000L).containsEntry(SpendingCategory.CAFE, 150_000L);
    }

    @Test
    @DisplayName("예산만 바뀌고 배분이 같아도 새 버전과 새 예산이 저장된다")
    void budgetOnlyChangeStillCreatesAVersion() throws Exception {
        // 엔진이 지금과 같은 배분을 돌려줘도, 예산이 달라졌으니 확정 예산은 새로 남아야 한다.
        when(replanner.replan(any())).thenReturn(success(
            new RemainingAllocation(cardA, SpendingCategory.DINING, 300_000),
            new RemainingAllocation(cardB, SpendingCategory.CAFE, 100_000)));

        assertThat(awaitFinished(startAndGetJobId(NEW_BUDGET))).isEqualTo("COMPLETED");

        MonthlyPlan latest = latestPlan();
        assertThat(latest.getVersion()).isEqualTo(2);
        assertThat(profileItems.findByProfileId(latest.getProfileId())).extracting(SpendingProfileItem::getExpectedAmount)
            .containsExactlyInAnyOrder(200_000L, 150_000L);
    }

    @Test
    @DisplayName("지금과 같은 예산이고 배분도 같으면 새 버전을 만들지 않고 작업은 지금 플랜으로 완료된다")
    void sameBudgetCreatesNoVersion() throws Exception {
        when(replanner.replan(any())).thenReturn(success(
            new RemainingAllocation(cardA, SpendingCategory.DINING, 300_000),
            new RemainingAllocation(cardB, SpendingCategory.CAFE, 100_000)));
        String sameBudget = """
            {"month": "%s", "budget": {"total": 400000, "byCategory": [
              {"category": "DINING", "amount": 300000}, {"category": "CAFE", "amount": 100000}]}}
            """.formatted(MONTH);

        String jobId = startAndGetJobId(sameBudget);

        assertThat(awaitFinished(jobId)).isEqualTo("COMPLETED");
        assertThat(plans.findByUserIdAndPlanMonth(user.getId(), MONTH.toString())).hasSize(1);
        api("/api/plans/jobs/" + jobId).andExpect(jsonPath("$.data.planId").value(basePlanId));
    }

    @Test
    @DisplayName("계산에 실패하면 이전 플랜과 이전 예산이 그대로 남고, 새 예산은 저장되지 않는다")
    void failureKeepsThePreviousPlanAndBudget() throws Exception {
        when(replanner.replan(any())).thenReturn(new RollingPlanResult(false, Map.of(), Map.of(), List.of(), 0, 0,
            SolverStatus.INFEASIBLE, List.of(), "조건이 충돌합니다."));

        String jobId = startAndGetJobId(NEW_BUDGET);

        assertThat(awaitFinished(jobId)).isEqualTo("FAILED");
        assertThat(plans.findByUserIdAndPlanMonth(user.getId(), MONTH.toString())).hasSize(1);
        assertThat(profiles.count()).isEqualTo(1);
        api("/api/plans?month=" + MONTH).andExpect(jsonPath("$.data.planId").value(basePlanId));
        api("/api/plans/jobs/" + jobId).andExpect(jsonPath("$.data.failureCode").value("SOLVER_FAILED"));
    }

    // ---- 요청 검증 ----

    @Test
    @DisplayName("로그인이 필요하다 (토큰이 없으면 401 AUTH-001)")
    void requiresLogin() throws Exception {
        putBudget(null, NEW_BUDGET).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH-001"));
    }

    @Test
    @DisplayName("예산이 올바르지 않으면 400 PLAN-003이고, 월이 이번 달이 아니면 400 COMMON-400이다")
    void validation() throws Exception {
        String mismatch = """
            {"month": "%s", "budget": {"total": 500000, "byCategory": [
              {"category": "DINING", "amount": 200000}, {"category": "CAFE", "amount": 150000}]}}
            """.formatted(MONTH);
        putBudget(accessToken, mismatch).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLAN-003"));

        String pastMonth = NEW_BUDGET.replace(MONTH.toString(), MONTH.minusMonths(1).toString());
        putBudget(accessToken, pastMonth).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-400"));
        putBudget(accessToken, NEW_BUDGET.replace(MONTH.toString(), "2026-13")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-400"));
        assertThat(jobs.count()).isZero();
    }

    @Test
    @DisplayName("그 달의 플랜이 없으면 404 PLAN-001이다")
    void noPlan() throws Exception {
        jdbc.update("delete from monthly_plans");

        putBudget(accessToken, NEW_BUDGET).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PLAN-001"));
    }

    @Test
    @DisplayName("다른 재계산이 진행 중이면 409 PLAN-005이고 새 작업을 만들지 않는다")
    void rejectsWhileARecalculationIsRunning() throws Exception {
        jobs.save(PlanJob.startRecalculation(user.getId(), UUID.randomUUID().toString(), MONTH.toString(), 400_000,
            "DINING:300000,CAFE:100000", "0".repeat(64), PlanChangeReason.TRANSACTION_SYNCED,
            LocalDateTime.now(CLOCK).withNano(0)));

        putBudget(accessToken, NEW_BUDGET).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PLAN-005"));
        assertThat(jobs.count()).isEqualTo(1);
    }

}
