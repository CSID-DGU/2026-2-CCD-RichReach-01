package com.richreach.plan.dto;

/**
 * 플랜 생성을 시작한 결과.
 *
 * @param jobId 진행 상태를 조회할 작업 ID ({@code GET /api/plans/jobs/{jobId}}). 진행 중인 같은 요청이 있으면 그 작업의 ID다.
 */
public record PlanJobStartResponse(String jobId) {

}
