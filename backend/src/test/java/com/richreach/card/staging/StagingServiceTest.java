package com.richreach.card.staging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.richreach.card.service.CardPromotionService;
import com.richreach.card.staging.CsvStagingLoader.LoadResult;
import com.richreach.card.staging.StagingService.MvpResult;
import com.richreach.card.staging.StagingService.Report;
import com.richreach.card.staging.StagingValidator.CheckResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 앱 전체를 띄우지 않고 일회용 MySQL에 migration(V1~)만 적용해서 적재기를 검증한다.
 * 테스트 CSV는 실행할 때 지어낸 값으로 만든다. (저장소에 CSV를 두지 않는다)
 */
@Testcontainers
class StagingServiceTest {

    /** 테스트가 실행될 때 만드는 가짜 CSV 8종이 들어가는 임시 폴더. 저장소에 CSV 파일을 두지 않는다. */
    @TempDir
    static Path fixtures;

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    static JdbcTemplate jdbc;
    static StagingService service;
    static CsvStagingLoader loader;
    static StagingValidator validator;
    static CardPromotionService promotion;

    @BeforeAll
    static void migrateAndWire() throws IOException {
        writeFixtures(fixtures);
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .load()
            .migrate();
        DriverManagerDataSource dataSource =
            new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        DataSourceTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        loader = new CsvStagingLoader(jdbc, txManager);
        validator = new StagingValidator(jdbc);
        service = new StagingService(loader, validator, jdbc, txManager);
        promotion = new CardPromotionService(jdbc, txManager);
    }

    @BeforeEach
    void clearStagingTables() {
        // 최종 테이블은 FK가 있어서 자식부터 지운다. 원문 테이블은 시드(V2)를 두고 연결 표시만 되돌린다.
        jdbc.update("DELETE FROM performance_tiers");
        jdbc.update("DELETE FROM performance_exclusions");
        jdbc.update("DELETE FROM benefit_rules");
        jdbc.update("DELETE FROM card_products");
        jdbc.update("DELETE FROM card_catalog_raw WHERE issuer LIKE 'v%'");
        jdbc.update("UPDATE card_catalog_raw SET structured_product_id = NULL, review_status = 'raw_collected' "
            + "WHERE structured_product_id IS NOT NULL");
        for (StagingTable table : StagingTables.ALL) {
            jdbc.execute("TRUNCATE TABLE `" + table.table() + "`");
        }
    }

    @Test
    void loadsAllEightCsvFilesIncludingBomHeaders() {
        Report report = service.loadAll(fixtures);

        assertThat(report.loads()).hasSize(8);
        assertThat(report.loads()).allSatisfy(r -> {
            assertThat(r.fileRows()).isEqualTo(3);
            assertThat(r.tableRows()).isEqualTo(3);
            assertThat(r.duplicateKeys()).isZero();
        });
    }

    @Test
    void loadingTwiceDoesNotDuplicateAndRestoresChangedValues() {
        service.loadAll(fixtures);
        jdbc.update("UPDATE stg_card_catalog SET card_name = '바뀐 이름'");

        Report second = service.loadAll(fixtures);

        assertThat(second.loads()).allSatisfy(r -> assertThat(r.tableRows()).isEqualTo(3));
        assertThat(count("SELECT COUNT(*) FROM stg_card_catalog WHERE card_name = '바뀐 이름'")).isZero();
    }

