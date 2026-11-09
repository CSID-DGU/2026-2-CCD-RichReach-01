package com.richreach.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class SignupServiceTest {

    @Test
    @DisplayName("이메일은 앞뒤 공백을 없애고 소문자로 맞춘다")
    void normalizesEmail() {
        assertThat(SignupService.normalizeEmail("  Rich@Example.COM ")).isEqualTo("rich@example.com");
    }

    @Test
    @DisplayName("전화번호는 하이픈과 공백을 없애고 숫자만 남긴다")
    void normalizesPhone() {
        assertThat(SignupService.normalizePhone("010-1234-5678")).isEqualTo("01012345678");
        assertThat(SignupService.normalizePhone("010 1234 5678")).isEqualTo("01012345678");
        assertThat(SignupService.normalizePhone("01012345678")).isEqualTo("01012345678");
    }

    @Test
    @DisplayName("유일 제약 이름에 phone이 들어 있으면 전화번호 중복, 아니면 이메일 중복으로 본다")
    void mapsDuplicateErrors() {
        Throwable phone = new DataIntegrityViolationException("could not execute statement",
            new RuntimeException("Duplicate entry '01012345678' for key 'users.uk_users_phone'"));
        Throwable email = new DataIntegrityViolationException("could not execute statement",
            new RuntimeException("Duplicate entry 'a@b.com' for key 'users.uk_users_email'"));

        assertThat(SignupService.duplicateErrorOf(phone)).isEqualTo(ErrorCode.DUPLICATE_PHONE);
        assertThat(SignupService.duplicateErrorOf(email)).isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        assertThat(SignupService.duplicateErrorOf(new DataIntegrityViolationException("unknown")))
            .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

}
