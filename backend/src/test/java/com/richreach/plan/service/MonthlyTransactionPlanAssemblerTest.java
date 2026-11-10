package com.richreach.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.richreach.benefit.dto.TransactionBenefitRule;
import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.service.BenefitRuleQueryService;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.transaction.entity.CardTransaction;
import com.richreach.transaction.repository.CardTransactionRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MonthlyTransactionPlanAssemblerTest {
    private final OwnedCardQueryService owned = mock(OwnedCardQueryService.class);
    private final CardTransactionRepository transactions = mock(CardTransactionRepository.class);
    private final BenefitRuleQueryService rules = mock(BenefitRuleQueryService.class);
    private final MonthlyTransactionPlanAssembler assembler =
        new MonthlyTransactionPlanAssembler(owned, transactions, rules);

    private static CardTransaction row(long id, long card, String time, long amount) {
        CardTransaction result = mock(CardTransaction.class);
        when(result.getId()).thenReturn(id);
        when(result.getUserCardId()).thenReturn(card);
        when(result.getApprovedAt()).thenReturn(LocalDateTime.parse(time));
        when(result.netAmount()).thenReturn(amount);
        when(result.effectiveCategory()).thenReturn("CAFE");
        when(result.getMerchantName()).thenReturn("CAFE");
        return result;
    }

    private static TransactionBenefitRule readyRule() {
        return new TransactionBenefitRule("r1", "r1", new BigDecimal("1"), null,
            null, null, null, null, null, 30_000L, null, null, "KRW", true,
            TransactionBenefitRule.parse("{\"schema\":\"card-benefit-v1\",\"performance_exclusions\":[\"GIFT_CARD\"]}"));
    }

    @Test
    void usesSelectedOwnedCardAndKeepsUnknownPerformanceUnknown() {
        when(owned.list(7L)).thenReturn(List.of(
            new OwnedCardResponse(101L, 301L, null, "selected", "", null, null, true, null),
            new OwnedCardResponse(102L, 302L, null, "other", "", null, null, false, null)));
        List<CardTransaction> rows = List.of(
                row(1, 101, "2026-08-20T10:00:00", 10_000),
                row(2, 102, "2026-08-21T10:00:00", 40_000),
                row(3, 101, "2026-09-03T10:00:00", 5_000));
        when(transactions.findByUserIdAndApprovedAtGreaterThanEqualAndApprovedAtLessThanOrderByApprovedAtAscIdAsc(
            eq(7L), any(), any())).thenReturn(rows);
        when(rules.findForTransactions(301L)).thenReturn(List.of(readyRule()));

        var input = assembler.assemble(7L, YearMonth.of(2026, 9),
            LocalDateTime.parse("2026-09-10T00:00:00"),
            Map.of(SpendingCategory.CAFE, 20_000L), Duration.ofSeconds(2));
        assertEquals(1, input.history().size());
        assertEquals(1, input.committed().size());
        assertEquals(101L, input.history().getFirst().userCardId());
        assertEquals(301L, input.cards().getFirst().cardProductId());
        assertNull(input.cards().getFirst().previousEligibleSpend());
        assertEquals(101L, input.cards().getFirst().userCardId());
    }

    @Test
    void refusesCatalogOnlyCardWithoutVerifiedTransactionRules() {
        when(owned.list(7L)).thenReturn(List.of(
            new OwnedCardResponse(101L, 301L, null, "selected", "", null, null, true, null)));
        CardTransaction previous = row(1, 101, "2026-08-20T10:00:00", 10_000);
        when(transactions.findByUserIdAndApprovedAtGreaterThanEqualAndApprovedAtLessThanOrderByApprovedAtAscIdAsc(
            eq(7L), any(), any())).thenReturn(List.of(previous));
        when(rules.findForTransactions(301L)).thenReturn(List.of());
        BusinessException error = assertThrows(BusinessException.class, () -> assembler.assemble(
            7L, YearMonth.of(2026, 9), LocalDateTime.parse("2026-09-01T00:00:00"),
            Map.of(SpendingCategory.CAFE, 10_000L), Duration.ofSeconds(2)));
        assertEquals(ErrorCode.PLAN_TRANSACTION_RULES_UNAVAILABLE, error.getErrorCode());
    }

    @Test
    void refusesPartialBenefitsWhenOneRuleIsUnverified() {
        when(owned.list(7L)).thenReturn(List.of(
            new OwnedCardResponse(101L, 301L, null, "selected", "", null, null, true, null)));
        CardTransaction previous = row(1, 101, "2026-08-20T10:00:00", 10_000);
        when(transactions.findByUserIdAndApprovedAtGreaterThanEqualAndApprovedAtLessThanOrderByApprovedAtAscIdAsc(
            eq(7L), any(), any())).thenReturn(List.of(previous));
        TransactionBenefitRule approved = readyRule();
        TransactionBenefitRule pending = new TransactionBenefitRule("pending", "pending", new BigDecimal("2"),
            null, null, null, null, null, null, null, null, null, "KRW", false,
            TransactionBenefitRule.parse("{\"schema\":\"card-benefit-v1\"}"));
        when(rules.findForTransactions(301L)).thenReturn(List.of(approved, pending));
        BusinessException error = assertThrows(BusinessException.class, () -> assembler.assemble(
            7L, YearMonth.of(2026, 9), LocalDateTime.parse("2026-09-01T00:00:00"),
            Map.of(SpendingCategory.CAFE, 10_000L), Duration.ofSeconds(2)));
        assertEquals(ErrorCode.PLAN_TRANSACTION_RULES_UNAVAILABLE, error.getErrorCode());
    }
}
