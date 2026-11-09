package com.richreach.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 새 비밀번호 설정 요청. 비밀번호 확인 값은 프론트에서만 비교하고 보내지 않는다. */
public record PasswordResetRequest(
    @NotBlank(message = "재설정 토큰이 필요합니다.")
    String resetToken,

    @NotBlank(message = "새 비밀번호를 입력해 주세요.")
    @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
    String newPassword) {

    /** 로그에 토큰과 비밀번호가 남지 않도록 직접 만든다. */
    @Override
    public String toString() {
        return "PasswordResetRequest[]";
    }

}
