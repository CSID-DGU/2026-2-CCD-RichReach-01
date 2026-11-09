package com.richreach.benefit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.TestcontainersConfiguration;
import com.richreach.benefit.config.BenefitProperties;
import com.richreach.benefit.config.BenefitProperties.RuleSelection;
import com.richreach.benefit.dto.BenefitQuery;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.card.repository.BenefitRuleRepository;
import com.richreach.card.repository.PerformanceTierRepository;
import com.richreach.card.service.BenefitCategoryMapper;
import com.richreach.global.category.SpendingCategory;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 임시 혜택 계산기가 DB의 규칙을 읽어 쓰는 흐름을 실제 MySQL로 확인한다. (규칙 선택, 업종 변환, 전월실적, 없는 카드) */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SimpleBenefitCalculatorTest {

    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final String PREFIX = "test-benefit-";

    @Autowired
    private BenefitRuleRepository rules;
    @Autowired
    private PerformanceTierRepository tiers;
    @Autowired
    private BenefitCategoryMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;

    private long productId;

    @BeforeEach
    void setUp() {
        jdbc.update("""
            insert into card_products (issuer_code, issuer, card_id, card_name, card_type, card_status, product_class,
                recommendable, source_snapshot_date, source_text_sha256, review_status)
            values ('kb', '테스트카드사', ?, '혜택 테스트 카드', 'credit', 'listed', 'PAYMENT_CARD', true, '2026-10-01', ?,
                'AUTO_EXTRACTED_NEEDS_REVIEW')
            """, PREFIX + "card", "0".repeat(64));
        productId = jdbc.queryForObject("select id from card_products where card_id = ?", Long.class, PREFIX + "card");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from performance_tiers where benefit_rule_id in "
            + "(select id from benefit_rules where source_benefit_id like ?)", PREFIX + "%");
        jdbc.update("delete from benefit_rules where source_benefit_id like ?", PREFIX + "%");
        jdbc.update("delete from card_products where card_id like ?", PREFIX + "%");
    }

    private long insertRule(String name, String category, String merchants, double ratePercent, Long monthlyLimit,
                            boolean ready) {
        jdbc.update("""
            insert into benefit_rules (card_product_id, source_benefit_id, benefit_type, category, category_name,
                merchants, target_raw, rate_percent, monthly_limit, monthly_limit_status, rule_mode,
                review_status, rule_engine_ready, source_scope_id, extraction_method)
            values (?, ?, 'DISCOUNT', ?, ?, ?, '원문', ?, ?, 'LIMITED', 'BASE', 'AUTO_EXTRACTED_NEEDS_REVIEW', ?, 'scope',
                'test')
            """, productId, PREFIX + name, category, category, merchants, ratePercent, monthlyLimit, ready);
        return jdbc.queryForObject("select id from benefit_rules where source_benefit_id = ?", Long.class,
            PREFIX + name);
    }

    private SimpleBenefitCalculator calculator(RuleSelection selection) {
        return new SimpleBenefitCalculator(rules, tiers, mapper, new BenefitProperties("simple", selection));
    }

    private BenefitQuery query(long previousMonthSpend) {
        return new BenefitQuery(productId, Map.of(SpendingCategory.DINING, 300_000L, SpendingCategory.CAFE, 100_000L),
            previousMonthSpend);
    }

    @Test
    @DisplayName("기본(READY_ONLY)은 확인된 규칙만 쓰고, ALL이면 확인 전 규칙도 쓴다")
    void ruleSelection() {
        insertRule("ready", "DINING", null, 5.0, null, true);
        insertRule("unconfirmed", "CAFE", null, 10.0, null, false);

        BenefitResult readyOnly = calculator(RuleSelection.READY_ONLY).calculate(query(0));
        BenefitResult all = calculator(RuleSelection.ALL).calculate(query(0));

        assertThat(readyOnly.totalBenefit()).isEqualTo(15_000);
        assertThat(readyOnly.confirmedOnly()).isTrue();
        assertThat(all.totalBenefit()).isEqualTo(25_000);
        assertThat(all.confirmedOnly()).isFalse();
    }

    @Test
    @DisplayName("혜택 업종 코드를 화면 업종으로 바꿔서 소비와 잇는다 (배달앱은 외식)")
    void mapsBenefitCategories() {
        insertRule("delivery", "DELIVERY_APP", null, 10.0, null, true);

        BenefitResult result = calculator(RuleSelection.READY_ONLY).calculate(query(0));

        assertThat(result.benefitByCategory()).containsOnly(Map.entry(SpendingCategory.DINING, 30_000L));
    }

    @Test
    @DisplayName("가맹점이 지정된 혜택은 빼고 안내를 남긴다")
    void skipsMerchantSpecificRules() {
        insertRule("brand", "DINING", "특정브랜드", 10.0, null, true);

        BenefitResult result = calculator(RuleSelection.READY_ONLY).calculate(query(0));

        assertThat(result.totalBenefit()).isZero();
        assertThat(result.notes()).isNotEmpty();
    }

    @Test
    @DisplayName("전월실적 조건과 월 한도를 적용한다")
    void appliesTierAndLimit() {
        long ruleId = insertRule("tiered", "DINING", null, 10.0, 20_000L, true);
        jdbc.update("insert into performance_tiers (benefit_rule_id, previous_month_min_krw) values (?, ?)", ruleId,
            500_000);

        assertThat(calculator(RuleSelection.READY_ONLY).calculate(query(499_999)).totalBenefit()).isZero();
        assertThat(calculator(RuleSelection.READY_ONLY).calculate(query(500_000)).totalBenefit()).isEqualTo(20_000);
    }

    @Test
    @DisplayName("규칙이 없거나 없는 카드 상품이면 예외 없이 혜택 0과 안내를 돌려준다")
    void noRules() {
        BenefitResult empty = calculator(RuleSelection.READY_ONLY).calculate(query(0));
        BenefitResult missing = calculator(RuleSelection.ALL)
            .calculate(new BenefitQuery(-1, Map.of(SpendingCategory.DINING, 1_000L), 0));

        assertThat(empty.totalBenefit()).isZero();
        assertThat(empty.notes()).isNotEmpty();
        assertThat(missing.totalBenefit()).isZero();
        assertThat(missing.confirmedOnly()).isFalse();
    }

}
