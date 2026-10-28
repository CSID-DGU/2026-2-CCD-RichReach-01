package com.richreach.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 로그인 실패 잠금 설정 ({@code app.auth.login-lock.*}).
 *
 * @param maxFailures  이 횟수만큼 연속으로 실패하면 잠근다.
 * @param lockDuration 잠겨 있는 시간
 */
@ConfigurationProperties("app.auth.login-lock")
public record LoginLockProperties(
    @DefaultValue("5") int maxFailures,
    @DefaultValue("10m") Duration lockDuration) {
}
