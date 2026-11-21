package com.richreach.codef.dto;

import com.richreach.codef.entity.CardCompany;
import java.time.OffsetDateTime;

/**
 * 카드가 연결된 카드사 계정의 화면용 정보. 카드 정보 화면(SETTINGS-03)의 "연결 계정" 영역에 쓴다.
 *
 * @param connectionId    카드사 연결 ID
 * @param cardCompany     카드사
 * @param maskedLoginId   가린 카드사 아이디(앞 2자만 남김). 이 값을 저장하기 전에 연결한 계정은 null이다.
 * @param status          계정 상태: ACTIVE, NEEDS_CARD_AUTH, LOCKED, ERROR
 * @param autoSyncEnabled 거래내역 자동 동기화 설정
 * @param lastSyncedAt    마지막으로 동기화에 성공한 시각. 한 번도 없으면 null
 */
public record LinkedAccountResponse(Long connectionId, CardCompany cardCompany, String maskedLoginId, String status,
                                    boolean autoSyncEnabled, OffsetDateTime lastSyncedAt) {

}
