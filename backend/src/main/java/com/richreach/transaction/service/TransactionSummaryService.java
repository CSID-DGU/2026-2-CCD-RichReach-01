package com.richreach.transaction.service;

import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.transaction.dto.CardScope;
import com.richreach.transaction.dto.SummaryPeriod;
import com.richreach.transaction.dto.TransactionSummaryResponse;
import com.richreach.transaction.dto.TransactionSummaryResponse.CardSummary;
import com.richreach.transaction.repository.CardTotal;
import com.richreach.transaction.repository.CardTransactionRepository;
import com.richreach.transaction.repository.CategoryTotal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자의 기간별 소비 요약. 오늘을 끝으로 1·3·6개월 동안의 합계와 화면용 6개 업종별 소비, 카드별 소비를 계산한다.
 *
 * <p>금액은 취소·환불을 뺀 실제 결제 금액이다. 합계와 업종은 카드 범위({@link CardScope})를 따른다. 기본은 분석 대상으로
 * 고른 카드이고, {@code ALL}이면 사용자의 모든 거래다. 카드별 소비는 범위와 상관없이 활성 카드 전체를 보여준다.
 * 보유카드 정보는 카드 서비스를 거쳐서 읽는다. 계산 방식은 {@link SummaryAssembler}에 있다.
 * 가맹점명과 금액은 소비 정보라서 로그에 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class TransactionSummaryService {

    /** 카드 조건이 필요 없을 때 쿼리에 넘기는 자리 채움 값. (IN 조건에 빈 목록을 넘기지 않으려는 것이다.) */
    private static final List<Long> NO_CARD_FILTER = List.of(-1L);

    private final CardTransactionRepository transactions;
    private final OwnedCardQueryService ownedCards;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TransactionSummaryResponse summarize(long userId, SummaryPeriod period, CardScope scope) {
        LocalDate to = LocalDate.now(clock);
        LocalDate from = to.minusMonths(period.months());
        // 기간 끝은 오늘이 끝나는 시각(내일 0시, 미포함)까지 본다.
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        List<OwnedCardResponse> cards = ownedCards.list(userId);
        boolean allCards = scope == CardScope.ALL;
        List<Long> selectedIds = cards.stream().filter(OwnedCardResponse::selected).map(OwnedCardResponse::cardId)
            .toList();

        List<CategoryTotal> totals;
        LocalDateTime earliest;
        if (!allCards && selectedIds.isEmpty()) {
            // 분석 대상 카드가 없으면 볼 거래가 없다.
            totals = List.of();
            earliest = null;
        } else {
            List<Long> cardIds = allCards ? NO_CARD_FILTER : selectedIds;
            totals = transactions.sumByCategory(userId, start, end, allCards, cardIds);
            earliest = transactions.findEarliestApprovedAt(userId, allCards, cardIds);
        }

        Map<Long, CardTotal> byCard = transactions.sumByCard(userId, start, end).stream()
            .collect(Collectors.toMap(CardTotal::userCardId, Function.identity()));
        List<CardSummary> cardSummaries = cards.stream().map(card -> {
            CardTotal total = byCard.get(card.cardId());
            return new CardSummary(card.cardId(), card.cardCompany(), card.cardName(), card.maskedNumber(),
                card.selected(), total == null ? 0 : valueOf(total.netAmount()),
                total == null ? 0 : valueOf(total.transactionCount()));
        }).toList();

        return SummaryAssembler.assemble(period, from, to, earliest, totals, cardSummaries);
    }

    private static long valueOf(Long value) {
        return value == null ? 0 : value;
    }

}
