package com.richreach.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 로그인 요청. 형식(이메일, 비밀번호 규칙)은 일부러 검사하지 않는다.
 * 형식 오류와 계정 없음/비밀번호 틀림을 다르게 응답하면 어떤 값이 맞는지 짐작할 수 있기 때문이다.
 */
public record LoginRequest(
    @NotBlank(message = "아이디(이메일)를 입력해 주세요.")
    @Size(max = 255, message = "아이디는 255자 이하여야 합니다.")
    String loginId,

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @Size(max = 128, message = "비밀번호가 너무 깁니다.")
    String password) {

    /** 로그에 비밀번호가 남지 않도록 직접 만든다. */
    @Override
    public String toString() {
        return "LoginRequest[loginId=" + loginId + "]";
    }

}
