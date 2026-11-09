package com.richreach.codef.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 카드 데이터 동기화 설정 ({@code codef.sync.*}).
 *
 * @param timeout    이 시간이 지나도 끝나지 않은 작업은 멈춘 것으로 보고 실패로 알린다. 서버가 작업 도중 재시작되면
 *                   진행 중으로 남는 기록이 생기는데, 이 기록이 새 동기화를 막지 않도록 하는 기준이기도 하다.
 *                   0 이하면 모든 작업이 바로 멈춘 것으로 보여 중복 시작을 막지 못하므로 앱을 시작할 때 거부한다.
 * @param retryBlock 다시 시도하면 안 되는 실패(반복 로그인으로 카드사 계정이 일시 제한됨, CF-12207) 뒤에 재연결과 동기화를
 *                   막는 시간. 카드사가 안내하는 제한 시간(최대 1시간)에 맞춘다. 이 시간이 지나면 다시 시도할 수 있다.
 *                   0 이하면 막지 못하므로 앱을 시작할 때 거부한다.
 */
@ConfigurationProperties("codef.sync")
public record CodefSyncProperties(@DefaultValue("5m") Duration timeout, @DefaultValue("1h") Duration retryBlock) {

    public CodefSyncProperties {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("codef.sync.timeout은 0보다 커야 합니다.");
        }
        if (retryBlock == null || retryBlock.isZero() || retryBlock.isNegative()) {
            throw new IllegalArgumentException("codef.sync.retry-block은 0보다 커야 합니다.");
        }
    }

}
