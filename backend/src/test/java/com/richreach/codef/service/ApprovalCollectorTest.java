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

import com.richreach.card.dto.OwnedCardResponse;
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
        when(ownedCards.list(1L)).thenReturn(List.of(card(10L, CardCompany.KB, "************4001"),
            card(11L, CardCompany.KB, "************4002"), card(12L, CardCompany.SHINHAN, "************4001")));
        when(store.saveCollected(anyLong(), anyLong(), anyList())).thenReturn(new SaveResult(1, 0, 0, 0));
    }

    private static OwnedCardResponse card(long id, CardCompany company, String masked) {
        return new OwnedCardResponse(id, null, company, "카드" + id, masked, null,
            OwnedCardResponse.AnalysisStatus.UNAVAILABLE, true, null);
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

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", null, FAR);

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

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", null, FAR);

        verify(store).saveCollected(eq(1L), eq(10L), anyList());
        verify(store, never()).saveCollected(eq(1L), eq(12L), anyList());
        assertThat(result.saved()).isEqualTo(1);
        assertThat(result.unmatched()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 끝 4자리 카드가 둘이면 어느 카드인지 알 수 없어 저장하지 않는다")
    void ambiguousCardIsNotSaved() {
        when(ownedCards.list(1L)).thenReturn(List.of(card(10L, CardCompany.KB, "************4001"),
            card(11L, CardCompany.KB, "************4001")));
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(ok(approval("A", "****4001")))
            .thenReturn(ok());

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", null, FAR);

        verify(store, never()).saveCollected(anyLong(), anyLong(), anyList());
        assertThat(result.unmatched()).isEqualTo(1);
    }

    @Test
    @DisplayName("중간 달에서 실패하면 더 오래된 달은 부르지 않고 PARTIAL이며, 마친 기간만 알려준다")
    void stopsAtFirstFailureAndReportsPartial() {
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(ok(), ok(),
            ApprovalListResult.failure("CF-12207"));

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", null, FAR);

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

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", null, FAR);

        assertThat(result.status()).isEqualTo(ApprovalCollector.FAILED);
        assertThat(result.from()).isNull();
        assertThat(result.to()).isNull();
    }

    @Test
    @DisplayName("제한 시각이 지났으면 더 부르지 않고 지금까지의 결과로 끝낸다")
    void stopsAfterDeadline() {
        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", null,
            LocalDateTime.of(2020, 1, 1, 0, 0));

        verify(approvals, never()).fetchApprovals(any(), any(), any(), any());
        assertThat(result.status()).isEqualTo(ApprovalCollector.FAILED);
    }

    @Test
    @DisplayName("끝자리 하나가 가려진 카드(신한)도 숫자가 충돌하지 않는 후보가 하나면 저장한다")
    void matchesCardWithMaskedLastDigit() {
        when(ownedCards.list(1L)).thenReturn(List.of(card(20L, CardCompany.KB, "************400*"),
            card(21L, CardCompany.KB, "************410*")));
        when(approvals.fetchApprovals(any(), any(), any(), any()))
            .thenReturn(ok(approval("A", "1234********400*"))).thenReturn(ok());

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", null, FAR);

        verify(store).saveCollected(eq(1L), eq(20L), anyList());
        assertThat(result.saved()).isEqualTo(1);
        assertThat(result.unmatched()).isZero();
    }

    @Test
    @DisplayName("가려진 자리 때문에 후보가 여러 장이면 정하지 않는다")
    void ambiguousWhenMaskedDigitsMatchSeveralCards() {
        when(ownedCards.list(1L)).thenReturn(List.of(card(20L, CardCompany.KB, "************4001"),
            card(21L, CardCompany.KB, "************4002")));
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(ok(approval("A", "************400*")))
            .thenReturn(ok());

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", null, FAR);

        verify(store, never()).saveCollected(anyLong(), anyLong(), anyList());
        assertThat(result.unmatched()).isEqualTo(1);
    }

    @Test
    @DisplayName("카드사에 보유카드가 한 장이면 번호가 모두 가려져 있어도 그 카드로 저장한다")
    void singleCandidateMatchesFullyMaskedNumber() {
        when(ownedCards.list(1L)).thenReturn(List.of(card(20L, CardCompany.KB, "************4001")));
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(ok(approval("A", "****************")))
            .thenReturn(ok());

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", null, FAR);

        verify(store).saveCollected(eq(1L), eq(20L), anyList());
        assertThat(result.unmatched()).isZero();
    }

    @Test
    @DisplayName("보유카드가 한 장이어도 숫자가 충돌하면 다른 카드의 승인내역이라 저장하지 않는다")
    void conflictingDigitsAreNeverMatched() {
        when(ownedCards.list(1L)).thenReturn(List.of(card(20L, CardCompany.KB, "************4001")));
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(ok(approval("A", "************9999")))
            .thenReturn(ok());

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", null, FAR);

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

    // ---- 수집 범위 기록과 증분 수집 ----
    // setUp의 보유카드: KB 10, 11 (SHINHAN 12). 오늘은 2026-11-15, 6개월 창은 2026-06-01부터다.

    private static final String KB_CARD_IDS = "10,11";

    private static ApprovalCollector.Range range(LocalDate from, LocalDate to, String cardIds) {
        return new ApprovalCollector.Range(from, to, cardIds);
    }

    @Test
    @DisplayName("기록이 없으면 6개월 전체를 수집하고, 수집한 기간과 카드 구성을 돌려준다")
    void firstCollectionReturnsFullRange() {
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(ok());

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", null, FAR);

        verify(approvals, times(6)).fetchApprovals(any(), any(), any(), any());
        assertThat(result.range()).isEqualTo(range(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 11, 15), KB_CARD_IDS));
    }

    @Test
    @DisplayName("이미 수집한 기록이 있으면 마지막으로 수집한 달부터 이번 달까지만 다시 가져오고, 기록은 이어서 늘린다")
    void incrementalCollectionFetchesOnlyFromLastCollectedMonth() {
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(ok());
        ApprovalCollector.Range previous = range(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 11, 10), KB_CARD_IDS);

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", previous, FAR);

        verify(approvals, times(1)).fetchApprovals(any(), any(), any(), any());
        verify(approvals).fetchApprovals("cid", "0301", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15));
        assertThat(result.status()).isEqualTo(ApprovalCollector.SUCCESS);
        assertThat(result.range()).isEqualTo(range(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 11, 15), KB_CARD_IDS));
    }

    @Test
    @DisplayName("마지막 수집이 몇 달 전이면 그 달부터 이번 달까지를 가져오고, 그보다 오래된 달은 건너뛴다")
    void incrementalCollectionCoversTheGapSinceLastCollection() {
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(ok());
        ApprovalCollector.Range previous = range(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 9, 20), KB_CARD_IDS);

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", previous, FAR);

        verify(approvals, times(3)).fetchApprovals(any(), any(), any(), any());
        verify(approvals).fetchApprovals("cid", "0301", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        assertThat(result.range()).isEqualTo(range(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 11, 15), KB_CARD_IDS));
    }

    @Test
    @DisplayName("기록된 시작일보다 오래된 달이 창 안에 있으면 그 달도 채운다")
    void incrementalCollectionBackfillsOlderMonths() {
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(ok());
        ApprovalCollector.Range previous = range(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 10), KB_CARD_IDS);

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", previous, FAR);

        // 이번 달 + 기록보다 오래된 8·7·6월. 10·9월은 이미 수집했다.
        verify(approvals, times(4)).fetchApprovals(any(), any(), any(), any());
        verify(approvals).fetchApprovals("cid", "0301", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        assertThat(result.range()).isEqualTo(range(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 11, 15), KB_CARD_IDS));
    }

    @Test
    @DisplayName("보유카드 구성이 기록할 때와 다르면 기록을 믿지 않고 6개월 전체를 다시 수집한다")
    void changedCardsIgnoreThePreviousRange() {
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(ok());
        ApprovalCollector.Range previous = range(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 11, 10), "10");

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", previous, FAR);

        verify(approvals, times(6)).fetchApprovals(any(), any(), any(), any());
        assertThat(result.range().cardIds()).isEqualTo(KB_CARD_IDS);
    }

    @Test
    @DisplayName("증분 수집이 첫 달부터 실패하면 FAILED이고 기존 기록은 그대로 둔다")
    void failedIncrementalCollectionKeepsPreviousRange() {
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(ApprovalListResult.failure("CF-09999"));
        ApprovalCollector.Range previous = range(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 11, 10), KB_CARD_IDS);

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", previous, FAR);

        assertThat(result.status()).isEqualTo(ApprovalCollector.FAILED);
        assertThat(result.range()).isEqualTo(previous);
    }

    @Test
    @DisplayName("증분 수집이 기록과 이어지기 전에 실패하면, 이어지지 않는 구간은 기록하지 않고 새로 수집한 구간만 남긴다")
    void gapBeforeThePreviousRangeIsNotRecorded() {
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(ok(), ApprovalListResult.failure("CF-09999"));
        ApprovalCollector.Range previous = range(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 9, 20), KB_CARD_IDS);

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", previous, FAR);

        // 11월은 가져왔지만 10월에서 실패해서 9월의 기록과 이어지지 않는다.
        assertThat(result.status()).isEqualTo(ApprovalCollector.PARTIAL);
        assertThat(result.range()).isEqualTo(range(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15), KB_CARD_IDS));
    }

    @Test
    @DisplayName("보유카드를 읽지 못하면 FAILED이고 기존 기록은 그대로 둔다")
    void unreadableCardsKeepPreviousRange() {
        when(ownedCards.list(1L)).thenThrow(new IllegalStateException("db"));
        ApprovalCollector.Range previous = range(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 11, 10), KB_CARD_IDS);

        ApprovalCollector.Result result = collector.collect(1L, CardCompany.KB, "cid", "0301", previous, FAR);

        assertThat(result.status()).isEqualTo(ApprovalCollector.FAILED);
        assertThat(result.range()).isEqualTo(previous);
    }

}
