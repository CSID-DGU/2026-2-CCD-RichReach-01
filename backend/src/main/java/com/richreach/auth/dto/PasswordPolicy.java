package com.richreach.auth.dto;

/**
 * 비밀번호 규칙. 회원가입과 비밀번호 재설정이 같은 규칙을 쓰도록 한곳에 둔다.
 * 영문과 숫자를 포함한 8~64자이고, BCrypt가 72바이트까지만 쓰므로 영문/숫자/기호만 받는다. (한글, 공백 불가)
 */
public final class PasswordPolicy {

    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]{8,64}$";

    public static final String MESSAGE = "비밀번호는 영문과 숫자를 포함해 8~64자(공백 제외)여야 합니다.";

    private PasswordPolicy() {
    }

}
