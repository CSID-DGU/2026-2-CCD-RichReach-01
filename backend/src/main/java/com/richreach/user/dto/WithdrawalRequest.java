package com.richreach.user.dto;

/**
 * 회원 탈퇴 요청({@code DELETE /api/users/me}). 삭제 내용을 안내받고 동의했는지만 받는다. 비밀번호는 받지 않는다.
 *
 * @param agreed 삭제·보관 데이터 안내를 확인하고 탈퇴에 동의했는지. {@code true}여야 한다.
 */
public record WithdrawalRequest(Boolean agreed) {

}
