package com.richreach.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    @DisplayName("성공 응답은 success=true, code=SUCCESS이고 data를 담는다")
    void ok_withData() {
        ApiResponse<String> response = ApiResponse.ok("hello");

        assertThat(response.success()).isTrue();
        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(response.data()).isEqualTo("hello");
    }

    @Test
    @DisplayName("데이터 없는 성공 응답은 data가 null이다")
    void ok_withoutData() {
        ApiResponse<Void> response = ApiResponse.ok();

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isNull();
    }

    @Test
    @DisplayName("실패 응답은 ErrorCode의 코드와 메시지를 담는다")
    void error_withErrorCode() {
        ApiResponse<Void> response = ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND);

        assertThat(response.success()).isFalse();
        assertThat(response.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode());
        assertThat(response.message()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getMessage());
        assertThat(response.data()).isNull();
    }

    @Test
    @DisplayName("실패 응답은 메시지를 직접 지정할 수 있다")
    void error_withCustomMessage() {
        ApiResponse<Void> response = ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE, "이름은 필수입니다.");

        assertThat(response.code()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getCode());
        assertThat(response.message()).isEqualTo("이름은 필수입니다.");
    }

    @Test
    @DisplayName("검증 실패 응답은 필드별 오류를 data에 담는다")
    void validationError() {
        List<FieldErrorResponse> errors = List.of(new FieldErrorResponse("name", "must not be blank"));

        ApiResponse<List<FieldErrorResponse>> response = ApiResponse.validationError(errors);

        assertThat(response.success()).isFalse();
        assertThat(response.code()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getCode());
        assertThat(response.data()).containsExactly(new FieldErrorResponse("name", "must not be blank"));
    }

}
