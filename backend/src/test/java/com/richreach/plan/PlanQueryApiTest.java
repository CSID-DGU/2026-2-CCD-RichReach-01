package com.richreach.plan;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.richreach.plan.entity.PlanStrategy;
import com.richreach.plan.entity.SpendingProfile;
import com.richreach.plan.repository.MonthlyPlanRepository;
import com.richreach.plan.repository.PlanCardAllocationRepository;
import com.richreach.plan.repository.SpendingProfileRepository;
import com.richreach.transaction.SelectedCards;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Base64;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 월간 플랜 조회 API를 보안 필터와 실제 MySQL로 확인한다. 로그인, 월 형식, 플랜이 없을 때, 보여줄 전략 선택, 최신 version,
 * 기존 예상(HABIT)이 없을 때의 null, 업종별 사용계획, 사용자 격리를 본다.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PlanQueryApiTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final YearMonth MONTH = YearMonth.of(2026, 12);

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
    private SpendingProfileRepository profiles;
    @Autowired
    private MonthlyPlanRepository plans;
    @Autowired
    private PlanCardAllocationRepository allocations;
    @Autowired
    private JdbcTemplate jdbc;

    private User user;
    private String accessToken;
    private long cardA;
    private long cardB;

    @BeforeEach
    void setUp() {
        user = newUser();
        accessToken = authTokenService.issue(user.getId()).accessToken();
        cardA = newCard(user, "테스트 카드A");
        cardB = newCard(user, "테스트 카드B");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from plan_card_allocations");
        jdbc.update("delete from monthly_plans");
        jdbc.update("delete from spending_profile_items");
        jdbc.update("delete from spending_profiles");
        jdbc.update("delete from user_cards");
        SelectedCards.cleanUp(jdbc);
        orgAccounts.deleteAll();
        connections.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    private User newUser() {
        int n = SEQUENCE.incrementAndGet();
        return users.save(new User("planquery" + n + "@example.com", "hash", "김리치", "010%08d".formatted(5000000 + n),
            "REAL", LocalDateTime.now()));
    }

    /** 보유카드. 연결은 사용자당 하나라서 이미 있으면 그 연결에 카드사 계정을 더한다. */
    private long newCard(User owner, String cardName) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Optional<CodefConnection> existing = connections.findByUserId(owner.getId());
        boolean first = existing.isEmpty();
        CodefConnection connection = existing.orElseGet(() -> connections.save(
            new CodefConnection(owner, "test-connected-id-" + owner.getId(), "DEMO", now, now, now, now)));
        CodefOrgAccount account = orgAccounts.save(CodefOrgAccount.connected(connection, first ? "0301" : "0306", now));
        ownedCardService.saveSynced(owner.getId(), account.getId(), first ? CardCompany.KB : CardCompany.SHINHAN,
            List.of(new CollectedCard(cardName, first ? "1111" : "2222", "check")));
        return ownedCards.findByOrgAccountId(account.getId()).get(0).getId();
    }

    /** 플랜 하나를 저장한다. 배분은 {업종, 카드, 목표 금액, 혜택} 순서의 값을 쓴다. */
    private long savePlan(User owner, PlanStrategy strategy, int version, long benefit, Object[]... rows) {
        SpendingProfile profile = profiles.save(SpendingProfile.create(owner.getId(), MONTH,
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30)));
        MonthlyPlan plan = plans.save(MonthlyPlan.create(owner.getId(), profile.getId(), MONTH, strategy, version,
            1_000_000, benefit));
        for (Object[] row : rows) {
            allocations.save(PlanCardAllocation.create(plan.getId(), (Long) row[1], (SpendingCategory) row[0],
                ((Number) row[2]).longValue(), ((Number) row[3]).longValue()));
        }
        return plan.getId();
    }

    private Object[] row(SpendingCategory category, long card, long amount, long benefit) {
        return new Object[] {category, card, amount, benefit};
    }

    private ResultActions monthly(String token, String query) throws Exception {
        var request = get("/api/plans" + query);
        return mockMvc.perform(token == null ? request : request.header("Authorization", "Bearer " + token));
    }

    private ResultActions monthly(String month) throws Exception {
        return monthly(accessToken, "?month=" + month);
    }

    private ResultActions categories(String token, long planId) throws Exception {
        var request = get("/api/plans/" + planId + "/categories");
        return mockMvc.perform(token == null ? request : request.header("Authorization", "Bearer " + token));
    }

    // ---- 로그인, 월 검증, 플랜 없음 ----

    @Test
    @DisplayName("로그인이 필요하다 (토큰이 없으면 401 AUTH-001)")
    void requiresLogin() throws Exception {
        monthly(null, "?month=2026-12").andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-001"));
        categories(null, 1L).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH-001"));
    }

    @Test
    @DisplayName("월이 없거나 yyyy-MM 형식이 아니면 400 COMMON-400이다")
    void invalidMonth() throws Exception {
        for (String query : new String[] {"", "?month=", "?month=2026-13", "?month=202612", "?month=abc",
            "?month=%2B10000-01"}) {
            monthly(accessToken, query).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        }
    }

    @Test
    @DisplayName("그 달의 플랜이 없으면 404 PLAN-001이다")
    void noPlan() throws Exception {
        monthly("2026-12").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PLAN-001"));
    }

    // ---- 보여줄 플랜 ----

    @Test
    @DisplayName("플랜과 기존 예상(HABIT)을 비교한 추가 혜택, 카드별 목표 금액(큰 순)을 내려준다")
    void returnsPlanWithBaseline() throws Exception {
        long evenSplit = savePlan(user, PlanStrategy.EVEN_SPLIT, 1, 32_400,
            row(SpendingCategory.DINING, cardA, 100_000, 3_000), row(SpendingCategory.CAFE, cardA, 50_000, 2_000),
            row(SpendingCategory.DINING, cardB, 300_000, 9_000));
        savePlan(user, PlanStrategy.HABIT, 1, 21_600, row(SpendingCategory.DINING, cardA, 450_000, 1_000));

        monthly("2026-12").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.planId").value(evenSplit))
            .andExpect(jsonPath("$.data.month").value("2026-12"))
            .andExpect(jsonPath("$.data.expectedBenefit").value(32_400))
            .andExpect(jsonPath("$.data.baselineBenefit").value(21_600))
            .andExpect(jsonPath("$.data.additionalBenefit").value(10_800))
            .andExpect(jsonPath("$.data.cardTargets", hasSize(2)))
            .andExpect(jsonPath("$.data.cardTargets[0].cardId").value(cardB))
            .andExpect(jsonPath("$.data.cardTargets[0].cardName").value("테스트 카드B"))
            .andExpect(jsonPath("$.data.cardTargets[0].targetAmount").value(300_000))
            .andExpect(jsonPath("$.data.cardTargets[1].cardId").value(cardA))
            .andExpect(jsonPath("$.data.cardTargets[1].targetAmount").value(150_000));
    }

    @Test
    @DisplayName("HABIT 플랜이 없으면 baselineBenefit과 additionalBenefit은 null이다")
    void baselineIsNullWithoutHabit() throws Exception {
        savePlan(user, PlanStrategy.EVEN_SPLIT, 1, 32_400, row(SpendingCategory.DINING, cardA, 100_000, 3_000));

        monthly("2026-12").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.expectedBenefit").value(32_400))
            .andExpect(jsonPath("$.data.baselineBenefit").value(nullValue()))
            .andExpect(jsonPath("$.data.additionalBenefit").value(nullValue()));
    }

    @Test
    @DisplayName("보여줄 플랜은 CP_SAT, GREEDY, EVEN_SPLIT, HABIT 순으로 고른다")
    void choosesByPriority() throws Exception {
        long habit = savePlan(user, PlanStrategy.HABIT, 1, 10_000, row(SpendingCategory.DINING, cardA, 100_000, 1));
        monthly("2026-12").andExpect(jsonPath("$.data.planId").value(habit));

        long evenSplit = savePlan(user, PlanStrategy.EVEN_SPLIT, 1, 20_000,
            row(SpendingCategory.DINING, cardA, 100_000, 1));
        monthly("2026-12").andExpect(jsonPath("$.data.planId").value(evenSplit));

        long greedy = savePlan(user, PlanStrategy.GREEDY, 1, 30_000, row(SpendingCategory.DINING, cardA, 100_000, 1));
        monthly("2026-12").andExpect(jsonPath("$.data.planId").value(greedy));

        long cpSat = savePlan(user, PlanStrategy.CP_SAT, 1, 40_000, row(SpendingCategory.DINING, cardA, 100_000, 1));
        monthly("2026-12").andExpect(jsonPath("$.data.planId").value(cpSat))
            .andExpect(jsonPath("$.data.baselineBenefit").value(10_000))
            .andExpect(jsonPath("$.data.additionalBenefit").value(30_000));
    }

    @Test
    @DisplayName("같은 전략의 플랜이 여럿이면 가장 최신 version을 쓴다")
    void usesLatestVersion() throws Exception {
        savePlan(user, PlanStrategy.EVEN_SPLIT, 1, 10_000, row(SpendingCategory.DINING, cardA, 100_000, 1));
        long v2 = savePlan(user, PlanStrategy.EVEN_SPLIT, 2, 15_000, row(SpendingCategory.DINING, cardA, 100_000, 1));

        monthly("2026-12").andExpect(jsonPath("$.data.planId").value(v2))
            .andExpect(jsonPath("$.data.expectedBenefit").value(15_000));
    }

    @Test
    @DisplayName("다른 달의 플랜은 보이지 않는다")
    void otherMonthIsNotVisible() throws Exception {
        savePlan(user, PlanStrategy.EVEN_SPLIT, 1, 10_000, row(SpendingCategory.DINING, cardA, 100_000, 1));

        monthly("2027-01").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PLAN-001"));
    }

    // ---- 업종별 사용계획 ----

    @Test
    @DisplayName("업종별 사용계획을 업종 순서로, 카드는 금액이 큰 순으로 내려준다")
    void returnsCategories() throws Exception {
        long plan = savePlan(user, PlanStrategy.EVEN_SPLIT, 1, 32_400,
            row(SpendingCategory.CAFE, cardA, 50_000, 2_000), row(SpendingCategory.DINING, cardA, 100_000, 3_000),
            row(SpendingCategory.DINING, cardB, 300_000, 9_000));

        categories(accessToken, plan).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.planId").value(plan))
            .andExpect(jsonPath("$.data.month").value("2026-12"))
            .andExpect(jsonPath("$.data.categories", hasSize(2)))
            .andExpect(jsonPath("$.data.categories[0].category").value("DINING"))
            .andExpect(jsonPath("$.data.categories[0].targetAmount").value(400_000))
            .andExpect(jsonPath("$.data.categories[0].expectedBenefit").value(12_000))
            .andExpect(jsonPath("$.data.categories[0].cards", hasSize(2)))
            .andExpect(jsonPath("$.data.categories[0].cards[0].cardId").value(cardB))
            .andExpect(jsonPath("$.data.categories[0].cards[0].cardName").value("테스트 카드B"))
            .andExpect(jsonPath("$.data.categories[0].cards[0].targetAmount").value(300_000))
            .andExpect(jsonPath("$.data.categories[0].cards[1].cardId").value(cardA))
            .andExpect(jsonPath("$.data.categories[1].category").value("CAFE"))
            .andExpect(jsonPath("$.data.categories[1].targetAmount").value(50_000));
    }

    @Test
    @DisplayName("없는 planId는 404 PLAN-001이다")
    void unknownPlanId() throws Exception {
        categories(accessToken, 999_999L).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PLAN-001"));
    }

    // ---- 사용자 격리 ----

    @Test
    @DisplayName("다른 사용자의 플랜은 월 조회에도, planId로도 보이지 않는다 (404 PLAN-001)")
    void usersAreIsolated() throws Exception {
        User other = newUser();
        long otherCard = newCard(other, "남의 카드");
        long otherPlan = savePlan(other, PlanStrategy.EVEN_SPLIT, 1, 99_000,
            row(SpendingCategory.DINING, otherCard, 100_000, 1));

        monthly("2026-12").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PLAN-001"));
        categories(accessToken, otherPlan).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PLAN-001"));
    }

}
