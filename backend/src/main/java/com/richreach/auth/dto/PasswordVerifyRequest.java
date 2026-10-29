package com.richreach.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 비밀번호 재설정 인증번호 확인 요청 */
public record PasswordVerifyRequest(
    @NotBlank(message = "아이디(이메일)를 입력해 주세요.")
    @Size(max = 255, message = "아이디는 255자 이하여야 합니다.")
    @Pattern(regexp = CheckIdRequest.EMAIL_PATTERN, message = "올바른 이메일 형식이 아닙니다.")
    String loginId,

    @NotBlank(message = "인증번호를 입력해 주세요.")
    @Pattern(regexp = "^\\d{6}$", message = "인증번호는 숫자 6자리입니다.")
    String code) {

    /** 로그에 인증번호가 남지 않도록 직접 만든다. */
    @Override
    public String toString() {
        return "PasswordVerifyRequest[loginId=" + loginId + "]";
    }

}
