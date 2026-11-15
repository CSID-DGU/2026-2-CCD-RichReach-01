package com.richreach.transaction.service;

import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.transaction.dto.CardScope;
import com.richreach.transaction.dto.SummaryMonth;
import com.richreach.transaction.dto.SummaryPeriod;
import com.richreach.transaction.dto.TransactionSummaryResponse;
import com.richreach.transaction.dto.TransactionSummaryResponse.CardSummary;
import com.richreach.transaction.repository.CardTotal;
import com.richreach.transaction.repository.CardTransactionRepository;
import com.richreach.transaction.repository.CategoryTotal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자의 기간별·월별 소비 요약. 오늘을 끝으로 1·3·6개월 동안(또는 한 달 동안)의 합계와 화면용 11개 업종별 소비,
 * 카드별 소비를 계산한다.
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

    /**
     * 요청 값을 그대로 받는 진입점. 어느 기간으로 요약할지의 규칙을 여기서 정한다.
     * <ul>
     *   <li>{@code month}가 있으면 월 조회이고 {@code period}는 무시한다. (잘못된 값이어도 오류가 아니다)</li>
     *   <li>{@code month}가 없으면 {@code period}(1M, 3M, 6M)가 필요하고, 없거나 다른 값이면 400({@code TRANS-002})이다.</li>
     *   <li>{@code cardScope}는 없으면 기본 범위이고, 알 수 없는 값이면 400({@code COMMON-400})이다. 기간 값을 먼저 확인한다.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public TransactionSummaryResponse summarize(long userId, String period, String month, String cardScope) {
        if (month != null) {
            YearMonth parsedMonth = SummaryMonth.parse(month);
            return summarize(userId, parsedMonth, CardScope.parse(cardScope));
        }
        SummaryPeriod parsedPeriod = SummaryPeriod.fromCode(period)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PERIOD));
        return summarize(userId, parsedPeriod, CardScope.parse(cardScope));
    }

    /** 오늘을 끝으로 1·3·6개월 동안의 요약. */
    @Transactional(readOnly = true)
    public TransactionSummaryResponse summarize(long userId, SummaryPeriod period, CardScope scope) {
        return summarize(userId, SummaryRange.ofPeriod(period, LocalDate.now(clock)), scope);
    }

    /**
     * 한 달(달력 기준)의 요약. 이번 달은 1일부터 오늘까지이고, 지난 달은 말일까지다. 아직 오지 않은 월이면 400
     * ({@code COMMON-400})이다. 합계, 업종, 카드별 금액이 모두 같은 기간이다.
     */
    @Transactional(readOnly = true)
    public TransactionSummaryResponse summarize(long userId, YearMonth month, CardScope scope) {
        return summarize(userId, SummaryRange.ofMonth(month, LocalDate.now(clock)), scope);
    }

    private TransactionSummaryResponse summarize(long userId, SummaryRange range, CardScope scope) {
        // 기간 끝은 마지막 날이 끝나는 시각(다음 날 0시, 미포함)까지 본다.
        LocalDateTime start = range.from().atStartOfDay();
        LocalDateTime end = range.to().plusDays(1).atStartOfDay();

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

        return SummaryAssembler.assemble(range, earliest, totals, cardSummaries);
    }

    /**
     * 한 달(달력 기준, 승인일) 동안 보유카드별로 쓴 금액(원). 취소·환불을 뺀 값이고, 거래가 없는 카드는 결과에 없다.
     * 카드 혜택의 전월실적 조건을 판단할 때 쓴다. 카드사가 알려주는 실적이 아니라 수집한 거래로 계산한 추정값이다.
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> cardSpend(long userId, YearMonth month) {
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
        return transactions.sumByCard(userId, start, end).stream()
            .collect(Collectors.toMap(CardTotal::userCardId, total -> valueOf(total.netAmount())));
    }

    private static long valueOf(Long value) {
        return value == null ? 0 : value;
    }

}
