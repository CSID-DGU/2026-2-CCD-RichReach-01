package com.richreach.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** 토큰 재발급과 로그아웃에서 보내는 refresh 토큰 */
public record RefreshTokenRequest(
    @NotBlank(message = "refreshToken이 필요합니다.")
    String refreshToken) {

    /** 로그에 토큰이 남지 않도록 직접 만든다. */
    @Override
    public String toString() {
        return "RefreshTokenRequest[]";
    }

}
