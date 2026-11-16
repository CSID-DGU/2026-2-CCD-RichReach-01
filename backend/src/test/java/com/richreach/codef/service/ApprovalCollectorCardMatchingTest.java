package com.richreach.codef.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.card.entity.OwnedCard;
import com.richreach.card.repository.OwnedCardRepository;
import com.richreach.card.service.CardNumberMasker;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.codef.client.CodefApprovalClient;
import com.richreach.codef.client.CodefApprovalClient.ApprovalListResult;
import com.richreach.codef.client.CodefApprovalClient.CodefApproval;
import com.richreach.codef.config.CodefCollectProperties;
import com.richreach.codef.entity.CardCompany;
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
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 저장된 보유카드가 카드 도메인의 조회를 거쳐 수집기에 전달되는 실제 경로에서, 끝자리가 가려진 카드(신한)의 승인내역이 짝지어지는지
 * 확인한다. 수집기 테스트가 직접 만든 카드로는 이 경로(저장 시 가림 처리, 화면용 변환)에서 가려진 자리의 위치가 사라지는 문제를 잡지 못한다.
 */
class ApprovalCollectorCardMatchingTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime FAR = LocalDateTime.of(2030, 1, 1, 0, 0);
    private static final String SHINHAN = "0306";

    private OwnedCardRepository repository;
    private CodefApprovalClient approvals;
    private TransactionStoreService store;
    private ApprovalCollector collector;

    @BeforeEach
    void setUp() {
        repository = mock(OwnedCardRepository.class);
        approvals = mock(CodefApprovalClient.class);
        store = mock(TransactionStoreService.class);
        when(store.saveCollected(anyLong(), anyLong(), anyList())).thenReturn(new SaveResult(1, 0, 0, 0));
        collector = new ApprovalCollector(approvals, new OwnedCardQueryService(repository), store,
            new CodefCollectProperties(1), CLOCK);
    }

    /** 동기화가 저장하는 방식(가림 처리)으로 보유카드를 만든다. */
    private static OwnedCard saved(long id, String rawNumber) {
        OwnedCard card = OwnedCard.discovered(1L, 1L, SHINHAN, "카드" + id, CardNumberMasker.mask(rawNumber), "credit",
            null, LocalDateTime.of(2026, 11, 1, 0, 0));
        ReflectionTestUtils.setField(card, "id", id);
        return card;
    }

    private static CodefApproval approval(String cardNumber) {
        return new CodefApproval("A1", LocalDate.of(2026, 11, 1), LocalTime.NOON, "가맹점", 1000, 0, 0, "", cardNumber,
            "0", "1", "1", "KRW");
    }

    private ApprovalCollector.Result collectWith(String approvalCardNumber) {
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(
            new ApprovalListResult(true, 1, "CF-00000", List.of(approval(approvalCardNumber))));
        return collector.collect(1L, CardCompany.SHINHAN, "cid", SHINHAN, null, FAR);
    }

    @Test
    @DisplayName("끝자리 하나가 가려진 신한 카드는 저장 경로를 거쳐도 같은 번호의 승인내역과 짝지어진다")
    void matchesShinhanCardWithMaskedLastDigit() {
        when(repository.findActiveByUserId(1L)).thenReturn(List.of(saved(20L, "123456******400*"),
            saved(21L, "123456******410*")));

        ApprovalCollector.Result result = collectWith("123456******400*");

        verify(store).saveCollected(eq(1L), eq(20L), anyList());
        assertThat(result.unmatched()).isZero();
    }

    @Test
    @DisplayName("승인내역의 앞자리가 모두 가려져 있어도 보이는 끝자리로 후보를 좁혀 짝짓는다")
    void matchesWhenApprovalNumberIsMoreMasked() {
        when(repository.findActiveByUserId(1L)).thenReturn(List.of(saved(20L, "123456******400*"),
            saved(21L, "123456******410*")));

        ApprovalCollector.Result result = collectWith("************400*");

        // 앞자리는 모두 가려져 있어도 끝에서 둘째·셋째 자리의 숫자로 가른다. 20번은 400, 21번은 410이라 21번은 다르다.
        verify(store).saveCollected(eq(1L), eq(20L), anyList());
        assertThat(result.unmatched()).isZero();
    }

    @Test
    @DisplayName("가려지지 않은 일반 카드도 끝 4자리가 같을 때만 짝지어진다")
    void matchesFullyVisibleLastFour() {
        when(repository.findActiveByUserId(1L)).thenReturn(List.of(saved(20L, "1234567890124001"),
            saved(21L, "1234567890124002")));

        ApprovalCollector.Result result = collectWith("1234-56**-****-4001");

        verify(store).saveCollected(eq(1L), eq(20L), anyList());
        verify(store, never()).saveCollected(eq(1L), eq(21L), anyList());
        assertThat(result.unmatched()).isZero();
    }

    @Test
    @DisplayName("끝자리가 가려진 두 카드가 모두 맞을 수 있으면 정하지 않는다")
    void ambiguousMaskedCardsAreNotMatched() {
        when(repository.findActiveByUserId(1L)).thenReturn(List.of(saved(20L, "123456******400*"),
            saved(21L, "654321******400*")));

        ApprovalCollector.Result result = collectWith("****************");

        verify(store, never()).saveCollected(anyLong(), anyLong(), anyList());
        assertThat(result.unmatched()).isEqualTo(1);
    }

}
