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
     * @param failureCode   실패했을 때 화면 처리용 코드(SYNC_TIMEOUT, CODEF_UNAVAILABLE, ACCOUNT_RESTRICTED, SYNC_FAILED). 아니면 null
     * @param failureReason 실패했을 때 사용자에게 그대로 보여줄 문구. 아니면 null
     * @param transactions  승인내역 수집 결과. 카드 동기화가 성공해 수집까지 마친 경우만 채워지고, 아니면 null
     */
    public record Result(
        Long connectionId,
        CardCompany cardCompany,
        String status,
        int cardCount,
        String failureCode,
        String failureReason,
        TransactionCollection transactions) {

    }

    /**
     * 승인내역(거래) 수집 결과. 수집이 실패해도 카드 동기화 자체는 SUCCESS다.
     *
     * @param status         SUCCESS(최근 기간 전부 수집), PARTIAL(일부 기간만 수집), FAILED(수집하지 못함)
     * @param savedCount     새로 저장하거나 갱신한 거래 수
     * @param unmatchedCount 보유카드를 정하지 못해 저장하지 않은 승인내역 수
     * @param collectedFrom  수집을 마친 기간의 시작일(승인일). 하나도 못 마쳤으면 null
     * @param collectedTo    수집을 마친 기간의 종료일. 하나도 못 마쳤으면 null
     */
    public record TransactionCollection(
        String status,
        int savedCount,
        int unmatchedCount,
        java.time.LocalDate collectedFrom,
        java.time.LocalDate collectedTo) {


    }

}
