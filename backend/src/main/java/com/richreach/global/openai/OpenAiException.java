package com.richreach.global.openai;

/**
 * OpenAI 호출 실패를 기능과 무관하게 표현한다. 호출하는 기능이 자기 ErrorCode로 바꿔 던진다.
 * 메시지에는 프롬프트·응답 원문을 담지 않는다.
 */
public class OpenAiException extends RuntimeException {

    public enum Kind {
        /** 키 누락, HTTP 오류, 형식 오류 등 응답을 쓸 수 없는 모든 경우 */
        UNAVAILABLE,
        /** 연결·읽기 시간 초과 */
        TIMEOUT
    }

    private final Kind kind;

    public OpenAiException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
