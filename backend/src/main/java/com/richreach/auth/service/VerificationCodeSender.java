package com.richreach.auth.service;

/**
 * 인증번호를 사용자에게 보낸다. 지금은 실제 이메일/SMS 발송이 없어서
 * {@link LoggingVerificationCodeSender}만 있고, 실제 발송을 붙일 때 이 인터페이스의 구현을 바꾼다.
 */
public interface VerificationCodeSender {

    /**
     * @param loginId 받는 사람의 아이디(이메일)
     * @param code    6자리 인증번호
     */
    void send(String loginId, String code);

}
