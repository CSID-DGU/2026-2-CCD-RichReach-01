package com.richreach.global.event;

import java.time.YearMonth;

/**
 * 사용자의 예정지출이 추가·수정·삭제되었다는 알림. 예정지출 쪽이 월간 플랜 도메인을 직접 알지 않고, 플랜 쪽이 이 알림을 받아
 * 남은 기간의 플랜을 다시 계산한다.
 *
 * @param userId 예정지출이 바뀐 사용자
 * @param month  바뀐 예정지출이 반영되는 월
 */
public record PlannedExpensesChangedEvent(long userId, YearMonth month) {

}
