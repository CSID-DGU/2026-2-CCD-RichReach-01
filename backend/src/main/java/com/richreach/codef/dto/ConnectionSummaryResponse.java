package com.richreach.codef.dto;

import com.richreach.codef.entity.CardCompany;
import java.time.OffsetDateTime;

/**
 * 연결한 카드사 하나의 현재 상태. 목록이 비어 있으면 연결한 카드사가 없다는 뜻이다.
 *
 * @param connectionId  카드사 연결 ID
 * @param status        가장 최근 동기화 기준: NOT_SYNCED(아직 안 함), IN_PROGRESS, SUCCESS, FAILED
 * @param cardCount     가장 최근에 가져온 카드 수. 아직 가져오지 않았거나 실패했으면 0이다.
 * @param failureCode   가장 최근 동기화가 실패했을 때 화면 처리용 코드. 아니면 null
 * @param failureReason 가장 최근 동기화가 실패했을 때 사용자에게 보여줄 문구. 아니면 null
 * @param lastSyncedAt  마지막으로 동기화에 성공한 시각. 한 번도 없으면 null
 */
public record ConnectionSummaryResponse(
    Long connectionId,
    CardCompany cardCompany,
    String status,
    int cardCount,
    String failureCode,
    String failureReason,
    OffsetDateTime lastSyncedAt) {

}
