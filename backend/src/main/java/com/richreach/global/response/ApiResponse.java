package com.richreach.global.response;

import com.richreach.global.exception.ErrorCode;
import java.util.List;

/**
 * 모든 API의 공통 응답 포맷.
 *
 * @param success 요청 성공 여부
 * @param code    성공이면 {@code SUCCESS}, 실패면 {@link ErrorCode}의 코드
 * @param message 사용자에게 보여줄 수 있는 메시지
 * @param data    응답 데이터 (없으면 null)
 */
public record ApiResponse<T>(boolean success, String code, String message, T data) {

    private static final String SUCCESS_CODE = "SUCCESS";
    private static final String SUCCESS_MESSAGE = "요청이 성공했습니다.";

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, SUCCESS_CODE, SUCCESS_MESSAGE, null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.getCode(), message, null);
    }

    /** 입력값 검증 실패: 필드별 오류를 data에 담는다. */
    public static ApiResponse<List<FieldErrorResponse>> validationError(List<FieldErrorResponse> errors) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), errors);
    }

}
