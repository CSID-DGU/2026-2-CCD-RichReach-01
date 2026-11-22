package com.richreach.card.staging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.richreach.SharedMysql;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 카드 이미지 주소 적재({@link CardImageLoader})를 일회용 MySQL에 migration(V1~)만 적용해 확인한다. 테스트 CSV는 실행할 때
 * 지어낸 값으로 만든다. (저장소에 CSV를 두지 않는다)
 */
class CardImageLoaderTest {

    private static final String HEADER = "no,issuer_code,issuer,card_id,card_name,image_url,image_type,url_status,"
        + "http_status,content_type,image_source_page,product_page,official_alternative_url,note";
    private static final String SHA = "0".repeat(64);

    @TempDir
    static Path fixtures;

    static final SharedMysql.Database DB = SharedMysql.newDatabase();

    static JdbcTemplate jdbc;
    static CardImageLoader loader;

    @BeforeAll
    static void migrateAndWire() {
        Flyway.configure().dataSource(DB.jdbcUrl(), DB.username(), DB.password()).load().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(DB.jdbcUrl(), DB.username(), DB.password());
        jdbc = new JdbcTemplate(dataSource);
        loader = new CardImageLoader(jdbc, new DataSourceTransactionManager(dataSource));
    }

    @BeforeEach
    void resetProducts() {
        jdbc.update("DELETE FROM card_products");
        product("kb", "01664");
        product("kb", "00001");
        product("woori", "839035");
        product("shinhan", "1225714");
    }

    private static void product(String issuerCode, String cardId) {
        jdbc.update("INSERT INTO card_products (issuer_code, issuer, card_id, card_name, card_type, card_status, "
                + "product_class, recommendable, source_snapshot_date, source_text_sha256, review_status) "
                + "VALUES (?, '테스트카드사', ?, '테스트 카드', 'credit', 'listed', 'PAYMENT_CARD', TRUE, '2026-10-01', ?, "
                + "'REVIEWED_SUBSET')", issuerCode, cardId, SHA);
    }

    private static String row(int no, String issuerCode, String cardId, String url, String type, String status) {
        return no + "," + issuerCode + ",카드사," + cardId + ",카드," + url + "," + type + "," + status
            + ",200,image/png,https://page.example,https://product.example,,비고";
    }

    private static Path csv(String name, String... lines) throws IOException {
        Path file = fixtures.resolve(name);
        // 실제 파일처럼 BOM을 붙인다.
        Files.writeString(file, "﻿" + String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        return file;
    }

    private static String imageOf(String issuerCode, String cardId) {
        return jdbc.queryForObject("SELECT image_url FROM card_products WHERE issuer_code = ? AND card_id = ?",
            String.class, issuerCode, cardId);
    }

    @Test
    @DisplayName("공식 앞면 이미지만 카드 상품에 반영하고, 제3자 이미지·http·확인 안 된 행은 쓰지 않는다")
    void appliesOnlyOfficialVerifiedHttpsImages() throws IOException {
        Path file = csv("official.csv", HEADER,
            row(1, "kb", "01664", "https://img.example/kb.png", "CARD_FRONT", "VERIFIED_IMAGE"),
            row(2, "woori", "839035", "https://blog.example/third.jpg", "THIRD_PARTY_CARD_FRONT", "VERIFIED_IMAGE"),
            row(3, "kb", "00001", "http://img.example/insecure.png", "CARD_FRONT", "VERIFIED_IMAGE"),
            row(4, "shinhan", "1225714", "https://img.example/shinhan.webp", "CARD_FRONT", "BROKEN"));

        CardImageLoader.Result result = loader.load(file);

        assertThat(result).isEqualTo(new CardImageLoader.Result(true, 1, 0, 3));
        assertThat(imageOf("kb", "01664")).isEqualTo("https://img.example/kb.png");
        assertThat(imageOf("woori", "839035")).isNull();
        assertThat(imageOf("kb", "00001")).isNull();
        assertThat(imageOf("shinhan", "1225714")).isNull();
    }

    @Test
    @DisplayName("카드 상품에 없는 카드는 건너뛰고 건수만 센다. 카드 ID의 앞자리 0은 그대로 맞춘다")
    void skipsCardsWithoutAProduct() throws IOException {
        Path file = csv("unknown.csv", HEADER,
            row(1, "kb", "01664", "https://img.example/kb.png", "CARD_FRONT", "VERIFIED_IMAGE"),
            row(2, "kb", "1664", "https://img.example/other.png", "CARD_FRONT", "VERIFIED_IMAGE"),
            row(3, "hyundai", "ABC", "https://img.example/hd.png", "CARD_FRONT", "VERIFIED_IMAGE"));

        CardImageLoader.Result result = loader.load(file);

        assertThat(result).isEqualTo(new CardImageLoader.Result(true, 1, 2, 0));
        assertThat(imageOf("kb", "01664")).isEqualTo("https://img.example/kb.png");
    }

    @Test
    @DisplayName("같은 파일을 다시 적재해도 결과가 같고, 파일에 없는 카드의 기존 이미지는 지우지 않는다")
    void isIdempotentAndKeepsExistingImages() throws IOException {
        loader.load(csv("first.csv", HEADER,
            row(1, "kb", "01664", "https://img.example/kb.png", "CARD_FRONT", "VERIFIED_IMAGE"),
            row(2, "kb", "00001", "https://img.example/kb2.png", "CARD_FRONT", "VERIFIED_IMAGE")));
        Path second = csv("second.csv", HEADER,
            row(1, "kb", "01664", "https://img.example/kb-new.png", "CARD_FRONT", "VERIFIED_IMAGE"));

        CardImageLoader.Result once = loader.load(second);
        CardImageLoader.Result twice = loader.load(second);

        assertThat(twice).isEqualTo(once);
        assertThat(imageOf("kb", "01664")).isEqualTo("https://img.example/kb-new.png");
        assertThat(imageOf("kb", "00001")).isEqualTo("https://img.example/kb2.png");
    }

    @Test
    @DisplayName("컬럼에 들어가지 않는 긴 주소는 쓰지 않는다")
    void skipsTooLongUrls() throws IOException {
        String tooLong = "https://img.example/" + "a".repeat(CardImageLoader.MAX_URL_LENGTH);
        Path file = csv("long.csv", HEADER, row(1, "kb", "01664", tooLong, "CARD_FRONT", "VERIFIED_IMAGE"));

        CardImageLoader.Result result = loader.load(file);

        assertThat(result).isEqualTo(new CardImageLoader.Result(true, 0, 0, 1));
        assertThat(imageOf("kb", "01664")).isNull();
    }

    @Test
    @DisplayName("파일이 없으면 아무것도 바꾸지 않는다")
    void doesNothingWithoutTheFile() {
        jdbc.update("UPDATE card_products SET image_url = 'https://img.example/keep.png' WHERE card_id = '01664'");

        CardImageLoader.Result result = loader.load(fixtures.resolve("missing.csv"));

        assertThat(result).isEqualTo(new CardImageLoader.Result(false, 0, 0, 0));
        assertThat(imageOf("kb", "01664")).isEqualTo("https://img.example/keep.png");
    }

    @Test
    @DisplayName("필수 컬럼이 없으면 아무것도 반영하지 않고 중단한다")
    void failsOnAMissingColumn() throws IOException {
        Path file = csv("nocolumn.csv", "no,issuer_code,card_id,image_url,image_type",
            "1,kb,01664,https://img.example/kb.png,CARD_FRONT");

        assertThatThrownBy(() -> loader.load(file)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("url_status");
        assertThat(imageOf("kb", "01664")).isNull();
    }

}
