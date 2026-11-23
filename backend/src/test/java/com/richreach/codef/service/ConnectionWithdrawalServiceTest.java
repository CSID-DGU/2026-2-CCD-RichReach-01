package com.richreach.codef.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.codef.config.CodefSyncProperties;
import com.richreach.codef.entity.CardCompany;
import com.richreach.codef.entity.CodefConnection;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.repository.CodefConnectionRepository;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.codef.repository.CodefSyncJobRepository;
import com.richreach.codef.service.ConnectionWithdrawalService.RemoteAccount;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

/** 탈퇴 때 카드 연동 정리가 해제한 계정과 지원하지 않는 카드사를 CODEF 호출 대상에서 빼고, 지우는 순서를 지키는지 확인한다. */
class ConnectionWithdrawalServiceTest {

    private static final long USER = 1L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private CodefConnectionRepository connections;
    private CodefOrgAccountRepository orgAccounts;
    private CodefSyncJobRepository jobs;
    private CardCompanyAuthenticator authenticator;
    private ConnectionWithdrawalService service;

    @BeforeEach
    void setUp() {
        connections = mock(CodefConnectionRepository.class);
        orgAccounts = mock(CodefOrgAccountRepository.class);
        jobs = mock(CodefSyncJobRepository.class);
        authenticator = mock(CardCompanyAuthenticator.class);
        service = new ConnectionWithdrawalService(connections, orgAccounts, jobs, authenticator,
            new CodefSyncProperties(Duration.ofMinutes(5), Duration.ofHours(1)), CLOCK);
    }

    private CodefConnection connection() {
        CodefConnection connection = new CodefConnection(null, "secret-connected-id", "DEMO", NOW, NOW, NOW, NOW);
        ReflectionTestUtils.setField(connection, "id", 10L);
        return connection;
    }

    private static CodefOrgAccount account(CodefConnection connection, String organization, boolean disconnected) {
        CodefOrgAccount account = CodefOrgAccount.connected(connection, organization, NOW);
        if (disconnected) {
            account.disconnect();
        }
        return account;
    }

    @Test
    @DisplayName("작업 기록, 계정, 연동 순서로 지우고, 해제하지 않은 지원 카드사 계정만 CODEF 대상으로 돌려준다")
    void deletesInOrderAndReturnsOnlyLiveSupportedAccounts() {
        CodefConnection connection = connection();
        when(connections.findByUserId(USER)).thenReturn(Optional.of(connection));
        when(orgAccounts.findByConnectionId(10L)).thenReturn(List.of(
            account(connection, "0301", false),   // KB, 정상
            account(connection, "0306", true),    // 신한, 이미 해제
            account(connection, "9999", false))); // 알 수 없는 카드사

        List<RemoteAccount> remote = service.removeLocalData(USER);

        assertThat(remote).containsExactly(new RemoteAccount("secret-connected-id", CardCompany.KB));
        InOrder order = inOrder(jobs, orgAccounts, connections);
        order.verify(jobs).deleteAllByUserId(USER);
        order.verify(orgAccounts).deleteAllByUserId(USER);
        order.verify(connections).deleteByUserId(USER);
    }

    @Test
    @DisplayName("카드 연동이 없으면 아무것도 지우지 않고 빈 목록이다")
    void doesNothingWithoutAConnection() {
        when(connections.findByUserId(USER)).thenReturn(Optional.empty());

        assertThat(service.removeLocalData(USER)).isEmpty();

        verify(jobs, never()).deleteAllByUserId(USER);
        verify(connections, never()).deleteByUserId(USER);
    }

    @Test
    @DisplayName("동기화가 진행 중이면 CONN-007이고 아무것도 지우지 않는다")
    void blocksWhileSyncing() {
        when(jobs.existsRunningSince(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.removeLocalData(USER)).isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.SYNC_ALREADY_RUNNING);

        verify(connections, never()).deleteByUserId(USER);
        verify(orgAccounts, never()).deleteAllByUserId(USER);
    }

    @Test
    @DisplayName("CODEF 삭제는 대상 계정마다 한 번씩 부르고, 하나가 실패하면 거기서 멈춘다")
    void callsCodefPerAccountAndStopsOnFailure() {
        List<RemoteAccount> remote = List.of(new RemoteAccount("c", CardCompany.KB),
            new RemoteAccount("c", CardCompany.SHINHAN), new RemoteAccount("c", CardCompany.HYUNDAI));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.CODEF_UNAVAILABLE)).when(authenticator)
            .disconnect("c", CardCompany.SHINHAN);

        assertThatThrownBy(() -> service.deleteAtCodef(remote)).isInstanceOf(BusinessException.class);

        verify(authenticator, times(1)).disconnect("c", CardCompany.KB);
        verify(authenticator, times(1)).disconnect("c", CardCompany.SHINHAN);
        verify(authenticator, never()).disconnect("c", CardCompany.HYUNDAI);
    }

}
