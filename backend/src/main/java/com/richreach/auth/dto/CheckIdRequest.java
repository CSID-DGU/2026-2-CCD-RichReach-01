package com.richreach.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 아이디(이메일) 중복 확인 요청. 이메일이 주소에 남지 않도록 본문으로 받는다. */
public record CheckIdRequest(
    @NotBlank(message = "아이디(이메일)를 입력해 주세요.")
    @Size(max = 255, message = "아이디는 255자 이하여야 합니다.")
    @Pattern(regexp = EMAIL_PATTERN, message = "올바른 이메일 형식이 아닙니다.")
    String loginId) {

    /** 공백 없이 '@'가 하나이고, 도메인에 '.'이 있는 형식. (Bean Validation의 @Email은 'a@b'도 통과시킨다.) */
    static final String EMAIL_PATTERN = "^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$";

}
