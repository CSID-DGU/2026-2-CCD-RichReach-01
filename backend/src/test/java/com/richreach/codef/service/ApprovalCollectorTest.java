package com.richreach.codef.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.card.dto.MatchableCard;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.codef.client.CodefApprovalClient;
import com.richreach.codef.client.CodefApprovalClient.ApprovalListResult;
import com.richreach.codef.client.CodefApprovalClient.CodefApproval;
import com.richreach.codef.config.CodefCollectProperties;
import com.richreach.codef.entity.CardCompany;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.transaction.service.TransactionStoreService;
import com.richreach.transaction.service.TransactionStoreService.SaveResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 승인내역 수집의 월 나누기, 카드 짝짓기, 중간 실패 처리를 DB 없이 확인한다. */
class ApprovalCollectorTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T03:00:00Z"), ZONE);
    private static final LocalDateTime FAR = LocalDateTime.of(2030, 1, 1, 0, 0);

    private CodefApprovalClient approvals;
    private OwnedCardQueryService ownedCards;
    private TransactionStoreService store;
    private ApprovalCollector collector;

    @BeforeEach
    void setUp() {
        approvals = mock(CodefApprovalClient.class);
        ownedCards = mock(OwnedCardQueryService.class);
        store = mock(TransactionStoreService.class);
        collector = new ApprovalCollector(approvals, ownedCards, store, new CodefCollectProperties(6), CLOCK);
        when(ownedCards.listForMatching(1L)).thenReturn(List.of(card(10L, CardCompany.KB, "************4001"),
            card(11L, CardCompany.KB, "************4002"), card(12L, CardCompany.SHINHAN, "************4001")));
        when(store.saveCollected(anyLong(), anyLong(), anyList())).thenReturn(new SaveResult(1, 0, 0, 0));
    }

    private static MatchableCard card(long id, CardCompany company, String masked) {
        return new MatchableCard(id, company, masked);
    }

    private static CodefApproval approval(String no, String cardNumber) {
        return new CodefApproval(no, LocalDate.of(2026, 11, 1), LocalTime.NOON, "가맹점", 1000, 0, 0, "", cardNumber,
            "0", "1", "1", "KRW");
    }

    private static ApprovalListResult ok(CodefApproval... list) {
        return new ApprovalListResult(true, list.length, "CF-00000", List.of(list));
    }

    @Test
    @DisplayName("이번 달은 오늘까지, 지난 달들은 1일부터 말일까지 최신 달부터 6번 나눠 부른다")
    void fetchesMonthlyChunksNewestFirst() {
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(ok());

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", FAR);

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(approvals, times(6)).fetchApprovals(eq("cid"), eq("0301"), from.capture(), to.capture());
        assertThat(from.getAllValues()).containsExactly(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 6, 1));
        assertThat(to.getAllValues()).containsExactly(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 10, 31),
            LocalDate.of(2026, 9, 30), LocalDate.of(2026, 8, 31), LocalDate.of(2026, 7, 31), LocalDate.of(2026, 6, 30));
        assertThat(result.status()).isEqualTo(ApprovalCollector.SUCCESS);
        assertThat(result.from()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(result.to()).isEqualTo(LocalDate.of(2026, 11, 15));
    }

    @Test
    @DisplayName("같은 카드사의 카드 중 끝 4자리가 하나로 정해질 때만 저장하고, 나머지는 못 찾음으로 센다")
    void matchesByLastFourWithinCompany() {
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(
            ok(approval("A", "1111-22**-****-4001"), approval("B", "9999-99**-****-9999"), approval("C", null)))
            .thenReturn(ok());

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", FAR);

        verify(store).saveCollected(eq(1L), eq(10L), anyList());
        verify(store, never()).saveCollected(eq(1L), eq(12L), anyList());
        assertThat(result.saved()).isEqualTo(1);
        assertThat(result.unmatched()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 끝 4자리 카드가 둘이면 어느 카드인지 알 수 없어 저장하지 않는다")
    void ambiguousCardIsNotSaved() {
        when(ownedCards.listForMatching(1L)).thenReturn(List.of(card(10L, CardCompany.KB, "************4001"),
            card(11L, CardCompany.KB, "************4001")));
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(ok(approval("A", "****4001")))
            .thenReturn(ok());

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", FAR);

        verify(store, never()).saveCollected(anyLong(), anyLong(), anyList());
        assertThat(result.unmatched()).isEqualTo(1);
    }

    @Test
    @DisplayName("중간 달에서 실패하면 더 오래된 달은 부르지 않고 PARTIAL이며, 마친 기간만 알려준다")
    void stopsAtFirstFailureAndReportsPartial() {
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(ok(), ok(),
            ApprovalListResult.failure("CF-12207"));

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", FAR);

        verify(approvals, times(3)).fetchApprovals(any(), any(), any(), any());
        assertThat(result.status()).isEqualTo(ApprovalCollector.PARTIAL);
        assertThat(result.from()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(result.to()).isEqualTo(LocalDate.of(2026, 11, 15));
    }

    @Test
    @DisplayName("첫 달부터 실패하면 FAILED이고 기간이 없다. CODEF 예외도 밖으로 던지지 않는다")
    void firstMonthFailureIsFailed() {
        when(approvals.fetchApprovals(any(), any(), any(), any()))
            .thenThrow(new BusinessException(ErrorCode.CODEF_UNAVAILABLE));

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", FAR);

        assertThat(result.status()).isEqualTo(ApprovalCollector.FAILED);
        assertThat(result.from()).isNull();
        assertThat(result.to()).isNull();
    }

    @Test
    @DisplayName("제한 시각이 지났으면 더 부르지 않고 지금까지의 결과로 끝낸다")
    void stopsAfterDeadline() {
        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301",
            LocalDateTime.of(2020, 1, 1, 0, 0));

        verify(approvals, never()).fetchApprovals(any(), any(), any(), any());
        assertThat(result.status()).isEqualTo(ApprovalCollector.FAILED);
    }

    @Test
    @DisplayName("끝자리 하나가 가려진 카드(신한)도 숫자가 충돌하지 않는 후보가 하나면 저장한다")
    void matchesCardWithMaskedLastDigit() {
        when(ownedCards.listForMatching(1L)).thenReturn(List.of(card(20L, CardCompany.KB, "************400*"),
            card(21L, CardCompany.KB, "************410*")));
        when(approvals.fetchApprovals(any(), any(), any(), any()))
            .thenReturn(ok(approval("A", "1234********400*"))).thenReturn(ok());

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", FAR);

        verify(store).saveCollected(eq(1L), eq(20L), anyList());
        assertThat(result.saved()).isEqualTo(1);
        assertThat(result.unmatched()).isZero();
    }

    @Test
    @DisplayName("가려진 자리 때문에 후보가 여러 장이면 정하지 않는다")
    void ambiguousWhenMaskedDigitsMatchSeveralCards() {
        when(ownedCards.listForMatching(1L)).thenReturn(List.of(card(20L, CardCompany.KB, "************4001"),
            card(21L, CardCompany.KB, "************4002")));
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(ok(approval("A", "************400*")))
            .thenReturn(ok());

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", FAR);

        verify(store, never()).saveCollected(anyLong(), anyLong(), anyList());
        assertThat(result.unmatched()).isEqualTo(1);
    }

    @Test
    @DisplayName("카드사에 보유카드가 한 장이면 번호가 모두 가려져 있어도 그 카드로 저장한다")
    void singleCandidateMatchesFullyMaskedNumber() {
        when(ownedCards.listForMatching(1L)).thenReturn(List.of(card(20L, CardCompany.KB, "************4001")));
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(ok(approval("A", "****************")))
            .thenReturn(ok());

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", FAR);

        verify(store).saveCollected(eq(1L), eq(20L), anyList());
        assertThat(result.unmatched()).isZero();
    }

    @Test
    @DisplayName("보유카드가 한 장이어도 숫자가 충돌하면 다른 카드의 승인내역이라 저장하지 않는다")
    void conflictingDigitsAreNeverMatched() {
        when(ownedCards.listForMatching(1L)).thenReturn(List.of(card(20L, CardCompany.KB, "************4001")));
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(ok(approval("A", "************9999")))
            .thenReturn(ok());

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", FAR);

        verify(store, never()).saveCollected(anyLong(), anyLong(), anyList());
        assertThat(result.unmatched()).isEqualTo(1);
    }

    @Test
    @DisplayName("카드 번호 비교는 구분자를 없애고 끝을 맞춰 숫자인 자리만 본다")
    void isCompatible() {
        assertThat(ApprovalCollector.isCompatible("************4001", "1111-22**-****-4001")).isTrue();
        assertThat(ApprovalCollector.isCompatible("************400*", "************4001")).isTrue();
        assertThat(ApprovalCollector.isCompatible("************4001", "************4002")).isFalse();
        assertThat(ApprovalCollector.isCompatible("************4001", null)).isTrue();
        assertThat(ApprovalCollector.isCompatible("************4001", "")).isTrue();
        // 앞쪽 숫자가 보이는 번호는 앞쪽도 비교한다. (끝 4자리가 우연히 같아도 앞이 다르면 다른 카드)
        assertThat(ApprovalCollector.isCompatible("1234********4001", "9999********4001")).isFalse();
    }

}
