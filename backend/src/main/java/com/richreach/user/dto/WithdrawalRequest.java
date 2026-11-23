package com.richreach.user.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * 회원 탈퇴 요청({@code DELETE /api/users/me}). 삭제 내용을 안내받고 동의했는지만 받는다. 비밀번호는 받지 않는다.
 * 동의 값이 없거나 {@code true}가 아니면 400({@code COMMON-400})이다.
 *
 * @param agreed 삭제·보관 데이터 안내를 확인하고 탈퇴에 동의했는지. {@code true}여야 한다.
 */
public record WithdrawalRequest(
    @NotNull(message = "탈퇴 동의 여부가 필요합니다.")
    @AssertTrue(message = "탈퇴 내용에 동의해야 합니다.")
    Boolean agreed) {

}
