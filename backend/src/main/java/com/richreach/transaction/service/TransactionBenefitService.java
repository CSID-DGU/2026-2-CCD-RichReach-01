package com.richreach.transaction.service;

import com.richreach.benefit.dto.BenefitQuery;
import com.richreach.benefit.dto.BenefitResult;
import com.richreach.benefit.service.BenefitCalculator;
import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.global.category.SpendingCategory;
import com.richreach.transaction.dto.TransactionResponse;
import com.richreach.transaction.repository.CardTotal;
import com.richreach.transaction.repository.CardTransactionRepository;
import com.richreach.transaction.repository.TransactionNet;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거래별 예상 혜택과 월 예상 혜택 합계. 카드사가 실제로 지급한 금액이 아니라, 카드의 혜택 규칙으로 계산한 <b>예상</b> 혜택이다.
 *
 * <p>카드 한 장의 한 달 거래를 업종별로 모아 기존 혜택 계산기({@link BenefitCalculator})에 한 번만 묻고, 업종별 월 혜택을 그
 * 업종의 거래들에 금액 비율대로 나눈다({@link TransactionBenefitAllocator}). 월 한도와 전월실적 구간은 계산기가 처리하므로 여기서
 * 따로 따지지 않는다. 전월실적은 그 카드의 전월 거래(취소·환불을 뺀 값)다. 거래마다 계산기를 부르지 않는 것은 계산기가 호출마다 혜택
 * 규칙을 DB에서 읽기 때문이다.
 *
 * <ul>
 *   <li>한도에 걸리는 달은 같은 업종 거래들의 혜택이 금액 비율대로 줄어든다. (승인 순서로 한도를 채우지 않는다)</li>
 *   <li>취소·환불은 취소를 뺀 금액으로 계산하므로 전액 취소된 거래는 0원이다.</li>
 *   <li>카드 상품과 매칭되지 않은 카드나 동기화에서 사라진 카드의 거래는 계산할 수 없어서 혜택을 주지 않는다({@code null}).
 *       계산했지만 혜택이 없는 거래는 0이다.</li>
 *   <li>어떤 혜택 규칙을 계산에 쓰는지는 계산기가 정한다. 이 서비스는 계산기의 결과를 나눌 뿐이다.</li>
 * </ul>
 * 금액은 소비 정보라서 로그에 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class TransactionBenefitService {

    private final CardTransactionRepository transactions;
    private final OwnedCardQueryService ownedCards;
    private final BenefitCalculator calculator;

    /** 카드 한 장의 한 달 계산 결과. */
    private record CardMonth(Map<Long, Long> byTransaction, long total) {

    }

    /** 카드와 달. 같은 요청 안에서 같은 계산을 다시 하지 않으려는 키다. */
    private record Key(long cardId, YearMonth month) {

    }

    /**
     * 주어진 거래들의 예상 혜택. 거래가 속한 카드·달 전체를 기준으로 계산해서 나눈 값이다.
     *
     * @return 거래 ID별 혜택(원). 계산할 수 없는 거래는 들어 있지 않다.
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> forTransactions(long userId, Collection<TransactionResponse> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> productByCard = productByCard(userId);
        Map<Key, CardMonth> computed = new HashMap<>();
        Map<YearMonth, Map<Long, Long>> previousSpend = new HashMap<>();
        Map<Long, Long> result = new HashMap<>();
        for (TransactionResponse item : items) {
            Long productId = productByCard.get(item.cardId());
            if (productId == null) {
                continue;
            }
            Key key = new Key(item.cardId(), YearMonth.from(item.approvedAt()));
            CardMonth cardMonth = computed.computeIfAbsent(key,
                k -> compute(userId, k, productId, previousSpend));
            Long benefit = cardMonth.byTransaction().get(item.transactionId());
            if (benefit != null) {
                result.put(item.transactionId(), benefit);
            }
        }
        return result;
    }

    /**
     * 한 달 동안 {@code cardIds}의 카드들로 받은 것으로 보는 예상 혜택 합계(원). 거래별 혜택의 합과 같다.
     * 카드가 없으면 0이고, 카드가 있지만 하나도 계산할 수 없으면(카드 상품과 매칭되지 않음) null이다.
     */
    @Transactional(readOnly = true)
    public Long monthTotal(long userId, YearMonth month, Collection<Long> cardIds) {
        if (cardIds.isEmpty()) {
            return 0L;
        }
        Map<Long, Long> productByCard = productByCard(userId);
        Map<YearMonth, Map<Long, Long>> previousSpend = new HashMap<>();
        long total = 0;
        boolean anyComputed = false;
        for (Long cardId : cardIds) {
            Long productId = productByCard.get(cardId);
            if (productId == null) {
                continue;
            }
            total += compute(userId, new Key(cardId, month), productId, previousSpend).total();
            anyComputed = true;
        }
        return anyComputed ? total : null;
    }

    private CardMonth compute(long userId, Key key, long productId, Map<YearMonth, Map<Long, Long>> previousSpend) {
        LocalDateTime from = key.month().atDay(1).atStartOfDay();
        LocalDateTime to = key.month().plusMonths(1).atDay(1).atStartOfDay();
        List<TransactionNet> nets = transactions.findNets(userId, key.cardId(), from, to);

        Map<SpendingCategory, Long> spend = new EnumMap<>(SpendingCategory.class);
        List<TransactionBenefitAllocator.Item> items = nets.stream().map(net -> {
            SpendingCategory category = SpendingCategory.fromCode(net.category()).orElse(SpendingCategory.ETC);
            spend.merge(category, net.netAmount(), Long::sum);
            return new TransactionBenefitAllocator.Item(net.id(), category, net.netAmount());
        }).toList();

        long previous = previousSpend.computeIfAbsent(key.month().minusMonths(1), month -> transactions
                .sumByCard(userId, month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay()).stream()
                .collect(Collectors.toMap(CardTotal::userCardId, total -> total.netAmount() == null ? 0L : total.netAmount())))
            .getOrDefault(key.cardId(), 0L);

        BenefitResult result = calculator.calculate(new BenefitQuery(productId, spend, previous));
        Map<Long, Long> byTransaction = TransactionBenefitAllocator.allocate(result.benefitByCategory(), items);
        long total = byTransaction.values().stream().mapToLong(Long::longValue).sum();
        return new CardMonth(byTransaction, total);
    }

    /** 활성 보유카드의 카드 상품 ID. 카드 상품과 매칭되지 않은 카드나 사라진 카드는 없다. */
    private Map<Long, Long> productByCard(long userId) {
        return ownedCards.list(userId).stream().filter(card -> card.cardProductId() != null)
            .collect(Collectors.toMap(OwnedCardResponse::cardId, OwnedCardResponse::cardProductId, (a, b) -> a));
    }

}
