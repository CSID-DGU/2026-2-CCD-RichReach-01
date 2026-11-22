package com.richreach.plan.dto;

/**
 * 새 달이 되어 이번 달 플랜이 아직 없을 때 보여줄 이번 달 예산 초안. 금액은 모두 원 단위 정수다. 읽기 전용이라 아무것도
 * 저장하지 않는다. 사용자가 고쳐서 확정하면 {@code budget}을 그대로 {@code POST /api/plans}의 {@code budget}에,
 * {@code targetMonth}를 {@code month}에 넣는다. (서버가 플랜을 자동으로 만들지는 않는다.)
 *
 * @param targetMonth        초안을 만든 대상 월(이번 달, {@code yyyy-MM})
 * @param planExists         이번 달 플랜이 이미 있는지. true이면 {@code POST /api/plans}가 409({@code PLAN-004})이므로 초안을
 *                           보여줄 필요가 없다.
 * @param source             초안의 출처. {@code PREVIOUS_BUDGET}(지난달에 확정한 예산을 이어서 씀), {@code ANALYSIS}(지난달 플랜이
 *                           없어 최근 소비로 추천한 예산), {@code NONE}(근거가 될 예산도 소비도 없음)
 * @param budget             초안 예산. {@code source}가 {@code NONE}이면 {@code null}이다.
 * @param previousMonth      지난달({@code yyyy-MM})
 * @param previousMonthSpent 지난달에 쓴 금액(분석 대상 카드, 취소·환불을 뺀 값). 초안과 견줘 보여 주는 참고값이다.
 */
public record PlanMonthDraftResponse(
    String targetMonth,
    boolean planExists,
    String source,
    PlanCreateRequest.Budget budget,
    String previousMonth,
    long previousMonthSpent) {

    public static final String SOURCE_PREVIOUS_BUDGET = "PREVIOUS_BUDGET";
    public static final String SOURCE_ANALYSIS = "ANALYSIS";
    public static final String SOURCE_NONE = "NONE";

}
