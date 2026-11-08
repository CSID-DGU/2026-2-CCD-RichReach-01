package com.richreach.transaction.service;

import com.richreach.transaction.dto.SummaryPeriod;
import com.richreach.transaction.dto.TransactionSummaryResponse;
import com.richreach.transaction.repository.CardTransactionRepository;
import com.richreach.transaction.repository.CategoryTotal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자의 기간별 소비 요약. 오늘을 끝으로 1·3·6개월 동안의 합계와 화면용 6개 업종별 소비를 계산한다.
 *
 * <p>금액은 취소·환불을 뺀 실제 결제 금액이다. 사용자의 모든 보유카드 거래를 합치고, 분석 대상으로 고른 카드만 보는 것은 아니다.
 * 계산 방식은 {@link SummaryAssembler}에 있다. 가맹점명과 금액은 소비 정보라서 로그에 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class TransactionSummaryService {

    private final CardTransactionRepository transactions;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TransactionSummaryResponse summarize(long userId, SummaryPeriod period) {
        LocalDate to = LocalDate.now(clock);
        LocalDate from = to.minusMonths(period.months());

        // 기간 끝은 오늘이 끝나는 시각(내일 0시, 미포함)까지 본다.
        List<CategoryTotal> totals = transactions.sumByCategory(userId, from.atStartOfDay(),
            to.plusDays(1).atStartOfDay());
        LocalDateTime earliest = transactions.findEarliestApprovedAt(userId);
        return SummaryAssembler.assemble(period, from, to, earliest, totals);
    }

}
