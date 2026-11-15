package com.richreach.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.transaction.dto.SummaryPeriod;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 요약 집계 기간 계산을 DB 없이 확인한다. 기간 조회와 월 조회가 같은 규칙으로 날짜를 정하는지가 핵심이다. */
class SummaryRangeTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);

    @Test
    @DisplayName("기간 조회는 오늘을 끝으로 N개월 전 같은 날부터 오늘까지이고 month는 없다")
    void periodRange() {
        SummaryRange range = SummaryRange.ofPeriod(SummaryPeriod.THREE_MONTHS, TODAY);

        assertThat(range.period()).isEqualTo("3M");
        assertThat(range.month()).isNull();
        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(range.to()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("이번 달은 1일부터 오늘까지이고 period는 없다")
    void currentMonthEndsToday() {
        SummaryRange range = SummaryRange.ofMonth(YearMonth.of(2026, 11), TODAY);

        assertThat(range.period()).isNull();
        assertThat(range.month()).isEqualTo("2026-11");
        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(range.to()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("지난 달은 1일부터 말일까지다")
    void pastMonthEndsAtMonthEnd() {
        SummaryRange range = SummaryRange.ofMonth(YearMonth.of(2026, 10), TODAY);

        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(range.to()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    @DisplayName("2월 말일은 평년 28일, 윤년 29일이다")
    void februaryEnd() {
        assertThat(SummaryRange.ofMonth(YearMonth.of(2026, 2), TODAY).to()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(SummaryRange.ofMonth(YearMonth.of(2024, 2), TODAY).to()).isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    @DisplayName("이번 달 1일과 말일에도 기간이 맞다")
    void currentMonthOnFirstAndLastDay() {
        SummaryRange first = SummaryRange.ofMonth(YearMonth.of(2026, 11), LocalDate.of(2026, 11, 1));
        assertThat(first.from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(first.to()).isEqualTo(LocalDate.of(2026, 11, 1));

        SummaryRange last = SummaryRange.ofMonth(YearMonth.of(2026, 11), LocalDate.of(2026, 11, 30));
        assertThat(last.from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(last.to()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    @DisplayName("연도가 바뀌어도 지난 달 기간이 맞다")
    void acrossYearBoundary() {
        SummaryRange range = SummaryRange.ofMonth(YearMonth.of(2025, 12), LocalDate.of(2026, 1, 3));

        assertThat(range.from()).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(range.to()).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    @DisplayName("아직 오지 않은 월은 COMMON-400이다")
    void futureMonthIsRejected() {
        for (YearMonth month : new YearMonth[] {YearMonth.of(2026, 12), YearMonth.of(2027, 1), YearMonth.of(2099, 1)}) {
            assertThatThrownBy(() -> SummaryRange.ofMonth(month, TODAY)).as("month=%s", month)
                .isInstanceOfSatisfying(BusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        }
    }

}
