package com.richreach.codef.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 승인내역(거래) 수집 설정 ({@code codef.collect.*}).
 *
 * @param months 동기화 때 수집할 최근 개월 수(이번 달 포함). CODEF 하루 호출 한도가 있어서 카드사 수 x 개월 수만큼 호출한다.
 *               1~12 범위여야 한다.
 */
@ConfigurationProperties("codef.collect")
public record CodefCollectProperties(@DefaultValue("6") int months) {

    public static final int MAX_MONTHS = 12;

    public CodefCollectProperties {
        if (months < 1 || months > MAX_MONTHS) {
            throw new IllegalArgumentException("codef.collect.months는 1~" + MAX_MONTHS + "이어야 합니다.");
        }
    }

}
