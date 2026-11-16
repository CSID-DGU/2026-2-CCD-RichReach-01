package com.richreach.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.benefit.dto.BenefitQuery;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.benefit.service.BenefitCalculator;
import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.global.category.SpendingCategory;
import com.richreach.transaction.dto.TransactionResponse;
import com.richreach.transaction.entity.TransactionStatus;
import com.richreach.transaction.repository.CardTotal;
import com.richreach.transaction.repository.CardTransactionRepository;
import com.richreach.transaction.repository.TransactionNet;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 거래별 예상 혜택 계산이 카드·달 단위로 계산기를 부르고 결과를 나누는지 DB 없이 확인한다. */
class TransactionBenefitServiceTest {

    private static final long USER = 1L;
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

    private CardTransactionRepository transactions;
    private OwnedCardQueryService ownedCards;
    private BenefitCalculator calculator;
    private TransactionBenefitService service;

    @BeforeEach
    void setUp() {
        transactions = mock(CardTransactionRepository.class);
        ownedCards = mock(OwnedCardQueryService.class);
        calculator = mock(BenefitCalculator.class);
        service = new TransactionBenefitService(transactions, ownedCards, calculator);
        // 10번 카드는 카드 상품 100에 매칭되어 있고, 11번 카드는 매칭되지 않았다.
        when(ownedCards.list(USER)).thenReturn(List.of(card(10L, 100L), card(11L, null)));
        when(transactions.sumByCard(any(), any(), any())).thenReturn(List.of());
    }

    private static OwnedCardResponse card(long id, Long productId) {
        return new OwnedCardResponse(id, productId, null, "카드" + id, "1111", null,
            OwnedCardResponse.AnalysisStatus.UNAVAILABLE, true, null);
    }

    private static TransactionResponse response(long id, long cardId, LocalDateTime at) {
        return new TransactionResponse(id, cardId, at, "가맹점", 1_000, 0, 1_000, TransactionStatus.APPROVED,
            SpendingCategory.CAFE, SpendingCategory.CAFE, false, 0, false, null);
    }

    private static BenefitResult benefit(long cafe) {
        return new BenefitResult(cafe, Map.of(SpendingCategory.CAFE, cafe), true, List.of());
    }

    @Test
    @DisplayName("같은 카드·달의 거래는 계산기를 한 번만 부르고, 그달 업종 소비 전체와 전월 사용액을 넘긴다")
    void callsTheCalculatorOncePerCardMonth() {
        when(transactions.findNets(any(), any(), any(), any())).thenReturn(List.of(
            new TransactionNet(1L, "CAFE", 4_000), new TransactionNet(2L, "CAFE", 6_000),
            new TransactionNet(3L, "DINING", 10_000)));
        when(transactions.sumByCard(any(), any(), any())).thenReturn(List.of(new CardTotal(10L, 300_000L, 5L)));
        when(calculator.calculate(any())).thenReturn(new BenefitResult(1_500,
            Map.of(SpendingCategory.CAFE, 1_000L, SpendingCategory.DINING, 500L), true, List.of()));

        Map<Long, Long> result = service.forTransactions(USER, List.of(
            response(1, 10, LocalDateTime.of(2026, 11, 3, 9, 0)), response(2, 10, LocalDateTime.of(2026, 11, 9, 9, 0))));

        ArgumentCaptor<BenefitQuery> query = ArgumentCaptor.forClass(BenefitQuery.class);
        verify(calculator, times(1)).calculate(query.capture());
        assertThat(query.getValue().cardProductId()).isEqualTo(100L);
        assertThat(query.getValue().monthlySpend()).containsEntry(SpendingCategory.CAFE, 10_000L)
            .containsEntry(SpendingCategory.DINING, 10_000L);
        assertThat(query.getValue().previousMonthSpend()).isEqualTo(300_000L);
        // 업종의 혜택을 그 업종 거래 비율대로 나눈다. (4,000 : 6,000)
        assertThat(result).containsEntry(1L, 400L).containsEntry(2L, 600L);
    }

