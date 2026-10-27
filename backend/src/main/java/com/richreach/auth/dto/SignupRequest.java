package com.richreach.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청. 비밀번호 확인 값은 프론트에서만 비교하고 서버로 보내지 않는다.
 *
 * @param name         이름
 * @param phoneNumber  전화번호. 하이픈 없이 숫자만 보내는 것이 약속이고, 하이픈이 있어도 서버가 숫자만 남겨서 저장한다.
 * @param loginId      아이디(이메일)
 * @param password     비밀번호. 영문과 숫자를 포함한 8~64자. BCrypt는 72바이트까지만 쓰므로 영문/숫자/기호만 받는다.
 * @param termsAgreed  [필수] 서비스 이용약관 및 개인정보 수집 동의
 */
public record SignupRequest(
    @NotBlank(message = "이름을 입력해 주세요.")
    @Size(max = 100, message = "이름은 100자 이하여야 합니다.")
    String name,

    @NotBlank(message = "전화번호를 입력해 주세요.")
    @Pattern(regexp = "^01[016789][- ]?\\d{3,4}[- ]?\\d{4}$", message = "올바른 전화번호 형식이 아닙니다.")
    String phoneNumber,

    @NotBlank(message = "아이디(이메일)를 입력해 주세요.")
    @Size(max = 255, message = "아이디는 255자 이하여야 합니다.")
    @Pattern(regexp = CheckIdRequest.EMAIL_PATTERN, message = "올바른 이메일 형식이 아닙니다.")
    String loginId,

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]{8,64}$",
        message = "비밀번호는 영문과 숫자를 포함해 8~64자(공백 제외)여야 합니다.")
    String password,

    boolean termsAgreed) {

    /** 로그에 비밀번호가 남지 않도록 직접 만든다. (record의 기본 toString은 모든 필드를 찍는다.) */
    @Override
    public String toString() {
        return "SignupRequest[loginId=" + loginId + ", termsAgreed=" + termsAgreed + "]";
    }

}
