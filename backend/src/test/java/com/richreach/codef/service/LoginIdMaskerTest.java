package com.richreach.codef.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 화면에 보여줄 카드사 아이디를 가리는 규칙을 확인한다. */
class LoginIdMaskerTest {

    @Test
    @DisplayName("앞 2자만 남기고 나머지는 길이와 상관없이 ***로 가린다")
    void keepsTheFirstTwoCharacters() {
        assertThat(LoginIdMasker.mask("richreach123")).isEqualTo("ri***");
        assertThat(LoginIdMasker.mask("abc")).isEqualTo("ab***");
        // 길이가 달라도 같은 모양이라 길이를 알 수 없다.
        assertThat(LoginIdMasker.mask("abcdefghijklmnopqrstuvwxyz")).isEqualTo("ab***");
    }

    @Test
    @DisplayName("2자 이하는 앞 1자만 남긴다")
    void shortIdsKeepOneCharacter() {
        assertThat(LoginIdMasker.mask("ab")).isEqualTo("a***");
        assertThat(LoginIdMasker.mask("a")).isEqualTo("a***");
    }

    @Test
    @DisplayName("앞뒤 공백은 빼고, 비어 있으면 null이다")
    void trimsAndHandlesBlank() {
        assertThat(LoginIdMasker.mask("  hello  ")).isEqualTo("he***");
        assertThat(LoginIdMasker.mask(null)).isNull();
        assertThat(LoginIdMasker.mask("")).isNull();
        assertThat(LoginIdMasker.mask("   ")).isNull();
    }

    @Test
    @DisplayName("두 칸을 쓰는 글자(이모지)를 중간에서 자르지 않는다")
    void doesNotSplitSurrogatePairs() {
        assertThat(LoginIdMasker.mask("😀😀abc")).isEqualTo("😀😀***");
    }

    @Test
    @DisplayName("짧은 아이디도 글자 수로 판정해서, 이모지가 섞여도 원문 전체가 남지 않는다")
    void shortIdsAreJudgedByCharacterCount() {
        // 두 글자(a + 이모지)는 UTF-16으로는 3칸이지만 2자 이하이므로 앞 1자만 남긴다.
        assertThat(LoginIdMasker.mask("a😀")).isEqualTo("a***");
        assertThat(LoginIdMasker.mask("😀a")).isEqualTo("😀***");
        assertThat(LoginIdMasker.mask("😀")).isEqualTo("😀***");
        // 세 글자부터는 앞 2자를 남긴다.
        assertThat(LoginIdMasker.mask("a😀b")).isEqualTo("a😀***");
        // 기존 결과는 그대로다.
        assertThat(LoginIdMasker.mask("ab")).isEqualTo("a***");
        assertThat(LoginIdMasker.mask("abc")).isEqualTo("ab***");
    }

    @Test
    @DisplayName("가린 값에 원문의 나머지가 들어 있지 않다")
    void doesNotLeakTheRest() {
        assertThat(LoginIdMasker.mask("secret-login-id")).doesNotContain("cret").doesNotContain("login");
    }

}
