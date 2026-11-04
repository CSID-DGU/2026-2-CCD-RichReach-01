package com.richreach.codef.dto;

import com.richreach.codef.entity.CardCompany;
import java.util.List;

/**
 * 동기화 진행 상태. 화면은 {@code status}가 {@code IN_PROGRESS}가 아닐 때까지 주기적으로 조회한다.
 *
 * @param jobId          동기화 시작 때 받은 ID
 * @param status         IN_PROGRESS(하나라도 진행 중), SUCCESS(모두 성공), PARTIAL_SUCCESS(일부만 성공), FAILED(모두 실패)
 * @param totalCount     이번 동기화에 포함된 카드사 수
 * @param completedCount 끝난(성공 또는 실패) 카드사 수. 진행률은 {@code completedCount / totalCount}로 계산한다.
 * @param results        카드사별 결과
 */
public record ConnectionSyncStatusResponse(
    String jobId,
    String status,
    int totalCount,
    int completedCount,
    List<Result> results) {

    /**
     * 카드사 하나의 결과.
     *
     * @param connectionId  카드사 연결 ID
     * @param status        IN_PROGRESS, SUCCESS, FAILED
     * @param cardCount     가져온 카드 수. 끝나지 않았거나 실패했으면 0이다.
     * @param failureCode   실패했을 때 화면 처리용 코드(SYNC_TIMEOUT, CODEF_UNAVAILABLE, SYNC_FAILED). 아니면 null
     * @param failureReason 실패했을 때 사용자에게 그대로 보여줄 문구. 아니면 null
     */
    public record Result(
        Long connectionId,
        CardCompany cardCompany,
        String status,
        int cardCount,
        String failureCode,
        String failureReason) {

    }

}
