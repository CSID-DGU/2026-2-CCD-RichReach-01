package com.richreach.codef.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.richreach.codef.dto.AutoSyncResponse;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

/** 자동 동기화 설정 변경이 다른 카드 연동 요청과 같은 사용자 잠금 아래에서 일어나는지 확인한다. */
class ConnectionAutoSyncServiceTest {

    private static final long USER = 1L;
    private static final long ACCOUNT = 5L;

    private ConnectionUserLock userLock;
    private CodefOrgAccountRepository orgAccounts;
    private ConnectionAutoSyncService service;

    @BeforeEach
    void setUp() {
        userLock = mock(ConnectionUserLock.class);
        orgAccounts = mock(CodefOrgAccountRepository.class);
        service = new ConnectionAutoSyncService(userLock, orgAccounts);
    }

    private static CodefOrgAccount account() {
        CodefOrgAccount account = CodefOrgAccount.connected(null, "0301", LocalDateTime.of(2026, 11, 18, 9, 0));
        ReflectionTestUtils.setField(account, "id", ACCOUNT);
        return account;
    }

    @Test
    @DisplayName("계정을 읽기 전에 사용자 잠금을 먼저 잡고, 설정을 바꾼다")
    void locksTheUserBeforeReadingTheAccount() {
        CodefOrgAccount account = account();
        when(orgAccounts.findActiveByUserIdAndIdIn(USER, List.of(ACCOUNT))).thenReturn(List.of(account));

        AutoSyncResponse response = service.change(USER, ACCOUNT, false);

        InOrder order = inOrder(userLock, orgAccounts);
        order.verify(userLock).lock(USER);
        order.verify(orgAccounts).findActiveByUserIdAndIdIn(USER, List.of(ACCOUNT));
        assertThat(response.autoSyncEnabled()).isFalse();
        assertThat(account.isAutoSyncEnabled()).isFalse();
    }

    @Test
    @DisplayName("없거나 남의 계정이면 CONN-001이다")
    void notFound() {
        when(orgAccounts.findActiveByUserIdAndIdIn(anyLong(), anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> service.change(USER, ACCOUNT, true)).isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.CONNECTION_NOT_FOUND);
    }

}
