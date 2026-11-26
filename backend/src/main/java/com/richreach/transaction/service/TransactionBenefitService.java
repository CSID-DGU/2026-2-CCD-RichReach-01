package com.richreach.transaction.service;

import com.richreach.benefit.dto.BenefitPurchase;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.benefit.service.BenefitCalculator;
import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.global.category.SpendingCategory;
import com.richreach.transaction.dto.TransactionResponse;
import com.richreach.transaction.repository.CardTotal;
import com.richreach.transaction.repository.CardTransactionRepository;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Replays approved purchases in order so a shared monthly cap is spent only once. */
@Service
@RequiredArgsConstructor
public class TransactionBenefitService {

    private final CardTransactionRepository transactions;
    private final OwnedCardQueryService ownedCards;
    private final BenefitCalculator calculator;
    private final MerchantCategoryClassifier classifier;

    private record Key(long cardId, YearMonth month) {
    }

    /** A read-only quote. The prospective purchase is not counted until the approval sync arrives. */
    public record Preview(BenefitResult purchase, long monthBenefitAfter, Map<String, Long> beforeByRule) {
    }

    /** Historical purchase units for forecast construction or current-month replanning. */
    @Transactional(readOnly = true)
    public List<BenefitPurchase> cardPurchases(long userId, long cardId, YearMonth month) {
        return purchases(userId, new Key(cardId, month));
    }

    /** Actual credited benefit by category, after replaying refunds and shared caps. */
    @Transactional(readOnly = true)
    public Map<SpendingCategory, Long> monthByCategory(long userId, long cardId, long productId,
                                                       YearMonth month, long previousMonthSpend) {
        Map<SpendingCategory, Long> amounts = new java.util.EnumMap<>(SpendingCategory.class);
        calculator.calculatePurchases(productId, purchases(userId, new Key(cardId, month)), previousMonthSpend)
            .values().forEach(result -> result.benefitByCategory()
                .forEach((category, value) -> amounts.merge(category, value, Math::addExact)));
        return amounts;
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> forTransactions(long userId, Collection<TransactionResponse> items) {
        if (items.isEmpty()) return Map.of();
        Map<Long, Long> productByCard = productByCard(userId);
        Map<Key, Map<Long, BenefitResult>> computed = new HashMap<>();
        Map<YearMonth, Map<Long, Long>> previousSpend = new HashMap<>();
        Map<Long, Long> result = new HashMap<>();
        for (TransactionResponse item : items) {
            Long productId = productByCard.get(item.cardId());
            if (productId == null) continue;
            Key key = new Key(item.cardId(), YearMonth.from(item.approvedAt()));
            Map<Long, BenefitResult> benefits = computed.computeIfAbsent(key,
                ignored -> compute(userId, key, productId, previousSpend));
            BenefitResult benefit = benefits.get(item.transactionId());
            if (benefit != null) result.put(item.transactionId(), benefit.totalBenefit());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Long monthTotal(long userId, YearMonth month, Collection<Long> cardIds) {
        if (cardIds.isEmpty()) return 0L;
        Map<Long, Long> productByCard = productByCard(userId);
        Map<YearMonth, Map<Long, Long>> previousSpend = new HashMap<>();
        long total = 0;
        boolean computed = false;
        for (Long cardId : cardIds) {
            Long productId = productByCard.get(cardId);
            if (productId == null) continue;
            for (BenefitResult benefit : compute(userId, new Key(cardId, month), productId, previousSpend).values()) {
                total = Math.addExact(total, benefit.totalBenefit());
            }
            computed = true;
        }
        return computed ? total : null;
    }

    /** Quote a single direct-card purchase against the current, net-of-refunds approval snapshot. */
    @Transactional(readOnly = true)
    public Preview preview(long userId, long cardId, long productId, YearMonth month,
                           SpendingCategory category, long amount, LocalDateTime at, long previousMonthSpend) {
        List<BenefitPurchase> purchases = new ArrayList<>(purchases(userId, new Key(cardId, month)));
        long candidateId = -1L;
        purchases.add(new BenefitPurchase(candidateId, at, category, amount, true));
        Map<Long, BenefitResult> calculated = calculator.calculatePurchases(productId, purchases, previousMonthSpend);
        BenefitResult candidate = calculated.get(candidateId);
        Map<String, Long> before = new HashMap<>();
        long monthTotal = 0;
        for (Map.Entry<Long, BenefitResult> entry : calculated.entrySet()) {
            monthTotal = Math.addExact(monthTotal, entry.getValue().totalBenefit());
            if (entry.getKey() != candidateId) {
                entry.getValue().benefitByRule().forEach((id, benefit) -> before.merge(id, benefit, Math::addExact));
            }
        }
        return new Preview(candidate, monthTotal, Map.copyOf(before));
    }

    private Map<Long, BenefitResult> compute(long userId, Key key, long productId,
                                              Map<YearMonth, Map<Long, Long>> previousSpend) {
        long previous = previousSpend.computeIfAbsent(key.month().minusMonths(1), month -> transactions
                .sumByCard(userId, month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay()).stream()
                .collect(Collectors.toMap(CardTotal::userCardId,
                    sum -> sum.netAmount() == null ? 0L : sum.netAmount())))
            .getOrDefault(key.cardId(), 0L);
        return calculator.calculatePurchases(productId, purchases(userId, key), previous);
    }

    private List<BenefitPurchase> purchases(long userId, Key key) {
        LocalDateTime from = key.month().atDay(1).atStartOfDay();
        LocalDateTime to = key.month().plusMonths(1).atDay(1).atStartOfDay();
        return transactions.findNets(userId, key.cardId(), from, to).stream().map(net -> {
            SpendingCategory category = SpendingCategory.fromCode(net.category()).orElse(SpendingCategory.ETC);
            // A user correction or a matching merchant rule establishes the category.
            // Unresolved merchants stay in ETC spending summaries, with zero benefit.
            boolean identified = category != SpendingCategory.ETC
                && (net.categoryOverride() != null
                    || classifier.classifyIfKnown(net.merchantName()).isPresent());
            return new BenefitPurchase(net.id(), net.approvedAt(), category, Math.max(0, net.netAmount()), identified);
        }).toList();
    }

    private Map<Long, Long> productByCard(long userId) {
        return ownedCards.list(userId).stream().filter(card -> card.cardProductId() != null)
            .collect(Collectors.toMap(OwnedCardResponse::cardId, OwnedCardResponse::cardProductId, (a, b) -> a));
    }
}
