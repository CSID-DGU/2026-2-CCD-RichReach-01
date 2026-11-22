package com.richreach.global.openai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenAiPrivacyFilterTest {

    @Test
    void masksCardNumberPhoneAndEmail() {
        assertThat(OpenAiPrivacyFilter.mask("카드 1234-5678-9012-3456 이야")).isEqualTo("카드 [카드번호] 이야");
        assertThat(OpenAiPrivacyFilter.mask("1234567890123456")).isEqualTo("[카드번호]");
        assertThat(OpenAiPrivacyFilter.mask("연락처 010-1234-5678")).isEqualTo("연락처 [전화번호]");
        assertThat(OpenAiPrivacyFilter.mask("메일 user@example.com 로")).isEqualTo("메일 [이메일] 로");
    }

    @Test
    void masksAmericanExpressAndDinersCardNumbersInGroupedFormat() {
        // 4-6-5 (American Express 15자리)
        assertThat(OpenAiPrivacyFilter.mask("카드 3782 822463 10005 야")).isEqualTo("카드 [카드번호] 야");
        assertThat(OpenAiPrivacyFilter.mask("3782-822463-10005")).isEqualTo("[카드번호]");
        assertThat(OpenAiPrivacyFilter.mask("378282246310005")).isEqualTo("[카드번호]");
        // 4-6-4 (Diners 14자리)
        assertThat(OpenAiPrivacyFilter.mask("3056 930902 5904")).isEqualTo("[카드번호]");
        assertThat(OpenAiPrivacyFilter.mask("3056-930902-5904")).isEqualTo("[카드번호]");
    }

    @Test
    void masksLandlineAndInternetPhoneNumbers() {
        assertThat(OpenAiPrivacyFilter.mask("연락처 02-123-4567")).isEqualTo("연락처 [전화번호]");
        assertThat(OpenAiPrivacyFilter.mask("02-1234-5678")).isEqualTo("[전화번호]");
        assertThat(OpenAiPrivacyFilter.mask("031-123-4567로")).isEqualTo("[전화번호]로");
        assertThat(OpenAiPrivacyFilter.mask("051 1234 5678")).isEqualTo("[전화번호]");
        assertThat(OpenAiPrivacyFilter.mask("070-8765-4321")).isEqualTo("[전화번호]");
        assertThat(OpenAiPrivacyFilter.mask("0212345678")).isEqualTo("[전화번호]");
    }

    @Test
    void keepsAmountsAndOrdinaryText() {
        assertThat(OpenAiPrivacyFilter.mask("카페 예산을 30000원으로 바꿔줘")).isEqualTo("카페 예산을 30000원으로 바꿔줘");
        assertThat(OpenAiPrivacyFilter.mask("총 1,200,000원")).isEqualTo("총 1,200,000원");
        assertThat(OpenAiPrivacyFilter.mask(null)).isNull();
    }

    @Test
    void keepsDatesAmountsAndRangesThatLookLikeNumbers() {
        // 날짜, 월, 금액 범위, 퍼센트, 카드 이름 속 숫자는 가리지 않는다.
        assertThat(OpenAiPrivacyFilter.mask("2026-11-22에 12월 예산 100000원")).isEqualTo("2026-11-22에 12월 예산 100000원");
        assertThat(OpenAiPrivacyFilter.mask("10-20만원 사이, 5% 할인")).isEqualTo("10-20만원 사이, 5% 할인");
        assertThat(OpenAiPrivacyFilter.mask("식비 200000 카페 30000 교통 50000"))
            .isEqualTo("식비 200000 카페 30000 교통 50000");
        assertThat(OpenAiPrivacyFilter.mask("현대카드 M3 2024-12 결제")).isEqualTo("현대카드 M3 2024-12 결제");
        // 0으로 시작하지 않는 일곱 자리·여덟 자리 숫자(금액 등)도 그대로 둔다.
        assertThat(OpenAiPrivacyFilter.mask("1234567원, 12345678원")).isEqualTo("1234567원, 12345678원");
    }
}
