package com.richreach.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 시연 데이터를 만들어도 되는 DB(로컬)인지 가려내는 규칙을 확인한다. 공용·운영 DB를 놓치지 않는 것이 핵심이다. */
class DemoSeedGuardTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("로컬 DB 접속 주소는 허용한다")
    @ValueSource(strings = {
        "jdbc:mysql://localhost:3307/richreach?serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
        "jdbc:mysql://localhost/richreach",
        "jdbc:mysql://127.0.0.1:3306/richreach",
        "jdbc:mysql://mysql:3306/richreach",
        "jdbc:mysql://[::1]:3306/richreach",
        "JDBC:MYSQL://LOCALHOST:3306/richreach",
        "  jdbc:mysql://localhost:3306/richreach  ",
        "jdbc:mysql://someone@localhost:3306/richreach"})
    void allowsLocalDatabases(String url) {
        assertThat(DemoSeedGuard.isLocal(url)).isTrue();
        DemoSeedGuard.requireLocalDatabase(url);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("로컬이 아니거나 알 수 없는 접속 주소는 막는다")
    @ValueSource(strings = {
        "jdbc:mysql://my-db.abc123.ap-northeast-2.rds.amazonaws.com:3306/richreach",
        "jdbc:mysql://10.0.0.5:3306/richreach",
        "jdbc:mysql://192.168.0.10:3306/richreach",
        "jdbc:mysql://localhost.example.com:3306/richreach",
        "jdbc:mysql://mysql.example.com:3306/richreach",
        "jdbc:mysql://example.com:3306/localhost",
        "jdbc:mysql://example.com:3306/richreach?host=localhost",
        "jdbc:mysql://localhost@example.com:3306/richreach",
        "jdbc:mysql://localhost,example.com:3306/richreach",
        "jdbc:mysql://address=(host=example.com)(port=3306)/richreach",
        "jdbc:postgresql://localhost:5432/richreach",
        "localhost",
        ""})
    void blocksNonLocalOrUnknownDatabases(String url) {
        assertThat(DemoSeedGuard.isLocal(url)).isFalse();
        assertThatThrownBy(() -> DemoSeedGuard.requireLocalDatabase(url)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("접속 대상을 알 수 없으면(null) 막는다")
    void blocksNull() {
        assertThat(DemoSeedGuard.isLocal(null)).isFalse();
        assertThatThrownBy(() -> DemoSeedGuard.requireLocalDatabase(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("막을 때 오류 메시지에 접속 주소를 적지 않는다")
    void errorMessageDoesNotRevealTheHost() {
        String url = "jdbc:mysql://secret-db.abc123.ap-northeast-2.rds.amazonaws.com:3306/richreach";

        assertThatThrownBy(() -> DemoSeedGuard.requireLocalDatabase(url))
            .isInstanceOf(IllegalStateException.class)
            .satisfies(e -> assertThat(e.getMessage()).doesNotContain("secret-db").doesNotContain("amazonaws"));
    }

}
