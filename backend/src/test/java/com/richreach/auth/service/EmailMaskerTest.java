package com.richreach.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailMaskerTest {

    @Test
    @DisplayName("아이디 앞 두 글자만 보여주고 나머지는 가리며 도메인은 그대로 둔다")
    void masksLocalPart() {
        assertThat(EmailMasker.mask("rich@example.com")).isEqualTo("ri***@example.com");
        assertThat(EmailMasker.mask("abc@example.com")).isEqualTo("ab***@example.com");
    }

    @Test
    @DisplayName("아이디가 한두 글자뿐이면 첫 글자만 보여준다")
    void masksShortLocalPart() {
        assertThat(EmailMasker.mask("ab@example.com")).isEqualTo("a***@example.com");
        assertThat(EmailMasker.mask("a@example.com")).isEqualTo("a***@example.com");
    }

    @Test
    @DisplayName("이메일 모양이 아니면 전체를 가린다")
    void masksNonEmail() {
        assertThat(EmailMasker.mask("not-an-email")).isEqualTo("***");
        assertThat(EmailMasker.mask("@example.com")).isEqualTo("***");
    }

    @Test
    @DisplayName("가려진 결과에는 원래 아이디가 그대로 남지 않는다")
    void doesNotLeakFullLocalPart() {
        assertThat(EmailMasker.mask("longusername@example.com")).doesNotContain("longusername");
    }

}
