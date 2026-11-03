package com.richreach.codef.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.codef.config.CodefProperties;
import com.richreach.codef.dto.CodefTokenResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CodefTokenProviderTest {

    private static final long EXPIRES_IN_SECONDS = 7 * 24 * 3600L;

    private CodefTokenClient tokenClient;
    private MutableClock clock;
    private CodefTokenProvider provider;

    @BeforeEach
    void setUp() {
        tokenClient = mock(CodefTokenClient.class);
        clock = new MutableClock(Instant.parse("2026-11-04T00:00:00Z"));
        CodefProperties properties = new CodefProperties(null, null, null, null, null, null, null, null,
            Duration.ofHours(1));
        provider = new CodefTokenProvider(tokenClient, properties, clock);
        when(tokenClient.issueToken()).thenReturn(
            new CodefTokenResponse("test-token-1", "bearer", EXPIRES_IN_SECONDS, "read"),
            new CodefTokenResponse("test-token-2", "bearer", EXPIRES_IN_SECONDS, "read"));
    }

    @Test
    @DisplayName("유효한 토큰은 다시 발급하지 않고 재사용한다")
    void reusesValidToken() {
        assertThat(provider.getAccessToken()).isEqualTo("test-token-1");
        assertThat(provider.getAccessToken()).isEqualTo("test-token-1");

        verify(tokenClient, times(1)).issueToken();
    }

    @Test
    @DisplayName("만료가 임박(여유 시간 이내)하면 새로 발급한다")
    void reissuesNearExpiry() {
        provider.getAccessToken();

        clock.advance(Duration.ofSeconds(EXPIRES_IN_SECONDS).minus(Duration.ofMinutes(30)));

        assertThat(provider.getAccessToken()).isEqualTo("test-token-2");
        verify(tokenClient, times(2)).issueToken();
    }

    @Test
    @DisplayName("여유 시간 전까지는 계속 재사용한다")
    void keepsTokenBeforeMargin() {
        provider.getAccessToken();

        clock.advance(Duration.ofSeconds(EXPIRES_IN_SECONDS).minus(Duration.ofHours(2)));

        assertThat(provider.getAccessToken()).isEqualTo("test-token-1");
        verify(tokenClient, times(1)).issueToken();
    }

    @Test
    @DisplayName("invalidate 하면 다음 호출에서 새로 발급한다")
    void invalidateForcesReissue() {
        provider.getAccessToken();

        provider.invalidate();

        assertThat(provider.getAccessToken()).isEqualTo("test-token-2");
    }

    @Test
    @DisplayName("토큰 값은 toString에 노출되지 않는다")
    void tokenIsMaskedInToString() {
        CodefTokenProvider.IssuedToken token = provider.getToken();

        assertThat(token.toString()).doesNotContain("test-token-1");
    }

    /** 시간을 앞으로 보낼 수 있는 테스트용 Clock. */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

    }

}
