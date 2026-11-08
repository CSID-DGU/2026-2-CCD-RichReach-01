package com.richreach.transaction.dto;

import java.util.Locale;
import java.util.Optional;

/**
 * 거래 요약의 조회 기간. 오늘을 끝으로 N개월 전 같은 날부터 오늘까지(양쪽 포함)를 본다.
 * 예를 들어 오늘이 11월 9일이면 1개월은 10월 9일부터 11월 9일까지다.
 */
public enum SummaryPeriod {

    ONE_MONTH("1M", 1),
    THREE_MONTHS("3M", 3),
    SIX_MONTHS("6M", 6);

    private final String code;
    private final int months;

    SummaryPeriod(String code, int months) {
        this.code = code;
        this.months = months;
    }

    /** API에서 쓰는 값 (1M, 3M, 6M) */
    public String code() {
        return code;
    }

    public int months() {
        return months;
    }

    /** API 값으로 찾는다. 대소문자와 앞뒤 공백은 무시하고, 없거나 지원하지 않는 값이면 비어 있다. */
    public static Optional<SummaryPeriod> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String normalized = code.strip().toUpperCase(Locale.ROOT);
        for (SummaryPeriod period : values()) {
            if (period.code.equals(normalized)) {
                return Optional.of(period);
            }
        }
        return Optional.empty();
    }

}
