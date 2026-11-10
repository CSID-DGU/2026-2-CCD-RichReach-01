package com.richreach.plan.service;

import com.richreach.benefit.dto.BenefitTransaction;
import com.richreach.benefit.dto.TransactionBenefitRule;
import com.richreach.benefit.service.TransactionBenefitCalculator;
import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.service.BenefitRuleQueryService;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.plan.optimizer.MonthlyTransactionPlanEngine;
import com.richreach.transaction.entity.CardTransaction;
import com.richreach.transaction.repository.CardTransactionRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only bridge from real owned cards, transactions and final benefit rules to the planner. */
@Service
@RequiredArgsConstructor
public class MonthlyTransactionPlanAssembler {
    private final OwnedCardQueryService ownedCards;
    private final CardTransactionRepository transactions;
    private final BenefitRuleQueryService rules;
    private final TransactionBenefitCalculator calculator = new TransactionBenefitCalculator();

    @Transactional(readOnly = true)
    public MonthlyTransactionPlanEngine.Input assemble(long userId, YearMonth month, LocalDateTime asOf,
                                                         Map<SpendingCategory, Long> confirmedBudget,
                                                         Duration timeLimit) {
        List<OwnedCardResponse> selected = ownedCards.list(userId).stream()
            .filter(card -> card.selected() && card.cardProductId() != null).toList();
        if (selected.isEmpty()) throw new BusinessException(ErrorCode.PLAN_NO_ANALYSIS_CARD);
        Set<Long> ids = selected.stream().map(OwnedCardResponse::cardId).collect(Collectors.toSet());
        LocalDateTime from = month.minusMonths(1).atDay(1).atStartOfDay();
        List<CardTransaction> observed = transactions
            .findByUserIdAndApprovedAtGreaterThanEqualAndApprovedAtLessThanOrderByApprovedAtAscIdAsc(
                userId, from, asOf).stream()
            .filter(transaction -> ids.contains(transaction.getUserCardId()) && transaction.netAmount() > 0)
            .toList();
        List<MonthlyTransactionPlanEngine.Observed> history = new ArrayList<>();
        List<MonthlyTransactionPlanEngine.Observed> committed = new ArrayList<>();
        Map<Long, List<BenefitTransaction>> historyByCard = new HashMap<>();
        for (CardTransaction row : observed) {
            BenefitTransaction event = toBenefitTransaction(row);
            MonthlyTransactionPlanEngine.Observed item = new MonthlyTransactionPlanEngine.Observed(
                event, row.getUserCardId());
            if (YearMonth.from(row.getApprovedAt()).equals(month.minusMonths(1))) {
                history.add(item);
                historyByCard.computeIfAbsent(row.getUserCardId(), key -> new ArrayList<>()).add(event);
            } else committed.add(item);
        }
        List<MonthlyTransactionPlanEngine.Card> cards = new ArrayList<>();
        for (OwnedCardResponse selectedCard : selected) {
            List<TransactionBenefitRule> found;
            try {
                found = rules.findForTransactions(selectedCard.cardProductId());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.PLAN_TRANSACTION_RULES_UNAVAILABLE,
                    "선택한 카드의 혜택 조건이 거래 단위 계산 형식으로 정리되지 않았습니다.");
            }
            if (found.isEmpty() || found.stream().anyMatch(rule -> !rule.ready()))
                throw new BusinessException(ErrorCode.PLAN_TRANSACTION_RULES_UNAVAILABLE,
                    "선택한 카드의 혜택 규칙이 없거나 아직 모두 검증되지 않았습니다.");
            Long previousEligibleSpend;
            try {
                previousEligibleSpend = calculator.eligibleSpend(found,
                    historyByCard.getOrDefault(selectedCard.cardId(), List.of()));
            } catch (IllegalArgumentException e) {
                previousEligibleSpend = null;
            }
            cards.add(new MonthlyTransactionPlanEngine.Card(selectedCard.cardId(),
                selectedCard.cardProductId(), found, previousEligibleSpend, Map.of()));
        }
        return new MonthlyTransactionPlanEngine.Input(month, asOf, history, committed, cards,
            confirmedBudget, timeLimit, false);
    }

    private static BenefitTransaction toBenefitTransaction(CardTransaction row) {
        String category = SpendingCategory.fromCode(row.effectiveCategory())
            .orElse(SpendingCategory.ETC).name();
        Map<String, String> facts = new HashMap<>();
        facts.put("overseas", Boolean.toString(row.isOverseas()));
        facts.put("installment_months", Integer.toString(row.getInstallmentMonths()));
        // CODEF's stored transaction does not establish gift-card/tax exclusions or posting time.
        // Those facts intentionally stay absent, so eligibility and performance may remain unknown.
        return new BenefitTransaction(Long.toString(row.getId()), row.getApprovedAt(), null,
            row.netAmount(), row.getMerchantName(), category, "PURCHASE", facts);
    }
}
