package com.richreach.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.richreach.TestcontainersConfiguration;
import com.richreach.auth.repository.RefreshTokenRepository;
import com.richreach.auth.service.AuthTokenService;
import com.richreach.benefit.dto.BenefitPurchase;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.benefit.service.BenefitCalculator;
import com.richreach.card.dto.CollectedCard;
import com.richreach.card.repository.OwnedCardRepository;
import com.richreach.card.service.OwnedCardService;
import com.richreach.codef.entity.CardCompany;
import com.richreach.codef.entity.CodefConnection;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.repository.CodefConnectionRepository;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.global.category.SpendingCategory;
import com.richreach.transaction.dto.CollectedTransaction;
import com.richreach.transaction.repository.CardTransactionRepository;
import com.richreach.transaction.service.TransactionStoreService;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Base64;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 거래별 예상 혜택({@code benefitAmount})을 거래 목록·상세·카테고리 수정과 월 요약에서 실제 MySQL로 확인한다.
 * 혜택 계산기는 가짜로 바꿔서 "카페 거래 한 건당 10%"를 돌려주게 하고, 이 테스트는 계산 결과를 카드·달 단위로 거래에 연결하고
 * 응답에 담는 부분만 본다. (혜택 규칙 계산은 {@code SimpleBenefitCalculatorTest}가 본다)
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TransactionBenefitApiTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final YearMonth LAST_MONTH = YearMonth.now().minusMonths(1);

    @MockitoBean
    private BenefitCalculator calculator;

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

    private final AtomicInteger approvals = new AtomicInteger();
    private User user;
    private String accessToken;
    /** 카드 상품과 매칭된 카드(분석 대상). */
    private long matchedCard;

    @BeforeEach
    void setUp() {
        // 거래별 계산기는 가짜로 바꿔 카페 결제 한 건마다 10%를 돌려준다.
        when(calculator.calculatePurchases(anyLong(), any(), anyLong())).thenAnswer(invocation -> {
            List<BenefitPurchase> purchases = invocation.getArgument(1);
            Map<Long, BenefitResult> result = new java.util.LinkedHashMap<>();
            for (BenefitPurchase purchase : purchases) {
                long benefit = purchase.category() == SpendingCategory.CAFE ? purchase.amount() / 10 : 0;
                result.put(purchase.id(), new BenefitResult(benefit,
                    benefit == 0 ? Map.of() : Map.of(SpendingCategory.CAFE, benefit), true, List.of()));
            }
            return result;
        });
        user = newUser();
        accessToken = authTokenService.issue(user.getId()).accessToken();
        CodefConnection connection = connections.save(new CodefConnection(user, "test-connected-id-" + user.getId(),
            "DEMO", LocalDateTime.now().withNano(0), LocalDateTime.now().withNano(0), LocalDateTime.now().withNano(0),
            LocalDateTime.now().withNano(0)));
        matchedCard = newCard(connection, "0301", CardCompany.KB, "혜택 테스트 카드", true);
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

    // ---- 준비 ----

    private User newUser() {
        int n = SEQUENCE.incrementAndGet();
        return users.save(new User("txben" + n + "@example.com", "hash", "김리치", "010%08d".formatted(3000000 + n),
            "REAL", LocalDateTime.now()));
    }

    /** 보유카드를 만든다. {@code matched}이면 카드 상품에 매칭되고 분석 대상으로 선택된 카드다. */
    private long newCard(CodefConnection connection, String organization, CardCompany company, String name,
                         boolean matched) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        CodefOrgAccount account = orgAccounts.save(CodefOrgAccount.connected(connection, organization, now));
        ownedCardService.saveSynced(user.getId(), account.getId(), company,
            List.of(new CollectedCard(name, "1111", "check")));
        long cardId = ownedCards.findByOrgAccountId(account.getId()).get(0).getId();
        if (matched) {
            SelectedCards.select(jdbc, cardId);
        }
        return cardId;
    }

    private long unmatchedCard() {
        CodefConnection connection = connections.findByUserId(user.getId()).orElseThrow();
        return newCard(connection, "0306", CardCompany.SHINHAN, "상품 미매칭 카드", false);
    }

    private void save(long cardId, LocalDateTime at, String merchant, long amount, long canceled, String category) {
        store.saveCollected(user.getId(), cardId, List.of(new CollectedTransaction(
            "AP-" + approvals.incrementAndGet(), at, merchant, amount, canceled, 0, false, category)));
    }

    private LocalDateTime day(int dayOfMonth) {
        return LAST_MONTH.atDay(dayOfMonth).atTime(12, 0);
    }

    private long idOf(String merchant) {
        return transactions.findByUserId(user.getId()).stream()
            .filter(t -> t.getMerchantName().equals(merchant)).findFirst().orElseThrow().getId();
    }

    private ResultActions api(String url) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", "Bearer " + accessToken));
    }

    // ---- 테스트 ----

    @Test
    @DisplayName("거래 목록과 상세에 거래별 예상 혜택이 들어가고, 월 요약의 혜택 합계는 거래별 혜택의 합과 같다")
    void benefitsAreSplitAcrossTransactionsAndSumToTheMonthTotal() throws Exception {
        save(matchedCard, day(3), "카페A", 4_000, 0, "CAFE");
        save(matchedCard, day(5), "카페B", 6_000, 0, "CAFE");
        save(matchedCard, day(7), "식당", 10_000, 0, "DINING");

        // 카페 소비 10,000원의 10%인 1,000원을 카페 거래들에 금액 비율(4:6)로 나눈다.
        String body = api("/api/transactions?month=" + LAST_MONTH).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        long sum = 0;
        for (Object value : (List<?>) JsonPath.read(body, "$.data.content[*].benefitAmount")) {
            sum += ((Number) value).longValue();
        }
        assertThat(sum).isEqualTo(1_000);
        api("/api/transactions?month=" + LAST_MONTH)
            .andExpect(jsonPath("$.data.content[?(@.merchantName=='카페A')].benefitAmount").value(400))
            .andExpect(jsonPath("$.data.content[?(@.merchantName=='카페B')].benefitAmount").value(600))
            .andExpect(jsonPath("$.data.content[?(@.merchantName=='식당')].benefitAmount").value(0));

        api("/api/transactions/" + idOf("카페B")).andExpect(jsonPath("$.data.benefitAmount").value(600));
        api("/api/transactions/summary?month=" + LAST_MONTH)
            .andExpect(jsonPath("$.data.benefitAmount").value(1_000));
    }

    @Test
    @DisplayName("전액 취소된 거래는 혜택이 0원이고, 혜택은 취소를 뺀 소비로 계산한다")
    void canceledTransactionHasNoBenefit() throws Exception {
        save(matchedCard, day(3), "취소된 카페", 4_000, 4_000, "CAFE");
        save(matchedCard, day(5), "카페", 6_000, 0, "CAFE");

        api("/api/transactions?month=" + LAST_MONTH)
            .andExpect(jsonPath("$.data.content[?(@.merchantName=='취소된 카페')].benefitAmount").value(0))
            .andExpect(jsonPath("$.data.content[?(@.merchantName=='카페')].benefitAmount").value(600));
        api("/api/transactions/summary?month=" + LAST_MONTH).andExpect(jsonPath("$.data.benefitAmount").value(600));
    }

    @Test
    @DisplayName("카드 상품과 매칭되지 않은 카드의 거래는 혜택이 null이고, 월 요약도 계산할 수 없으면 null이다")
    void unmatchedCardHasNoBenefit() throws Exception {
        long other = unmatchedCard();
        save(other, day(3), "미매칭 카드 카페", 10_000, 0, "CAFE");

        api("/api/transactions?month=" + LAST_MONTH + "&cardScope=ALL")
            .andExpect(jsonPath("$.data.content[0].merchantName").value("미매칭 카드 카페"))
            .andExpect(jsonPath("$.data.content[0].benefitAmount").doesNotExist());
        api("/api/transactions/summary?month=" + LAST_MONTH + "&cardScope=ALL")
            .andExpect(jsonPath("$.data.benefitAmount").doesNotExist());
    }

    @Test
    @DisplayName("월 요약의 혜택 합계는 합계와 같은 카드 범위를 따른다 (분석 대상 카드만, ALL이면 계산할 수 있는 카드 전부)")
    void monthTotalFollowsTheCardScope() throws Exception {
        long other = unmatchedCard();
        save(matchedCard, day(3), "분석 대상 카페", 10_000, 0, "CAFE");
        save(other, day(4), "미매칭 카드 카페", 20_000, 0, "CAFE");

        api("/api/transactions/summary?month=" + LAST_MONTH).andExpect(jsonPath("$.data.benefitAmount").value(1_000));
        api("/api/transactions/summary?month=" + LAST_MONTH + "&cardScope=ALL")
            .andExpect(jsonPath("$.data.benefitAmount").value(1_000));
    }

    @Test
    @DisplayName("거래가 없는 달의 혜택 합계는 0이고, 기간 조회에는 혜택 합계를 주지 않는다")
    void emptyMonthAndPeriodQuery() throws Exception {
        api("/api/transactions/summary?month=" + LAST_MONTH).andExpect(jsonPath("$.data.benefitAmount").value(0));

        save(matchedCard, day(3), "카페", 10_000, 0, "CAFE");
        api("/api/transactions/summary?period=3M").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.benefitAmount").doesNotExist());
    }

    @Test
    @DisplayName("업종을 고치거나 되돌리면 응답의 예상 혜택도 고친 뒤의 값이다")
    void categoryChangeUpdatesTheBenefit() throws Exception {
        save(matchedCard, day(3), "카페", 10_000, 0, "CAFE");
        long id = idOf("카페");
        api("/api/transactions/" + id).andExpect(jsonPath("$.data.benefitAmount").value(1_000));

        mockMvc.perform(patch("/api/transactions/" + id + "/category").contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"DINING\"}").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.category").value("DINING"))
            .andExpect(jsonPath("$.data.benefitAmount").value(0));

        mockMvc.perform(delete("/api/transactions/" + id + "/category")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.category").value("CAFE"))
            .andExpect(jsonPath("$.data.benefitAmount").value(1_000));
    }

}
