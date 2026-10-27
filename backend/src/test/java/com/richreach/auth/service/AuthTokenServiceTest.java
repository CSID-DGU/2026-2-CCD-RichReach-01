package com.richreach.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.richreach.TestcontainersConfiguration;
import com.richreach.auth.dto.TokenPair;
import com.richreach.auth.entity.RefreshToken;
import com.richreach.auth.repository.RefreshTokenRepository;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.global.security.JwtTokenProvider;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 일회용 MySQL에 V12까지 적용한 뒤 refresh 토큰의 저장, 교체, 폐기를 확인한다. */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AuthTokenServiceTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    @Autowired
    private AuthTokenService service;
    @Autowired
    private RefreshTokenRepository refreshTokens;
    @Autowired
    private UserRepository users;
    @Autowired
    private JwtTokenProvider tokenProvider;

    private long userId;

    @BeforeEach
    void createUser() {
        userId = newUser();
    }

    @AfterEach
    void cleanUp() {
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    private long newUser() {
        return users.save(new User(UUID.randomUUID() + "@example.com", "hash", "테스터", "01012345678", "REAL",
            LocalDateTime.now())).getId();
    }

    private static void assertRejectedWith(ErrorCode expected, Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(expected);
    }

    @Test
    @DisplayName("토큰을 발급하면 access/refresh 토큰과 남은 시간을 돌려주고, DB에는 해시만 저장한다")
    void issueStoresOnlyTheHash() {
        TokenPair pair = service.issue(userId);

        assertThat(pair.accessToken()).isNotBlank();
        assertThat(pair.refreshToken()).isNotBlank().isNotEqualTo(pair.accessToken());
        assertThat(pair.accessTokenExpiresIn()).isBetween(1790L, 1800L);

        List<RefreshToken> stored = refreshTokens.findAll();
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getTokenHash()).hasSize(64).isNotEqualTo(pair.refreshToken());
        assertThat(stored.get(0).getUserId()).isEqualTo(userId);
        assertThat(stored.get(0).isRevoked()).isFalse();
        assertThat(stored.get(0).getExpiresAt()).isAfter(LocalDateTime.now().plusDays(13));
    }

    @Test
    @DisplayName("재발급하면 새 토큰 한 쌍을 주고 쓴 refresh 토큰은 폐기한다")
    void reissueRotatesTheRefreshToken() {
        TokenPair first = service.issue(userId);

        TokenPair second = service.reissue(first.refreshToken());

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(tokenProvider.parse(second.accessToken(), com.richreach.global.security.TokenType.ACCESS).userId())
            .isEqualTo(userId);
        assertThat(refreshTokens.findAll()).hasSize(2)
            .filteredOn(RefreshToken::isRevoked).hasSize(1);
        // 새로 받은 토큰으로 다시 재발급할 수 있다.
        assertThatCode(() -> service.reissue(second.refreshToken())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("이미 쓴 refresh 토큰이 다시 오면 탈취로 보고 그 사용자의 토큰을 모두 폐기한다")
    void reusedRefreshTokenRevokesEverything() {
        TokenPair first = service.issue(userId);
        TokenPair second = service.reissue(first.refreshToken());

        assertRejectedWith(ErrorCode.INVALID_TOKEN, () -> service.reissue(first.refreshToken()));

        // 정상 사용자가 가진 최신 토큰도 폐기되어 다시 로그인해야 한다.
        assertRejectedWith(ErrorCode.INVALID_TOKEN, () -> service.reissue(second.refreshToken()));
        assertThat(refreshTokens.findAll()).allMatch(RefreshToken::isRevoked);
    }

    @Test
    @DisplayName("다른 사용자의 토큰은 탈취 의심 폐기의 영향을 받지 않는다")
    void reuseDetectionIsPerUser() {
        long otherUser = newUser();
        TokenPair mine = service.issue(userId);
        TokenPair others = service.issue(otherUser);
        service.reissue(mine.refreshToken());

        assertRejectedWith(ErrorCode.INVALID_TOKEN, () -> service.reissue(mine.refreshToken()));

        assertThatCode(() -> service.reissue(others.refreshToken())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("로그아웃(revoke)한 refresh 토큰으로는 재발급할 수 없다")
    void revokedTokenCannotBeReissued() {
        TokenPair pair = service.issue(userId);

        service.revoke(pair.refreshToken());

        assertRejectedWith(ErrorCode.INVALID_TOKEN, () -> service.reissue(pair.refreshToken()));
    }

    @Test
    @DisplayName("알 수 없거나 이미 폐기된 토큰으로 로그아웃해도 오류 없이 끝난다")
    void revokeIsIdempotent() {
        TokenPair pair = service.issue(userId);

        assertThatCode(() -> {
            service.revoke(pair.refreshToken());
            service.revoke(pair.refreshToken());
            service.revoke(tokenProvider.createRefreshToken(userId).value());
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("access 토큰으로는 재발급할 수 없다")
    void accessTokenCannotBeUsedToReissue() {
        TokenPair pair = service.issue(userId);

        assertRejectedWith(ErrorCode.INVALID_TOKEN, () -> service.reissue(pair.accessToken()));
    }

    @Test
    @DisplayName("서명은 맞지만 서버가 발급해 저장한 적 없는 refresh 토큰은 거부한다")
    void unknownRefreshTokenIsRejected() {
        String unknown = tokenProvider.createRefreshToken(userId).value();

        assertRejectedWith(ErrorCode.INVALID_TOKEN, () -> service.reissue(unknown));
    }

    @Test
    @DisplayName("revokeAll은 사용자의 모든 refresh 토큰을 폐기한다")
    void revokeAllRevokesEveryTokenOfTheUser() {
        TokenPair first = service.issue(userId);
        TokenPair second = service.issue(userId);

        service.revokeAll(userId);

        assertRejectedWith(ErrorCode.INVALID_TOKEN, () -> service.reissue(first.refreshToken()));
        assertRejectedWith(ErrorCode.INVALID_TOKEN, () -> service.reissue(second.refreshToken()));
    }

    @Test
    @DisplayName("사용자를 삭제하면 그 사용자의 refresh 토큰도 함께 삭제된다")
    void tokensAreDeletedWithTheUser() {
        service.issue(userId);

        users.deleteById(userId);

        assertThat(refreshTokens.findAll()).isEmpty();
    }

}
