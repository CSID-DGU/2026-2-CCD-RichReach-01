package com.richreach.card.staging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
    }

    @BeforeEach
    void clearStagingTables() {
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

    /**
     * 적재기 선언 목록(StagingTables)의 컬럼대로 가짜 CSV를 만든다. 첫 글자에 BOM을 붙여 실제 파일과 같은 형태로 만든다.
     * 값은 "v1", "v2" 같은 지어낸 문자열이라 크롤링 원문이 들어가지 않는다.
     */
    private static void writeFixtures(Path dir) throws IOException {
        for (StagingTable table : StagingTables.ALL) {
            StringBuilder csv = new StringBuilder("﻿").append(String.join(",", table.columns())).append('\n');
            for (int row = 1; row <= 3; row++) {
                List<String> cells = new ArrayList<>();
                for (String column : table.columns()) {
                    cells.add(cell(table, column, row));
                }
                csv.append(String.join(",", cells)).append('\n');
            }
            Files.writeString(dir.resolve(table.csvFile()), csv, StandardCharsets.UTF_8);
        }
    }

    private static String cell(StagingTable table, String column, int row) {
        return switch (column) {
            case "card_id" -> "0000" + row; // 앞자리 0이 유지되는지 확인
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
