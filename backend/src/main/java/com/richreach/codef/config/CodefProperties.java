package com.richreach.codef.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * CODEF 연동 설정. 시크릿은 코드에 쓰지 않고 환경변수(.env)로 주입한다.
 *
 * @param clientId           CODEF 클라이언트 ID (데모/정식 서비스 승인 후 발급)
 * @param clientSecret       CODEF 클라이언트 시크릿
 * @param publicKey          RSA 공개키 (카드사 비밀번호 등 민감정보 암호화용, codef.io 계정 관리에서 확인)
 * @param oauthBaseUrl       토큰 발급 서버 주소
 * @param apiBaseUrl         상품 API 서버 주소 (데모: development.codef.io, 정식: api.codef.io)
 * @param connectTimeout     연결 타임아웃
 * @param readTimeout        토큰 발급 응답 대기 타임아웃
 * @param apiReadTimeout     상품 API 응답 대기 타임아웃 (카드사 조회는 오래 걸릴 수 있다)
 * @param tokenRefreshMargin 토큰 만료 이 시간 전부터는 새로 발급한다.
 */
@ConfigurationProperties(prefix = "codef")
public record CodefProperties(
    String clientId,
    String clientSecret,
    String publicKey,
    @DefaultValue("https://oauth.codef.io") String oauthBaseUrl,
    @DefaultValue("https://development.codef.io") String apiBaseUrl,
    @DefaultValue("3s") Duration connectTimeout,
    @DefaultValue("10s") Duration readTimeout,
    @DefaultValue("120s") Duration apiReadTimeout,
    @DefaultValue("1h") Duration tokenRefreshMargin
) {

    public boolean hasCredentials() {
        return clientId != null && !clientId.isBlank()
            && clientSecret != null && !clientSecret.isBlank();
    }

    public boolean hasPublicKey() {
        return publicKey != null && !publicKey.isBlank();
    }

    /** 로그 등에 실수로 출력되어도 시크릿이 노출되지 않도록 마스킹한다. */
    @Override
    public String toString() {
        return "CodefProperties[clientId=****, clientSecret=****, publicKey=****, oauthBaseUrl=" + oauthBaseUrl
            + ", apiBaseUrl=" + apiBaseUrl + "]";
    }

}
