package com.richreach.plannedexpense.dto;

import com.richreach.plannedexpense.entity.BudgetMethod;
import java.util.List;

/**
 * 한 달의 예정지출 목록과 반영 방식별 합계.
 *
 * @param month    조회한 월(yyyy-MM)
 * @param expenses 가장 최근에 추가한 것이 먼저
 * @param summary  이번 달에 반영되는 금액(할부면 월 금액)의 합계
 */
public record PlannedExpensesResponse(String month, List<PlannedExpenseResponse> expenses, Summary summary) {

    /**
     * @param withinBudget {@code WITHIN_BUDGET}(기존 예산 안에서 조정) 합계(원)
     * @param extra        {@code EXTRA}(예산 밖 추가 지출) 합계(원)
     * @param total        두 합계의 합(원)
     */
    public record Summary(long withinBudget, long extra, long total) {

    }

    public static PlannedExpensesResponse of(String month, List<PlannedExpenseResponse> expenses) {
        long within = sum(expenses, BudgetMethod.WITHIN_BUDGET);
        long extra = sum(expenses, BudgetMethod.EXTRA);
        return new PlannedExpensesResponse(month, expenses, new Summary(within, extra, within + extra));
    }

    private static long sum(List<PlannedExpenseResponse> expenses, BudgetMethod method) {
        return expenses.stream().filter(expense -> expense.budgetMethod().equals(method.name()))
            .mapToLong(PlannedExpenseResponse::monthlyAmount).sum();
    }

}
