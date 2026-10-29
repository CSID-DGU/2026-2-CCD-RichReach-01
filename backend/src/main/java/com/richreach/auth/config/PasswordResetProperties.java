package com.richreach.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 비밀번호 재설정 설정 ({@code app.auth.password-reset.*}).
 *
 * @param codeTtl        인증번호 유효 시간
 * @param resendInterval 인증번호를 다시 보내려면 기다려야 하는 시간
 * @param maxAttempts    인증번호를 이만큼 틀리면 그 인증번호를 폐기한다.
 * @param resetTokenTtl  인증번호 확인 뒤 받는 재설정 토큰의 유효 시간
 * @param exposeCode     true면 인증번호를 응답에도 담는다. 실제 이메일/SMS 발송이 없는 개발 단계에서만 켠다.
 */
@ConfigurationProperties("app.auth.password-reset")
public record PasswordResetProperties(
    @DefaultValue("3m") Duration codeTtl,
    @DefaultValue("60s") Duration resendInterval,
    @DefaultValue("5") int maxAttempts,
    @DefaultValue("10m") Duration resetTokenTtl,
    @DefaultValue("false") boolean exposeCode) {
}
