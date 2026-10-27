package com.richreach.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * 에러 코드. 공통 코드는 {@code COMMON-<HTTP 상태>}, 도메인 코드는 {@code <도메인>-<일련번호>} 형식이다.
 * 클라이언트는 {@code code}로 분기하고, {@code message}는 사용자에게 그대로 보여줘도 되는 문구만 쓴다.
 * 로그인처럼 계정 존재 여부가 드러나면 안 되는 곳은 원인을 구분하지 않는 문구를 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON-400", "잘못된 요청입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON-404", "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON-405", "지원하지 않는 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COMMON-415", "지원하지 않는 미디어 타입입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-500", "서버 내부 오류가 발생했습니다."),

    // 인증
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH-001", "로그인이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH-002", "이메일 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "AUTH-003", "로그인 시도가 너무 많아 잠시 후 다시 시도해 주세요."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH-004", "로그인이 만료되었습니다. 다시 로그인해 주세요."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-005", "유효하지 않은 인증 정보입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH-006", "접근 권한이 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "AUTH-007", "이미 가입된 이메일입니다."),
    TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "AUTH-008", "필수 약관에 동의해 주세요."),
    VERIFICATION_CODE_INVALID(HttpStatus.BAD_REQUEST, "AUTH-009", "인증번호가 올바르지 않습니다."),
    VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "AUTH-010", "인증번호가 만료되었습니다. 다시 요청해 주세요."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "AUTH-011", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),

    // 카드사 연결
    CONNECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "CONN-001", "카드사 연결 정보를 찾을 수 없습니다."),
    CARD_COMPANY_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "CONN-002", "지원하지 않는 카드사입니다."),
    CARD_COMPANY_AUTH_FAILED(HttpStatus.BAD_REQUEST, "CONN-003", "카드사 인증에 실패했습니다. 입력한 정보를 확인해 주세요."),
    CARD_COMPANY_AUTH_EXPIRED(HttpStatus.BAD_REQUEST, "CONN-004", "카드사 인증 시간이 만료되었습니다. 다시 시도해 주세요."),
    CODEF_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "CONN-005", "카드사 연동 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    SYNC_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "CONN-006", "동기화 작업을 찾을 수 없습니다."),
    SYNC_ALREADY_RUNNING(HttpStatus.CONFLICT, "CONN-007", "이미 동기화가 진행 중입니다."),

    // 카드
    CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "CARD-001", "카드를 찾을 수 없습니다."),

    // 거래
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "TRANS-001", "거래를 찾을 수 없습니다."),
    INVALID_PERIOD(HttpStatus.BAD_REQUEST, "TRANS-002", "지원하지 않는 조회 기간입니다."),

    // 플랜
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAN-001", "플랜을 찾을 수 없습니다."),
    PLAN_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAN-002", "플랜 생성 작업을 찾을 수 없습니다."),
    INVALID_BUDGET(HttpStatus.BAD_REQUEST, "PLAN-003", "예산 금액이 올바르지 않습니다."),
    PLAN_ALREADY_EXISTS(HttpStatus.CONFLICT, "PLAN-004", "해당 월의 플랜이 이미 있습니다."),

    // 추천
    INSUFFICIENT_RECOMMENDATION_INPUT(HttpStatus.BAD_REQUEST, "RECOMMEND-001", "추천에 필요한 정보가 부족합니다."),
    RECOMMENDATION_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "RECOMMEND-002", "추천 계산 시간이 초과되었습니다. 다시 시도해 주세요."),
    RECOMMENDATION_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "RECOMMEND-003", "추천 서비스를 일시적으로 사용할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    /** Spring MVC가 던지는 표준 예외의 HTTP 상태를 공통 에러 코드로 대응시킨다. */
    public static ErrorCode fromStatus(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 404 -> RESOURCE_NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> statusCode.is5xxServerError() ? INTERNAL_SERVER_ERROR : INVALID_INPUT_VALUE;
        };
    }

}
