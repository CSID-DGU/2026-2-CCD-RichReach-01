package com.richreach.global.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 로그인 토큰 설정 ({@code app.jwt.*}).
 *
 * @param issuer          토큰 발급자. 다른 곳에서 만든 토큰을 거르는 데 쓴다.
 * @param accessTokenTtl  access 토큰 유효 시간
 * @param refreshTokenTtl refresh 토큰 유효 시간
 * @param secret          서명 키(32바이트 이상). 비어 있으면 실행 때마다 임시 키를 만든다. ({@link JwtSigningKey})
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(
    @DefaultValue("richreach") String issuer,
    @DefaultValue("30m") Duration accessTokenTtl,
    @DefaultValue("14d") Duration refreshTokenTtl,
    String secret) {
}
