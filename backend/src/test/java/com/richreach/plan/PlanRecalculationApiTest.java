package com.richreach.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.entity.MonthlyPlan;
import com.richreach.plan.entity.PlanCardAllocation;
import com.richreach.plan.entity.PlanChangeReason;
import com.richreach.plan.entity.PlanJob;
import com.richreach.plan.entity.PlanStrategy;
import com.richreach.plan.entity.SolverStatus;
import com.richreach.plan.entity.SpendingProfile;
import com.richreach.plan.entity.SpendingProfileItem;
import com.richreach.plan.optimizer.RollingPlanReplanner;
import com.richreach.plan.optimizer.RollingPlanResult;
import com.richreach.plan.optimizer.RollingPlanResult.RemainingAllocation;
import com.richreach.plan.repository.MonthlyPlanRepository;
import com.richreach.plan.repository.PlanCardAllocationRepository;
import com.richreach.plan.repository.PlanJobRepository;
import com.richreach.plan.repository.SpendingProfileItemRepository;
import com.richreach.plan.repository.SpendingProfileRepository;
import com.richreach.plan.service.PlanRecalculationService;
import com.richreach.plan.service.PlanRecalculationWorker;
import com.richreach.transaction.SelectedCards;
import com.richreach.transaction.dto.CollectedTransaction;
import com.richreach.transaction.service.TransactionStoreService;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 승인 거래 반영 후 월간 플랜 재계산을 보안 필터와 실제 MySQL로 확인한다. 재계산 엔진(CP-SAT)은 가짜로 바꿔서 정해진 결과를
 * 돌려주게 하고, 이 테스트는 입력(승인 거래 스냅샷) 조립, 새 플랜 버전과 변경 기록 저장, 작업 상태, 중복 실행 방지를 본다.
 * (엔진의 계산은 {@code RollingPlanReplannerTest}가 본다)
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PlanRecalculationApiTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    /** 서비스와 테스트가 같은 시각을 보도록 고정한 시계. 달이 바뀌는 시각에 실행해도 이번 달 판정이 어긋나지 않는다. */
    private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneId.systemDefault());
    /** 재계산은 이번 달 플랜만 대상이다. */
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
    private PlanRecalculationWorker worker;
    @Autowired
    private TransactionStoreService store;
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
    @Autowired
    private TransactionTemplate tx;

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
        jdbc.update("delete from card_transactions");
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
        return users.save(new User("planrecalc" + n + "@example.com", "hash", "김리치", "010%08d".formatted(6000000 + n),
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

    /** 이번 달에 한 번 승인된 거래를 저장한다. */
    private void spend(long cardId, SpendingCategory category, long amount) {
        store.saveCollected(user.getId(), cardId, List.of(new CollectedTransaction("AP-" + UUID.randomUUID(),
            LocalDate.now(CLOCK).atTime(9, 0), "가맹점", amount, 0, 0, false, category.name())));
    }

    private static RollingPlanResult success(RemainingAllocation... remaining) {
        return new RollingPlanResult(true, Map.of(), Map.of(), List.of(remaining), 0, 0, SolverStatus.OPTIMAL,
            List.of(), null);
    }

    /** 카드A로 외식 5만원을 쓴 뒤, 남은 외식은 카드B로 옮기는 재계산 결과. */
    private void engineMovesDiningToCardB() {
        when(replanner.replan(any())).thenReturn(success(
            new RemainingAllocation(cardB, SpendingCategory.DINING, 250_000),
            new RemainingAllocation(cardB, SpendingCategory.CAFE, 100_000)));
    }

    private List<PlanJob> recalculationJobs() {
        return jobs.findAll().stream().filter(job -> PlanJob.TYPE_RECALCULATE.equals(job.getJobType())).toList();
    }

    private ResultActions api(String token, String url) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", "Bearer " + token));
    }

    // ---- 재계산과 새 플랜 버전 ----

    @Test
    @DisplayName("승인 거래를 반영해 남은 기간을 다시 계산하면 새 플랜 버전이 보이고, 이미 쓴 금액이 고정된 이번 달 전체 목표로 저장된다")
    void recalculationSavesANewVersion() throws Exception {
        spend(cardA, SpendingCategory.DINING, 50_000);
        engineMovesDiningToCardB();

        recalculation.requestAndRun(user.getId(), MONTH, PlanChangeReason.TRANSACTION_SYNCED);

        List<PlanJob> done = recalculationJobs();
        assertThat(done).hasSize(1);
        assertThat(done.get(0).getStatus()).isEqualTo(PlanJob.STATUS_COMPLETED);
        MonthlyPlan latest = plans.findFirstByUserIdAndPlanMonthAndStrategyOrderByVersionDesc(user.getId(),
            MONTH.toString(), PlanStrategy.CP_SAT).orElseThrow();
        assertThat(latest.getVersion()).isEqualTo(2);
        assertThat(latest.getId()).isNotEqualTo(basePlanId);
        assertThat(done.get(0).getPlanId()).isEqualTo(latest.getId());
        // 카드A는 쓴 5만원, 카드B는 외식 25만원과 카페 10만원. 이번 달 전체 목표이므로 합이 예산(40만원)과 같다.
        api(accessToken, "/api/plans?month=" + MONTH).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.planId").value(latest.getId()))
            .andExpect(jsonPath("$.data.cardTargets[0].cardId").value(cardB))
            .andExpect(jsonPath("$.data.cardTargets[0].targetAmount").value(350_000))
            .andExpect(jsonPath("$.data.cardTargets[1].cardId").value(cardA))
            .andExpect(jsonPath("$.data.cardTargets[1].targetAmount").value(50_000))
            .andExpect(jsonPath("$.data.recalculationJobId").doesNotExist());
        // 이전 버전은 지우지 않는다.
        assertThat(plans.findById(basePlanId)).isPresent();
    }

    @Test
    @DisplayName("플랜이 바뀌면 변경 기록이 쌓이고, 금액·우선 카드·예상 혜택 전후와 고정된 이유 문구를 조회할 수 있다")
    void changeHistoryIsRecorded() throws Exception {
        spend(cardA, SpendingCategory.DINING, 50_000);
        engineMovesDiningToCardB();

        recalculation.requestAndRun(user.getId(), MONTH, PlanChangeReason.TRANSACTION_SYNCED);

        long latestPlanId = plans.findFirstByUserIdAndPlanMonthAndStrategyOrderByVersionDesc(user.getId(),
            MONTH.toString(), PlanStrategy.CP_SAT).orElseThrow().getId();
        api(accessToken, "/api/plans/" + latestPlanId + "/changes").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.month").value(MONTH.toString()))
            .andExpect(jsonPath("$.data.changes.length()").value(1))
            .andExpect(jsonPath("$.data.changes[0].previousPlanId").value(basePlanId))
            .andExpect(jsonPath("$.data.changes[0].planId").value(latestPlanId))
            .andExpect(jsonPath("$.data.changes[0].reasonCode").value("TRANSACTION_SYNCED"))
            .andExpect(jsonPath("$.data.changes[0].reasonText").value(PlanChangeReason.TRANSACTION_SYNCED.text()))
            .andExpect(jsonPath("$.data.changes[0].previousBenefit").value(5_000))
            .andExpect(jsonPath("$.data.changes[0].confirmed").value(false))
            // 외식의 우선 카드가 A에서 B로 바뀌었다.
            .andExpect(jsonPath("$.data.changes[0].items[0].type").value("PRIORITY"))
            .andExpect(jsonPath("$.data.changes[0].items[0].category").value("DINING"))
            .andExpect(jsonPath("$.data.changes[0].items[0].cardId").value(cardB))
            .andExpect(jsonPath("$.data.changes[0].items[0].cardName").value("테스트 카드B"))
            .andExpect(jsonPath("$.data.changes[0].items[0].previousCardId").value(cardA))
            .andExpect(jsonPath("$.data.changes[0].items[0].previousCardName").value("테스트 카드A"))
            // 카드A의 외식 목표는 30만원에서 5만원으로, 카드B의 외식 목표는 0에서 25만원으로 바뀌었다.
            .andExpect(jsonPath("$.data.changes[0].items[?(@.type=='AMOUNT' && @.cardId==" + cardA
                + ")].previousAmount").value(300_000))
            .andExpect(jsonPath("$.data.changes[0].items[?(@.type=='AMOUNT' && @.cardId==" + cardA
                + ")].currentAmount").value(50_000))
            .andExpect(jsonPath("$.data.changes[0].items[?(@.type=='AMOUNT' && @.cardId==" + cardB
                + " && @.category=='DINING')].currentAmount").value(250_000));
    }

    @Test
    @DisplayName("배분이 달라지지 않았으면 새 버전도 변경 기록도 만들지 않고 작업은 지금 플랜으로 완료된다")
    void noChangeCreatesNoVersion() throws Exception {
        when(replanner.replan(any())).thenReturn(success(
            new RemainingAllocation(cardA, SpendingCategory.DINING, 300_000),
            new RemainingAllocation(cardB, SpendingCategory.CAFE, 100_000)));

        recalculation.requestAndRun(user.getId(), MONTH, PlanChangeReason.CATEGORY_EDITED);

        assertThat(plans.findByUserIdAndPlanMonth(user.getId(), MONTH.toString())).hasSize(1);
        PlanJob job = recalculationJobs().get(0);
        assertThat(job.getStatus()).isEqualTo(PlanJob.STATUS_COMPLETED);
        assertThat(job.getPlanId()).isEqualTo(basePlanId);
        api(accessToken, "/api/plans/" + basePlanId + "/changes").andExpect(jsonPath("$.data.changes.length()").value(0));
    }

    @Test
    @DisplayName("계산에 실패하면 작업만 실패로 남고 이전 플랜은 그대로이며 변경 기록도 없다")
    void failureKeepsThePreviousPlan() throws Exception {
        when(replanner.replan(any())).thenReturn(new RollingPlanResult(false, Map.of(), Map.of(), List.of(), 0, 0,
            SolverStatus.INFEASIBLE, List.of(), "조건이 충돌합니다."));

        recalculation.requestAndRun(user.getId(), MONTH, PlanChangeReason.TRANSACTION_SYNCED);

        PlanJob job = recalculationJobs().get(0);
        assertThat(job.getStatus()).isEqualTo(PlanJob.STATUS_FAILED);
        assertThat(job.getFailureCode()).isEqualTo("SOLVER_FAILED");
        assertThat(plans.findByUserIdAndPlanMonth(user.getId(), MONTH.toString())).hasSize(1);
        api(accessToken, "/api/plans?month=" + MONTH).andExpect(jsonPath("$.data.planId").value(basePlanId));
        api(accessToken, "/api/plans/jobs/" + job.getJobId()).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.failureCode").value("SOLVER_FAILED"));
    }

    @Test
    @DisplayName("그 달의 플랜이 없거나 이번 달이 아니면 작업을 만들지 않는다")
    void ignoresMonthsWithoutAPlanOrThePast() {
        recalculation.requestAndRun(user.getId(), MONTH.minusMonths(1), PlanChangeReason.TRANSACTION_SYNCED);
        assertThat(recalculationJobs()).isEmpty();

        jdbc.update("delete from monthly_plans");
        recalculation.requestAndRun(user.getId(), MONTH, PlanChangeReason.TRANSACTION_SYNCED);
        assertThat(recalculationJobs()).isEmpty();
        verify(replanner, times(0)).replan(any());
    }

    // ---- 중복 실행 방지 ----

    @Test
    @DisplayName("재계산이 진행 중일 때 거래가 또 바뀌면 새 작업을 만들지 않고 진행 중인 작업에 한 번 더 계산 표시만 한다")
    void concurrentRequestOnlyMarksTheRunningJob() {
        PlanJob running = jobs.save(PlanJob.startRecalculation(user.getId(), UUID.randomUUID().toString(),
            MONTH.toString(), 400_000, "DINING:300000,CAFE:100000", "0".repeat(64),
            PlanChangeReason.TRANSACTION_SYNCED, LocalDateTime.now(CLOCK).withNano(0)));

        recalculation.requestAndRun(user.getId(), MONTH, PlanChangeReason.CATEGORY_EDITED);

        assertThat(recalculationJobs()).hasSize(1);
        assertThat(jobs.findById(running.getId()).orElseThrow().isRerunRequested()).isTrue();
        verify(replanner, times(0)).replan(any());
    }

    @Test
    @DisplayName("끝나기 직전에 한 번 더 계산 표시가 켜져 있으면 최신 거래로 다시 계산한 뒤 완료한다")
    void rerunRequestedWhileRunningRecomputesBeforeCompleting() {
        engineMovesDiningToCardB();
        PlanJob running = jobs.save(PlanJob.startRecalculation(user.getId(), UUID.randomUUID().toString(),
            MONTH.toString(), 400_000, "DINING:300000,CAFE:100000", "0".repeat(64),
            PlanChangeReason.TRANSACTION_SYNCED, LocalDateTime.now(CLOCK).withNano(0)));
        tx.executeWithoutResult(status -> jobs.requestRerun(running.getId()));

        worker.run(running.getId());

        verify(replanner, times(2)).replan(any());
        PlanJob finished = jobs.findById(running.getId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(PlanJob.STATUS_COMPLETED);
        assertThat(finished.isRerunRequested()).isFalse();
    }

    @Test
    @DisplayName("계산에 실패했는데 대기 중인 재계산 요청이 있으면 실패로 끝내지 않고 다시 계산해서 완료한다")
    void failureWithAPendingRerunIsRecomputed() {
        when(replanner.replan(any())).thenReturn(
            new RollingPlanResult(false, Map.of(), Map.of(), List.of(), 0, 0, SolverStatus.INFEASIBLE, List.of(),
                "일시적인 실패"),
            success(new RemainingAllocation(cardB, SpendingCategory.DINING, 300_000),
                new RemainingAllocation(cardB, SpendingCategory.CAFE, 100_000)));
        PlanJob running = jobs.save(PlanJob.startRecalculation(user.getId(), UUID.randomUUID().toString(),
            MONTH.toString(), 400_000, "DINING:300000,CAFE:100000", "0".repeat(64),
            PlanChangeReason.TRANSACTION_SYNCED, LocalDateTime.now(CLOCK).withNano(0)));
        tx.executeWithoutResult(status -> jobs.requestRerun(running.getId()));

        worker.run(running.getId());

        verify(replanner, times(2)).replan(any());
        PlanJob finished = jobs.findById(running.getId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(PlanJob.STATUS_COMPLETED);
        assertThat(finished.isRerunRequested()).isFalse();
    }

    @Test
    @DisplayName("계산에 실패했고 대기 중인 요청이 없으면 실패로 기록한다")
    void failureWithoutAPendingRerunFails() {
        when(replanner.replan(any())).thenReturn(new RollingPlanResult(false, Map.of(), Map.of(), List.of(), 0, 0,
            SolverStatus.INFEASIBLE, List.of(), "조건이 충돌합니다."));
        PlanJob running = jobs.save(PlanJob.startRecalculation(user.getId(), UUID.randomUUID().toString(),
            MONTH.toString(), 400_000, "DINING:300000,CAFE:100000", "0".repeat(64),
            PlanChangeReason.TRANSACTION_SYNCED, LocalDateTime.now(CLOCK).withNano(0)));

        worker.run(running.getId());

        verify(replanner, times(1)).replan(any());
        PlanJob failed = jobs.findById(running.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(PlanJob.STATUS_FAILED);
        assertThat(failed.getFailureCode()).isEqualTo("SOLVER_FAILED");
    }

    @Test
    @DisplayName("이미 끝난 작업을 다시 실행해도 아무것도 바꾸지 않는다")
    void finishedJobsAreNotRunAgain() {
        PlanJob finished = jobs.save(PlanJob.startRecalculation(user.getId(), UUID.randomUUID().toString(),
            MONTH.toString(), 400_000, "DINING:300000,CAFE:100000", "0".repeat(64),
            PlanChangeReason.TRANSACTION_SYNCED, LocalDateTime.now(CLOCK).withNano(0)));
        tx.executeWithoutResult(status -> jobs.failIfInProgress(finished.getId(), "X", "이미 실패", LocalDateTime.now(CLOCK)));

        worker.run(finished.getId());

        verify(replanner, times(0)).replan(any());
    }

    // ---- 조회와 확인 ----

    @Test
    @DisplayName("재계산이 진행 중이면 이번 달 플랜 조회에 작업 ID가 들어 있다")
    void planResponseShowsTheRunningRecalculation() throws Exception {
        PlanJob running = jobs.save(PlanJob.startRecalculation(user.getId(), UUID.randomUUID().toString(),
            MONTH.toString(), 400_000, "DINING:300000,CAFE:100000", "0".repeat(64),
            PlanChangeReason.TRANSACTION_SYNCED, LocalDateTime.now(CLOCK).withNano(0)));

        api(accessToken, "/api/plans?month=" + MONTH).andExpect(jsonPath("$.data.recalculationJobId")
            .value(running.getJobId()));
        api(accessToken, "/api/plans/jobs/" + running.getJobId()).andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("변경 내용을 확인하면 confirmed가 true가 되고, 다시 확인해도 그대로다. 남의 기록은 PLAN-009다")
    void confirmingAChange() throws Exception {
        spend(cardA, SpendingCategory.DINING, 50_000);
        engineMovesDiningToCardB();
        recalculation.requestAndRun(user.getId(), MONTH, PlanChangeReason.TRANSACTION_SYNCED);
        long latestPlanId = plans.findFirstByUserIdAndPlanMonthAndStrategyOrderByVersionDesc(user.getId(),
            MONTH.toString(), PlanStrategy.CP_SAT).orElseThrow().getId();
        Number changeId = com.jayway.jsonpath.JsonPath.read(api(accessToken, "/api/plans/" + latestPlanId + "/changes")
            .andReturn().getResponse().getContentAsString(), "$.data.changes[0].changeId");

        mockMvc.perform(patch("/api/plans/changes/" + changeId + "/confirm").header("Authorization",
                "Bearer " + accessToken)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.confirmed").value(true));
        mockMvc.perform(patch("/api/plans/changes/" + changeId + "/confirm").header("Authorization",
                "Bearer " + accessToken)).andExpect(jsonPath("$.data.confirmed").value(true));
        api(accessToken, "/api/plans/" + latestPlanId + "/changes")
            .andExpect(jsonPath("$.data.changes[0].confirmed").value(true));

        User other = newUser();
        String otherToken = authTokenService.issue(other.getId()).accessToken();
        mockMvc.perform(patch("/api/plans/changes/" + changeId + "/confirm").header("Authorization",
                "Bearer " + otherToken)).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PLAN-009"));
        api(otherToken, "/api/plans/" + latestPlanId + "/changes").andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PLAN-001"));
    }

    @Test
    @DisplayName("변경 기록 조회와 확인은 로그인이 필요하다 (토큰이 없으면 401 AUTH-001)")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/plans/" + basePlanId + "/changes")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-001"));
        mockMvc.perform(patch("/api/plans/changes/1/confirm")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-001"));
    }

}
