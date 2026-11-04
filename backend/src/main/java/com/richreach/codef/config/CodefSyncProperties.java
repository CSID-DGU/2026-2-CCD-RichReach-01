package com.richreach.codef.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 카드 데이터 동기화 설정 ({@code codef.sync.*}).
 *
 * @param timeout 이 시간이 지나도 끝나지 않은 작업은 멈춘 것으로 보고 실패로 알린다. 서버가 작업 도중 재시작되면
 *                진행 중으로 남는 기록이 생기는데, 이 기록이 새 동기화를 막지 않도록 하는 기준이기도 하다.
 */
@ConfigurationProperties("codef.sync")
public record CodefSyncProperties(@DefaultValue("5m") Duration timeout) {

}
