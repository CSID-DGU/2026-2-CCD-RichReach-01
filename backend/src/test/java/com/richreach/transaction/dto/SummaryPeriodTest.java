package com.richreach.transaction.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SummaryPeriodTest {

    @Test
    @DisplayName("1M, 3M, 6M만 지원하고 개월 수가 맞다")
    void supportsOneThreeSixMonths() {
        assertThat(SummaryPeriod.values()).extracting(SummaryPeriod::code).containsExactly("1M", "3M", "6M");
        assertThat(SummaryPeriod.ONE_MONTH.months()).isEqualTo(1);
        assertThat(SummaryPeriod.THREE_MONTHS.months()).isEqualTo(3);
        assertThat(SummaryPeriod.SIX_MONTHS.months()).isEqualTo(6);
    }

    @Test
    @DisplayName("API 값으로 찾는다 (대소문자와 앞뒤 공백은 무시)")
    void findsByCode() {
        assertThat(SummaryPeriod.fromCode("1M")).contains(SummaryPeriod.ONE_MONTH);
        assertThat(SummaryPeriod.fromCode("3m")).contains(SummaryPeriod.THREE_MONTHS);
        assertThat(SummaryPeriod.fromCode(" 6M ")).contains(SummaryPeriod.SIX_MONTHS);
    }

    @Test
    @DisplayName("없거나 지원하지 않는 값은 비어 있다")
    void unsupportedIsEmpty() {
        assertThat(SummaryPeriod.fromCode(null)).isEmpty();
        assertThat(SummaryPeriod.fromCode("")).isEmpty();
        assertThat(SummaryPeriod.fromCode("2M")).isEmpty();
        assertThat(SummaryPeriod.fromCode("12M")).isEmpty();
        assertThat(SummaryPeriod.fromCode("1Y")).isEmpty();
        assertThat(SummaryPeriod.fromCode("ONE_MONTH")).isEmpty();
    }

}
