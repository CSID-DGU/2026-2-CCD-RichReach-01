package com.richreach.plan;

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
import com.richreach.transaction.dto.CollectedTransaction;
import com.richreach.transaction.service.TransactionStoreService;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
 * 카드별 상세({@code GET /api/plans/{planId}/card-targets})와 업종별 상세({@code /categories/{category}})를 보안 필터와 실제
 * MySQL로 확인한다. 목표와 사용 금액이 플랜과 거래에서 합쳐지는지, 알 수 없는 값이 0이 아니라 null로 내려가는지, 다른 사용자의
 * 플랜이 보이지 않는지를 본다. (혜택 규칙으로 구간과 한도를 계산하는 부분은 단위 테스트가 본다)
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PlanCardTargetsApiTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    /** 플랜 대상 월. 사용 금액은 플랜의 월 기준이라 날짜를 이 달과 전달에 고정하면 시계와 상관없이 같은 결과가 나온다. */
    private static final YearMonth MONTH = YearMonth.of(2026, 12);

    @Autowired
    private MockMvc mockMvc;
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
    private MonthlyPlanRepository plans;
    @Autowired
    private PlanCardAllocationRepository allocations;
    @Autowired
    private JdbcTemplate jdbc;

    private User user;
    private String accessToken;
    private long cardA;
    private long cardB;
    private long planId;

    @BeforeEach
    void setUp() {
        user = newUser();
        accessToken = authTokenService.issue(user.getId()).accessToken();
        cardA = newCard(user, "테스트 카드A");
        cardB = newCard(user, "테스트 카드B");
        // 플랜: 카드A는 외식 30만원 + 카페 5만원, 카드B는 카페 10만원
        planId = savePlan(user, row(SpendingCategory.DINING, cardA, 300_000, 6_000), row(SpendingCategory.CAFE, cardA, 50_000, 1_500),
            row(SpendingCategory.CAFE, cardB, 100_000, 3_000));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from plan_card_allocations");
        jdbc.update("delete from monthly_plans");
        jdbc.update("delete from spending_profiles");
        jdbc.update("delete from card_transactions");
        jdbc.update("delete from performance_exclusions where source_condition_id like 't273-%'");
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
        return users.save(new User("plancard" + n + "@example.com", "hash", "김리치", "010%08d".formatted(8000000 + n),
            "REAL", LocalDateTime.now()));
    }

    /** 카드 상품에 매칭되고 분석 대상으로 선택된 보유카드. 연결은 사용자당 하나라서 이미 있으면 그 연결에 카드사 계정을 더한다. */
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

    private long savePlan(User owner, Object[]... rows) {
        SpendingProfile profile = profiles.save(SpendingProfile.create(owner.getId(), MONTH,
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30)));
        MonthlyPlan plan = plans.save(MonthlyPlan.create(owner.getId(), profile.getId(), MONTH, PlanStrategy.CP_SAT, 1,
            450_000, 10_500));
        for (Object[] row : rows) {
            allocations.save(PlanCardAllocation.create(plan.getId(), (Long) row[1], (SpendingCategory) row[0],
                ((Number) row[2]).longValue(), ((Number) row[3]).longValue()));
        }
        return plan.getId();
    }

    private Object[] row(SpendingCategory category, long card, long amount, long benefit) {
        return new Object[] {category, card, amount, benefit};
    }

    private void spend(long cardId, LocalDate date, SpendingCategory category, long amount) {
        store.saveCollected(user.getId(), cardId, List.of(new CollectedTransaction("AP-" + UUID.randomUUID(),
            date.atTime(12, 0), "가맹점", amount, 0, 0, false, category.name())));
    }

    private ResultActions api(String token, String url) throws Exception {
        var request = get(url);
        return mockMvc.perform(token == null ? request : request.header("Authorization", "Bearer " + token));
    }

    // ---- 카드별 상세 ----

    @Test
    @DisplayName("카드마다 목표, 이번 달 사용 금액, 남은 목표, 전월 사용액, 업종별 사용 금액이 합쳐지고 목표가 큰 카드부터 나온다")
    void cardTargets() throws Exception {
        spend(cardA, LocalDate.of(2026, 12, 5), SpendingCategory.DINING, 120_000);
        spend(cardA, LocalDate.of(2026, 12, 6), SpendingCategory.TRANSPORT, 30_000);
        spend(cardA, LocalDate.of(2026, 11, 10), SpendingCategory.DINING, 310_000);

        api(accessToken, "/api/plans/" + planId + "/card-targets").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.planId").value(planId))
            .andExpect(jsonPath("$.data.month").value("2026-12"))
            .andExpect(jsonPath("$.data.cards.length()").value(2))
            .andExpect(jsonPath("$.data.cards[0].cardId").value(cardA))
            .andExpect(jsonPath("$.data.cards[0].cardName").value("테스트 카드A"))
            .andExpect(jsonPath("$.data.cards[0].targetAmount").value(350_000))
            .andExpect(jsonPath("$.data.cards[0].spentAmount").value(150_000))
            .andExpect(jsonPath("$.data.cards[0].remainingTarget").value(200_000))
            .andExpect(jsonPath("$.data.cards[0].expectedBenefit").value(7_500))
            .andExpect(jsonPath("$.data.cards[0].performance.previousMonthSpend").value(310_000))
            .andExpect(jsonPath("$.data.cards[0].performance.currentMonthAmount").value(150_000))
            .andExpect(jsonPath("$.data.cards[0].performance.currentMonthAmountExact").value(false))
            .andExpect(jsonPath("$.data.cards[0].categories[?(@.category=='DINING')].targetAmount").value(300_000))
            .andExpect(jsonPath("$.data.cards[0].categories[?(@.category=='DINING')].spentAmount").value(120_000))
            // 목표가 없는 업종도 이번 달에 썼으면 나온다.
            .andExpect(jsonPath("$.data.cards[0].categories[?(@.category=='TRANSPORT')].targetAmount").value(0))
            .andExpect(jsonPath("$.data.cards[0].categories[?(@.category=='TRANSPORT')].spentAmount").value(30_000))
            .andExpect(jsonPath("$.data.cards[1].cardId").value(cardB))
            .andExpect(jsonPath("$.data.cards[1].spentAmount").value(0))
            .andExpect(jsonPath("$.data.cards[1].remainingTarget").value(100_000));
    }

    @Test
    @DisplayName("혜택 규칙이 없는 카드의 구간은 빈 목록, 월 혜택 한도는 0이 아니라 null이다")
    void unknownValuesAreNullNotZero() throws Exception {
        api(accessToken, "/api/plans/" + planId + "/card-targets").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.cards[0].performance.tiers.length()").value(0))
            .andExpect(jsonPath("$.data.cards[0].performance.appliedTier").doesNotExist())
            .andExpect(jsonPath("$.data.cards[0].performance.nextTier").doesNotExist())
            .andExpect(jsonPath("$.data.cards[0].benefitLimit.monthlyLimit").doesNotExist())
            .andExpect(jsonPath("$.data.cards[0].benefitLimit.remainingAmount").doesNotExist());
    }

    @Test
    @DisplayName("전월실적 산정 제외 조건은 문장으로 내려가고, 혜택 제외 조건은 내려가지 않는다")
    void exclusionNotes() throws Exception {
        long productId = jdbc.queryForObject("select card_product_id from user_cards where id = ?", Long.class, cardA);
        jdbc.update("""
            insert into performance_exclusions (card_product_id, source_condition_id, applies_to, condition_type,
                condition_text, source_scope_id) values (?, 't273-1', 'PERFORMANCE', 'EXCLUSION', '상품권 구입액은 실적에서 제외', 'scope')
            """, productId);
        jdbc.update("""
            insert into performance_exclusions (card_product_id, source_condition_id, applies_to, condition_type,
                condition_text, source_scope_id) values (?, 't273-2', 'BENEFIT', 'EXCLUSION', '해외 가맹점은 혜택 제외', 'scope')
            """, productId);

        api(accessToken, "/api/plans/" + planId + "/card-targets").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.cards[0].performance.exclusionNotes.length()").value(1))
            .andExpect(jsonPath("$.data.cards[0].performance.exclusionNotes[0]").value("상품권 구입액은 실적에서 제외"));
    }

    @Test
    @DisplayName("다른 사용자의 플랜이나 없는 플랜은 404 PLAN-001이고, 로그인이 필요하다")
    void accessControl() throws Exception {
        User other = newUser();
        String otherToken = authTokenService.issue(other.getId()).accessToken();

        api(otherToken, "/api/plans/" + planId + "/card-targets").andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PLAN-001"));
        api(accessToken, "/api/plans/999999/card-targets").andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PLAN-001"));
        api(null, "/api/plans/" + planId + "/card-targets").andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-001"));
    }

    // ---- 업종별 상세 ----

    @Test
    @DisplayName("업종별 상세는 그 업종의 목표, 이번 달 사용 금액, 카드별 금액을 준다")
    void categoryDetail() throws Exception {
        spend(cardA, LocalDate.of(2026, 12, 5), SpendingCategory.CAFE, 20_000);
        spend(cardB, LocalDate.of(2026, 12, 7), SpendingCategory.CAFE, 70_000);

        api(accessToken, "/api/plans/" + planId + "/categories/CAFE").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.category").value("CAFE"))
            .andExpect(jsonPath("$.data.targetAmount").value(150_000))
            .andExpect(jsonPath("$.data.spentAmount").value(90_000))
            .andExpect(jsonPath("$.data.cards[0].cardId").value(cardB))
            .andExpect(jsonPath("$.data.cards[0].targetAmount").value(100_000))
            .andExpect(jsonPath("$.data.cards[0].spentAmount").value(70_000))
            .andExpect(jsonPath("$.data.cards[1].cardId").value(cardA))
            .andExpect(jsonPath("$.data.cards[1].spentAmount").value(20_000));
        // 업종 코드는 대소문자를 구분하지 않는다.
        api(accessToken, "/api/plans/" + planId + "/categories/cafe").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.category").value("CAFE"));
    }

    @Test
    @DisplayName("플랜에 배분이 없는 업종은 오류가 아니라 목표와 혜택이 0이고 카드가 없다. 11개 업종이 아니면 400이다")
    void emptyAndInvalidCategory() throws Exception {
        api(accessToken, "/api/plans/" + planId + "/categories/CULTURE_TRAVEL").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.targetAmount").value(0))
            .andExpect(jsonPath("$.data.expectedBenefit").value(0))
            .andExpect(jsonPath("$.data.cards.length()").value(0));
        api(accessToken, "/api/plans/" + planId + "/categories/NOPE").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    @DisplayName("기존 업종별 사용계획 목록에도 이번 달 사용 금액이 들어간다")
    void categoriesListHasSpentAmount() throws Exception {
        spend(cardA, LocalDate.of(2026, 12, 5), SpendingCategory.DINING, 120_000);

        api(accessToken, "/api/plans/" + planId + "/categories").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.categories[?(@.category=='DINING')].spentAmount").value(120_000))
            .andExpect(jsonPath("$.data.categories[?(@.category=='DINING')].cards[0].spentAmount").value(120_000));
    }

}
