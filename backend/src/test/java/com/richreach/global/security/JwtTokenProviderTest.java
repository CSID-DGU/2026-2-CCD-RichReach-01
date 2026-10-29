package com.richreach.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.global.security.JwtTokenProvider.IssuedToken;
import com.richreach.global.security.JwtTokenProvider.TokenClaims;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    /** 레포에 키 문자열을 남기지 않고 실행할 때마다 새로 만든다. (32바이트 이상) */
    private static final String SECRET = randomSecret();
    private static final Duration ACCESS_TTL = Duration.ofMinutes(30);
    private static final Duration REFRESH_TTL = Duration.ofDays(14);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-01T00:00:00Z"));
    private final JwtTokenProvider provider = providerWith(SECRET, "richreach", clock);

    private static String randomSecret() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static JwtTokenProvider providerWith(String secret, String issuer, MutableClock clock) {
        JwtProperties properties = new JwtProperties(issuer, ACCESS_TTL, REFRESH_TTL, secret);
        JwtSigningKey key = new JwtSigningKey(properties);
        SecurityConfig config = new SecurityConfig();
        return new JwtTokenProvider(config.jwtEncoder(key), config.jwtDecoder(key, properties), properties, clock);
    }

    private static void assertRejectedWith(ErrorCode expected, Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(expected);
    }

    @Test
    @DisplayName("access 토큰을 발급하면 사용자 ID, 종류, 만료 시각을 되돌려 읽을 수 있다")
    void accessTokenRoundTrip() {
        IssuedToken issued = provider.createAccessToken(42L);

        TokenClaims claims = provider.parse(issued.value(), TokenType.ACCESS);

        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.type()).isEqualTo(TokenType.ACCESS);
        assertThat(claims.id()).isEqualTo(issued.id());
        assertThat(claims.expiresAt()).isEqualTo(clock.instant().plus(ACCESS_TTL));
    }

    @Test
    @DisplayName("refresh 토큰은 access 토큰보다 오래 유효하다")
    void refreshTokenLivesLonger() {
        TokenClaims claims = provider.parse(provider.createRefreshToken(7L).value(), TokenType.REFRESH);

        assertThat(claims.expiresAt()).isEqualTo(clock.instant().plus(REFRESH_TTL));
    }

    @Test
    @DisplayName("비밀번호 재설정 토큰은 묶어 둔 값을 그대로 돌려주고, access/refresh 토큰에는 묶인 값이 없다")
    void passwordResetTokenCarriesBinding() {
        IssuedToken reset = provider.createPasswordResetToken(9L, "binding-value", Duration.ofMinutes(10));

        TokenClaims claims = provider.parse(reset.value(), TokenType.PASSWORD_RESET);

        assertThat(claims.userId()).isEqualTo(9L);
        assertThat(claims.binding()).isEqualTo("binding-value");
        assertThat(claims.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(10)));
        assertThat(provider.parse(provider.createAccessToken(9L).value(), TokenType.ACCESS).binding()).isNull();
    }

    @Test
    @DisplayName("재설정 토큰은 access/refresh 토큰 자리에서 쓸 수 없고 반대도 마찬가지다")
    void passwordResetTokenCannotBeUsedElsewhere() {
        String reset = provider.createPasswordResetToken(9L, "b", Duration.ofMinutes(10)).value();
        String access = provider.createAccessToken(9L).value();

        assertRejectedWith(ErrorCode.INVALID_TOKEN, () -> provider.parse(reset, TokenType.ACCESS));
        assertRejectedWith(ErrorCode.INVALID_TOKEN, () -> provider.parse(reset, TokenType.REFRESH));
        assertRejectedWith(ErrorCode.INVALID_TOKEN, () -> provider.parse(access, TokenType.PASSWORD_RESET));
    }

    @Test
    @DisplayName("재설정 토큰은 유효 시간이 지나면 만료된다")
    void passwordResetTokenExpires() {
        String reset = provider.createPasswordResetToken(9L, "b", Duration.ofMinutes(10)).value();

        clock.advance(Duration.ofMinutes(10));

        assertRejectedWith(ErrorCode.TOKEN_EXPIRED, () -> provider.parse(reset, TokenType.PASSWORD_RESET));
    }

    @Test
    @DisplayName("토큰마다 고유 ID가 달라서 같은 시각에 발급해도 값이 겹치지 않는다")
    void tokensAreUnique() {
        assertThat(provider.createRefreshToken(1L).value()).isNotEqualTo(provider.createRefreshToken(1L).value());
    }

    @Test
    @DisplayName("access 자리에 refresh 토큰을, refresh 자리에 access 토큰을 쓰면 거부한다")
    void rejectsWrongTokenType() {
        String access = provider.createAccessToken(1L).value();
        String refresh = provider.createRefreshToken(1L).value();

        assertRejectedWith(ErrorCode.INVALID_TOKEN, () -> provider.parse(refresh, TokenType.ACCESS));
        assertRejectedWith(ErrorCode.INVALID_TOKEN, () -> provider.parse(access, TokenType.REFRESH));
    }

    @Test
    @DisplayName("만료된 토큰은 위조와 구분되는 TOKEN_EXPIRED로 거부한다")
    void rejectsExpiredToken() {
        String access = provider.createAccessToken(1L).value();
        String refresh = provider.createRefreshToken(1L).value();

        clock.advance(ACCESS_TTL.plusSeconds(1));

        assertRejectedWith(ErrorCode.TOKEN_EXPIRED, () -> provider.parse(access, TokenType.ACCESS));
        assertThat(provider.parse(refresh, TokenType.REFRESH).userId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("만료 시각 정각에는 이미 만료된 것으로 본다")
    void expiresExactlyAtExpiry() {
        String access = provider.createAccessToken(1L).value();

        clock.advance(ACCESS_TTL);

        assertRejectedWith(ErrorCode.TOKEN_EXPIRED, () -> provider.parse(access, TokenType.ACCESS));
    }

    @Test
    @DisplayName("내용을 바꾼(서명이 맞지 않는) 토큰은 거부한다")
    void rejectsTamperedToken() {
        String token = provider.createAccessToken(1L).value();
        String[] parts = token.split("\\.");
        String tamperedPayload = parts[1].substring(0, parts[1].length() - 2) + (parts[1].endsWith("AA") ? "BB" : "AA");

        assertRejectedWith(ErrorCode.INVALID_TOKEN,
            () -> provider.parse(parts[0] + "." + tamperedPayload + "." + parts[2], TokenType.ACCESS));
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 거부한다")
    void rejectsTokenSignedWithAnotherKey() {
        JwtTokenProvider other = providerWith(randomSecret(), "richreach", clock);

        assertRejectedWith(ErrorCode.INVALID_TOKEN,
            () -> provider.parse(other.createAccessToken(1L).value(), TokenType.ACCESS));
    }

    @Test
    @DisplayName("다른 발급자가 만든 토큰은 거부한다")
    void rejectsTokenFromAnotherIssuer() {
        JwtTokenProvider other = providerWith(SECRET, "someone-else", clock);

        assertRejectedWith(ErrorCode.INVALID_TOKEN,
            () -> provider.parse(other.createAccessToken(1L).value(), TokenType.ACCESS));
    }

    @Test
    @DisplayName("JWT가 아니거나 서명이 없는(alg=none) 토큰은 거부한다")
    void rejectsMalformedAndUnsignedTokens() {
        assertRejectedWith(ErrorCode.INVALID_TOKEN, () -> provider.parse("not-a-jwt", TokenType.ACCESS));
        assertRejectedWith(ErrorCode.INVALID_TOKEN, () -> provider.parse("", TokenType.ACCESS));
        // {"alg":"none"} . {"sub":"1","type":"access"} .
        assertRejectedWith(ErrorCode.INVALID_TOKEN, () -> provider.parse(
            "eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIiwidHlwZSI6ImFjY2VzcyJ9.", TokenType.ACCESS));
    }

    @Test
    @DisplayName("서명 키가 32바이트보다 짧으면 앱이 시작되지 않는다")
    void rejectsShortSecret() {
        JwtProperties properties = new JwtProperties("richreach", ACCESS_TTL, REFRESH_TTL, "too-short");

        assertThatThrownBy(() -> new JwtSigningKey(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("32");
    }

    @Test
    @DisplayName("서명 키를 설정하지 않으면 임시 키를 만들고, 실행마다 키가 달라서 이전 토큰은 쓸 수 없다")
    void blankSecretGeneratesRandomKey() {
        JwtTokenProvider first = providerWith("", "richreach", clock);
        JwtTokenProvider second = providerWith(null, "richreach", clock);

        String token = first.createAccessToken(1L).value();

        assertThat(first.parse(token, TokenType.ACCESS).userId()).isEqualTo(1L);
        assertRejectedWith(ErrorCode.INVALID_TOKEN, () -> second.parse(token, TokenType.ACCESS));
    }

}
