package com.richreach.card.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CardNumberMaskerTest {

    @Test
    @DisplayName("전체 번호가 오면 마지막 4자리만 남기고 가리며, 구분자는 유지한다")
    void masksFullNumber() {
        assertThat(CardNumberMasker.mask("1234-5678-9012-3456")).isEqualTo("****-****-****-3456");
        assertThat(CardNumberMasker.mask("1234 5678 9012 3456")).isEqualTo("**** **** **** 3456");
    }

    @Test
    @DisplayName("구분자 없는 숫자도 마지막 4자리만 남긴다")
    void masksPlainDigits() {
        assertThat(CardNumberMasker.mask("1234567890123456")).isEqualTo("************3456");
    }

    @Test
    @DisplayName("이미 가려진 값은 그대로 둔다")
    void keepsAlreadyMasked() {
        assertThat(CardNumberMasker.mask("1234-****-****-5678")).isEqualTo("1234-****-****-5678");
        assertThat(CardNumberMasker.mask("****-5678")).isEqualTo("****-5678");
    }

    @Test
    @DisplayName("null이나 빈 값은 빈 문자열이다")
    void blankBecomesEmpty() {
        assertThat(CardNumberMasker.mask(null)).isEmpty();
        assertThat(CardNumberMasker.mask("")).isEmpty();
        assertThat(CardNumberMasker.mask("   ")).isEmpty();
    }

    @Test
    @DisplayName("숫자가 4자리 이하이면 그대로 둔다")
    void shortNumberIsKept() {
        assertThat(CardNumberMasker.mask("5678")).isEqualTo("5678");
        assertThat(CardNumberMasker.mask(" 56 78 ")).isEqualTo("56 78");
    }

    @Test
    @DisplayName("앞뒤 공백은 떼고, 컬럼 길이(30자)를 넘으면 끝에서부터 30자만 남긴다")
    void trimsAndCapsLength() {
        assertThat(CardNumberMasker.mask("  1234567890123456  ")).isEqualTo("************3456");
        assertThat(CardNumberMasker.mask("*".repeat(50) + "9999")).hasSize(30).endsWith("9999");
    }

    @Test
    @DisplayName("전체 번호의 앞자리 숫자는 결과에 남지 않는다")
    void leadingDigitsAreNotKept() {
        assertThat(CardNumberMasker.mask("4111111111111234")).doesNotContain("4111").endsWith("1234");
    }

}
