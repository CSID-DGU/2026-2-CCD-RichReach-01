package com.richreach.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V12까지 적용된 DB에 사용자가 이미 있는 상태에서 V13을 적용했을 때를 확인한다.
 * 앱 전체를 띄우지 않고 일회용 MySQL에 Flyway만 적용한다.
 */
@Testcontainers
class UserPhoneMigrationTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    private JdbcTemplate jdbc;

    private Flyway flyway(String target) {
        return Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .target(target)
            .load();
    }

    @BeforeEach
    void migrateToV12() {
        flyway("latest").clean();
        flyway("12").migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
    }

    private void insertUser(String email, String phone) {
        jdbc.update("INSERT INTO users (email, password_hash, name, phone, user_type, terms_agreed_at, created_at, "
            + "updated_at) VALUES (?, 'hash', '테스터', ?, 'REAL', NOW(), NOW(), NOW())", email, phone);
    }

    private String phoneOf(String email) {
        return jdbc.queryForObject("SELECT phone FROM users WHERE email = ?", String.class, email);
    }

    @Test
    @DisplayName("V13을 적용하면 기존 사용자의 전화번호에서 하이픈과 공백이 없어진다")
    void normalizesExistingPhones() {
        insertUser("hyphen@example.com", "010-1111-2222");
        insertUser("space@example.com", "010 3333 4444");
        insertUser("plain@example.com", "01055556666");

        flyway("latest").migrate();

        assertThat(phoneOf("hyphen@example.com")).isEqualTo("01011112222");
        assertThat(phoneOf("space@example.com")).isEqualTo("01033334444");
        assertThat(phoneOf("plain@example.com")).isEqualTo("01055556666");
    }

    @Test
    @DisplayName("정규화한 뒤에는 같은 번호로 다시 넣을 수 없다")
    void uniqueConstraintAppliesAfterNormalization() {
        insertUser("hyphen@example.com", "010-1111-2222");
        flyway("latest").migrate();

        assertThatThrownBy(() -> insertUser("new@example.com", "01011112222"))
            .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("표기만 다른 같은 번호가 이미 있으면 V13이 실패해서 데이터를 먼저 정리하게 한다")
    void failsWhenNormalizedPhonesCollide() {
        insertUser("a@example.com", "010-1111-2222");
        insertUser("b@example.com", "01011112222");

        assertThatThrownBy(() -> flyway("latest").migrate()).isInstanceOf(FlywayException.class);
    }

    @Test
    @DisplayName("사용자가 없는 DB에도 문제없이 적용된다")
    void appliesToEmptyUsersTable() {
        flyway("latest").migrate();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
    }

}
