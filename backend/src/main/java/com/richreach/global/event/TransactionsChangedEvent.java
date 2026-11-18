package com.richreach.global.event;

import java.time.YearMonth;

/**
 * 사용자의 거래가 새로 들어오거나 업종이 바뀌었다는 알림. 거래를 바꾼 쪽(카드 동기화, 업종 수정)이 월간 플랜 도메인을 직접 알지
 * 않고, 플랜 쪽이 이 알림을 받아 남은 기간의 플랜을 다시 계산한다.
 *
 * @param userId 거래가 바뀐 사용자
 * @param month  바뀐 거래의 승인 월
 * @param reason 바뀐 이유
 */
public record TransactionsChangedEvent(long userId, YearMonth month, Reason reason) {

    public enum Reason {
        /** 카드 동기화로 거래가 새로 저장되거나 갱신되었다. */
        SYNCED,
        /** 사용자가 거래의 업종을 고치거나 되돌렸다. */
        CATEGORY_EDITED,
        /** 사용자가 카드사 연결을 해제해 그 카드사의 카드와 거래가 지워졌다. */
        CARD_DISCONNECTED
    }

}
