package com.richreach.plan.dto;

/**
 * 플랜 생성 작업의 진행 상태. 화면은 {@code status}가 {@code IN_PROGRESS}가 아닐 때까지 1~2초마다 조회한다.
 *
 * @param status        IN_PROGRESS(진행 중), COMPLETED(완료), FAILED(실패). 서버가 멈춰 제한 시간이 지난 작업은 FAILED다.
 * @param progress      진행률 0~100. 완료되면 100이다.
 * @param planId        완료되면 사용자에게 보여줄 플랜의 ID ({@code GET /api/plans?month=}로 조회). 아니면 null
 * @param failureCode   실패하면 구분 코드(예: NO_STRATEGY_RESULT, TIMEOUT). 아니면 null
 * @param failureReason 실패하면 이유. 아니면 null
 */
public record PlanJobStatusResponse(String status, int progress, Long planId, String failureCode,
                                    String failureReason) {

}
