package com.richreach.auth.service;

import com.richreach.auth.dto.TokenPair;
import com.richreach.auth.entity.RefreshToken;
import com.richreach.auth.repository.RefreshTokenRepository;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.global.security.JwtTokenProvider;
import com.richreach.global.security.JwtTokenProvider.IssuedToken;
import com.richreach.global.security.JwtTokenProvider.TokenClaims;
import com.richreach.global.security.TokenType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 토큰 발급, 재발급, 폐기. 로그인/로그아웃/재발급 API가 이 서비스를 쓴다.
 *
 * <ul>
 *   <li>refresh 토큰은 원문이 아니라 SHA-256 해시만 DB에 저장한다.</li>
 *   <li>재발급할 때마다 새 refresh 토큰으로 교체하고 이전 것은 폐기한다. (rotation)</li>
 *   <li>이미 폐기된 refresh 토큰이 다시 오면 탈취로 보고 그 사용자의 refresh 토큰을 모두 폐기한다.</li>
 * </ul>
 */
@Slf4j
@Service
public class AuthTokenService {

    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public AuthTokenService(JwtTokenProvider tokenProvider, RefreshTokenRepository refreshTokenRepository,
                            Clock clock) {
        this.tokenProvider = tokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    /** 로그인에 성공한 사용자에게 access/refresh 토큰을 새로 발급한다. */
    @Transactional
    public TokenPair issue(long userId) {
        IssuedToken access = tokenProvider.createAccessToken(userId);
        IssuedToken refresh = tokenProvider.createRefreshToken(userId);
        refreshTokenRepository.save(new RefreshToken(userId, hash(refresh.value()), toLocal(refresh.expiresAt())));
        return new TokenPair(access.value(), refresh.value(), secondsUntil(access.expiresAt()));
    }

    /**
     * refresh 토큰으로 새 토큰 한 쌍을 발급하고 쓴 토큰은 폐기한다.
     * 폐기된 토큰이 다시 오면 그 사용자의 토큰을 모두 폐기하므로, 예외를 던져도 폐기 결과는 되돌리지 않는다.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenPair reissue(String refreshToken) {
        TokenClaims claims = tokenProvider.parse(refreshToken, TokenType.REFRESH);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(refreshToken))
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        LocalDateTime now = LocalDateTime.now(clock);
        if (stored.isRevoked()) {
            log.warn("폐기된 refresh 토큰이 다시 사용되어 사용자 {}의 토큰을 모두 폐기합니다.", stored.getUserId());
            refreshTokenRepository.revokeAllByUserId(stored.getUserId(), now);
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        stored.revoke(now);
        return issue(claims.userId());
    }

    /** 로그아웃. 이 refresh 토큰을 폐기한다. 이미 폐기됐거나 알 수 없는 토큰이어도 조용히 끝낸다. */
    @Transactional
    public void revoke(String refreshToken) {
        refreshTokenRepository.findByTokenHash(hash(refreshToken))
            .ifPresent(stored -> stored.revoke(LocalDateTime.now(clock)));
    }

    /** 사용자의 모든 refresh 토큰을 폐기한다. (비밀번호 변경, 탈퇴, 전체 기기 로그아웃) */
    @Transactional
    public void revokeAll(long userId) {
        refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now(clock));
    }

    private LocalDateTime toLocal(Instant instant) {
        return LocalDateTime.ofInstant(instant, clock.getZone());
    }

    private long secondsUntil(Instant instant) {
        return Duration.between(clock.instant(), instant).toSeconds();
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

}
