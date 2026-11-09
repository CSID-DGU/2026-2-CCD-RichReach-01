package com.richreach.codef.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.codef.client.CodefApprovalClient;
import com.richreach.codef.client.CodefApprovalClient.ApprovalListResult;
import com.richreach.codef.client.CodefApprovalClient.CodefApproval;
import com.richreach.codef.dto.ApprovalProbeResponse;
import com.richreach.codef.entity.CodefConnection;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

/** 개발용 승인내역 확인 호출의 기간 제한, 계정 선택, 응답에 건수만 담는지를 DB 없이 확인한다. */
class CodefApprovalProbeServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private final CodefOrgAccountRepository orgAccounts = mock(CodefOrgAccountRepository.class);
    private final CodefApprovalClient approvals = mock(CodefApprovalClient.class);
    private final TransactionTemplate transaction = mock(TransactionTemplate.class);
    private final Clock clock = Clock.fixed(TODAY.atStartOfDay(ZONE).toInstant(), ZONE);
    private final CodefApprovalProbeService service =
        new CodefApprovalProbeService(orgAccounts, approvals, transaction, clock);

    @SuppressWarnings("unchecked")
    private CodefApprovalProbeServiceTest() {
        when(transaction.execute(any())).thenAnswer(invocation ->
            ((org.springframework.transaction.support.TransactionCallback<Object>) invocation.getArgument(0))
                .doInTransaction(null));
    }

    private CodefOrgAccount account(long id, boolean locked) {
        CodefOrgAccount account = mock(CodefOrgAccount.class);
        CodefConnection connection = mock(CodefConnection.class);
        when(account.getId()).thenReturn(id);
        when(account.isLocked()).thenReturn(locked);
        when(account.getOrganization()).thenReturn("0301");
        when(account.getConnection()).thenReturn(connection);
        when(connection.getConnectedId()).thenReturn("test-connected-id");
        return account;
    }

    private void assertInvalid(LocalDate from, LocalDate to) {
        assertThatThrownBy(() -> service.probe(1L, null, from, to)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(approvals, never()).fetchApprovals(any(), any(), any(), any());
    }

    @Test
    @DisplayName("기간이 거꾸로이거나 오늘 이후이거나 31일을 넘으면 카드사를 호출하지 않고 400이다")
    void rejectsInvalidPeriod() {
        assertInvalid(TODAY, TODAY.minusDays(1));
        assertInvalid(TODAY, TODAY.plusDays(1));
        assertInvalid(TODAY.minusDays(31), TODAY);
    }

    @Test
    @DisplayName("기간을 비우면 오늘까지 최근 7일을 조회하고, 31일은 허용한다")
    void defaultAndMaximumPeriod() {
        CodefOrgAccount usable = account(10L, false);
        when(orgAccounts.findActiveByUserId(1L)).thenReturn(List.of(usable));
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(ApprovalListResult.failure("CF-00001"));

        service.probe(1L, null, null, null);
        verify(approvals).fetchApprovals("test-connected-id", "0301", TODAY.minusDays(6), TODAY);

        service.probe(1L, null, TODAY.minusDays(30), TODAY);
        verify(approvals).fetchApprovals("test-connected-id", "0301", TODAY.minusDays(30), TODAY);
    }

    @Test
    @DisplayName("카드사 계정이 없거나 모두 제한 중이면 CONN-001이다")
    void noUsableAccount() {
        CodefOrgAccount locked = account(10L, true);
        when(orgAccounts.findActiveByUserId(1L)).thenReturn(List.of(locked));

        assertThatThrownBy(() -> service.probe(1L, null, null, null)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONNECTION_NOT_FOUND));
        verify(approvals, never()).fetchApprovals(any(), any(), any(), any());
    }

    @Test
    @DisplayName("accountId를 주면 그 계정을 고르고, 내 계정이 아니면 CONN-001이다")
    void picksRequestedAccount() {
        CodefOrgAccount first = account(10L, false);
        CodefOrgAccount second = account(11L, false);
        when(orgAccounts.findActiveByUserId(1L)).thenReturn(List.of(first, second));
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(ApprovalListResult.failure("CF-00001"));

        service.probe(1L, 11L, null, null);
        verify(approvals).fetchApprovals(eq("test-connected-id"), eq("0301"), any(), any());

        assertThatThrownBy(() -> service.probe(1L, 99L, null, null)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONNECTION_NOT_FOUND));
    }

    @Test
    @DisplayName("응답에는 결과 코드와 건수만 담는다 (읽은 건수가 응답 건수보다 적으면 형식이 다르다는 신호)")
    void responseHasOnlyCounts() {
        CodefOrgAccount usable = account(10L, false);
        when(orgAccounts.findActiveByUserId(1L)).thenReturn(List.of(usable));
        CodefApproval one = new CodefApproval("1", TODAY, null, "비밀상점", 99_999, 0, 0, "", "", "", "", "", "");
        when(approvals.fetchApprovals(any(), any(), any(), any()))
            .thenReturn(new ApprovalListResult(true, 3, "CF-00000", List.of(one)));

        ApprovalProbeResponse response = service.probe(1L, null, null, null);

        assertThat(response.success()).isTrue();
        assertThat(response.code()).isEqualTo("CF-00000");
        assertThat(response.approvalCount()).isEqualTo(3);
        assertThat(response.readCount()).isEqualTo(1);
        assertThat(response.toString()).doesNotContain("비밀상점").doesNotContain("99999");
    }

    @Test
    @DisplayName("코드값별 건수와 취소 건수를 센다 (가맹점명, 금액, 카드번호는 통계에 들어가지 않는다)")
    void countsCodesWithoutPersonalValues() {
        CodefApproval normal = new CodefApproval("1", TODAY, null, "비밀상점A", 10_000, 0, 0, "음식점", "1234567890123456",
            "0", "1", "1", "KRW");
        CodefApproval full = new CodefApproval("2", TODAY, null, "비밀상점B", 20_000, 20_000, 0, "음식점", "1234567890123456",
            "1", "1", "1", "KRW");
        CodefApproval partial = new CodefApproval("3", TODAY, null, "비밀상점C", 30_000, 5_000, 0, "", "1234567890123456",
            "2", "2", "1", "KRW");
        CodefOrgAccount usable = account(10L, false);
        when(orgAccounts.findActiveByUserId(1L)).thenReturn(List.of(usable));
        when(approvals.fetchApprovals(any(), any(), any(), any()))
            .thenReturn(new ApprovalListResult(true, 3, "CF-00000", List.of(normal, full, partial)));

        ApprovalProbeResponse response = service.probe(1L, null, null, null);

        assertThat(response.canceledCount()).isEqualTo(2);
        assertThat(response.fullCanceledCount()).isEqualTo(1);
        assertThat(response.codeStats().get("cancelStatus")).containsEntry("0", 1L).containsEntry("1", 1L)
            .containsEntry("2", 1L);
        assertThat(response.codeStats().get("merchantType")).containsEntry("음식점", 2L).containsEntry("(빈 값)", 1L);
        assertThat(response.toString()).doesNotContain("비밀상점").doesNotContain("1234567890123456")
            .doesNotContain("10000");
    }

}
