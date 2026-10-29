package com.richreach.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 개발용 발송기. 이메일/SMS를 보내지 않고 서버 로그에만 남긴다.
 * 실제 사용자에게 인증번호가 전달되지 않으므로 서비스로 공개하기 전에 실제 발송 구현으로 바꿔야 한다.
 */
@Slf4j
@Component
public class LoggingVerificationCodeSender implements VerificationCodeSender {

    @Override
    public void send(String loginId, String code) {
        log.warn("[개발용 발송] 비밀번호 재설정 인증번호 {} -> {}", EmailMasker.mask(loginId), code);
    }

}
