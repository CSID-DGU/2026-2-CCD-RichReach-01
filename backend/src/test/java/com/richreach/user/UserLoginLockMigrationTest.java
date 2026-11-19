package com.richreach.user;

import com.richreach.SharedMysql;
import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** V13까지 적용된 DB에 사용자가 이미 있는 상태에서 V14(로그인 실패 잠금 컬럼)를 적용해도 안전한지 확인한다. */
class UserLoginLockMigrationTest {

    /** 공유 MySQL 컨테이너 안에 이 테스트만 쓰는 새 DB. (flyway clean으로 지워도 다른 테스트에 영향이 없다) */
    static final SharedMysql.Database DB = SharedMysql.newDatabase();

    private JdbcTemplate jdbc;

    private Flyway flyway(String target) {
        return Flyway.configure()
            .dataSource(DB.jdbcUrl(), DB.username(), DB.password())
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .target(target)
            .load();
    }

    @BeforeEach
    void migrateToV13() {
        flyway("latest").clean();
        flyway("13").migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(DB.jdbcUrl(), DB.username(), DB.password()));
    }

    @Test
    @DisplayName("V14를 적용하면 기존 사용자는 실패 횟수 0, 잠금 없음으로 시작한다")
    void existingUsersStartUnlocked() {
        jdbc.update("INSERT INTO users (email, password_hash, name, phone, user_type, terms_agreed_at, created_at, "
            + "updated_at) VALUES ('old@example.com', 'hash', '기존', '01011112222', 'REAL', NOW(), NOW(), NOW())");

        flyway("latest").migrate();

        assertThat(jdbc.queryForObject("SELECT failed_login_count FROM users WHERE email = 'old@example.com'",
            Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT locked_until FROM users WHERE email = 'old@example.com'",
            java.sql.Timestamp.class)).isNull();
    }

}
