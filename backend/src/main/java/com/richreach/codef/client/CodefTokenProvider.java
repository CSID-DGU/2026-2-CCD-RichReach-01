package com.richreach.codef.client;

import com.richreach.codef.config.CodefProperties;
import com.richreach.codef.dto.CodefTokenResponse;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * CODEF 액세스 토큰을 발급받아 만료 전까지 재사용한다. (토큰 유효기간은 약 7일)
 * API를 호출할 때마다 토큰을 새로 발급하지 않도록 이 클래스를 통해 토큰을 얻는다.
 *
 * 현재는 서버 메모리에만 캐시하므로 재시작하면 새로 발급받는다.
 * 서버를 여러 대로 늘리면 공유 저장소(DB/Redis)로 옮겨야 한다.
 */
@Component
public class CodefTokenProvider {

    private final CodefTokenClient tokenClient;
    private final CodefProperties properties;
    private final Clock clock;

    private String accessToken;
    private String tokenType;
    private Instant expiresAt;

    public CodefTokenProvider(CodefTokenClient tokenClient, CodefProperties properties, Clock clock) {
        this.tokenClient = tokenClient;
        this.properties = properties;
        this.clock = clock;
    }

    /** 캐시된 토큰이 유효하면 재사용하고, 없거나 만료가 임박했으면 새로 발급한다. */
    public synchronized IssuedToken getToken() {
        Instant now = clock.instant();
        if (accessToken != null && now.isBefore(expiresAt.minus(properties.tokenRefreshMargin()))) {
            return new IssuedToken(accessToken, tokenType, expiresAt, true);
        }

        CodefTokenResponse response = tokenClient.issueToken();
        this.accessToken = response.accessToken();
        this.tokenType = response.tokenType();
        this.expiresAt = now.plusSeconds(response.expiresIn());
        return new IssuedToken(accessToken, tokenType, expiresAt, false);
    }

    /** API 호출에 사용할 액세스 토큰 값 */
    public String getAccessToken() {
        return getToken().value();
    }

    /** 캐시를 비운다. (API가 401을 반환하는 등 토큰을 다시 발급받아야 할 때) */
    public synchronized void invalidate() {
        this.accessToken = null;
        this.tokenType = null;
        this.expiresAt = null;
    }

    /**
     * @param value     토큰 값 (로그에 출력하지 않는다)
     * @param expiresAt 만료 시각
     * @param fromCache 캐시된 토큰을 재사용했는지 여부
     */
    public record IssuedToken(String value, String tokenType, Instant expiresAt, boolean fromCache) {

        @Override
        public String toString() {
            return "IssuedToken[value=****, tokenType=" + tokenType + ", expiresAt=" + expiresAt
                + ", fromCache=" + fromCache + "]";
        }

    }

}
