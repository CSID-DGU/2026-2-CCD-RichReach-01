package com.richreach.global.exception;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반 등 예상 가능한 오류. service에서 던지면
 * {@link GlobalExceptionHandler}가 {@link ErrorCode}에 맞는 응답으로 변환한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

}
