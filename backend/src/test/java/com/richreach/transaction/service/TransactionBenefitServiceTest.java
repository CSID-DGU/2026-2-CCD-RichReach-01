package com.richreach.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.benefit.dto.BenefitPurchase;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.benefit.service.BenefitCalculator;
import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.global.category.SpendingCategory;
import com.richreach.transaction.dto.TransactionResponse;
import com.richreach.transaction.entity.TransactionStatus;
import com.richreach.transaction.repository.CardTransactionRepository;
import com.richreach.transaction.repository.TransactionNet;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TransactionBenefitServiceTest {
    private static final long USER = 1L;
    private static final YearMonth MONTH = YearMonth.of(2026, 11);
    private CardTransactionRepository transactions;
    private BenefitCalculator calculator;
    private MerchantCategoryClassifier classifier;
    private TransactionBenefitService service;

    @BeforeEach
    void setUp() {
        transactions = mock(CardTransactionRepository.class);
        calculator = mock(BenefitCalculator.class);
        classifier = mock(MerchantCategoryClassifier.class);
        OwnedCardQueryService owned = mock(OwnedCardQueryService.class);
        when(owned.list(USER)).thenReturn(List.of(
            new OwnedCardResponse(10L, 100L, null, "카드", "1111", null,
                OwnedCardResponse.AnalysisStatus.UNAVAILABLE, true, null)));
        service = new TransactionBenefitService(transactions, owned, calculator, classifier);
        when(transactions.sumByCard(any(), any(), any())).thenReturn(List.of());
        when(classifier.classifyIfKnown("카페")).thenReturn(Optional.of(SpendingCategory.CAFE));
    }

    private static TransactionNet net(long id, String category, long amount, int day, String merchant,
                                      String override) {
        return new TransactionNet(id, category, amount, LocalDateTime.of(2026, 11, day, 12, 0), merchant, override);
    }

    private static TransactionResponse response(long id) {
        return new TransactionResponse(id, 10L, LocalDateTime.of(2026, 11, 2, 12, 0), "카페", 10_000,
            0, 10_000, TransactionStatus.APPROVED, SpendingCategory.CAFE, SpendingCategory.CAFE,
            false, 0, false, null);
    }

    private static BenefitResult benefit(long amount) {
        return new BenefitResult(amount, amount == 0 ? Map.of() : Map.of(SpendingCategory.CAFE, amount),
            true, List.of());
    }

    @Test
    void replaysKnownPurchasesAndKeepsUnresolvedMerchantAtZero() {
        when(transactions.findNets(any(), any(), any(), any())).thenReturn(List.of(
            net(1, "CAFE", 10_000, 2, "카페", null),
            net(2, "ETC", 5_000, 3, "알 수 없음", "ETC")));
        when(calculator.calculatePurchases(eq(100L), any(), eq(0L)))
            .thenReturn(Map.of(1L, benefit(2_000), 2L, benefit(0)));

        assertThat(service.forTransactions(USER, List.of(response(1), response(2))))
            .containsEntry(1L, 2_000L).containsEntry(2L, 0L);
        assertThat(service.monthTotal(USER, MONTH, List.of(10L))).isEqualTo(2_000L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BenefitPurchase>> purchases = ArgumentCaptor.forClass(List.class);
        verify(calculator, org.mockito.Mockito.times(2)).calculatePurchases(eq(100L), purchases.capture(), eq(0L));
        assertThat(purchases.getValue()).extracting(BenefitPurchase::merchantIdentified)
            .containsExactly(true, false);
    }

    @Test
    void previewAddsOneProspectivePurchaseWithoutSavingIt() {
        when(transactions.findNets(any(), any(), any(), any())).thenReturn(List.of(
            net(1, "CAFE", 10_000, 2, "카페", null)));
        when(calculator.calculatePurchases(eq(100L), any(), eq(300_000L))).thenReturn(Map.of(
            1L, new BenefitResult(2_000, Map.of(SpendingCategory.CAFE, 2_000L), true,
                List.of(), Map.of("7", 2_000L)),
            -1L, new BenefitResult(2_000, Map.of(SpendingCategory.CAFE, 2_000L), true,
                List.of(), Map.of("7", 2_000L))));

        TransactionBenefitService.Preview quote = service.preview(USER, 10L, 100L, MONTH,
            SpendingCategory.CAFE, 10_000, LocalDateTime.of(2026, 11, 5, 12, 0), 300_000);

        assertThat(quote.purchase().totalBenefit()).isEqualTo(2_000);
        assertThat(quote.monthBenefitAfter()).isEqualTo(4_000);
        assertThat(quote.beforeByRule()).containsEntry("7", 2_000L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BenefitPurchase>> purchases = ArgumentCaptor.forClass(List.class);
        verify(calculator).calculatePurchases(eq(100L), purchases.capture(), eq(300_000L));
        assertThat(purchases.getValue()).extracting(BenefitPurchase::id).containsExactly(1L, -1L);
    }
}
