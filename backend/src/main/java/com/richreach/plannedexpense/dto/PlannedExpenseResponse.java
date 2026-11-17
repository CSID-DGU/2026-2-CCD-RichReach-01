package com.richreach.plannedexpense.dto;

import com.richreach.plannedexpense.entity.PlannedExpense;

/**
 * 예정지출 한 건.
 *
 * @param amount        예상 금액(원). 할부면 전체 금액
 * @param monthlyAmount 이번 달 예산에 반영되는 금액. 할부면 전체 금액을 개월 수로 나눈 값(원 단위 반올림)
 * @param category      업종 코드
 * @param budgetMethod  {@code WITHIN_BUDGET} 또는 {@code EXTRA}
 */
public record PlannedExpenseResponse(long expenseId, String month, String name, long amount, Integer installmentMonths,
                                     long monthlyAmount, String category, String budgetMethod) {

    public static PlannedExpenseResponse from(PlannedExpense expense) {
        return new PlannedExpenseResponse(expense.getId(), expense.getPlanMonth(), expense.getName(),
            expense.getAmount(), expense.getInstallmentMonths(), expense.monthlyAmount(),
            expense.getCategory().name(), expense.getBudgetMethod().name());
    }

}
