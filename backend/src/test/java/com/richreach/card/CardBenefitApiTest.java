package com.richreach.card;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.richreach.TestcontainersConfiguration;
import com.richreach.auth.repository.RefreshTokenRepository;
import com.richreach.auth.service.AuthTokenService;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
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
 * 보안 필터를 켠 채로 실제 MySQL에서 카드 혜택 조회를 확인한다. 최종 테이블(card_products 등)의 엔티티에는 생성자가 없어서
 * (승격 코드만 행을 만든다) 테스트 데이터는 SQL로 직접 넣는다.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CardBenefitApiTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final String SHA = "a".repeat(64);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserRepository users;
    @Autowired
    private RefreshTokenRepository refreshTokens;
    @Autowired
    private AuthTokenService authTokenService;

    private String accessToken;
    private long cardId;

    @BeforeEach
    void setUp() {
        User user = users.save(new User("benefit@example.com", "hash", "김리치", "01099990001", "REAL",
            LocalDateTime.now()));
        accessToken = authTokenService.issue(user.getId()).accessToken();
        cardId = insertCard("kb", "0001", "KB 테스트 카드", "listed", true);
    }

    @AfterEach
    void cleanUp() {
        // 자식 테이블부터 지운다. (외래키)
        jdbc.update("DELETE FROM performance_tiers");
        jdbc.update("DELETE FROM performance_exclusions");
        jdbc.update("DELETE FROM benefit_rules");
        jdbc.update("DELETE FROM card_products");
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    private long insertCard(String issuerCode, String code, String name, String status, boolean recommendable) {
        jdbc.update("INSERT INTO card_products (issuer_code, issuer, card_id, card_name, card_type, card_status, "
                + "product_class, recommendable, source_snapshot_date, source_text_sha256, review_status) "
                + "VALUES (?, 'KB국민카드', ?, ?, 'credit', ?, 'PAYMENT_CARD', ?, '2026-10-01', ?, 'REVIEWED_SUBSET')",
            issuerCode, code, name, status, recommendable, SHA);
        return jdbc.queryForObject("SELECT id FROM card_products WHERE issuer_code = ? AND card_id = ?", Long.class,
            issuerCode, code);
    }

    /** 값이 모두 채워진 혜택. 사람이 확인한 규칙(rule_engine_ready)이다. */
    private long insertReviewedBenefit(long card, String sourceId) {
        jdbc.update("INSERT INTO benefit_rules (card_product_id, source_benefit_id, benefit_type, category, "
                + "category_name, merchants, target_raw, rate_percent, monthly_limit, monthly_limit_unit, "
                + "monthly_limit_status, review_status, rule_engine_ready, source_scope_id, extraction_method) "
                + "VALUES (?, ?, 'DISCOUNT', 'CAFE', '카페', '스타벅스', '카페 전 가맹점', 10.00, 5000, 'KRW', "
                + "'LIMITED', 'SNAPSHOT_FIELDS_REVIEWED', TRUE, 'scope-1', 'TABLE')", card, sourceId);
        return benefitId(sourceId);
    }

    /** 율, 금액, 한도가 모두 비어 있는 자동 추출 혜택. 원문 조건 텍스트가 들어 있다. */
    private long insertUnknownBenefit(long card, String sourceId) {
        jdbc.update("INSERT INTO benefit_rules (card_product_id, source_benefit_id, benefit_type, category, "
                + "category_name, target_raw, monthly_limit_status, conditions, review_status, source_scope_id, "
                + "extraction_method) VALUES (?, ?, 'POINT', 'ETC', '기타', '비밀 원문 대상', 'NOT_STATED', "
                + "'비밀 원문 조건', 'AUTO_EXTRACTED_NEEDS_REVIEW', 'scope-2', 'TEXT')", card, sourceId);
        return benefitId(sourceId);
    }

    private long benefitId(String sourceId) {
        return jdbc.queryForObject("SELECT id FROM benefit_rules WHERE source_benefit_id = ?", Long.class, sourceId);
    }

    private void insertTier(long benefit, Long min, Long maxExclusive, String status, Long annual) {
        jdbc.update("INSERT INTO performance_tiers (benefit_rule_id, previous_month_min_krw, "
            + "previous_month_max_exclusive_krw, previous_month_requirement_status, annual_spend_min_krw) "
            + "VALUES (?, ?, ?, ?, ?)", benefit, min, maxExclusive, status, annual);
    }

    private void insertExclusion(long card, String sourceId, String appliesTo, String text) {
        jdbc.update("INSERT INTO performance_exclusions (card_product_id, source_condition_id, applies_to, "
            + "condition_type, condition_text, source_scope_id) VALUES (?, ?, ?, 'EXCLUSION|PERFORMANCE', ?, 'scope-1')",
            card, sourceId, appliesTo, text);
    }

    private ResultActions benefits(String path, String token) throws Exception {
        var request = get(path);
        return mockMvc.perform(token == null ? request : request.header("Authorization", "Bearer " + token));
    }

    private ResultActions benefitsOf(long id) throws Exception {
        return benefits("/api/cards/" + id + "/benefits", accessToken);
    }

    // ---- 접근과 오류 ----

    @Test
    @DisplayName("로그인이 필요하다 (토큰이 없으면 401 AUTH-001)")
    void requiresLogin() throws Exception {
        benefits("/api/cards/" + cardId + "/benefits", null)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-001"));
    }

    @Test
    @DisplayName("없는 카드는 404(CARD-001)이다")
    void unknownCardIsNotFound() throws Exception {
        benefitsOf(cardId + 9999).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CARD-001"));
    }

    @Test
    @DisplayName("카드 ID가 숫자가 아니면 400이다")
    void nonNumericCardIdIsRejected() throws Exception {
        benefits("/api/cards/abc/benefits", accessToken).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    // ---- 카드 기본 정보 ----

    @Test
    @DisplayName("카드 기본 정보를 내려준다")
    void returnsCardInfo() throws Exception {
        benefitsOf(cardId).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.cardId").value(cardId))
            .andExpect(jsonPath("$.data.issuerCode").value("kb"))
            .andExpect(jsonPath("$.data.issuer").value("KB국민카드"))
            .andExpect(jsonPath("$.data.cardName").value("KB 테스트 카드"))
            .andExpect(jsonPath("$.data.cardType").value("credit"))
            .andExpect(jsonPath("$.data.cardStatus").value("listed"))
            .andExpect(jsonPath("$.data.recommendable").value(true))
            .andExpect(jsonPath("$.data.sourceSnapshotDate").value("2026-10-01"));
    }

    @Test
    @DisplayName("혜택이 없는 카드는 빈 목록으로 200이다")
    void cardWithoutBenefits() throws Exception {
        benefitsOf(cardId).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.benefits").isEmpty())
            .andExpect(jsonPath("$.data.exclusions").isEmpty());
    }

    @Test
    @DisplayName("발급 중단된 카드(recommendable=false)도 조회할 수 있다")
    void suspendedCardIsStillReadable() throws Exception {
        long suspended = insertCard("kb", "0002", "단종 카드", "issuance_suspended", false);

        benefitsOf(suspended).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.cardStatus").value("issuance_suspended"))
            .andExpect(jsonPath("$.data.recommendable").value(false));
    }

    // ---- 혜택 ----

    @Test
    @DisplayName("혜택의 업종·율·한도를 값 그대로 내려주고 id 순으로 정렬한다")
    void returnsBenefitsInOrder() throws Exception {
        insertReviewedBenefit(cardId, "b-1");
        insertUnknownBenefit(cardId, "b-2");

        benefitsOf(cardId).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.benefits.length()").value(2))
            .andExpect(jsonPath("$.data.benefits[0].benefitType").value("DISCOUNT"))
            .andExpect(jsonPath("$.data.benefits[0].category").value("CAFE"))
            .andExpect(jsonPath("$.data.benefits[0].categoryName").value("카페"))
            .andExpect(jsonPath("$.data.benefits[0].merchants").value("스타벅스"))
            .andExpect(jsonPath("$.data.benefits[0].ratePercent").value(10.0))
            .andExpect(jsonPath("$.data.benefits[0].monthlyLimit").value(5000))
            .andExpect(jsonPath("$.data.benefits[0].monthlyLimitUnit").value("KRW"))
            .andExpect(jsonPath("$.data.benefits[0].monthlyLimitStatus").value("LIMITED"))
            .andExpect(jsonPath("$.data.benefits[1].benefitType").value("POINT"));
    }

    @Test
    @DisplayName("빈칸은 키는 있고 값이 null로 내려간다 (0이나 무제한으로 바뀌지 않는다)")
    void unknownValuesStayNull() throws Exception {
        insertUnknownBenefit(cardId, "b-1");

        benefitsOf(cardId).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.benefits[0]", hasKey("ratePercent")))
            .andExpect(jsonPath("$.data.benefits[0].ratePercent", nullValue()))
            .andExpect(jsonPath("$.data.benefits[0]", hasKey("amount")))
            .andExpect(jsonPath("$.data.benefits[0].amount", nullValue()))
            .andExpect(jsonPath("$.data.benefits[0]", hasKey("monthlyLimit")))
            .andExpect(jsonPath("$.data.benefits[0].monthlyLimit", nullValue()))
            .andExpect(jsonPath("$.data.benefits[0].dailyLimit", nullValue()))
            .andExpect(jsonPath("$.data.benefits[0].sharedMonthlyLimit", nullValue()))
            // 한도가 있는지는 숫자가 아니라 상태로 구분한다.
            .andExpect(jsonPath("$.data.benefits[0].monthlyLimitStatus").value("NOT_STATED"));
    }

    @Test
    @DisplayName("계산 포함 여부는 사람이 확인한 규칙만 true다")
    void includedInCalculationFollowsRuleEngineReady() throws Exception {
        insertReviewedBenefit(cardId, "b-1");
        insertUnknownBenefit(cardId, "b-2");

        benefitsOf(cardId).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.benefits[0].includedInCalculation").value(true))
            .andExpect(jsonPath("$.data.benefits[0].reviewStatus").value("SNAPSHOT_FIELDS_REVIEWED"))
            .andExpect(jsonPath("$.data.benefits[1].includedInCalculation").value(false))
            .andExpect(jsonPath("$.data.benefits[1].reviewStatus").value("AUTO_EXTRACTED_NEEDS_REVIEW"));
    }

    @Test
    @DisplayName("긴 원문(혜택 조건, 적용 대상 원문)은 응답에 담지 않는다")
    void rawTextIsNotExposed() throws Exception {
        insertUnknownBenefit(cardId, "b-1");

        benefitsOf(cardId).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.benefits[0]", not(hasKey("conditions"))))
            .andExpect(jsonPath("$.data.benefits[0]", not(hasKey("targetRaw"))))
            .andExpect(jsonPath("$.data.benefits[0]", not(hasKey("sourceScopeId"))))
            .andExpect(jsonPath("$.data.benefits[0]", not(hasKey("sourceBenefitId"))));
    }

    @Test
    @DisplayName("다른 카드의 혜택은 섞이지 않는다")
    void benefitsAreScopedToTheCard() throws Exception {
        long other = insertCard("kb", "0003", "다른 카드", "listed", true);
        insertReviewedBenefit(cardId, "mine-1");
        insertUnknownBenefit(other, "other-1");
        insertUnknownBenefit(other, "other-2");

        benefitsOf(cardId).andExpect(jsonPath("$.data.benefits.length()").value(1));
        benefitsOf(other).andExpect(jsonPath("$.data.benefits.length()").value(2));
    }

    // ---- 실적 조건과 제외 조건 ----

    @Test
    @DisplayName("전월 실적 조건이 있는 혜택에는 performance가 붙고, 없는 혜택은 null이다")
    void performanceConditionIsAttachedOnlyWhenPresent() throws Exception {
        long withTier = insertReviewedBenefit(cardId, "b-1");
        insertUnknownBenefit(cardId, "b-2");
        insertTier(withTier, 300000L, 500000L, "MINIMUM", null);

        benefitsOf(cardId).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.benefits[0].performance.previousMonthMinKrw").value(300000))
            .andExpect(jsonPath("$.data.benefits[0].performance.previousMonthMaxExclusiveKrw").value(500000))
            .andExpect(jsonPath("$.data.benefits[0].performance.previousMonthRequirementStatus").value("MINIMUM"))
            .andExpect(jsonPath("$.data.benefits[0].performance.annualSpendMinKrw", nullValue()))
            .andExpect(jsonPath("$.data.benefits[1]", hasKey("performance")))
            .andExpect(jsonPath("$.data.benefits[1].performance", nullValue()));
    }

    @Test
    @DisplayName("실적 조건의 빈칸도 0이 아니라 null이다")
    void performanceUnknownValuesStayNull() throws Exception {
        long benefit = insertReviewedBenefit(cardId, "b-1");
        insertTier(benefit, null, null, null, 12000000L);

        benefitsOf(cardId).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.benefits[0].performance.previousMonthMinKrw", nullValue()))
            .andExpect(jsonPath("$.data.benefits[0].performance.previousMonthRequirementStatus", nullValue()))
            .andExpect(jsonPath("$.data.benefits[0].performance.annualSpendMinKrw").value(12000000));
    }

    @Test
    @DisplayName("제외 조건은 카드 단위로 원문 그대로 내려가고, 다른 카드의 것은 섞이지 않는다")
    void exclusionsAreReturnedPerCard() throws Exception {
        long other = insertCard("kb", "0004", "다른 카드", "listed", true);
        insertExclusion(cardId, "c-1", "PERFORMANCE", "상품권 구입 금액은 전월 실적에서 제외");
        insertExclusion(cardId, "c-2", "BENEFIT", "사업자 결제는 혜택에서 제외");
        insertExclusion(other, "c-3", "BENEFIT", "다른 카드의 제외 조건");

        benefitsOf(cardId).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.exclusions.length()").value(2))
            .andExpect(jsonPath("$.data.exclusions[*].appliesTo", contains("PERFORMANCE", "BENEFIT")))
            .andExpect(jsonPath("$.data.exclusions[0].conditionText").value("상품권 구입 금액은 전월 실적에서 제외"))
            .andExpect(jsonPath("$.data.exclusions[0].conditionType").value("EXCLUSION|PERFORMANCE"));
    }

}