    @Test
    @DisplayName("전월 사용액은 전월 1일부터 말일까지 그 카드의 거래다. 전월 거래가 없으면 0이다")
    void previousMonthSpendComesFromTheCardsPreviousMonth() {
        when(transactions.findNets(any(), any(), any(), any())).thenReturn(List.of(new TransactionNet(1L, "CAFE", 1_000)));
        when(calculator.calculate(any())).thenReturn(benefit(10));

        service.forTransactions(USER, List.of(response(1, 10, LocalDateTime.of(2026, 11, 3, 9, 0))));

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(transactions).sumByCard(any(), from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 0));
        assertThat(to.getValue()).isEqualTo(LocalDateTime.of(2026, 11, 1, 0, 0));
        ArgumentCaptor<BenefitQuery> query = ArgumentCaptor.forClass(BenefitQuery.class);
        verify(calculator).calculate(query.capture());
        assertThat(query.getValue().previousMonthSpend()).isZero();
    }

    @Test
    @DisplayName("카드와 달이 다르면 따로 계산한다")
    void differentMonthsAreComputedSeparately() {
        when(transactions.findNets(any(), any(), any(), any())).thenReturn(
            List.of(new TransactionNet(1L, "CAFE", 1_000)), List.of(new TransactionNet(2L, "CAFE", 1_000)));
        when(calculator.calculate(any())).thenReturn(benefit(10));

        service.forTransactions(USER, List.of(response(1, 10, LocalDateTime.of(2026, 11, 3, 9, 0)),
            response(2, 10, LocalDateTime.of(2026, 10, 20, 9, 0))));

        verify(calculator, times(2)).calculate(any());
    }

    @Test
    @DisplayName("카드 상품과 매칭되지 않은 카드나 사라진 카드의 거래는 계산하지 않고 결과에 없다 (null)")
    void unmatchedCardsAreNotComputed() {
        Map<Long, Long> result = service.forTransactions(USER, List.of(
            response(1, 11, LocalDateTime.of(2026, 11, 3, 9, 0)), response(2, 99, LocalDateTime.of(2026, 11, 3, 9, 0))));

        assertThat(result).isEmpty();
        verify(calculator, never()).calculate(any());
    }

    @Test
    @DisplayName("혜택이 없는 거래는 null이 아니라 0이다")
    void noBenefitIsZeroNotNull() {
        when(transactions.findNets(any(), any(), any(), any())).thenReturn(List.of(new TransactionNet(1L, "CAFE", 1_000)));
        when(calculator.calculate(any())).thenReturn(BenefitResult.none(true, List.of()));

        Map<Long, Long> result = service.forTransactions(USER, List.of(
            response(1, 10, LocalDateTime.of(2026, 11, 3, 9, 0))));

        assertThat(result).containsEntry(1L, 0L);
    }

    @Test
    @DisplayName("거래가 없으면 계산하지 않는다")
    void emptyInput() {
        assertThat(service.forTransactions(USER, List.of())).isEmpty();
        verify(calculator, never()).calculate(any());
    }

    @Test
    @DisplayName("월 합계는 카드별 거래별 혜택의 합이고, 계산할 수 없는 카드는 뺀다")
    void monthTotalSumsComputableCards() {
        when(transactions.findNets(any(), any(), any(), any())).thenReturn(List.of(
            new TransactionNet(1L, "CAFE", 4_000), new TransactionNet(2L, "CAFE", 6_000)));
        when(calculator.calculate(any())).thenReturn(benefit(1_000));

        assertThat(service.monthTotal(USER, NOVEMBER, List.of(10L, 11L))).isEqualTo(1_000L);
        verify(calculator, times(1)).calculate(any());
    }

    @Test
    @DisplayName("월 합계: 카드가 없으면 0, 카드가 있지만 하나도 계산할 수 없으면 null")
    void monthTotalWithoutCards() {
        assertThat(service.monthTotal(USER, NOVEMBER, List.of())).isZero();
        assertThat(service.monthTotal(USER, NOVEMBER, List.of(11L, 99L))).isNull();
        verify(calculator, never()).calculate(any());
    }

}
