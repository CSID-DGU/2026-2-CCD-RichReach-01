package com.richreach.transaction.service;

import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.transaction.dto.CardScope;
import com.richreach.transaction.dto.SummaryPeriod;
import com.richreach.transaction.dto.TransactionResponse;
import com.richreach.transaction.repository.CardTransactionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자의 거래 목록과 상세. 항상 요청한 사용자의 거래만 보고, 다른 사용자의 거래와 없는 거래는 똑같이 {@code TRANS-001}로
 * 응답해서 남의 거래가 있는지 알려주지 않는다.
 *
 * <ul>
 *   <li>목록은 승인일시 최신순이고, 같으면 나중에 저장된 것이 먼저다.</li>
 *   <li>기간은 요약과 같은 기준이다. 오늘을 끝으로 N개월 전 같은 날부터 오늘까지, 승인일 기준이다. 기간이 없으면 전체다.</li>
 *   <li>취소된 거래는 기본으로 목록에 나오고 {@code status}로 구분한다. 전액 취소를 숨길 수 있다.</li>
 * </ul>
 * 가맹점명과 금액은 소비 정보라서 로그에 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class TransactionQueryService {

    private final CardTransactionRepository transactions;
    private final OwnedCardQueryService ownedCards;
    private final Clock clock;

    /** 목록 조회 조건. 값이 없는 조건은 적용하지 않는다. */
    public record Filter(SummaryPeriod period, Long cardId, CardScope scope, SpendingCategory category,
                         boolean includeCanceled) {

    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> list(long userId, Filter filter, int page, int size) {
        LocalDateTime from = null;
        LocalDateTime to = null;
        if (filter.period() != null) {
            LocalDate today = LocalDate.now(clock);
            from = today.minusMonths(filter.period().months()).atStartOfDay();
            to = today.plusDays(1).atStartOfDay();
        }
        // 카드를 직접 지정하면 그 카드를 본다. 지정하지 않았을 때만 카드 범위를 따른다.
        List<Long> cardIds = null;
        if (filter.cardId() == null && filter.scope() == CardScope.SELECTED) {
            cardIds = ownedCards.list(userId).stream().filter(OwnedCardResponse::selected)
                .map(OwnedCardResponse::cardId).toList();
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("approvedAt"), Sort.Order.desc("id")));
        return transactions.findAll(TransactionSpecifications.filter(userId, from, to, filter.cardId(), cardIds,
            filter.category(), filter.includeCanceled()), pageable).map(TransactionResponse::from);
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(long userId, long transactionId) {
        return transactions.findByIdAndUserId(transactionId, userId).map(TransactionResponse::from)
            .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
    }

}
