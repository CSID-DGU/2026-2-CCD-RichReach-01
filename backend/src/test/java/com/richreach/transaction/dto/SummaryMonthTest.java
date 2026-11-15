package com.richreach.transaction.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 월 조회 값(yyyy-MM)의 해석 규칙을 확인한다. */
class SummaryMonthTest {

    @Test
    @DisplayName("네 자리 연도와 01~12월을 받고, 앞뒤 공백은 무시한다")
    void parsesValidMonths() {
        assertThat(SummaryMonth.parse("2026-11")).isEqualTo(YearMonth.of(2026, 11));
        assertThat(SummaryMonth.parse("2026-01")).isEqualTo(YearMonth.of(2026, 1));
        assertThat(SummaryMonth.parse("2026-12")).isEqualTo(YearMonth.of(2026, 12));
        assertThat(SummaryMonth.parse(" 2026-11 ")).isEqualTo(YearMonth.of(2026, 11));
    }

    @Test
    @DisplayName("형식이 틀리거나 비어 있으면 COMMON-400이다")
    void rejectsInvalidMonths() {
        for (String month : new String[] {null, "", " ", "2026-13", "2026-00", "2026-1", "202611", "2026/11", "26-11",
            "abc", "2026-11-01", "+10000-01", "-0001-01", "10000-01", "٢٠٢٦-١١"}) {
            assertThatThrownBy(() -> SummaryMonth.parse(month)).as("month=%s", month)
                .isInstanceOfSatisfying(BusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        }
    }

}
