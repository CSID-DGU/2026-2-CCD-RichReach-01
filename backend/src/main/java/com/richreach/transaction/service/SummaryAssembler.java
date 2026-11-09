package com.richreach.transaction.service;

import com.richreach.global.category.SpendingCategory;
import com.richreach.transaction.dto.SummaryPeriod;
import com.richreach.transaction.dto.TransactionSummaryResponse;
import com.richreach.transaction.dto.TransactionSummaryResponse.CardSummary;
import com.richreach.transaction.dto.TransactionSummaryResponse.CategorySummary;
import com.richreach.transaction.repository.CategoryTotal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 업종별 합계를 요약 응답으로 조립한다. DB 없이 계산만 하므로 따로 확인할 수 있다.
 *
 * <ul>
 *   <li>업종은 화면용 6개를 항상 같은 순서로 모두 내려준다. 거래가 없는 업종은 0이다.</li>
 *   <li>분류 전(null)이거나 6개 업종이 아닌 값은 {@code ETC}로 모은다.</li>
 *   <li>비율은 전체 합계에서 차지하는 비중(0~1)이고 소수 넷째 자리까지 반올림한다. 합계가 0이면 0이다.</li>
 *   <li>수집된 가장 오래된 거래가 기간 시작일보다 늦으면 부분 데이터({@code partial})로 표시한다.</li>
 * </ul>
 */
final class SummaryAssembler {

    private static final int RATIO_SCALE = 4;

    private SummaryAssembler() {
    }

    /** 한 업종으로 모은 합계. */
    private static final class Accumulator {

        private long net;
        private long canceled;
        private long count;

    }

    static TransactionSummaryResponse assemble(SummaryPeriod period, LocalDate from, LocalDate to,
                                               LocalDateTime earliestApprovedAt, List<CategoryTotal> totals,
                                               List<CardSummary> cards) {
        Map<SpendingCategory, Accumulator> byCategory = new EnumMap<>(SpendingCategory.class);
        for (SpendingCategory category : SpendingCategory.values()) {
            byCategory.put(category, new Accumulator());
        }
        for (CategoryTotal total : totals) {
            Accumulator target = byCategory.get(SpendingCategory.fromCode(total.category()).orElse(SpendingCategory.ETC));
            target.net += valueOf(total.netAmount());
            target.canceled += valueOf(total.canceledAmount());
            target.count += valueOf(total.transactionCount());
        }

        long totalAmount = byCategory.values().stream().mapToLong(accumulator -> accumulator.net).sum();
        long canceledAmount = byCategory.values().stream().mapToLong(accumulator -> accumulator.canceled).sum();
        long transactionCount = byCategory.values().stream().mapToLong(accumulator -> accumulator.count).sum();

        List<CategorySummary> categories = new ArrayList<>();
        for (SpendingCategory category : SpendingCategory.values()) {
            Accumulator accumulator = byCategory.get(category);
            categories.add(new CategorySummary(category, accumulator.net, ratio(accumulator.net, totalAmount),
                accumulator.count));
        }

        LocalDate collectedFrom = earliestApprovedAt == null ? null : earliestApprovedAt.toLocalDate();
        boolean partial = collectedFrom != null && collectedFrom.isAfter(from);
        return new TransactionSummaryResponse(period.code(), from, to, collectedFrom, partial, totalAmount,
            canceledAmount, transactionCount, List.copyOf(categories), List.copyOf(cards));
    }

    private static double ratio(long amount, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(total), RATIO_SCALE, RoundingMode.HALF_UP)
            .doubleValue();
    }

    private static long valueOf(Long value) {
        return value == null ? 0 : value;
    }

}
