package com.richreach.plan.service;

import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.dto.PlanAnalysisResponse;
import com.richreach.plan.dto.PlanAnalysisResponse.CategoryAmount;
import com.richreach.plan.dto.PlanAnalysisResponse.CategoryRatio;
import com.richreach.plan.dto.PlanAnalysisResponse.SuggestedBudget;
import com.richreach.transaction.dto.CardScope;
import com.richreach.transaction.dto.SummaryPeriod;
import com.richreach.transaction.dto.TransactionSummaryResponse;
import com.richreach.transaction.dto.TransactionSummaryResponse.CategorySummary;
import com.richreach.transaction.service.TransactionSummaryService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 소비 패턴 분석과 월 예산 추천. 분석 대상으로 고른 카드의 거래만 보고, 거래 요약의 집계(취소·환불을 뺀 승인일 기준 금액)를
 * 그대로 쓴다. 읽기만 하고 아무것도 저장하지 않는다. 계산식은 {@link SpendingAnalysisCalculator}에 있다.
 *
 * <ul>
 *   <li>월평균은 기간 합계를 분석한 개월 수로 나눈다. 수집된 거래가 요청한 기간보다 짧으면(부분 데이터) 수집된 기간만큼의
 *       개월 수로 나눈다. 전체 개월 수로 나누면 예산이 실제보다 크게 줄기 때문이다. 수집 기간이 한 달보다 짧아도 1개월로 본다.</li>
 *   <li>총예산은 월평균을 1,000원 단위로 반올림한 값이고, 업종 예산은 소비 비중대로 나누되 합이 총예산과 같다.</li>
 *   <li>소비가 없으면 오류가 아니라 {@code hasData=false}이고 비율과 예산이 모두 0이다.</li>
 * </ul>
 * 금액은 소비 정보라서 로그에 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SpendingAnalysisService {

    private final TransactionSummaryService summaryService;

    public PlanAnalysisResponse analyze(long userId, SummaryPeriod period) {
        TransactionSummaryResponse summary = summaryService.summarize(userId, period, CardScope.SELECTED);

        Map<SpendingCategory, Long> amounts = new EnumMap<>(SpendingCategory.class);
        for (CategorySummary category : summary.categories()) {
            amounts.put(category.category(), category.amount());
        }

        boolean hasData = summary.totalAmount() > 0;
        BigDecimal months = analyzedMonths(summary, period);
        long monthlyAverage = hasData
            ? BigDecimal.valueOf(summary.totalAmount()).divide(months, 0, RoundingMode.HALF_UP).longValueExact()
            : 0;
        long budgetTotal = SpendingAnalysisCalculator.roundBudgetTotal(monthlyAverage);

        Map<SpendingCategory, Integer> ratios = SpendingAnalysisCalculator.ratios(amounts);
        Map<SpendingCategory, Long> budget = SpendingAnalysisCalculator.budgetByCategory(budgetTotal, amounts);

        List<CategoryRatio> categoryRatios = ratios.entrySet().stream()
            .map(entry -> new CategoryRatio(entry.getKey(), entry.getValue())).toList();
        List<CategoryAmount> byCategory = budget.entrySet().stream()
            .map(entry -> new CategoryAmount(entry.getKey(), entry.getValue())).toList();

        return new PlanAnalysisResponse(
            months.setScale(0, RoundingMode.HALF_UP).max(BigDecimal.ONE).intValueExact(), monthlyAverage,
            SpendingAnalysisCalculator.topCategory(amounts), hasData, summary.partial(), categoryRatios,
            new SuggestedBudget(budgetTotal, byCategory));
    }

    /**
     * 분석한 개월 수. 요청한 기간을 다 채웠으면 그 기간의 개월 수이고, 수집된 거래가 기간보다 짧으면 수집된 일수만큼 줄인다.
     * 한 달보다 짧으면 1이다.
     */
    private static BigDecimal analyzedMonths(TransactionSummaryResponse summary, SummaryPeriod period) {
        BigDecimal requested = BigDecimal.valueOf(period.months());
        if (!summary.partial() || summary.collectedFrom() == null) {
            return requested;
        }
        long windowDays = ChronoUnit.DAYS.between(summary.from(), summary.to()) + 1;
        long collectedDays = ChronoUnit.DAYS.between(summary.collectedFrom(), summary.to()) + 1;
        BigDecimal months = requested.multiply(BigDecimal.valueOf(collectedDays))
            .divide(BigDecimal.valueOf(windowDays), 6, RoundingMode.HALF_UP);
        return months.max(BigDecimal.ONE);
    }

}
