package com.richreach.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ErrorCodeTest {

    private static final Pattern CODE_FORMAT = Pattern.compile("[A-Z]+-\\d{3}");

    @Test
    @DisplayName("에러 코드 값은 중복되지 않는다")
    void codesAreUnique() {
        long distinct = Arrays.stream(ErrorCode.values()).map(ErrorCode::getCode).distinct().count();

        assertThat(distinct).isEqualTo(ErrorCode.values().length);
    }

    @Test
    @DisplayName("모든 에러 코드는 '도메인-세 자리 숫자' 형식이고 메시지가 있다")
    void codesFollowFormat() {
        assertThat(ErrorCode.values()).allSatisfy(errorCode -> {
            assertThat(errorCode.getCode()).matches(CODE_FORMAT);
            assertThat(errorCode.getMessage()).isNotBlank();
            assertThat(errorCode.getStatus().isError()).isTrue();
        });
    }

    @Test
    @DisplayName("공통 코드는 HTTP 상태 숫자와 일치한다")
    void commonCodesMatchHttpStatus() {
        assertThat(Arrays.stream(ErrorCode.values()).filter(c -> c.getCode().startsWith("COMMON-")))
            .allSatisfy(c -> assertThat(c.getCode()).isEqualTo("COMMON-" + c.getStatus().value()));
    }

    @Test
    @DisplayName("로그인 실패 메시지는 이메일/비밀번호 중 무엇이 틀렸는지 알려주지 않는다")
    void invalidCredentialsDoesNotRevealCause() {
        assertThat(ErrorCode.INVALID_CREDENTIALS.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.INVALID_CREDENTIALS.getMessage()).contains("이메일 또는 비밀번호");
    }

}
