package com.richreach.auth.dto;

/**
 * 로그인/재발급 때 클라이언트에 내려주는 토큰.
 *
 * @param accessToken            API 호출에 쓰는 토큰
 * @param refreshToken           access 토큰이 만료됐을 때 재발급에 쓰는 토큰
 * @param accessTokenExpiresIn   access 토큰이 만료될 때까지 남은 시간(초)
 */
public record TokenPair(String accessToken, String refreshToken, long accessTokenExpiresIn) {
}
