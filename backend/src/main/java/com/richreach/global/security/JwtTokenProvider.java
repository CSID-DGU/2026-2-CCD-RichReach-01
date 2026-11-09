package com.richreach.global.security;

import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * access/refresh 토큰을 만들고 검증한다. 토큰에는 사용자 ID(sub), 종류(type), 고유 ID(jti), 만료 시각만 담는다.
 * 검증에 실패하면 만료는 {@link ErrorCode#TOKEN_EXPIRED}, 그 외(위조, 형식 오류, 종류 불일치)는
 * {@link ErrorCode#INVALID_TOKEN}으로 {@link BusinessException}을 던진다.
 */
@Component
public class JwtTokenProvider {

    private static final String TYPE_CLAIM = "type";
    private static final String BINDING_CLAIM = "bind";

    /** 발급한 토큰. {@code id}는 토큰마다 달라서 같은 시각에 발급해도 값이 겹치지 않는다. */
    public record IssuedToken(String value, String id, Instant expiresAt) {
    }

    /**
     * 검증을 통과한 토큰의 내용.
     *
     * @param binding 토큰을 만들 때 묶어 둔 값. 비밀번호 재설정 토큰에만 있고 나머지는 null이다.
     */
    public record TokenClaims(long userId, String id, TokenType type, Instant expiresAt, String binding) {
    }

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtTokenProvider(JwtEncoder encoder, JwtDecoder decoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken createAccessToken(long userId) {
        return create(userId, TokenType.ACCESS, properties.accessTokenTtl(), null);
    }

    public IssuedToken createRefreshToken(long userId) {
        return create(userId, TokenType.REFRESH, properties.refreshTokenTtl(), null);
    }

    /**
     * 비밀번호 재설정 토큰. {@code binding}에 현재 비밀번호를 가리키는 값을 묶어 두면, 비밀번호가 바뀐 뒤에는
     * 같은 토큰을 다시 쓸 수 없다. (저장소 없이 1회용으로 만드는 방법이다.)
     */
    public IssuedToken createPasswordResetToken(long userId, String binding, Duration ttl) {
        return create(userId, TokenType.PASSWORD_RESET, ttl, binding);
    }

    private IssuedToken create(long userId, TokenType type, Duration ttl, String binding) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        String id = UUID.randomUUID().toString();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .subject(String.valueOf(userId))
            .id(id)
            .issuedAt(now)
            .expiresAt(expiresAt)
            .claim(TYPE_CLAIM, type.claimValue());
        if (binding != null) {
            builder.claim(BINDING_CLAIM, binding);
        }
        JwtClaimsSet claims = builder.build();
        String value = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
        return new IssuedToken(value, id, expiresAt);
    }

    /**
     * 서명, 발급자, 종류, 만료를 확인하고 내용을 돌려준다.
     *
     * @param expected 기대하는 토큰 종류. access 자리에 refresh 토큰이 오면 거부한다.
     */
    public TokenClaims parse(String token, TokenType expected) {
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        if (!expected.claimValue().equals(jwt.getClaimAsString(TYPE_CLAIM))) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null || jwt.getId() == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        // 만료는 디코더가 아니라 여기서 확인한다. 만료와 위조를 다른 에러 코드로 구분해야 하기 때문이다.
        if (!expiresAt.isAfter(clock.instant())) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }
        try {
            return new TokenClaims(Long.parseLong(jwt.getSubject()), jwt.getId(), expected, expiresAt,
                jwt.getClaimAsString(BINDING_CLAIM));
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

}
