package com.richreach.global.response;

import org.springframework.validation.FieldError;

/** 입력값 검증 실패 시 필드별 오류 정보 */
public record FieldErrorResponse(String field, String message) {

    public static FieldErrorResponse from(FieldError fieldError) {
        return new FieldErrorResponse(fieldError.getField(), fieldError.getDefaultMessage());
    }

}
