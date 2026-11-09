package com.richreach.transaction;

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
import com.richreach.transaction.dto.CollectedTransaction;
import com.richreach.transaction.entity.CardTransaction;
import com.richreach.transaction.repository.CardTransactionRepository;
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
 * 기간별 거래 요약 API를 보안 필터와 실제 MySQL로 확인한다. 로그인, 기간 값 검증, 기간 경계, 취소·환불 반영, 업종 모으기,
 * 부분 데이터 표시, 거래가 없는 응답, 사용자 격리를 본다. 거래는 저장 서비스로 만들어 실제 수집과 같은 상태에서 확인한다.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TransactionSummaryApiTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    /**
     * 테스트와 서비스가 같은 시각을 보도록 고정한 시계. 자정을 넘겨도 기간 경계와 기대 날짜가 어긋나지 않는다.
     * 토큰 유효 시간 안에 끝나도록 테스트를 시작하는 시각으로 고정한다.
     */
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
    private CardTransactionRepository transactions;
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
        return users.save(new User("summary" + n + "@example.com", "hash", "김리치", "010%08d".formatted(3000000 + n),
            "REAL", LocalDateTime.now()));
    }

    private long newCard(User owner, String cardName) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        CodefConnection connection = connections.save(
            new CodefConnection(owner, "test-connected-id-" + owner.getId(), "DEMO", now, now, now, now));
        CodefOrgAccount account = orgAccounts.save(CodefOrgAccount.connected(connection, "0301", now));
        ownedCardService.saveSynced(owner.getId(), account.getId(), CardCompany.KB,
            List.of(new CollectedCard(cardName, "1111", "check")));
        long cardId = ownedCards.findByOrgAccountId(account.getId()).get(0).getId();
        SelectedCards.select(jdbc, cardId);
        return cardId;
    }

    private final AtomicInteger approvals = new AtomicInteger();

    private void save(User owner, long ownerCard, LocalDateTime at, long amount, long canceled, String category) {
        store.saveCollected(owner.getId(), ownerCard, List.of(new CollectedTransaction(
            "AP-" + approvals.incrementAndGet(), at, "테스트 상점", amount, canceled, 0, false, category)));
    }

    private void save(LocalDateTime at, long amount, String category) {
        save(user, card, at, amount, 0, category);
    }

    private ResultActions summary(String token, String query) throws Exception {
        var request = get("/api/transactions/summary" + query);
        return mockMvc.perform(token == null ? request : request.header("Authorization", "Bearer " + token));
    }

    private ResultActions summary(String period) throws Exception {
        return summary(accessToken, "?period=" + period);
    }

    // ---- 로그인, 기간 검증 ----

    @Test
    @DisplayName("로그인이 필요하다 (토큰이 없으면 401 AUTH-001)")
    void requiresLogin() throws Exception {
        summary(null, "?period=1M").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH-001"));
    }

    @Test
    @DisplayName("기간이 없거나 1M, 3M, 6M이 아니면 400 TRANS-002다")
    void invalidPeriod() throws Exception {
        for (String query : new String[] {"", "?period=", "?period=2M", "?period=12M", "?period=1Y", "?period=abc"}) {
            summary(accessToken, query).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANS-002"));
        }
    }

    @Test
    @DisplayName("기간 값의 대소문자는 구분하지 않는다")
    void periodIsCaseInsensitive() throws Exception {
        summary("1m").andExpect(status().isOk()).andExpect(jsonPath("$.data.period").value("1M"));
    }

    // ---- 기간 ----

    @Test
    @DisplayName("1M, 3M, 6M은 각각 오늘을 끝으로 그 기간 안의 거래만 합친다")
    void sumsOnlyTransactionsInThePeriod() throws Exception {
        save(today.minusDays(5).atTime(12, 0), 5_000, "CAFE");       // 1M 안
        save(today.minusDays(40).atTime(12, 0), 7_000, "DINING");    // 3M 안
        save(today.minusDays(100).atTime(12, 0), 9_000, "SHOPPING"); // 6M 안
        save(today.minusDays(200).atTime(12, 0), 3_000, "TRANSPORT"); // 6M 밖

        summary("1M").andExpect(jsonPath("$.data.totalAmount").value(5_000));
        summary("3M").andExpect(jsonPath("$.data.totalAmount").value(12_000));
        summary("6M").andExpect(jsonPath("$.data.totalAmount").value(21_000));
    }

    @Test
    @DisplayName("기간은 N개월 전 같은 날 0시부터 오늘 끝까지이고, 그 밖의 거래는 제외한다")
    void periodBoundaries() throws Exception {
        LocalDate from = today.minusMonths(1);
        save(from.atStartOfDay(), 1_000, "CAFE");                          // 시작일 0시: 포함
        save(today.atTime(23, 59, 59), 2_000, "CAFE");                      // 오늘 끝: 포함
        save(from.minusDays(1).atTime(23, 59, 59), 4_000, "CAFE");          // 시작 전날: 제외
        save(today.plusDays(1).atStartOfDay(), 8_000, "CAFE");              // 내일 0시: 제외

        summary("1M").andExpect(jsonPath("$.data.totalAmount").value(3_000))
            .andExpect(jsonPath("$.data.transactionCount").value(2))
            .andExpect(jsonPath("$.data.from").value(from.toString()))
            .andExpect(jsonPath("$.data.to").value(today.toString()));
    }

    // ---- 취소·환불 ----

    @Test
    @DisplayName("취소·환불을 뺀 금액으로 합계를 내고, 전액 취소된 거래는 거래 수에서 뺀다")
    void reflectsCancellations() throws Exception {
        LocalDateTime at = today.minusDays(3).atTime(10, 0);
        save(user, card, at, 10_000, 0, "CAFE");           // 정상
        save(user, card, at, 5_000, 5_000, "DINING");      // 전액 취소
        save(user, card, at, 8_000, 3_000, "SHOPPING");    // 일부 취소

        summary("1M").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalAmount").value(15_000))
            .andExpect(jsonPath("$.data.canceledAmount").value(8_000))
            .andExpect(jsonPath("$.data.transactionCount").value(2))
            .andExpect(jsonPath("$.data.categories[0].category").value("DINING"))
            .andExpect(jsonPath("$.data.categories[0].amount").value(0))
            .andExpect(jsonPath("$.data.categories[0].transactionCount").value(0))
            .andExpect(jsonPath("$.data.categories[3].category").value("SHOPPING"))
            .andExpect(jsonPath("$.data.categories[3].amount").value(5_000));
    }

    @Test
    @DisplayName("승인 금액이 0원인 거래는 거래 수에 포함하고, 전액 취소된 거래만 뺀다")
    void zeroAmountApprovalIsCounted() throws Exception {
        LocalDateTime at = today.minusDays(3).atTime(10, 0);
        save(user, card, at, 0, 0, "CAFE");              // 0원 승인: 센다
        save(user, card, at, 5_000, 5_000, "CAFE");      // 전액 취소: 세지 않는다
        save(user, card, at, 2_000, 0, "CAFE");          // 정상

        summary("1M").andExpect(jsonPath("$.data.transactionCount").value(2))
            .andExpect(jsonPath("$.data.categories[1].transactionCount").value(2))
            .andExpect(jsonPath("$.data.totalAmount").value(2_000));
    }

    @Test
    @DisplayName("기간 전에 승인한 거래는 기간 안에 환불해도 이 기간 합계에 들어가지 않는다 (승인일 기준)")
    void periodIsBasedOnApprovalDate() throws Exception {
        save(user, card, today.minusMonths(2).atTime(10, 0), 10_000, 10_000, "CAFE"); // 1M 밖에서 승인, 환불됨
        save(today.minusDays(1).atTime(10, 0), 3_000, "CAFE");

        summary("1M").andExpect(jsonPath("$.data.totalAmount").value(3_000))
            .andExpect(jsonPath("$.data.canceledAmount").value(0))
            .andExpect(jsonPath("$.data.transactionCount").value(1));
    }

    // ---- 업종 ----

    @Test
    @DisplayName("11개 업종을 항상 같은 순서로 모두 내려주고, 비율은 합계에서 차지하는 비중이다")
    void categoriesAreAlwaysElevenInOrder() throws Exception {
        LocalDateTime at = today.minusDays(2).atTime(9, 0);
        save(at, 3_000, "DINING");
        save(at, 1_000, "CAFE");

        summary("1M").andExpect(jsonPath("$.data.categories", hasSize(11)))
            .andExpect(jsonPath("$.data.categories[0].category").value("DINING"))
            .andExpect(jsonPath("$.data.categories[0].amount").value(3_000))
            .andExpect(jsonPath("$.data.categories[0].ratio").value(0.75))
            .andExpect(jsonPath("$.data.categories[1].category").value("CAFE"))
            .andExpect(jsonPath("$.data.categories[1].ratio").value(0.25))
            .andExpect(jsonPath("$.data.categories[2].category").value("CONVENIENCE_STORE"))
            .andExpect(jsonPath("$.data.categories[3].category").value("SHOPPING"))
            .andExpect(jsonPath("$.data.categories[4].category").value("TRANSPORT"))
            .andExpect(jsonPath("$.data.categories[5].category").value("FUEL_VEHICLE"))
            .andExpect(jsonPath("$.data.categories[6].category").value("LIFE_HEALTH"))
            .andExpect(jsonPath("$.data.categories[7].category").value("TELECOM_SUBSCRIPTION"))
            .andExpect(jsonPath("$.data.categories[8].category").value("CULTURE_TRAVEL"))
            .andExpect(jsonPath("$.data.categories[9].category").value("EDUCATION"))
            .andExpect(jsonPath("$.data.categories[10].category").value("ETC"))
            .andExpect(jsonPath("$.data.categories[10].amount").value(0));
    }

    @Test
    @DisplayName("사용자가 고친 카테고리가 있으면 그 업종으로 모은다")
    void userOverrideIsUsed() throws Exception {
        save(today.minusDays(2).atTime(9, 0), 4_000, "CAFE");
        CardTransaction saved = transactions.findByUserCardId(card).get(0);
        saved.overrideCategory("DINING");
        transactions.save(saved);

        summary("1M").andExpect(jsonPath("$.data.categories[0].category").value("DINING"))
            .andExpect(jsonPath("$.data.categories[0].amount").value(4_000))
            .andExpect(jsonPath("$.data.categories[1].amount").value(0));
    }

    @Test
    @DisplayName("분류 전(NULL)이거나 11개 업종이 아닌 값은 ETC로 모은다")
    void unclassifiedGoesToEtc() throws Exception {
        LocalDateTime at = today.minusDays(2).atTime(9, 0);
        save(at, 1_000, null);
        save(at, 2_000, "TEST_UNKNOWN");

        summary("1M").andExpect(jsonPath("$.data.categories[10].category").value("ETC"))
            .andExpect(jsonPath("$.data.categories[10].amount").value(3_000))
            .andExpect(jsonPath("$.data.categories[10].transactionCount").value(2));
    }

    // ---- 부분 데이터 ----

    @Test
    @DisplayName("수집된 가장 오래된 거래가 기간 시작일보다 늦으면 부분 데이터이고, 수집 시작일을 알려준다")
    void partialData() throws Exception {
        save(today.minusDays(10).atTime(9, 0), 1_000, "CAFE");

        summary("3M").andExpect(jsonPath("$.data.partial").value(true))
            .andExpect(jsonPath("$.data.collectedFrom").value(today.minusDays(10).toString()));
    }

    @Test
    @DisplayName("가장 오래된 거래가 기간 시작일 이전이면 부분 데이터가 아니다 (기간 밖 거래도 수집 범위로 본다)")
    void notPartialWhenCollectedBeforeTheStart() throws Exception {
        save(today.minusDays(200).atTime(9, 0), 1_000, "CAFE");
        save(today.minusDays(5).atTime(9, 0), 2_000, "CAFE");

        summary("6M").andExpect(jsonPath("$.data.partial").value(false))
            .andExpect(jsonPath("$.data.collectedFrom").value(today.minusDays(200).toString()));
        // 1M 기간에는 최근 거래만 합치지만 수집은 그 이전부터 되어 있다.
        summary("1M").andExpect(jsonPath("$.data.partial").value(false))
            .andExpect(jsonPath("$.data.totalAmount").value(2_000));
    }

    @Test
    @DisplayName("가장 오래된 거래가 기간 시작일과 같은 날이면 부분 데이터가 아니다")
    void notPartialWhenCollectedExactlyAtTheStart() throws Exception {
        save(today.minusMonths(3).atTime(8, 0), 1_000, "CAFE");

        summary("3M").andExpect(jsonPath("$.data.partial").value(false));
    }

    // ---- 거래가 없는 경우, 격리 ----

    @Test
    @DisplayName("거래가 없으면 합계와 11개 업종이 모두 0이고 부분 데이터도 수집 시작일도 없다")
    void noTransactions() throws Exception {
        summary("1M").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalAmount").value(0))
            .andExpect(jsonPath("$.data.canceledAmount").value(0))
            .andExpect(jsonPath("$.data.transactionCount").value(0))
            .andExpect(jsonPath("$.data.partial").value(false))
            .andExpect(jsonPath("$.data.collectedFrom").value(nullValue()))
            .andExpect(jsonPath("$.data.categories", hasSize(11)))
            .andExpect(jsonPath("$.data.categories[0].amount").value(0))
            .andExpect(jsonPath("$.data.categories[0].ratio").value(0.0));
    }

    @Test
    @DisplayName("다른 사용자의 거래는 합계와 수집 시작일에 섞이지 않는다")
    void usersAreIsolated() throws Exception {
        User other = newUser();
        long otherCard = newCard(other, "남의 카드");
        save(other, otherCard, today.minusDays(300).atTime(9, 0), 99_000, 0, "SHOPPING");
        save(other, otherCard, today.minusDays(2).atTime(9, 0), 77_000, 0, "DINING");
        save(today.minusDays(4).atTime(9, 0), 2_000, "CAFE");

        summary("6M").andExpect(jsonPath("$.data.totalAmount").value(2_000))
            .andExpect(jsonPath("$.data.transactionCount").value(1))
            .andExpect(jsonPath("$.data.collectedFrom").value(today.minusDays(4).toString()))
            .andExpect(jsonPath("$.data.partial").value(true));
    }

    @Test
    @DisplayName("여러 보유카드의 거래를 합친다")
    void sumsAcrossCards() throws Exception {
        long second = newCardForSameUser("테스트 카드B");
        save(user, card, today.minusDays(2).atTime(9, 0), 1_000, 0, "CAFE");
        save(user, second, today.minusDays(3).atTime(9, 0), 2_000, 0, "CAFE");

        summary("1M").andExpect(jsonPath("$.data.totalAmount").value(3_000))
            .andExpect(jsonPath("$.data.categories[1].amount").value(3_000));
    }

    /** 같은 사용자의 두 번째 보유카드. 연결은 사용자당 하나이므로 같은 연결의 다른 카드사 계정을 쓴다. */
    private long newCardForSameUser(String cardName) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        CodefConnection connection = connections.findByUserId(user.getId()).orElseThrow();
        CodefOrgAccount account = orgAccounts.save(CodefOrgAccount.connected(connection, "0306", now));
        ownedCardService.saveSynced(user.getId(), account.getId(), CardCompany.SHINHAN,
            List.of(new CollectedCard(cardName, "2222", "check")));
        long cardId = ownedCards.findByOrgAccountId(account.getId()).get(0).getId();
        SelectedCards.select(jdbc, cardId);
        return cardId;
    }


    // ---- 카드 범위, 카드별 소비 ----

    @Test
    @DisplayName("기본은 분석 대상 카드만 합치고, cardScope=ALL이면 선택하지 않은 카드의 거래도 합친다")
    void cardScopeSelectedAndAll() throws Exception {
        long unselected = newCardForSameUser("선택 안 한 카드");
        jdbc.update("update user_cards set selected = false where id = ?", unselected);
        save(user, card, today.minusDays(2).atTime(9, 0), 1_000, 0, "CAFE");
        save(user, unselected, today.minusDays(40).atTime(9, 0), 5_000, 0, "SHOPPING");
        save(user, unselected, today.minusDays(3).atTime(9, 0), 2_000, 0, "CAFE");

        summary(accessToken, "?period=6M").andExpect(jsonPath("$.data.totalAmount").value(1_000))
            .andExpect(jsonPath("$.data.transactionCount").value(1))
            .andExpect(jsonPath("$.data.collectedFrom").value(today.minusDays(2).toString()));
        summary(accessToken, "?period=6M&cardScope=SELECTED").andExpect(jsonPath("$.data.totalAmount").value(1_000));
        summary(accessToken, "?period=6M&cardScope=all").andExpect(jsonPath("$.data.totalAmount").value(8_000))
            .andExpect(jsonPath("$.data.transactionCount").value(3))
            .andExpect(jsonPath("$.data.collectedFrom").value(today.minusDays(40).toString()));
    }

    @Test
    @DisplayName("분석 대상 카드가 없으면 기본 범위의 합계는 0이고 오류가 아니다")
    void noSelectedCards() throws Exception {
        jdbc.update("update user_cards set selected = false where id = ?", card);
        save(today.minusDays(2).atTime(9, 0), 1_000, "CAFE");

        summary(accessToken, "?period=1M").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalAmount").value(0))
            .andExpect(jsonPath("$.data.transactionCount").value(0))
            .andExpect(jsonPath("$.data.collectedFrom").doesNotExist())
            .andExpect(jsonPath("$.data.categories.length()").value(11));
        summary(accessToken, "?period=1M&cardScope=ALL").andExpect(jsonPath("$.data.totalAmount").value(1_000));
    }

    @Test
    @DisplayName("알 수 없는 cardScope는 400 COMMON-400이다")
    void invalidCardScope() throws Exception {
        for (String query : new String[] {"&cardScope=", "&cardScope=MINE", "&cardScope=1"}) {
            summary(accessToken, "?period=1M" + query).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        }
    }

    @Test
    @DisplayName("cards는 범위와 상관없이 활성 카드 전체의 기간 건수·금액을 내려주고, 취소는 뺀다")
    void cardsAreListedRegardlessOfScope() throws Exception {
        long second = newCardForSameUser("테스트 카드B");
        long idle = newCardForSameUser("거래 없는 카드");
        jdbc.update("update user_cards set selected = false where id = ?", second);
        save(user, card, today.minusDays(2).atTime(9, 0), 1_000, 0, "CAFE");
        save(user, card, today.minusDays(3).atTime(9, 0), 4_000, 1_000, "DINING");
        save(user, card, today.minusDays(4).atTime(9, 0), 7_000, 7_000, "DINING");
        save(user, second, today.minusDays(5).atTime(9, 0), 2_000, 0, "CAFE");
        save(user, second, today.minusDays(200).atTime(9, 0), 9_000, 0, "CAFE");

        for (String query : new String[] {"?period=1M", "?period=1M&cardScope=ALL"}) {
            summary(accessToken, query).andExpect(jsonPath("$.data.cards.length()").value(3))
                .andExpect(jsonPath("$.data.cards[0].cardId").value(card))
                .andExpect(jsonPath("$.data.cards[0].cardName").value("테스트 카드A"))
                .andExpect(jsonPath("$.data.cards[0].cardCompany").value("KB"))
                .andExpect(jsonPath("$.data.cards[0].maskedNumber").value("1111"))
                .andExpect(jsonPath("$.data.cards[0].selected").value(true))
                .andExpect(jsonPath("$.data.cards[0].amount").value(4_000))
                .andExpect(jsonPath("$.data.cards[0].transactionCount").value(2))
                .andExpect(jsonPath("$.data.cards[1].cardId").value(second))
                .andExpect(jsonPath("$.data.cards[1].selected").value(false))
                .andExpect(jsonPath("$.data.cards[1].amount").value(2_000))
                .andExpect(jsonPath("$.data.cards[1].transactionCount").value(1))
                .andExpect(jsonPath("$.data.cards[2].cardId").value(idle))
                .andExpect(jsonPath("$.data.cards[2].amount").value(0))
                .andExpect(jsonPath("$.data.cards[2].transactionCount").value(0));
        }
    }

    @Test
    @DisplayName("cards에는 다른 사용자의 카드와 거래가 섞이지 않는다")
    void cardsAreIsolatedFromOtherUsers() throws Exception {
        User other = newUser();
        long otherCard = newCard(other, "남의 카드");
        save(other, otherCard, today.minusDays(2).atTime(9, 0), 99_000, 0, "SHOPPING");
        save(today.minusDays(2).atTime(9, 0), 1_000, "CAFE");

        summary("1M").andExpect(jsonPath("$.data.cards.length()").value(1))
            .andExpect(jsonPath("$.data.cards[0].cardId").value(card))
            .andExpect(jsonPath("$.data.cards[0].amount").value(1_000));
    }

}
