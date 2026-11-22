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
    void keepsAmountsAndOrdinaryText() {
        assertThat(OpenAiPrivacyFilter.mask("카페 예산을 30000원으로 바꿔줘")).isEqualTo("카페 예산을 30000원으로 바꿔줘");
        assertThat(OpenAiPrivacyFilter.mask("총 1,200,000원")).isEqualTo("총 1,200,000원");
        assertThat(OpenAiPrivacyFilter.mask(null)).isNull();
    }
}
