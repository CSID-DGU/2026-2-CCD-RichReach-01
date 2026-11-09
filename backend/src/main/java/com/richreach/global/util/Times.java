package com.richreach.global.util;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** 시각을 다루는 공통 도구. 현재 시각은 항상 주입받은 {@link Clock}으로 구해서 테스트에서 시간을 고정할 수 있게 한다. */
public final class Times {

    private Times() {
    }

    /**
     * 현재 시각을 초 단위로 자른다. DB의 DATETIME은 초 단위라서, 저장한 값과 응답하거나 비교하는 값이
     * 달라지지 않도록 처음부터 초 단위로 맞춘다.
     */
    public static LocalDateTime nowSeconds(Clock clock) {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

}
