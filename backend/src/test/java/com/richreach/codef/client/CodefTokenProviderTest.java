package com.richreach.codef.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.codef.client.CodefTokenProvider.IssuedToken;
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

    private static final long EXPIRES_IN = 7 * 24 * 60 * 60L;

    private CodefTokenClient tokenClient;
    private MutableClock clock;
    private CodefTokenProvider provider;

    @BeforeEach
    void setUp() {
        tokenClient = mock(CodefTokenClient.class);
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        CodefProperties properties = new CodefProperties("id", "secret", null, "https://oauth.codef.io",
            "https://development.codef.io", Duration.ofSeconds(3), Duration.ofSeconds(10),
            Duration.ofSeconds(120), Duration.ofHours(1));
        provider = new CodefTokenProvider(tokenClient, properties, clock);

        when(tokenClient.issueToken())
            .thenReturn(new CodefTokenResponse("token-1", "bearer", EXPIRES_IN, "read"))
            .thenReturn(new CodefTokenResponse("token-2", "bearer", EXPIRES_IN, "read"));
    }

    @Test
    @DisplayName("처음 호출하면 토큰을 새로 발급한다")
    void firstCallIssuesToken() {
        IssuedToken token = provider.getToken();

        assertThat(token.value()).isEqualTo("token-1");
        assertThat(token.fromCache()).isFalse();
        assertThat(token.expiresAt()).isEqualTo(Instant.parse("2026-01-08T00:00:00Z"));
        verify(tokenClient, times(1)).issueToken();
    }

    @Test
    @DisplayName("유효한 토큰이 있으면 재발급 없이 재사용한다")
    void reusesCachedToken() {
        provider.getToken();
        clock.advance(Duration.ofDays(3));

        IssuedToken token = provider.getToken();

        assertThat(token.value()).isEqualTo("token-1");
        assertThat(token.fromCache()).isTrue();
        verify(tokenClient, times(1)).issueToken();
    }

    @Test
    @DisplayName("만료 1시간 전부터는 토큰을 새로 발급한다")
    void refreshesNearExpiry() {
        provider.getToken();
        clock.advance(Duration.ofDays(7).minusMinutes(30));

        IssuedToken token = provider.getToken();

        assertThat(token.value()).isEqualTo("token-2");
        assertThat(token.fromCache()).isFalse();
        verify(tokenClient, times(2)).issueToken();
    }

    @Test
    @DisplayName("invalidate 후에는 토큰을 새로 발급한다")
    void invalidateForcesReissue() {
        provider.getToken();
        provider.invalidate();

        IssuedToken token = provider.getToken();

        assertThat(token.value()).isEqualTo("token-2");
        verify(tokenClient, times(2)).issueToken();
    }

    @Test
    @DisplayName("IssuedToken은 toString으로 출력되어도 토큰 값이 노출되지 않는다")
    void tokenIsMaskedInToString() {
        IssuedToken token = provider.getToken();

        assertThat(token.toString()).doesNotContain("token-1");
    }

    /** 테스트에서 시간을 이동시킬 수 있는 Clock */
    private static class MutableClock extends Clock {

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
