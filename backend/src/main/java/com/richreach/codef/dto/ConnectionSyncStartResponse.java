package com.richreach.codef.dto;

/**
 * 동기화 시작 결과.
 *
 * @param jobId 상태 조회({@code GET /api/connections/sync/{jobId}})에 쓰는 ID. 이번에 시작한 카드사 전체를 묶는다.
 */
public record ConnectionSyncStartResponse(String jobId) {

}
