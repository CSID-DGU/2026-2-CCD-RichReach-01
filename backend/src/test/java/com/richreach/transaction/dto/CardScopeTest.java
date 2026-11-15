package com.richreach.transaction.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 요청 값의 카드 범위 해석 규칙을 확인한다. 거래 요약과 목록이 같은 규칙을 쓴다. */
class CardScopeTest {

    @Test
    @DisplayName("값이 없으면 기본 범위(SELECTED)이다")
    void defaultsWhenAbsent() {
        assertThat(CardScope.parse(null)).isEqualTo(CardScope.SELECTED);
        assertThat(CardScope.parse(null)).isEqualTo(CardScope.DEFAULT);
    }

    @Test
    @DisplayName("대소문자와 앞뒤 공백은 무시한다")
    void ignoresCaseAndWhitespace() {
        assertThat(CardScope.parse("ALL")).isEqualTo(CardScope.ALL);
        assertThat(CardScope.parse("all")).isEqualTo(CardScope.ALL);
        assertThat(CardScope.parse(" Selected ")).isEqualTo(CardScope.SELECTED);
    }

    @Test
    @DisplayName("빈 값이나 알 수 없는 값은 COMMON-400이다")
    void rejectsUnknownValues() {
        for (String code : new String[] {"", " ", "MINE", "1", "SELECTED,ALL"}) {
            assertThatThrownBy(() -> CardScope.parse(code)).as("cardScope=%s", code)
                .isInstanceOfSatisfying(BusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        }
    }

}
