package com.richreach.transaction.service;

import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.transaction.dto.SummaryPeriod;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 거래 요약이 집계하는 기간. 승인일 기준으로 {@code from}부터 {@code to}까지(양쪽 포함)를 본다. DB 없이 날짜만으로 정해지므로
 * 따로 확인할 수 있다.
 *
 * <ul>
 *   <li>기간 조회({@code period}): 오늘을 끝으로 N개월 전 같은 날부터 오늘까지</li>
 *   <li>월 조회({@code month}): 그달 1일부터 말일까지. 이번 달은 오늘까지다. 아직 오지 않은 월은 조회할 수 없다.</li>
 * </ul>
 *
 * @param period 기간 조회의 기간 코드(1M, 3M, 6M). 월 조회면 null
 * @param month  월 조회의 월({@code yyyy-MM}). 기간 조회면 null
 * @param from   기간 시작일 (포함)
 * @param to     기간 종료일 (포함)
 */
record SummaryRange(String period, String month, LocalDate from, LocalDate to) {

    static SummaryRange ofPeriod(SummaryPeriod period, LocalDate today) {
        return new SummaryRange(period.code(), null, today.minusMonths(period.months()), today);
    }

    /** 아직 오지 않은 월이면 400 {@code COMMON-400}이다. */
    static SummaryRange ofMonth(YearMonth month, LocalDate today) {
        if (month.isAfter(YearMonth.from(today))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "아직 오지 않은 월은 조회할 수 없습니다.");
        }
        LocalDate end = month.atEndOfMonth();
        return new SummaryRange(null, month.toString(), month.atDay(1), end.isAfter(today) ? today : end);
    }

}
