package com.richreach.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.codef.entity.CardCompany;
import com.richreach.codef.service.ConnectionWithdrawalService;
import com.richreach.codef.service.ConnectionWithdrawalService.RemoteAccount;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** 탈퇴가 되돌릴 수 없는 CODEF 호출을 마지막에 하는지, 앞 단계가 실패하면 CODEF를 건드리지 않는지 확인한다. */
class UserWithdrawalServiceTest {

    private static final long USER = 1L;

    private UserLockService userLock;
    private UserRepository users;
    private ConnectionWithdrawalService connections;
    private UserWithdrawalService service;

    @BeforeEach
    void setUp() {
        userLock = mock(UserLockService.class);
        users = mock(UserRepository.class);
        connections = mock(ConnectionWithdrawalService.class);
        service = new UserWithdrawalService(userLock, users, connections);
    }

    @Test
    @DisplayName("잠금, 카드 연동 정리, 사용자 삭제와 반영, CODEF 삭제 순서로 하고 CODEF가 마지막이다")
    void callsCodefLast() {
        List<RemoteAccount> remote = List.of(new RemoteAccount("connected", CardCompany.KB));
        when(connections.removeLocalData(USER)).thenReturn(remote);

        service.withdraw(USER);

        InOrder order = inOrder(userLock, connections, users);
        order.verify(userLock).lock(USER);
        order.verify(connections).removeLocalData(USER);
        order.verify(users).deleteById(USER);
        order.verify(users).flush();
        order.verify(connections).deleteAtCodef(remote);
    }

    @Test
    @DisplayName("이미 없는 사용자면 아무것도 지우지 않고 CODEF도 부르지 않는다")
    void stopsWhenTheUserIsGone() {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.INVALID_TOKEN)).when(userLock).lock(USER);

        assertThatThrownBy(() -> service.withdraw(USER)).isInstanceOf(BusinessException.class);

        verify(connections, never()).removeLocalData(USER);
        verify(users, never()).deleteById(USER);
        verify(connections, never()).deleteAtCodef(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("카드 연동 정리가 막히면(동기화 진행 중) 사용자를 지우지 않고 CODEF도 부르지 않는다")
    void stopsWhenLocalCleanupIsBlocked() {
        when(connections.removeLocalData(USER)).thenThrow(new BusinessException(ErrorCode.SYNC_ALREADY_RUNNING));

        assertThatThrownBy(() -> service.withdraw(USER)).isInstanceOf(BusinessException.class);

        verify(users, never()).deleteById(USER);
        verify(connections, never()).deleteAtCodef(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("사용자 삭제가 DB에서 거절되면 CODEF를 부르지 않는다")
    void doesNotCallCodefWhenTheUserDeleteFails() {
        when(connections.removeLocalData(USER)).thenReturn(List.of(new RemoteAccount("c", CardCompany.KB)));
        org.mockito.Mockito.doThrow(new IllegalStateException("fk")).when(users).flush();

        assertThatThrownBy(() -> service.withdraw(USER)).isInstanceOf(IllegalStateException.class);

        verify(connections, never()).deleteAtCodef(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("RemoteAccount는 비밀인 connectedId를 문자열로 드러내지 않는다")
    void remoteAccountHidesTheConnectedId() {
        assertThat(new RemoteAccount("secret-connected-id", CardCompany.KB).toString()).doesNotContain("secret");
    }

}
