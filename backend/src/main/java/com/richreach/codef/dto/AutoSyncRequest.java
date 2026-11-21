package com.richreach.codef.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 거래내역 자동 동기화 설정 변경 요청.
 *
 * @param enabled true면 켜고 false면 끈다.
 */
public record AutoSyncRequest(@NotNull(message = "자동 동기화 사용 여부를 보내 주세요.") Boolean enabled) {

}
