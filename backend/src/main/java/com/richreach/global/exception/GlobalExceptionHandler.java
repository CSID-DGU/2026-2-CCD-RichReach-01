package com.richreach.global.exception;

import com.richreach.global.response.ApiResponse;
import com.richreach.global.response.FieldErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 예외를 {@link ApiResponse} 형식으로 변환한다.
 * Spring MVC 표준 예외(404, 405, 잘못된 JSON 등)는 {@link ResponseEntityExceptionHandler}를 통해
 * 같은 형식으로 응답하고, 예상하지 못한 예외는 내부 정보를 숨긴 채 500으로 응답한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("BusinessException: {} - {}", errorCode.getCode(), e.getMessage());
        return ResponseEntity.status(errorCode.getStatus())
            .body(ApiResponse.error(errorCode, e.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("ConstraintViolationException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE));
    }

    /** 예상하지 못한 예외. 스택 트레이스 등 내부 정보는 로그로만 남기고 응답에는 담지 않는다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode));
    }

    /** {@code @Valid} 검증 실패: 필드별 오류를 함께 내려준다. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorResponse> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldErrorResponse::from)
            .toList();
        log.warn("Validation failed: {}", errors);
        return ResponseEntity.status(status).body(ApiResponse.validationError(errors));
    }

    /** Spring MVC 표준 예외의 응답 본문을 ProblemDetail 대신 {@link ApiResponse}로 통일한다. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
        Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ErrorCode errorCode = ErrorCode.fromStatus(statusCode);
        if (statusCode.is5xxServerError()) {
            log.error("Server error: {}", errorCode.getCode(), ex);
        } else {
            log.warn("Request error: {} - {}", errorCode.getCode(), ex.getMessage());
        }
        return ResponseEntity.status(statusCode).headers(headers).body(ApiResponse.error(errorCode));
    }

}
