package com.richreach.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.TestcontainersConfiguration;
import com.richreach.global.category.SpendingCategory;
import com.richreach.transaction.entity.MerchantCategoryRule;
import com.richreach.transaction.repository.MerchantCategoryRuleRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 가맹점 분류 규칙(마이그레이션 시드)을 실제 MySQL로 확인한다. 시드가 올바른지, 대표 가맹점과 경계 사례가 기대한 업종으로
 * 분류되는지, 규칙을 데이터로 바꿀 수 있는지를 본다.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MerchantCategoryClassifierTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    @Autowired
    private MerchantCategoryClassifier classifier;
    @Autowired
    private MerchantCategoryRuleRepository rules;
    @Autowired
    private JdbcTemplate jdbc;

    /** 규칙을 바꾸는 테스트가 다른 테스트에 영향을 주지 않게 시드 값으로 되돌린다. */
    @AfterEach
    void restoreSeed() {
        jdbc.update("update merchant_category_rules set ui_category = 'CAFE', active = true where pattern = '스타벅스'");
        jdbc.update("delete from merchant_category_rules where pattern = '테스트전용상점'");
        classifier.refresh();
    }

    // ---- 시드 ----

    @Test
    @DisplayName("시드 규칙의 업종은 모두 11개 업종 중 하나이고, 문구는 정규화해도 비지 않는다")
    void seedRulesAreValid() {
        List<MerchantCategoryRule> all = rules.findAll();

        assertThat(all).hasSizeGreaterThan(100);
        for (MerchantCategoryRule rule : all) {
            assertThat(rule.spendingCategory()).as("업종 규칙 %d", rule.getId()).isPresent();
            assertThat(MerchantMatcher.normalize(rule.getPattern())).as("문구 규칙 %d", rule.getId()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("정규화하면 같아지는 문구가 같은 일치 방식으로 두 번 들어 있지 않다")
    void seedRulesHaveNoDuplicatesAfterNormalization() {
        Set<String> seen = new HashSet<>();
        for (MerchantCategoryRule rule : rules.findAll()) {
            String key = rule.getMatchType() + ":" + MerchantMatcher.normalize(rule.getPattern());
            assertThat(seen.add(key)).as("중복 규칙 %s", key).isTrue();
        }
    }

    @Test
    @DisplayName("ETC를 뺀 10개 업종은 모두 규칙이 있다")
    void everyNonEtcCategoryHasRules() {
        Set<String> used = new HashSet<>();
        rules.findAll().forEach(rule -> used.add(rule.getUiCategory()));

        for (SpendingCategory category : SpendingCategory.values()) {
            if (category != SpendingCategory.ETC) {
                assertThat(used).contains(category.name());
            }
        }
    }

    // ---- 대표 가맹점 ----

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @DisplayName("대표 가맹점이 기대한 업종으로 분류된다")
    @CsvSource(delimiter = '|', value = {
        "스타벅스 강남역점 | CAFE",
        "STARBUCKS COFFEE | CAFE",
        "투썸플레이스 | CAFE",
        "메가MGC커피 | CAFE",
        "파리바게뜨 신촌점 | CAFE",
        "맥도날드 | DINING",
        "롯데리아 홍대점 | DINING",
        "배달의민족 | DINING",
        "(주)우아한형제들 | DINING",
        "요기요 | DINING",
        "쿠팡이츠 | DINING",
        "교촌치킨 | DINING",
        "홍길동 곱창 | DINING",
        "쿠팡 | SHOPPING",
        "쿠팡(주) | SHOPPING",
        "11번가 | SHOPPING",
        "G마켓 | SHOPPING",
        "무신사 | SHOPPING",
        "올리브영 명동점 | LIFE_HEALTH",
        "이마트 은평점 | CONVENIENCE_STORE",
        "홈플러스 | CONVENIENCE_STORE",
        "롯데백화점 본점 | SHOPPING",
        "교보문고 | EDUCATION",
        "행복마트 | CONVENIENCE_STORE",
        "티머니 | TRANSPORT",
        "서울교통공사 | TRANSPORT",
        "카카오T 택시 | TRANSPORT",
        "한국철도공사 | TRANSPORT",
        "GS칼텍스 강남주유소 | FUEL_VEHICLE",
        "S-OIL | FUEL_VEHICLE",
        "한국도로공사 하이패스 | TRANSPORT",
        "GS25 역삼점 | CONVENIENCE_STORE",
        "(주)지에스리테일 | CONVENIENCE_STORE",
        "세븐일레븐 | CONVENIENCE_STORE",
        "CU 강남역점 | CONVENIENCE_STORE",
        "이마트24 | CONVENIENCE_STORE",
        "미니스톱 | CONVENIENCE_STORE",
        "씨유 | CONVENIENCE_STORE",
        "동네 편의점 | CONVENIENCE_STORE",
        "서울내과의원 | LIFE_HEALTH",
        "온누리약국 | LIFE_HEALTH",
        "다비치안경 | LIFE_HEALTH",
        "SKT 통신요금 | TELECOM_SUBSCRIPTION",
        "넷플릭스 | TELECOM_SUBSCRIPTION",
        "삼성화재 | TELECOM_SUBSCRIPTION",
        "한국전력공사 | TELECOM_SUBSCRIPTION",
        "CGV 용산 | CULTURE_TRAVEL",
        "대한항공 | CULTURE_TRAVEL",
        "야놀자 | CULTURE_TRAVEL",
        "영어학원 | EDUCATION",
        "예스24 | EDUCATION"})
    void representativeMerchants(String merchant, String expected) {
        assertThat(classifier.classify(merchant.strip())).isEqualTo(SpendingCategory.valueOf(expected.strip()));
    }

    // ---- 경계 사례 ----

    @Test
    @DisplayName("쿠팡이츠는 쿠팡(쇼핑)이 아니라 음식점이고, 이마트24는 이마트(마트)가 아니라 편의점이다 (긴 문구 우선)")
    void longerBrandWinsOverShorterBrand() {
        assertThat(classifier.classify("쿠팡이츠")).isEqualTo(SpendingCategory.DINING);
        assertThat(classifier.classify("쿠팡")).isEqualTo(SpendingCategory.SHOPPING);
        assertThat(classifier.classify("이마트24")).isEqualTo(SpendingCategory.CONVENIENCE_STORE);
        assertThat(classifier.classify("이마트")).isEqualTo(SpendingCategory.CONVENIENCE_STORE);
    }

    @Test
    @DisplayName("상호 규칙이 일반 단어 규칙보다 먼저 걸린다 (스타벅스 카페는 카페, 이마트 커피는 마트)")
    void brandRulesWinOverGenericWords() {
        assertThat(classifier.classify("스타벅스 리저브 카페")).isEqualTo(SpendingCategory.CAFE);
        assertThat(classifier.classify("이마트 커피")).isEqualTo(SpendingCategory.CONVENIENCE_STORE);
        assertThat(classifier.classify("올리브영 카페")).isEqualTo(SpendingCategory.LIFE_HEALTH);
    }

    @Test
    @DisplayName("간편결제 접두어가 붙어도 가맹점으로 분류하고, 간편결제 이름만 있으면 ETC다")
    void paymentPrefixIsIgnored() {
        assertThat(classifier.classify("NAVERPAY-스타벅스 강남점")).isEqualTo(SpendingCategory.CAFE);
        assertThat(classifier.classify("KAKAOPAY_GS25 역삼점")).isEqualTo(SpendingCategory.CONVENIENCE_STORE);
        assertThat(classifier.classify("네이버페이")).isEqualTo(SpendingCategory.ETC);
        assertThat(classifier.classify("카카오페이")).isEqualTo(SpendingCategory.ETC);
    }

    @Test
    @DisplayName("스마트스터디처럼 마트가 섞인 말은 마트가 아니고, 스마트스토어는 쇼핑이다 (예외 규칙)")
    void smartExceptionRules() {
        assertThat(classifier.classify("스마트스터디")).isEqualTo(SpendingCategory.ETC);
        assertThat(classifier.classify("스마트폰 수리")).isEqualTo(SpendingCategory.ETC);
        assertThat(classifier.classify("네이버 스마트스토어")).isEqualTo(SpendingCategory.SHOPPING);
        assertThat(classifier.classify("행복마트")).isEqualTo(SpendingCategory.CONVENIENCE_STORE);
    }

    @Test
    @DisplayName("cu는 접두어일 때만 편의점이다 (컬처랜드는 아님)")
    void cuIsOnlyAPrefix() {
        assertThat(classifier.classify("CU 역삼점")).isEqualTo(SpendingCategory.CONVENIENCE_STORE);
        assertThat(classifier.classify("(주)CU")).isEqualTo(SpendingCategory.CONVENIENCE_STORE);
        assertThat(classifier.classify("CULTURELAND")).isEqualTo(SpendingCategory.ETC);
        assertThat(classifier.classify("ABC CU")).isEqualTo(SpendingCategory.ETC);
    }

    @Test
    @DisplayName("규칙에 없는 가맹점이나 가맹점명이 없으면 ETC다")
    void unknownMerchantIsEtc() {
        assertThat(classifier.classify("알 수 없는 상점")).isEqualTo(SpendingCategory.ETC);
        assertThat(classifier.classify(null)).isEqualTo(SpendingCategory.ETC);
        assertThat(classifier.classify("   ")).isEqualTo(SpendingCategory.ETC);
    }

    // ---- 데이터로 관리 ----

    @Test
    @DisplayName("규칙 행을 바꾸고 다시 읽으면 코드 수정 없이 분류가 바뀐다")
    void ruleChangeIsPickedUpAfterRefresh() {
        assertThat(classifier.classify("스타벅스")).isEqualTo(SpendingCategory.CAFE);

        jdbc.update("update merchant_category_rules set ui_category = 'DINING' where pattern = '스타벅스'");
        classifier.refresh();

        assertThat(classifier.classify("스타벅스")).isEqualTo(SpendingCategory.DINING);
    }

    @Test
    @DisplayName("규칙을 끄면(active=false) 그 규칙은 쓰지 않는다")
    void inactiveRuleIsIgnored() {
        jdbc.update("update merchant_category_rules set active = false where pattern = '스타벅스'");
        classifier.refresh();

        // 스타벅스 규칙을 껐어도 일반 단어 '카페'가 있으면 카페로 걸린다. 없는 가맹점명은 ETC다.
        assertThat(classifier.classify("스타벅스")).isEqualTo(SpendingCategory.ETC);
        assertThat(classifier.classify("스타벅스 카페")).isEqualTo(SpendingCategory.CAFE);
    }

    @Test
    @DisplayName("새 규칙을 추가하면 그 가맹점이 지정한 업종으로 분류된다")
    void newRuleCanBeAddedAsData() {
        assertThat(classifier.classify("테스트전용상점")).isEqualTo(SpendingCategory.ETC);

        jdbc.update("insert into merchant_category_rules (pattern, ui_category) values ('테스트전용상점', 'DINING')");
        classifier.refresh();

        assertThat(classifier.classify("테스트전용상점 본점")).isEqualTo(SpendingCategory.DINING);
    }

}
