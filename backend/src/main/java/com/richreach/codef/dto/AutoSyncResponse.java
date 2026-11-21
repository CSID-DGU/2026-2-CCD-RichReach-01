package com.richreach.codef.dto;

/**
 * 거래내역 자동 동기화 설정 결과.
 *
 * @param connectionId    카드사 연결 ID
 * @param autoSyncEnabled 바뀐 뒤의 설정
 */
public record AutoSyncResponse(Long connectionId, boolean autoSyncEnabled) {

}
