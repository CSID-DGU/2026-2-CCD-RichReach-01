package com.richreach.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.richreach.TestcontainersConfiguration;
import com.richreach.card.entity.BenefitCategoryMapping;
import com.richreach.card.repository.BenefitCategoryMappingRepository;
import com.richreach.card.service.BenefitCategoryMapper.MappedCategories;
import com.richreach.global.category.SpendingCategory;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 혜택 업종 코드 → 화면용 6개 업종 대응표를 실제 MySQL(마이그레이션 시드)로 확인한다.
 * 시드 내용, 복수 업종, 업종이 아닌 코드, 대응표에 없는 코드, 대응표를 데이터로 바꿀 수 있는지를 본다.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BenefitCategoryMapperTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    /** 공통 코드표(ref_common_standard)의 업종(CATEGORY) 코드 30개. */
    private static final Set<String> STANDARD_CATEGORY_CODES = Set.of(
        "ALL_MERCHANTS", "DINING", "CAFE", "BAKERY", "DELIVERY_APP", "CONVENIENCE", "GROCERY", "ONLINE_SHOPPING",
        "DEPARTMENT", "SHOPPING_OTHER", "PUBLIC_TRANSIT", "TAXI", "INTERCITY", "FUEL", "EV_CHARGING", "TELECOM",
        "SUBSCRIPTION", "DIGITAL_CONTENT", "UTILITIES", "INSURANCE", "HEALTH", "EDUCATION", "BEAUTY", "PET", "MOVIE",
        "LEISURE", "TRAVEL", "BOOKS", "AUTOMOTIVE", "OTHER");

    @Autowired
    private BenefitCategoryMapper mapper;
    @Autowired
    private BenefitCategoryMappingRepository mappings;
    @Autowired
    private JdbcTemplate jdbc;

    /** 대응표를 바꾸는 테스트가 다른 테스트에 영향을 주지 않게 시드 값으로 되돌린다. */
    @AfterEach
    void restoreSeed() {
        jdbc.update("update benefit_category_mappings set ui_category = 'ETC' where category_code = 'MOVIE'");
        jdbc.update("delete from benefit_category_mappings where category_code = 'TEST_NEW_CODE'");
        mapper.refresh();
    }

    private static MappedCategories only(SpendingCategory... categories) {
        return new MappedCategories(Set.of(categories), false);
    }

    // ---- 시드 ----

    @Test
    @DisplayName("공통 코드표의 업종 코드 30개가 모두 대응표에 있다")
    void everyStandardCategoryCodeIsMapped() {
        Set<String> mapped = mappings.findAll().stream().map(BenefitCategoryMapping::getCategoryCode)
            .collect(java.util.stream.Collectors.toSet());

        assertThat(mapped).containsAll(STANDARD_CATEGORY_CODES);
    }

    @Test
    @DisplayName("일부 데이터가 쓰는 코드(ALL, PAYMENT_METHOD, OVERSEAS)도 대응표에 있다")
    void extraCodesUsedByDataAreMapped() {
        assertThat(mappings.findAllById(Set.of("ALL", "PAYMENT_METHOD", "OVERSEAS"))).hasSize(3);
    }

    @Test
    @DisplayName("대응표의 화면용 업종은 모두 6개 업종 중 하나이거나 NULL(업종이 아닌 코드)이다")
    void everyUiCategoryIsValid() {
        for (BenefitCategoryMapping mapping : mappings.findAll()) {
            assertThat(mapping.spendingCategory()).as(mapping.getCategoryCode()).isNotNull();
        }
    }

    @Test
    @DisplayName("6개 업종 모두 대응표에서 한 번 이상 쓰인다")
    void everySpendingCategoryIsUsed() {
        Set<SpendingCategory> used = mappings.findAll().stream()
            .flatMap(mapping -> mapping.spendingCategory().stream()).collect(java.util.stream.Collectors.toSet());

        assertThat(used).containsExactlyInAnyOrder(SpendingCategory.values());
    }

    // ---- 단일 코드 ----

    @Test
    @DisplayName("대표 코드는 화면용 업종으로 바뀐다")
    void mapsRepresentativeCodes() {
        assertThat(mapper.map("DINING")).isEqualTo(only(SpendingCategory.DINING));
        assertThat(mapper.map("DELIVERY_APP")).isEqualTo(only(SpendingCategory.DINING));
        assertThat(mapper.map("CAFE")).isEqualTo(only(SpendingCategory.CAFE));
        assertThat(mapper.map("BAKERY")).isEqualTo(only(SpendingCategory.CAFE));
        assertThat(mapper.map("CONVENIENCE")).isEqualTo(only(SpendingCategory.CONVENIENCE_STORE));
        assertThat(mapper.map("ONLINE_SHOPPING")).isEqualTo(only(SpendingCategory.SHOPPING));
        assertThat(mapper.map("GROCERY")).isEqualTo(only(SpendingCategory.SHOPPING));
        assertThat(mapper.map("PUBLIC_TRANSIT")).isEqualTo(only(SpendingCategory.TRANSPORT));
        assertThat(mapper.map("FUEL")).isEqualTo(only(SpendingCategory.TRANSPORT));
        assertThat(mapper.map("MOVIE")).isEqualTo(only(SpendingCategory.ETC));
        assertThat(mapper.map("OTHER")).isEqualTo(only(SpendingCategory.ETC));
    }

    @Test
    @DisplayName("코드의 대소문자와 앞뒤 공백은 무시한다")
    void ignoresCaseAndWhitespace() {
        assertThat(mapper.map(" cafe ")).isEqualTo(only(SpendingCategory.CAFE));
    }

    // ---- 복수 업종 ----

    @Test
    @DisplayName("'|'로 이어진 코드는 각각 바꿔서 모으고, 같은 업종으로 모이면 하나로 본다")
    void mapsMultipleCodes() {
        assertThat(mapper.map("CAFE|BAKERY")).isEqualTo(only(SpendingCategory.CAFE));
        assertThat(mapper.map("PUBLIC_TRANSIT|TAXI|INTERCITY")).isEqualTo(only(SpendingCategory.TRANSPORT));
        assertThat(mapper.map("DINING|CAFE|CONVENIENCE|TELECOM|MOVIE")).isEqualTo(only(SpendingCategory.DINING,
            SpendingCategory.CAFE, SpendingCategory.CONVENIENCE_STORE, SpendingCategory.ETC));
        assertThat(mapper.map(" CAFE | CONVENIENCE ||")).isEqualTo(only(SpendingCategory.CAFE,
            SpendingCategory.CONVENIENCE_STORE));
    }

    // ---- 업종이 아닌 코드 ----

    @Test
    @DisplayName("전 가맹점 코드는 업종을 만들지 않고 allMerchants만 켠다")
    void allMerchantsCodes() {
        assertThat(mapper.map("ALL_MERCHANTS")).isEqualTo(new MappedCategories(Set.of(), true));
        assertThat(mapper.map("ALL")).isEqualTo(new MappedCategories(Set.of(), true));
        assertThat(mapper.map("ALL_MERCHANTS|ONLINE_SHOPPING"))
            .isEqualTo(new MappedCategories(Set.of(SpendingCategory.SHOPPING), true));
    }

    @Test
    @DisplayName("결제 수단·해외 조건 코드는 업종도 전 가맹점도 아니다")
    void conditionCodesAreNotCategories() {
        assertThat(mapper.map("PAYMENT_METHOD")).isEqualTo(MappedCategories.NONE);
        assertThat(mapper.map("OVERSEAS")).isEqualTo(MappedCategories.NONE);
        assertThat(mapper.map("OVERSEAS|ONLINE_SHOPPING")).isEqualTo(only(SpendingCategory.SHOPPING));
    }

    // ---- 없거나 모르는 코드 ----

    @Test
    @DisplayName("업종 코드가 없거나 비어 있으면 아무것도 아니다")
    void emptyInput() {
        assertThat(mapper.map(null)).isEqualTo(MappedCategories.NONE);
        assertThat(mapper.map("")).isEqualTo(MappedCategories.NONE);
        assertThat(mapper.map("  | ")).isEqualTo(MappedCategories.NONE);
    }

    @Test
    @DisplayName("대응표에 없는 코드는 ETC로 보되 다른 코드는 그대로 바꾼다")
    void unknownCodeBecomesEtc() {
        assertThat(mapper.map("TEST_NEW_CODE")).isEqualTo(only(SpendingCategory.ETC));
        assertThat(mapper.map("CAFE|TEST_NEW_CODE")).isEqualTo(only(SpendingCategory.CAFE, SpendingCategory.ETC));
    }

    @Test
    @DisplayName("결과 업종 집합은 바꿀 수 없다")
    void resultIsImmutable() {
        MappedCategories result = mapper.map("CAFE|DINING");

        assertThatThrownBy(() -> result.categories().add(SpendingCategory.ETC))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ---- 데이터로 관리 ----

    @Test
    @DisplayName("대응표 행을 바꾸고 다시 읽으면 코드 수정 없이 결과가 바뀐다")
    void tableChangeIsPickedUpAfterRefresh() {
        assertThat(mapper.map("MOVIE")).isEqualTo(only(SpendingCategory.ETC));

        jdbc.update("update benefit_category_mappings set ui_category = 'DINING' where category_code = 'MOVIE'");
        mapper.refresh();

        assertThat(mapper.map("MOVIE")).isEqualTo(only(SpendingCategory.DINING));
    }

    @Test
    @DisplayName("대응표에 새 코드를 추가하면 그 코드가 ETC가 아닌 지정한 업종으로 바뀐다")
    void newCodeCanBeAddedAsData() {
        jdbc.update("insert into benefit_category_mappings (category_code, ui_category) values ('TEST_NEW_CODE', 'CAFE')");
        mapper.refresh();

        assertThat(mapper.map("TEST_NEW_CODE")).isEqualTo(only(SpendingCategory.CAFE));
    }

    @Test
    @DisplayName("대응표에 6개 업종이 아닌 값이 들어 있으면 조용히 넘기지 않고 오류로 알린다")
    void invalidUiCategoryFailsLoudly() {
        jdbc.update("update benefit_category_mappings set ui_category = 'NOT_A_CATEGORY' where category_code = 'MOVIE'");
        mapper.refresh();

        assertThatThrownBy(() -> mapper.map("MOVIE")).isInstanceOf(IllegalStateException.class);
    }

}
