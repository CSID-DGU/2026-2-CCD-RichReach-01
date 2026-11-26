package com.richreach.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import com.richreach.TestcontainersConfiguration;
import com.richreach.card.dto.CollectedCard;
import com.richreach.card.service.BenefitRuleQueryService;
import com.richreach.card.service.OwnedCardService;
import com.richreach.codef.entity.CardCompany;
import com.richreach.codef.entity.CodefConnection;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.repository.CodefConnectionRepository;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.recommendation.dto.ManualRecommendationRequest;
import com.richreach.recommendation.dto.ManualRecommendationResponse;
import com.richreach.recommendation.service.ManualRecommendationService;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 추천 한 번에 혜택 규칙을 몇 번 읽고 SQL이 몇 번 나가는지 센다. 보유카드 5장이 모두 승인된 규칙을 가진 경우다.
 * 시간 측정은 환경 변수 {@code MEASURE_LATENCY=true}일 때만 실행한다. (CI에서는 돌지 않는다)
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class RecommendationQueryCountTest {

    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final String PRODUCT_PREFIX = "RQC-";
    private static final String[] CARD_NAMES = {"측정에이카드", "측정비카드", "측정씨카드", "측정디카드", "측정이카드"};

    @Autowired
    private ManualRecommendationService recommendations;
    @MockitoSpyBean
    private BenefitRuleQueryService ruleQuery;
    @Autowired
    private UserRepository users;
    @Autowired
    private CodefConnectionRepository connections;
    @Autowired
    private CodefOrgAccountRepository orgAccounts;
    @Autowired
    private OwnedCardService ownedCardService;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private JdbcTemplate jdbc;

    private User user;

    @BeforeEach
    void setUp() {
        user = users.save(new User("querycount@example.com", "hash", "김측정", "01098880000", "REAL",
            LocalDateTime.now()));
        LocalDateTime now = LocalDateTime.now().withNano(0);
        CodefConnection connection = connections.save(
            new CodefConnection(user, "test-connected-id-" + user.getId(), "DEMO", now, now, now, now));
        CodefOrgAccount account = orgAccounts.save(CodefOrgAccount.connected(connection, "0301", now));

        List<CollectedCard> collected = new ArrayList<>();
        for (int i = 0; i < CARD_NAMES.length; i++) {
            insertCardWithRule(i, CARD_NAMES[i]);
            collected.add(new CollectedCard("KB국민카드 " + CARD_NAMES[i], "22223333444400%02d".formatted(i), "신용"));
        }
        ownedCardService.saveSynced(user.getId(), account.getId(), CardCompany.KB, collected);
        clearInvocations(ruleQuery);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from user_cards");
        orgAccounts.deleteAll();
        connections.deleteAll();
        jdbc.update("delete from benefit_rules where card_product_id in "
            + "(select id from card_products where card_id like '" + PRODUCT_PREFIX + "%')");
        jdbc.update("delete from card_products where card_id like '" + PRODUCT_PREFIX + "%'");
        users.deleteAll();
    }

    private void insertCardWithRule(int index, String cardName) {
        String cardId = PRODUCT_PREFIX + index;
        jdbc.update("""
            insert into card_products (issuer_code, issuer, card_id, card_name, card_type, card_status, product_class,
                recommendable, source_snapshot_date, source_text_sha256, review_status)
            values ('kb', '테스트카드사', ?, ?, 'credit', 'listed', 'PAYMENT_CARD', true, '2026-10-01', ?,
                'AUTO_EXTRACTED_NEEDS_REVIEW')
            """, cardId, cardName, "0".repeat(64));
        long productId = jdbc.queryForObject("select id from card_products where card_id = ?", Long.class, cardId);
        jdbc.update("INSERT INTO benefit_rules (card_product_id, source_benefit_id, benefit_type, category, "
                + "category_name, merchants, target_raw, rate_percent, monthly_limit, monthly_limit_unit, "
                + "monthly_limit_status, review_status, rule_engine_ready, source_scope_id, extraction_method) "
                + "VALUES (?, ?, 'DISCOUNT', 'CAFE', '카페', NULL, '카페 전 가맹점', ?, 5000, 'KRW', 'LIMITED', "
                + "'SNAPSHOT_FIELDS_REVIEWED', true, 'scope-rqc', 'TABLE')", productId, "rqc-rule-" + index,
            10 - index);
    }

    private ManualRecommendationResponse recommend(Long amount) {
        return recommendations.recommend(user.getId(), new ManualRecommendationRequest("CAFE", amount));
    }

    private long statementsFor(Long amount) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        clearInvocations(ruleQuery);
        recommend(amount);
        return statistics.getPrepareStatementCount();
    }

    @Test
    @DisplayName("카드 5장: 금액이 있을 때 혜택 규칙을 카드당 한 번(5번) 읽는다")
    void countsRuleReadsForASingleAmount() {
        long statements = statementsFor(10_000L);

        System.out.printf("QUERY_COUNT single ruleReads=%d statements=%d%n", ruleReads(), statements);
        verify(ruleQuery, times(5)).findForCalculation(anyLong());
    }

    @Test
    @DisplayName("카드 5장: 금액이 없을 때(구간 3개) 혜택 규칙을 카드당 한 번(5번) 읽는다")
    void countsRuleReadsForRanges() {
        long statements = statementsFor(null);

        System.out.printf("QUERY_COUNT range ruleReads=%d statements=%d%n", ruleReads(), statements);
        verify(ruleQuery, times(5)).findForCalculation(anyLong());
    }

    private long ruleReads() {
        return mockingDetails(ruleQuery).getInvocations().stream()
            .filter(invocation -> invocation.getMethod().getName().equals("findForCalculation")).count();
    }

    @Test
    @DisplayName("추천 응답 시간 측정(수동 실행)")
    @EnabledIfEnvironmentVariable(named = "MEASURE_LATENCY", matches = "true")
    void measuresLatency() {
        for (Long amount : new Long[] {10_000L, null}) {
            for (int i = 0; i < 50; i++) {
                recommend(amount); // 예열
            }
            long[] nanos = new long[200];
            for (int i = 0; i < nanos.length; i++) {
                long startedAt = System.nanoTime();
                ManualRecommendationResponse response = recommend(amount);
                nanos[i] = System.nanoTime() - startedAt;
                assertThat(response.type()).isNotNull();
                clearInvocations(ruleQuery);
            }
            Arrays.sort(nanos);
            System.out.printf("LATENCY %s medianMs=%.2f p95Ms=%.2f%n", amount == null ? "range" : "single",
                nanos[nanos.length / 2] / 1e6, nanos[(int) (nanos.length * 0.95)] / 1e6);
        }
    }

}
