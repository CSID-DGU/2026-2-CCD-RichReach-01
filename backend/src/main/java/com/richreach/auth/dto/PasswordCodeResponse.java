package com.richreach.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 인증번호 발송 결과. 가입된 이메일인지와 상관없이 같은 모양으로 답한다.
 * (완전히 숨기지는 못한다. 가입된 이메일은 재전송 대기 시간 안에 다시 요청하면 429이고 응답도 조금 늦다.
 * 이메일 찾기와 아이디 중복 확인이 이미 가입 여부를 알려주는 API라서 거기까지는 막지 않았다.)
 *
 * @param expiresIn   인증번호 유효 시간(초)
 * @param resendIn    다시 보내려면 기다려야 하는 시간(초)
 * @param devCode     인증번호. 실제 발송이 없는 개발 단계에서만 담기고(app.auth.password-reset.expose-code),
 *                    꺼져 있거나 가입되지 않은 이메일이면 필드 자체가 없다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PasswordCodeResponse(int expiresIn, int resendIn, String devCode) {
}