    @Test
    void convertsTypesAndKeepsBlanksAsNull() {
        service.loadAll(fixtures);

        // CSV의 "False" 문자열 -> 0, 빈칸 -> NULL (빈 문자열이나 0이 아니다)
        assertThat(count("SELECT COUNT(*) FROM stg_card_benefit WHERE rule_engine_ready = 0")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM stg_card_benefit WHERE per_transaction_min_krw IS NOT NULL")).isZero();
        assertThat(count("SELECT COUNT(*) FROM stg_card_benefit WHERE merchants = ''")).isZero();
        assertThat(count("SELECT COUNT(*) FROM stg_card_benefit WHERE rate_percent IS NOT NULL")).isPositive();
        assertThat(count("SELECT COUNT(*) FROM stg_card_catalog WHERE source_snapshot_date = '2026-10-06'")).isEqualTo(3);
        // 따옴표와 줄바꿈이 들어 있는 값도 잘리지 않고 들어간다
        assertThat(count("SELECT COUNT(*) FROM stg_card_benefit WHERE conditions LIKE '%\"포함\"%' AND INSTR(conditions, CHAR(10)) > 0"))
            .isEqualTo(3);
        // 빈칸이었던 공통 코드 컬럼은 NULL이다
        assertThat(count("SELECT COUNT(*) FROM ref_common_standard WHERE source_alias_pattern IS NULL")).isEqualTo(2);
    }

    @Test
    void keepsCardIdAsStringWithLeadingZero() {
        service.loadAll(fixtures);

        assertThat(count("SELECT COUNT(*) FROM stg_card_catalog WHERE card_id LIKE '0%'")).isPositive();
    }

    @Test
    void failsWithColumnNameWhenCsvLacksRequiredColumn(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("common_standards.csv"), "standard_kind,code,name\nCATEGORY,DINING,음식점\n",
            StandardCharsets.UTF_8);
        StagingTable standards = StagingTables.ALL.stream()
            .filter(t -> t.table().equals("ref_common_standard")).findFirst().orElseThrow();

        assertThatThrownBy(() -> loader.load(dir.resolve("common_standards.csv"), standards))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("source_alias_pattern");
        assertThat(count("SELECT COUNT(*) FROM ref_common_standard")).isZero();
    }

    @Test
    void failsBeforeLoadingAnythingWhenACsvFileIsMissing(@TempDir Path dir) throws IOException {
        Files.copy(fixtures.resolve("common_standards.csv"), dir.resolve("common_standards.csv"));

        assertThatThrownBy(() -> service.loadAll(dir))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CSV 파일이 없습니다");
        assertThat(count("SELECT COUNT(*) FROM ref_common_standard")).isZero();
    }

    @Test
    void marksOnlyListedCardsAsMvpAndReportsUnmatched(@TempDir Path dir) throws IOException {
        service.loadAll(fixtures);
        List<String> first = jdbc.queryForObject(
            "SELECT CONCAT(issuer_code, ',', card_id) FROM stg_card_catalog ORDER BY id LIMIT 1",
            (rs, i) -> List.of(rs.getString(1).split(",")));
        Path mvp = dir.resolve("mvp_cards.csv");
        Files.writeString(mvp, "issuer_code,card_id\n" + first.get(0) + "," + first.get(1) + "\nshinhan,NOT_CRAWLED\n",
            StandardCharsets.UTF_8);

        MvpResult result = service.selectMvpCards(mvp);

        assertThat(result.applied()).isTrue();
        assertThat(result.selected()).isEqualTo(1);
        assertThat(result.unmatched()).containsExactly("shinhan/NOT_CRAWLED");
        assertThat(count("SELECT COUNT(*) FROM stg_card_catalog WHERE mvp_selected = 'SELECTED' AND sample_owner = 'SAMPLE_USER'"))
            .isEqualTo(1);
    }

    @Test
    void mvpSelectionSurvivesReloadingTheCatalogCsv(@TempDir Path dir) throws IOException {
        service.loadAll(fixtures);
        String issuer = jdbc.queryForObject("SELECT issuer_code FROM stg_card_catalog ORDER BY id LIMIT 1", String.class);
        String cardId = jdbc.queryForObject("SELECT card_id FROM stg_card_catalog ORDER BY id LIMIT 1", String.class);
        service.selectMvpCards(writeMvp(dir, issuer + "," + cardId));

        service.loadAll(fixtures); // mvp_cards.csv가 폴더에 없으므로 표시를 건드리지 않아야 한다

        assertThat(count("SELECT COUNT(*) FROM stg_card_catalog WHERE mvp_selected = 'SELECTED'")).isEqualTo(1);
    }

    @Test
    void doesNotTouchMvpWhenMvpFileIsMissing(@TempDir Path dir) {
        service.loadAll(fixtures);
        jdbc.update("UPDATE stg_card_catalog SET mvp_selected = 'SELECTED' LIMIT 1");

        MvpResult result = service.selectMvpCards(dir.resolve("mvp_cards.csv"));

        assertThat(result.applied()).isFalse();
        assertThat(count("SELECT COUNT(*) FROM stg_card_catalog WHERE mvp_selected = 'SELECTED'")).isEqualTo(1);
    }

