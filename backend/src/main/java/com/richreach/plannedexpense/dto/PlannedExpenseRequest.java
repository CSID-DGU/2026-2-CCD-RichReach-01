package com.richreach.plannedexpense.dto;

/**
 * 예정지출 추가·수정 요청. 검증은 서비스가 한다. (어기면 400 {@code EXPENSE-002})
 *
 * @param name              이름. 앞뒤 공백을 뺀 길이가 1~30자
 * @param amount            예상 금액(원). 0보다 크다. 할부면 전체 금액
 * @param installmentMonths 할부 개월 수(2, 3, 6, 12). 일시불이면 생략하거나 null
 * @param category          업종 코드 11개 중 하나
 * @param budgetMethod      {@code WITHIN_BUDGET}(기존 예산 안에서 조정) 또는 {@code EXTRA}(예산 밖 추가 지출). 생략하면 {@code WITHIN_BUDGET}
 */
public record PlannedExpenseRequest(String name, Long amount, Integer installmentMonths, String category,
                                    String budgetMethod) {

}
