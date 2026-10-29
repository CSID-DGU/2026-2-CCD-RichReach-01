package com.richreach.auth.service;

import com.richreach.auth.config.PasswordResetProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 개발용 발송기. 이메일/SMS를 보내지 않는다.
 *
 * <ul>
 *   <li>{@code app.auth.password-reset.expose-code}가 켜진 환경(local)에서만 인증번호를 서버 로그에 남긴다.</li>
 *   <li>꺼져 있으면 인증번호는 어디에도 기록하지 않고, 실제 발송 구현이 없어 전달되지 않았다는 안내만 남긴다.
 *       (로그를 읽을 수 있는 사람이 남의 인증번호를 얻어 비밀번호를 바꿀 수 없게 하기 위해서다.)</li>
 * </ul>
 *
 * 서비스로 공개하기 전에 실제 발송 구현으로 바꿔야 한다.
 */
@Slf4j
@Component
public class LoggingVerificationCodeSender implements VerificationCodeSender {

    private final PasswordResetProperties properties;

    public LoggingVerificationCodeSender(PasswordResetProperties properties) {
        this.properties = properties;
    }

    @Override
    public void send(String loginId, String code) {
        String recipient = EmailMasker.mask(loginId);
        if (properties.exposeCode()) {
            log.warn("[개발용 발송] 비밀번호 재설정 인증번호 {} -> {}", recipient, code);
        } else {
            log.warn("[개발용 발송] 실제 발송 구현이 없어 인증번호를 전달하지 않았습니다. (받는 사람: {})", recipient);
        }
    }

}
