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
import java.time.ZoneId;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 소비 패턴 분석과 월 예산 추천 API를 보안 필터와 실제 MySQL로 확인한다. 로그인, 기간 값 검증, 소비 비율과 예산의 합계,
 * 분석 대상 카드만 보는지, 취소·환불 반영, 거래가 없을 때와 부분 데이터, 사용자 격리를 본다.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PlanAnalysisApiTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    /** 테스트와 서비스가 같은 시각을 보도록 고정한 시계. */
    private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneId.systemDefault());

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return CLOCK;
        }

    }

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
    private JdbcTemplate jdbc;

    private final LocalDate today = LocalDate.now(CLOCK);
    private final AtomicInteger approvals = new AtomicInteger();
    private User user;
    private String accessToken;
    private long card;

    @BeforeEach
    void setUp() {
        user = newUser();
        accessToken = authTokenService.issue(user.getId()).accessToken();
        card = newCard(user, "테스트 카드A");
    }

    @AfterEach
    void cleanUp() {
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
        return users.save(new User("analysis" + n + "@example.com", "hash", "김리치", "010%08d".formatted(4000000 + n),
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

    private void save(User owner, long ownerCard, LocalDateTime at, long amount, long canceled, String category) {
        store.saveCollected(owner.getId(), ownerCard, List.of(new CollectedTransaction(
            "AP-" + approvals.incrementAndGet(), at, "테스트 상점", amount, canceled, 0, false, category)));
    }

    private void save(LocalDateTime at, long amount, String category) {
        save(user, card, at, amount, 0, category);
    }

    private ResultActions analysis(String token, String query) throws Exception {
        var request = get("/api/plans/analysis" + query);
        return mockMvc.perform(token == null ? request : request.header("Authorization", "Bearer " + token));
    }

    private ResultActions analysis(String period) throws Exception {
        return analysis(accessToken, "?period=" + period);
    }

    // ---- 로그인, 기간 검증 ----

    @Test
    @DisplayName("로그인이 필요하다 (토큰이 없으면 401 AUTH-001)")
    void requiresLogin() throws Exception {
        analysis(null, "?period=3M").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH-001"));
    }

    @Test
    @DisplayName("기간이 없거나 1M, 3M, 6M이 아니면 400 TRANS-002다")
    void invalidPeriod() throws Exception {
        for (String query : new String[] {"", "?period=", "?period=2M", "?period=12M", "?period=abc"}) {
            analysis(accessToken, query).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANS-002"));
        }
        analysis("3m").andExpect(status().isOk());
    }

    // ---- 분석과 예산 ----

    @Test
    @DisplayName("소비 비율(합 100)과 추천 예산(합이 총예산과 같고 1,000원 단위)을 11개 업종 순서대로 내려준다")
    void analyzesSpendingAndSuggestsBudget() throws Exception {
        LocalDateTime start = today.minusMonths(3).atTime(9, 0);          // 3개월 기간의 첫날: 부분 데이터가 아니다
        save(start, 120_000, "DINING");
        save(today.minusDays(10).atTime(9, 0), 180_000, "DINING");
        save(today.minusDays(9).atTime(9, 0), 100_000, "CAFE");
        save(today.minusDays(8).atTime(9, 0), 200_000, "SHOPPING");

        analysis("3M").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.analyzedMonths").value(3))
            .andExpect(jsonPath("$.data.monthlyAverage").value(200_000))
            .andExpect(jsonPath("$.data.topCategory").value("DINING"))
            .andExpect(jsonPath("$.data.hasData").value(true))
            .andExpect(jsonPath("$.data.partial").value(false))
            .andExpect(jsonPath("$.data.categoryRatios", hasSize(11)))
            .andExpect(jsonPath("$.data.categoryRatios[0].category").value("DINING"))
            .andExpect(jsonPath("$.data.categoryRatios[0].ratio").value(50))
            .andExpect(jsonPath("$.data.categoryRatios[1].category").value("CAFE"))
            .andExpect(jsonPath("$.data.categoryRatios[1].ratio").value(17))
            .andExpect(jsonPath("$.data.categoryRatios[3].category").value("SHOPPING"))
            .andExpect(jsonPath("$.data.categoryRatios[3].ratio").value(33))
            .andExpect(jsonPath("$.data.categoryRatios[10].category").value("ETC"))
            .andExpect(jsonPath("$.data.categoryRatios[10].ratio").value(0))
            .andExpect(jsonPath("$.data.suggestedBudget.total").value(200_000))
            .andExpect(jsonPath("$.data.suggestedBudget.byCategory", hasSize(11)))
            .andExpect(jsonPath("$.data.suggestedBudget.byCategory[0].amount").value(100_000))
            .andExpect(jsonPath("$.data.suggestedBudget.byCategory[1].amount").value(33_000))
            .andExpect(jsonPath("$.data.suggestedBudget.byCategory[3].amount").value(67_000))
            .andExpect(jsonPath("$.data.suggestedBudget.byCategory[10].amount").value(0));
    }

    @Test
    @DisplayName("취소·환불은 뺀 금액으로 분석한다 (전액 취소는 소비가 아니다)")
    void reflectsCancellations() throws Exception {
        save(user, card, today.minusMonths(1).atTime(9, 0), 50_000, 20_000, "DINING");   // 실제 30,000
        save(user, card, today.minusDays(5).atTime(9, 0), 70_000, 70_000, "SHOPPING");   // 전액 취소
        save(user, card, today.minusDays(4).atTime(9, 0), 10_000, 0, "CAFE");

        analysis("1M").andExpect(jsonPath("$.data.monthlyAverage").value(40_000))
            .andExpect(jsonPath("$.data.categoryRatios[0].ratio").value(75))
            .andExpect(jsonPath("$.data.categoryRatios[1].ratio").value(25))
            .andExpect(jsonPath("$.data.categoryRatios[3].ratio").value(0))
            .andExpect(jsonPath("$.data.suggestedBudget.total").value(40_000))
            .andExpect(jsonPath("$.data.suggestedBudget.byCategory[0].amount").value(30_000))
            .andExpect(jsonPath("$.data.suggestedBudget.byCategory[1].amount").value(10_000));
    }

    @Test
    @DisplayName("분석 대상으로 고른 카드의 거래만 분석한다")
    void analyzesOnlySelectedCards() throws Exception {
        long unselected = newCard(user, "선택 안 한 카드");
        jdbc.update("update user_cards set selected = false where id = ?", unselected);
        save(user, card, today.minusMonths(1).atTime(9, 0), 60_000, 0, "DINING");
        save(user, unselected, today.minusDays(3).atTime(9, 0), 500_000, 0, "SHOPPING");

        analysis("1M").andExpect(jsonPath("$.data.monthlyAverage").value(60_000))
            .andExpect(jsonPath("$.data.topCategory").value("DINING"))
            .andExpect(jsonPath("$.data.categoryRatios[0].ratio").value(100))
            .andExpect(jsonPath("$.data.categoryRatios[3].ratio").value(0))
            .andExpect(jsonPath("$.data.suggestedBudget.total").value(60_000));
    }

    @Test
    @DisplayName("다른 사용자의 거래는 분석에 섞이지 않는다")
    void usersAreIsolated() throws Exception {
        User other = newUser();
        long otherCard = newCard(other, "남의 카드");
        save(other, otherCard, today.minusDays(2).atTime(9, 0), 900_000, 0, "SHOPPING");
        save(user, card, today.minusMonths(1).atTime(9, 0), 20_000, 0, "CAFE");

        analysis("1M").andExpect(jsonPath("$.data.monthlyAverage").value(20_000))
            .andExpect(jsonPath("$.data.topCategory").value("CAFE"))
            .andExpect(jsonPath("$.data.categoryRatios[3].ratio").value(0));
    }

    // ---- 거래가 없거나 부족할 때 ----

    @Test
    @DisplayName("거래가 없으면 오류 없이 hasData=false이고 비율과 예산이 모두 0이다")
    void noTransactions() throws Exception {
        analysis("3M").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.hasData").value(false))
            .andExpect(jsonPath("$.data.partial").value(false))
            .andExpect(jsonPath("$.data.monthlyAverage").value(0))
            .andExpect(jsonPath("$.data.topCategory").value(nullValue()))
            .andExpect(jsonPath("$.data.categoryRatios", hasSize(11)))
            .andExpect(jsonPath("$.data.categoryRatios[0].ratio").value(0))
            .andExpect(jsonPath("$.data.suggestedBudget.total").value(0))
            .andExpect(jsonPath("$.data.suggestedBudget.byCategory", hasSize(11)))
            .andExpect(jsonPath("$.data.suggestedBudget.byCategory[0].amount").value(0));
    }

    @Test
    @DisplayName("분석 대상 카드가 없으면 거래가 있어도 hasData=false다")
    void noSelectedCards() throws Exception {
        jdbc.update("update user_cards set selected = false where id = ?", card);
        save(today.minusDays(2).atTime(9, 0), 50_000, "DINING");

        analysis("1M").andExpect(status().isOk()).andExpect(jsonPath("$.data.hasData").value(false))
            .andExpect(jsonPath("$.data.suggestedBudget.total").value(0));
    }

    @Test
    @DisplayName("전부 취소된 거래뿐이면 소비가 없는 것으로 본다")
    void onlyCanceledTransactions() throws Exception {
        save(user, card, today.minusDays(2).atTime(9, 0), 30_000, 30_000, "DINING");

        analysis("1M").andExpect(jsonPath("$.data.hasData").value(false))
            .andExpect(jsonPath("$.data.monthlyAverage").value(0));
    }

    @Test
    @DisplayName("수집된 거래가 기간보다 짧으면 partial=true이고, 월평균은 수집된 기간으로 나눈다 (한 달 미만은 1개월)")
    void partialData() throws Exception {
        save(today.minusDays(10).atTime(9, 0), 90_000, "DINING");

        analysis("3M").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.partial").value(true))
            .andExpect(jsonPath("$.data.hasData").value(true))
            .andExpect(jsonPath("$.data.analyzedMonths").value(1))
            .andExpect(jsonPath("$.data.monthlyAverage").value(90_000))
            .andExpect(jsonPath("$.data.suggestedBudget.total").value(90_000));
    }

    @Test
    @DisplayName("부분 데이터에서 월평균은 응답의 analyzedMonths로 나눈 값이다 (응답끼리 어긋나지 않는다)")
    void monthlyAverageMatchesReturnedMonths() throws Exception {
        // 3개월 기간(약 90일) 중 47일만 수집: 약 1.5개월이라 2개월로 반올림하고, 월평균도 2로 나눈다.
        save(today.minusDays(46).atTime(9, 0), 120_000, "DINING");

        analysis("3M").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.partial").value(true))
            .andExpect(jsonPath("$.data.analyzedMonths").value(2))
            .andExpect(jsonPath("$.data.monthlyAverage").value(60_000))
            .andExpect(jsonPath("$.data.suggestedBudget.total").value(60_000));
    }

}
