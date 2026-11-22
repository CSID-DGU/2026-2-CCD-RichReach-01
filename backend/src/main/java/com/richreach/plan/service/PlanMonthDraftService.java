package com.richreach.plan.service;

import com.richreach.plan.dto.PlanAnalysisResponse;
import com.richreach.plan.dto.PlanCreateRequest;
import com.richreach.plan.dto.PlanCreateRequest.CategoryBudget;
import com.richreach.plan.dto.PlanMonthDraftResponse;
import com.richreach.plan.repository.MonthlyPlanRepository;
import com.richreach.transaction.dto.CardScope;
import com.richreach.transaction.dto.SummaryPeriod;
import com.richreach.transaction.service.TransactionSummaryService;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 새 달이 되어 이번 달 플랜이 아직 없을 때, 이번 달 예산 초안을 제안한다. 플랜은 월 단위이고 재계산은 이번 달만 대상이라
 * 12월 1일이 되면 이번 달 플랜이 없다. 사용자가 새 플랜을 쉽게 만들도록 기본값만 주고, <b>서버가 플랜을 자동으로 만들지는
 * 않는다.</b> (자동 생성은 정책을 정한 뒤 후속) 읽기만 하고 아무것도 저장하지 않는다.
 *
 * <ul>
 *   <li>출처는 지난달에 확정한 예산({@code PREVIOUS_BUDGET})이 먼저다. 지난달 플랜이 없으면 최근 3개월 소비로 추천한
 *       예산({@code ANALYSIS})을 쓰고, 그것도 소비가 없으면 {@code NONE}이다.</li>
 *   <li>지난달 예산은 예정지출을 얹기 전 확정 예산이라 예정지출이 이중으로 들어가지 않는다. 할부 예정지출은 이월하지 않는다.</li>
 *   <li>이번 달 플랜이 이미 있으면 {@code planExists=true}로 알린다. 초안은 그래도 계산해서 준다.</li>
 * </ul>
 * 금액은 소비 정보라서 로그에 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PlanMonthDraftService {

    private final PlanQueryService planQuery;
    private final SpendingAnalysisService analysis;
    private final TransactionSummaryService transactions;
    private final MonthlyPlanRepository plans;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PlanMonthDraftResponse draft(long userId) {
        YearMonth target = YearMonth.now(clock);
        YearMonth previous = target.minusMonths(1);

        boolean planExists = !plans.findByUserIdAndPlanMonth(userId, target.toString()).isEmpty();
        long previousSpent = transactions.summarize(userId, previous, CardScope.SELECTED).totalAmount();

        Optional<PlanCreateRequest.Budget> previousBudget = planQuery.findConfirmedBudget(userId, previous);
        if (previousBudget.isPresent()) {
            return response(target, previous, planExists, PlanMonthDraftResponse.SOURCE_PREVIOUS_BUDGET,
                previousBudget.get(), previousSpent);
        }
        PlanAnalysisResponse suggested = analysis.analyze(userId, SummaryPeriod.THREE_MONTHS);
        if (suggested.hasData()) {
            List<CategoryBudget> byCategory = suggested.suggestedBudget().byCategory().stream()
                .map(item -> new CategoryBudget(item.category().name(), item.amount())).toList();
            return response(target, previous, planExists, PlanMonthDraftResponse.SOURCE_ANALYSIS,
                new PlanCreateRequest.Budget(suggested.suggestedBudget().total(), byCategory), previousSpent);
        }
        return response(target, previous, planExists, PlanMonthDraftResponse.SOURCE_NONE, null, previousSpent);
    }

    private static PlanMonthDraftResponse response(YearMonth target, YearMonth previous, boolean planExists,
                                                   String source, PlanCreateRequest.Budget budget, long spent) {
        return new PlanMonthDraftResponse(target.toString(), planExists, source, budget, previous.toString(), spent);
    }

}
