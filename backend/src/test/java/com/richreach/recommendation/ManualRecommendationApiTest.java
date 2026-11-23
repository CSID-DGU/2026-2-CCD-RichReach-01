package com.richreach.recommendation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.richreach.TestcontainersConfiguration;
import com.richreach.auth.repository.RefreshTokenRepository;
import com.richreach.auth.service.AuthTokenService;
import com.richreach.card.dto.CollectedCard;
import com.richreach.card.service.OwnedCardService;
import com.richreach.codef.entity.CardCompany;
import com.richreach.codef.entity.CodefConnection;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.repository.CodefConnectionRepository;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.global.category.SpendingCategory;
import com.richreach.transaction.dto.CollectedTransaction;
import com.richreach.transaction.service.TransactionStoreService;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 결제 전 수동 입력 추천 API({@code POST /api/recommendations/manual})를 보안 필터, 실제 MySQL, 실제 혜택 계산기로 확인한다.
 * 카드 상품과 혜택 규칙을 테스트 안에서 만들고, 승인된 규칙(rule_engine_ready)만 쓰는 기본 설정에서 추천 결과, 월 한도 소진,
 * 확인 전 규칙(TEXT), 후보 카드 없음, 입력 오류, 로그인 필요, 사용자 격리를 본다.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ManualRecommendationApiTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String PRODUCT_PREFIX = "REC-";
    /** 서비스와 테스트가 같은 시각을 보도록 고정한 시계. 이번 달은 2026년 11월이다. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T03:00:00Z"), ZoneId.of("Asia/Seoul"));

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
    private UserRepository users;
    @Autowired
    private CodefConnectionRepository connections;
    @Autowired
    private CodefOrgAccountRepository orgAccounts;
    @Autowired
    private OwnedCardService ownedCardService;
    @Autowired
    private TransactionStoreService store;
    @Autowired
    private RefreshTokenRepository refreshTokens;
    @Autowired
    private AuthTokenService authTokenService;
    @Autowired
    private JdbcTemplate jdbc;

    private User user;
    private String token;
    private CodefOrgAccount account;

    @BeforeEach
    void setUp() {
        user = newUser();
        token = authTokenService.issue(user.getId()).accessToken();
        LocalDateTime now = LocalDateTime.now(CLOCK).withNano(0);
        CodefConnection connection = connections.save(
            new CodefConnection(user, "test-connected-id-" + user.getId(), "DEMO", now, now, now, now));
        account = orgAccounts.save(CodefOrgAccount.connected(connection, "0301", now));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from card_transactions");
        jdbc.update("delete from user_cards");
        orgAccounts.deleteAll();
        connections.deleteAll();
        jdbc.update("delete from performance_tiers");
        jdbc.update("delete from benefit_rules where card_product_id in "
            + "(select id from card_products where card_id like '" + PRODUCT_PREFIX + "%')");
        jdbc.update("delete from card_products where card_id like '" + PRODUCT_PREFIX + "%'");
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    // ---- 준비 ----

    private User newUser() {
        int n = SEQUENCE.incrementAndGet();
        return users.save(new User("recommend" + n + "@example.com", "hash", "김리치",
            "010%08d".formatted(9000000 + n), "REAL", LocalDateTime.now()));
    }

    private long insertProduct(String cardName) {
        String cardId = PRODUCT_PREFIX + SEQUENCE.incrementAndGet();
        jdbc.update("""
            insert into card_products (issuer_code, issuer, card_id, card_name, card_type, card_status, product_class,
                recommendable, source_snapshot_date, source_text_sha256, review_status)
            values ('kb', '테스트카드사', ?, ?, 'credit', 'listed', 'PAYMENT_CARD', true, '2026-10-01', ?,
                'AUTO_EXTRACTED_NEEDS_REVIEW')
            """, cardId, cardName, "0".repeat(64));
        return jdbc.queryForObject("select id from card_products where card_id = ?", Long.class, cardId);
    }

    /** 카페 전 가맹점에 율({@code rate}%)과 월 한도를 주는 혜택 규칙. {@code reviewed}가 true면 사람이 확인한 규칙이다. */
    private void insertRule(long productId, double rate, long monthlyLimit, boolean reviewed) {
        jdbc.update("INSERT INTO benefit_rules (card_product_id, source_benefit_id, benefit_type, category, "
                + "category_name, merchants, target_raw, rate_percent, monthly_limit, monthly_limit_unit, "
                + "monthly_limit_status, review_status, rule_engine_ready, source_scope_id, extraction_method) "
                + "VALUES (?, ?, 'DISCOUNT', 'CAFE', '카페', NULL, '카페 전 가맹점', ?, ?, 'KRW', 'LIMITED', "
                + "'SNAPSHOT_FIELDS_REVIEWED', ?, 'scope-rec', 'TABLE')", productId,
            "rec-rule-" + SEQUENCE.incrementAndGet(), rate, monthlyLimit, reviewed);
    }

    /**
     * 카드 상품과 이름이 맞는 보유카드를 <b>한 번의 동기화로</b> 만든다. (상품에 매칭되면 분석 대상으로 선택된다) 동기화는 받은
     * 목록을 그 계정의 전체 카드로 보므로, 카드를 나눠서 동기화하면 앞의 카드가 비활성이 된다.
     */
    private void syncCards(String... productNames) {
        List<CollectedCard> collected = new java.util.ArrayList<>();
        for (int i = 0; i < productNames.length; i++) {
            collected.add(new CollectedCard("KB국민카드 " + productNames[i], "11112222333300%02d".formatted(i), "신용"));
        }
        ownedCardService.saveSynced(user.getId(), account.getId(), CardCompany.KB, collected);
    }

    private long ownedCardId(String productName) {
        return jdbc.queryForObject("select id from user_cards where user_id = ? and card_name = ?", Long.class,
            user.getId(), "KB국민카드 " + productName);
    }

    private void spendThisMonth(long cardId, long amount) {
        store.saveCollected(user.getId(), cardId, List.of(new CollectedTransaction("AP-" + UUID.randomUUID(),
            LocalDateTime.of(2026, 11, 10, 12, 0), "카페", amount, 0, 0, false, SpendingCategory.CAFE.name())));
    }

    private ResultActions recommend(String jwt, String body) throws Exception {
        var request = post("/api/recommendations/manual").contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(jwt == null ? request : request.header("Authorization", "Bearer " + jwt));
    }

    // ---- 테스트 ----

    @Test
    @DisplayName("승인된 규칙으로 카드별 이번 결제의 추가 혜택을 비교해 가장 큰 카드를 추천하고 나머지는 대안으로 준다")
    void recommendsTheBestCard() throws Exception {
        long strong = insertProduct("추천에이카드");
        long weak = insertProduct("추천비카드");
        insertRule(strong, 10, 5_000, true);
        insertRule(weak, 5, 5_000, true);
        syncCards("추천에이카드", "추천비카드");
        long strongCard = ownedCardId("추천에이카드");
        long weakCard = ownedCardId("추천비카드");

        recommend(token, "{\"category\":\"CAFE\",\"amount\":10000}").andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.type").value("CARD"))
            .andExpect(jsonPath("$.data.parsed.category").value("CAFE"))
            .andExpect(jsonPath("$.data.parsed.amount").value(10000))
            .andExpect(jsonPath("$.data.card.cardId").value(strongCard))
            .andExpect(jsonPath("$.data.card.cardProductId").value(strong))
            .andExpect(jsonPath("$.data.card.cardCompany").value("KB"))
            .andExpect(jsonPath("$.data.card.expectedBenefit").value(1000))
            .andExpect(jsonPath("$.data.card.confirmedOnly").value(true))
            .andExpect(jsonPath("$.data.alternatives[0].cardId").value(weakCard))
            .andExpect(jsonPath("$.data.alternatives[0].expectedBenefit").value(500))
            .andExpect(jsonPath("$.data.message").isNotEmpty())
            .andExpect(jsonPath("$.data.calculationScope").isNotEmpty());
    }

    @Test
    @DisplayName("이번 달 월 한도를 이미 다 채운 카드는 빠지고, 한도가 남은 카드가 추천된다")
    void skipsACardWhoseMonthlyLimitIsUsedUp() throws Exception {
        long strong = insertProduct("추천에이카드");
        long weak = insertProduct("추천비카드");
        insertRule(strong, 10, 5_000, true);
        insertRule(weak, 5, 5_000, true);
        syncCards("추천에이카드", "추천비카드");
        long strongCard = ownedCardId("추천에이카드");
        long weakCard = ownedCardId("추천비카드");
        spendThisMonth(strongCard, 60_000); // 10% = 6,000원이라 한도 5,000원을 이미 넘겼다.

        recommend(token, "{\"category\":\"CAFE\",\"amount\":10000}").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.type").value("CARD"))
            .andExpect(jsonPath("$.data.card.cardId").value(weakCard))
            .andExpect(jsonPath("$.data.card.expectedBenefit").value(500))
            .andExpect(jsonPath("$.data.alternatives").isEmpty());
    }

    @Test
    @DisplayName("원문 확인 전 규칙만 있으면 기본 설정에서는 계산하지 않아 TEXT이다")
    void textWhenRulesAreNotReviewed() throws Exception {
        long product = insertProduct("추천에이카드");
        insertRule(product, 10, 5_000, false);
        syncCards("추천에이카드");

        recommend(token, "{\"category\":\"CAFE\",\"amount\":10000}").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.type").value("TEXT"))
            .andExpect(jsonPath("$.data.card").doesNotExist())
            .andExpect(jsonPath("$.data.alternatives").isEmpty())
            .andExpect(jsonPath("$.data.message").isNotEmpty());
    }

    @Test
    @DisplayName("카드 상품에 매칭되지 않은 카드만 있으면 후보가 없어 RECOMMEND-001이다")
    void noMatchedCardIsAnInputError() throws Exception {
        ownedCardService.saveSynced(user.getId(), account.getId(), CardCompany.KB,
            List.of(new CollectedCard("KB국민카드 전혀 다른 카드", "1111222233339999", "신용")));

        recommend(token, "{\"category\":\"CAFE\",\"amount\":10000}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("RECOMMEND-001"));
    }

    @Test
    @DisplayName("업종이나 금액이 올바르지 않으면 400 RECOMMEND-001이다")
    void rejectsBadInput() throws Exception {
        long product = insertProduct("추천에이카드");
        insertRule(product, 10, 5_000, true);
        syncCards("추천에이카드");

        recommend(token, "{\"category\":\"GAMBLING\",\"amount\":10000}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("RECOMMEND-001"));
        recommend(token, "{\"amount\":10000}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("RECOMMEND-001"));
        recommend(token, "{\"category\":\"CAFE\",\"amount\":0}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("RECOMMEND-001"));
        recommend(token, "{\"category\":\"CAFE\",\"amount\":100000001}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("RECOMMEND-001"));
        recommend(token, "{\"category\":\"CAFE\"}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("RECOMMEND-001"));
    }

    @Test
    @DisplayName("로그인 없이는 401이다")
    void requiresLogin() throws Exception {
        recommend(null, "{\"category\":\"CAFE\",\"amount\":10000}").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("다른 사용자의 카드는 후보가 아니다")
    void ignoresOtherUsersCards() throws Exception {
        long product = insertProduct("추천에이카드");
        insertRule(product, 10, 5_000, true);
        syncCards("추천에이카드");
        User other = newUser();
        String otherToken = authTokenService.issue(other.getId()).accessToken();

        recommend(otherToken, "{\"category\":\"CAFE\",\"amount\":10000}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("RECOMMEND-001"));
    }

}
