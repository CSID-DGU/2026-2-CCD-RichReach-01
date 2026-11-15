package com.richreach.transaction.dto;

import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.time.YearMonth;
import java.util.regex.Pattern;

/**
 * 거래 요약의 월 조회 값({@code month=yyyy-MM}) 해석. 네 자리 연도와 01~12월만 받는다.
 * ({@link YearMonth#parse}는 {@code +10000-01}처럼 연도가 네 자리가 아닌 값도 받아서, 먼저 형식을 확인한다.)
 */
public final class SummaryMonth {

    private static final Pattern FORMAT = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    private SummaryMonth() {
    }

    /** 앞뒤 공백은 무시한다. 형식이 틀리거나 비어 있으면 400 {@code COMMON-400}이다. */
    public static YearMonth parse(String month) {
        String normalized = month == null ? "" : month.strip();
        if (!FORMAT.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "month는 yyyy-MM 형식이어야 합니다.");
        }
        return YearMonth.parse(normalized);
    }

}