    @Test
    void validatorCountsRowsWhoseCardIsNotInCatalog() {
        StagingTable reviewQueue = StagingTables.ALL.stream()
            .filter(t -> t.table().equals("stg_review_queue")).findFirst().orElseThrow();
        loader.load(fixtures.resolve("review_queue.csv"), reviewQueue); // 카탈로그는 비어 있다

        List<CheckResult> checks = validator.validate();

        assertThat(checks).extracting(CheckResult::name).contains("review_queue_without_catalog", "catalog_hash_mismatch");
        assertThat(checks).filteredOn(c -> c.name().equals("review_queue_without_catalog"))
            .singleElement().extracting(CheckResult::count).isEqualTo(3L);
    }

    @Test
    void promotesOnlyMvpCardsWithTheirBenefitsTiersAndExclusions(@TempDir Path dir) throws IOException {
        service.loadAll(fixtures);
        service.selectMvpCards(writeMvp(dir, "v1,00001\nv2,00002")); // 3번 카드는 MVP가 아니다

        CardPromotionService.Result result = promotion.promote();

        assertThat(result.mvpSelected()).isEqualTo(2);
        assertThat(result.cardProducts()).isEqualTo(2);
        assertThat(result.benefitRules()).isEqualTo(2);
        assertThat(result.performanceTiers()).isEqualTo(2); // 실적 조건이 있는 혜택마다 한 행
        assertThat(result.performanceExclusions()).isEqualTo(1); // 조건 3개 중 EXCLUSION이 있고 MVP 카드인 것은 1개
        assertThat(count("SELECT COUNT(*) FROM card_products WHERE card_id = '00003'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM performance_exclusions WHERE applies_to = 'PERFORMANCE'")).isEqualTo(1);
    }

