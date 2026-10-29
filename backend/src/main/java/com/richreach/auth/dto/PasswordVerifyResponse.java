package com.richreach.auth.dto;

/** @param resetToken 새 비밀번호를 설정할 때 보내는 1회용 토큰. 유효 시간이 짧다. */
public record PasswordVerifyResponse(String resetToken) {

    /** 로그에 토큰이 남지 않도록 직접 만든다. */
    @Override
    public String toString() {
        return "PasswordVerifyResponse[]";
    }

}
