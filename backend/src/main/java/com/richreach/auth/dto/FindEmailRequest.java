package com.richreach.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 이메일 찾기 요청. 이름과 전화번호가 모두 같은 계정을 찾는다. */
public record FindEmailRequest(
    @NotBlank(message = "이름을 입력해 주세요.")
    @Size(max = 100, message = "이름은 100자 이하여야 합니다.")
    String name,

    @NotBlank(message = "전화번호를 입력해 주세요.")
    @Pattern(regexp = "^01[016789][- ]?\\d{3,4}[- ]?\\d{4}$", message = "올바른 전화번호 형식이 아닙니다.")
    String phoneNumber) {

    /** 로그에 이름과 전화번호가 남지 않도록 직접 만든다. */
    @Override
    public String toString() {
        return "FindEmailRequest[]";
    }

}
