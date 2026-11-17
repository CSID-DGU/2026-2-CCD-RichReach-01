package com.richreach;

import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 통합 테스트의 DB 연결. 컨텍스트마다 MySQL 컨테이너를 따로 띄우지 않고, JVM이 공유하는 컨테이너 안에 이 컨텍스트만의 새 DB를
 * 만들어 연결한다. ({@link SharedMysql} 참고) Flyway와 JPA, JdbcTemplate은 이 연결 정보로 같은 DB를 쓴다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    JdbcConnectionDetails jdbcConnectionDetails() {
        SharedMysql.Database database = SharedMysql.newDatabase();
        return new JdbcConnectionDetails() {

            @Override
            public String getUsername() {
                return database.username();
            }

            @Override
            public String getPassword() {
                return database.password();
            }

            @Override
            public String getJdbcUrl() {
                return database.jdbcUrl();
            }

        };
    }

}