    @Test
    void marksSuspendedCardsAsNotRecommendableButStillPromotesThem(@TempDir Path dir) throws IOException {
        service.loadAll(fixtures);
        service.selectMvpCards(writeMvp(dir, "v1,00001\nv2,00002"));

        promotion.promote();

        assertThat(count("SELECT COUNT(*) FROM card_products WHERE card_id = '00001' AND recommendable = 1")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM card_products WHERE card_id = '00002' AND recommendable = 0")).isEqualTo(1);
    }

    @Test
    void promotingAgainKeepsHumanApprovalAndRefreshesOtherValues(@TempDir Path dir) throws IOException {
        service.loadAll(fixtures);
        service.selectMvpCards(writeMvp(dir, "v1,00001"));
        promotion.promote();
        jdbc.update("UPDATE benefit_rules SET rule_engine_ready = 1"); // 사람이 원문과 대조해 확인했다고 표시
        jdbc.update("UPDATE stg_card_benefit SET rate_percent = 9.90 WHERE benefit_id = 'v1'");

        CardPromotionService.Result second = promotion.promote();

        assertThat(second.benefitRules()).isEqualTo(1); // 중복 없음
        assertThat(second.cardProducts()).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM benefit_rules WHERE rule_engine_ready = 1")).isEqualTo(1); // 확인 표시 유지
        assertThat(count("SELECT COUNT(*) FROM benefit_rules WHERE rate_percent = 9.90")).isEqualTo(1); // 값은 갱신
    }

    @Test
    void linksPromotedCardsBackToRawCatalog(@TempDir Path dir) throws IOException {
        jdbc.update("INSERT INTO card_catalog_raw (issuer, card_id, card_name, card_type, status, raw_text, collected_at) "
            + "VALUES ('v1', '00001', 'x', 'credit', 'listed', 'text', NOW())");
        service.loadAll(fixtures);
        service.selectMvpCards(writeMvp(dir, "v1,00001"));

        CardPromotionService.Result result = promotion.promote();

        assertThat(result.rawCatalogLinked()).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM card_catalog_raw r JOIN card_products p ON p.id = r.structured_product_id "
            + "WHERE r.issuer = 'v1' AND r.review_status = 'structured'")).isEqualTo(1);
    }

    @Test
    void promotesNothingWhenNoCardIsSelected() {
        service.loadAll(fixtures);

        CardPromotionService.Result result = promotion.promote();

        assertThat(result.mvpSelected()).isZero();
        assertThat(result.cardProducts()).isZero();
        assertThat(result.benefitRules()).isZero();
        assertThat(result.performanceExclusions()).isZero();
    }

    @Test
    void loadsBlankAndLongValuesLikeTheAddedCards(@TempDir Path sparse) throws IOException {
        writeFixtures(sparse, true);

        Report first = service.loadAll(sparse);

        assertThat(first.loads()).allSatisfy(r -> {
            assertThat(r.fileRows()).isEqualTo(3);
            assertThat(r.tableRows()).isEqualTo(3);
        });
        // 빈칸은 NULL로 저장된다 (0이나 기본값으로 채우지 않는다)
        assertThat(count("SELECT COUNT(*) FROM stg_card_catalog WHERE card_status IS NULL AND source_table_count IS NULL"))
            .isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM stg_card_benefit WHERE category_name IS NULL AND target_raw IS NULL "
            + "AND monthly_limit_status IS NULL")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM stg_source_scope WHERE html_line IS NULL")).isEqualTo(3);
        // 길어진 값도 잘리지 않고 그대로 저장된다
        assertThat(count("SELECT COUNT(*) FROM stg_card_benefit WHERE CHAR_LENGTH(extraction_method) = 39 "
            + "AND reward_unit = 'FREE_SERVICE' AND CHAR_LENGTH(amount_basis) = 32")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM stg_card_condition WHERE CHAR_LENGTH(condition_id) = 31")).isEqualTo(3);
        // 키 컬럼(target_code)의 빈칸은 NULL이 아니라 ''로 저장된다
        assertThat(count("SELECT COUNT(*) FROM stg_target_name WHERE target_code = ''")).isEqualTo(3);

        // 같은 CSV를 다시 넣어도 코드가 빈 줄이 중복되지 않는다 (NULL이었다면 중복됐다)
        Report second = service.loadAll(sparse);
        assertThat(second.loads()).allSatisfy(r -> assertThat(r.tableRows()).isEqualTo(3));

        // 코드가 비어 있는 연결은 "코드표에 없는 코드"로 세지 않고, 별도 참고 항목으로만 센다
        assertThat(second.checks()).filteredOn(c -> c.name().equals("target_name_without_standard"))
            .singleElement().extracting(CheckResult::count).isEqualTo(0L);
        assertThat(second.checks()).filteredOn(c -> c.name().equals("target_name_without_code"))
            .singleElement().extracting(CheckResult::count).isEqualTo(3L);
    }

    @Test
    void promotesCardsWhoseStatusAndTargetsAreBlank(@TempDir Path sparse, @TempDir Path dir) throws IOException {
        writeFixtures(sparse, true);
        service.loadAll(sparse);
        service.selectMvpCards(writeMvp(dir, "v1,00001\nv2,00002"));

        CardPromotionService.Result first = promotion.promote();
        CardPromotionService.Result second = promotion.promote();

        assertThat(first.cardProducts()).isEqualTo(2);
        assertThat(first.benefitRules()).isEqualTo(2);
        assertThat(first.performanceExclusions()).isEqualTo(1);
        // card_status가 비어 있으면 미확정이라 신규 추천 후보에서는 뺀다
        assertThat(count("SELECT COUNT(*) FROM card_products WHERE card_status IS NULL AND recommendable = 0")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM benefit_rules WHERE category_name IS NULL AND target_raw IS NULL "
            + "AND monthly_limit_status IS NULL")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM performance_exclusions WHERE CHAR_LENGTH(source_condition_id) = 31"))
            .isEqualTo(1);
        // 다시 승격해도 중복되지 않는다
        assertThat(second.cardProducts()).isEqualTo(2);
        assertThat(second.benefitRules()).isEqualTo(2);
    }

    /**
     * 기존 319종 위에 추가 카드 12장 CSV를 얹어 적재하고 승격한다.
     * STAGING_CSV_DIR(319종)과 STAGING_ADDED_DIR(추가 12장 폴더)이 모두 설정된 경우에만 실행한다.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "STAGING_CSV_DIR", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "STAGING_ADDED_DIR", matches = ".+")
    void loadsTheAddedCardsOnTopOfTheBaseSet(@TempDir Path dir) throws IOException {
        service.loadAll(Path.of(System.getenv("STAGING_CSV_DIR")));
        Report added = service.loadAll(Path.of(System.getenv("STAGING_ADDED_DIR")));

        added.loads().forEach(r -> System.out.printf("[added] %s -> %s: 파일 %d행, 테이블 전체 %d행, 파일 내 중복 키 %d%n",
            r.csvFile(), r.table(), r.fileRows(), r.tableRows(), r.duplicateKeys()));
        added.checks().forEach(c -> System.out.printf("[added-check] %s = %d%n", c.name(), c.count()));

        assertThat(count("SELECT COUNT(*) FROM stg_card_catalog")).isEqualTo(331); // 319 + 12
        assertThat(count("SELECT COUNT(*) FROM stg_card_benefit")).isEqualTo(4126 + 21);
        assertThat(count("SELECT COUNT(*) FROM stg_card_condition")).isEqualTo(11164 + 24);
        assertThat(count("SELECT COUNT(*) FROM stg_source_scope")).isEqualTo(1545 + 21);
        assertThat(count("SELECT COUNT(*) FROM stg_special_case")).isEqualTo(129 + 5);

        // 팀원 카드 7장(혜택이 있는 카드 6장 + 혜택이 아직 없는 신한 1장)을 MVP로 승격한다
        service.selectMvpCards(writeMvp(dir, "nh,90010036\nkb,01664\nwoori,839035\nbc,nf-card-02\n"
            + "bc,1000027768\nkakaobank,friends_kpass\nshinhan,1225714"));
        CardPromotionService.Result result = promotion.promote();

        System.out.printf("[added-promotion] %s%n", result);
        assertThat(result.cardProducts()).isEqualTo(7);
        assertThat(result.benefitRules()).isEqualTo(5 + 2 + 12 + 3 + 2 + 2); // 신한은 혜택 0행
        assertThat(count("SELECT COUNT(*) FROM benefit_rules WHERE rule_engine_ready = 1")).isZero();
        // 발급 중단 카드(우리 K-패스)는 승격되지만 신규 추천 후보는 아니다
        assertThat(count("SELECT COUNT(*) FROM card_products WHERE card_id = '839035' AND recommendable = 0")).isEqualTo(1);
    }

    /**
     * 실제 CSV 전체를 넣어 건수를 확인한다. STAGING_CSV_DIR이 설정된 경우에만 실행한다. (평소 CI에서는 건너뜀)
     * 실행: STAGING_CSV_DIR=C:\경로\319종_카드혜택_전체자료 ./gradlew test --tests "*StagingServiceTest.loadsRealCsvFiles"
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "STAGING_CSV_DIR", matches = ".+")
    void loadsRealCsvFiles() {
        Report report = service.loadAll(Path.of(System.getenv("STAGING_CSV_DIR")));

        assertThat(report.loads()).extracting(LoadResult::table, LoadResult::fileRows, LoadResult::tableRows,
                LoadResult::duplicateKeys)
            .containsExactlyInAnyOrder(
                tuple("stg_card_catalog", 319, 319L, 0),
                tuple("stg_source_scope", 1545, 1545L, 0),
                tuple("stg_card_benefit", 4126, 4126L, 0),
                tuple("stg_card_condition", 11164, 11164L, 0),
                tuple("ref_common_standard", 84, 84L, 0),
                tuple("stg_target_name", 1046, 1046L, 0),
                tuple("stg_review_queue", 311, 311L, 0),
                tuple("stg_special_case", 129, 129L, 0));
    }

    /** 실제 CSV로 MVP 카드 1장(NH 올바른NEW HAVE체크카드)을 승격한다. STAGING_CSV_DIR이 있을 때만 실행한다. */
    @Test
    @EnabledIfEnvironmentVariable(named = "STAGING_CSV_DIR", matches = ".+")
    void promotesARealMvpCard(@TempDir Path dir) throws IOException {
        service.loadAll(Path.of(System.getenv("STAGING_CSV_DIR")));
        service.selectMvpCards(writeMvp(dir, "nh,90010036"));

        CardPromotionService.Result result = promotion.promote();

        assertThat(result.cardProducts()).isEqualTo(1);
        assertThat(result.benefitRules()).isEqualTo(5); // 혜택 5행
        assertThat(result.performanceExclusions()).isPositive();
        assertThat(result.rawCatalogLinked()).isEqualTo(1); // V2로 들어간 원문 행과 연결
        assertThat(count("SELECT COUNT(*) FROM card_products WHERE recommendable = 1")).isEqualTo(1); // 발급 중인 결제 카드
        assertThat(count("SELECT COUNT(*) FROM benefit_rules WHERE rule_engine_ready = 1")).isZero(); // 사람 확인 전
    }

    /**
     * 적재기 선언 목록(StagingTables)의 컬럼대로 가짜 CSV를 만든다. 첫 글자에 BOM을 붙여 실제 파일과 같은 형태로 만든다.
     * 값은 "v1", "v2" 같은 지어낸 문자열이라 크롤링 원문이 들어가지 않는다.
     */
    private static void writeFixtures(Path dir) throws IOException {
        writeFixtures(dir, false);
    }

    /**
     * @param sparse true면 추가 카드 12장 CSV처럼 일부 칸을 비우고 일부 값을 길게 만든다.
     *               (빈칸은 "미확정"이라 정상이고, 새 ID/코드는 기존보다 길다)
     */
    private static void writeFixtures(Path dir, boolean sparse) throws IOException {
        for (StagingTable table : StagingTables.ALL) {
            StringBuilder csv = new StringBuilder("﻿").append(String.join(",", table.columns())).append('\n');
            for (int row = 1; row <= 3; row++) {
                List<String> cells = new ArrayList<>();
                for (String column : table.columns()) {
                    cells.add(sparse ? sparseCell(table, column, row) : cell(table, column, row));
                }
                csv.append(String.join(",", cells)).append('\n');
            }
            Files.writeString(dir.resolve(table.csvFile()), csv, StandardCharsets.UTF_8);
        }
    }

    /** 추가 카드 CSV에서 실제로 나온 빈칸과 긴 값을 흉내 낸다. 나머지 칸은 {@link #cell}과 같다. */
    private static String sparseCell(StagingTable table, String column, int row) {
        return switch (column) {
            // 실제 CSV에서 비어 있던 칸들 (이전에는 NOT NULL이라 적재가 실패했다)
            case "card_status", "source_table_count", "category_name", "target_raw", "monthly_limit_status",
                 "html_line", "target_code" -> "";
            // 실제 CSV에서 기존 한도를 넘었던 값들
            case "reward_unit" -> "FREE_SERVICE"; // 12자 (기존 한도 10자)
            case "amount_basis" -> "건당 할인대상 결제금액 상한 20000원; 정액 할인 아님"; // 32자 (기존 한도 30자)
            case "extraction_method" -> "MANUAL_OFFICIAL_GUIDE_AND_RENDERED_PAGE"; // 39자 (기존 한도 30자)
            case "condition_id" -> "mvp_kb_01664_c_a32d2ce1305d850" + row; // 31자 (기존 한도 20자)
            default -> cell(table, column, row);
        };
    }

    private static String cell(StagingTable table, String column, int row) {
        return switch (column) {
            case "card_id" -> "0000" + row; // 앞자리 0이 유지되는지 확인
            case "card_status" -> row == 2 ? "issuance_suspended" : "listed"; // 2번 카드는 발급 중단
            case "product_class" -> "PAYMENT_CARD";
            case "condition_type" -> row == 1 ? "PERFORMANCE|EXCLUSION" : "LIMIT"; // 제외 조건은 1번 카드만
            case "per_transaction_min_krw", "merchants" -> ""; // 전 행 빈칸 -> NULL
            case "source_alias_pattern" -> row == 1 ? "alias-1" : ""; // 일부만 빈칸
            case "conditions" -> "\"따옴표 \"\"포함\"\" 와\n줄바꿈 " + row + "\""; // CSV 따옴표/줄바꿈 규칙
            default -> switch (table.typeOf(column)) {
                case STRING -> "v" + row; // 가장 짧은 컬럼(VARCHAR(10))에도 들어가도록 짧게. 행마다 달라서 키가 겹치지 않는다
                case INT -> "1";
                case LONG -> "1000";
                case DECIMAL -> "0.7";
                case DATE -> "2026-10-06";
                case BOOL -> "False";
            };
        };
    }

    private static Path writeMvp(Path dir, String line) throws IOException {
        Path file = dir.resolve("mvp_cards.csv");
        Files.writeString(file, "issuer_code,card_id\n" + line + "\n", StandardCharsets.UTF_8);
        return file;
    }

    private static long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
