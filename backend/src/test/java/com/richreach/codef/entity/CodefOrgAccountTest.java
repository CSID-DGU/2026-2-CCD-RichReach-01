package com.richreach.codef.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CodefOrgAccountTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0, 0);

    @Test
    @DisplayName("연결한 카드사 계정은 카드(CD)·개인(P)·아이디 로그인(1)·ACTIVE로 만들어진다")
    void connectedFactoryFixesTheDefaults() {
        CodefOrgAccount account = CodefOrgAccount.connected(null, "0301", NOW);

        assertThat(account.getOrganization()).isEqualTo("0301");
        assertThat(account.getBusinessType()).isEqualTo("CD");
        assertThat(account.getClientType()).isEqualTo("P");
        assertThat(account.getLoginType()).isEqualTo("1");
        assertThat(account.getStatus()).isEqualTo(CodefOrgAccount.STATUS_ACTIVE);
        assertThat(account.getCreatedAt()).isEqualTo(NOW);
        assertThat(account.isConnected()).isTrue();
    }

    @Test
    @DisplayName("잠긴 계정은 연결로 보지 않고 isLocked가 true다")
    void lockedAccountIsNotConnected() {
        CodefOrgAccount account = new CodefOrgAccount(null, null, "0301", "CD", "P", "1",
            CodefOrgAccount.STATUS_LOCKED, NOW);

        assertThat(account.isLocked()).isTrue();
        assertThat(account.isConnected()).isFalse();
    }

    @Test
    @DisplayName("markConnected는 상태를 ACTIVE로 되돌리고 마지막 오류 코드를 지운다")
    void markConnectedResetsStateAndError() {
        CodefOrgAccount account = new CodefOrgAccount(null, null, "0301", "CD", "P", "1", "ERROR", NOW);
        account.recordSyncError("CF-12345");

        account.markConnected();

        assertThat(account.getStatus()).isEqualTo(CodefOrgAccount.STATUS_ACTIVE);
        assertThat(account.getLastErrorCode()).isNull();
    }

}
