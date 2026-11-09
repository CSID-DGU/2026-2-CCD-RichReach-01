package com.richreach.plan.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 월간 플랜 생성 설정 ({@code plan.generation.*}).
 *
 * @param timeout           이 시간이 지나도 끝나지 않은 작업은 멈춘 것으로 보고 실패로 알린다. 서버가 작업 도중 재시작되면
 *                          진행 중으로 남는 기록이 생기는데, 이 기록이 새 요청을 막지 않게 하는 기준이기도 하다.
 *                          0 이하면 모든 작업이 바로 멈춘 것으로 보이므로 앱을 시작할 때 거부한다.
 * @param strategyTimeLimit 전략 하나에 허용하는 계산 시간. 전략(CP-SAT 등)의 풀이 시간 제한으로 쓴다.
 *                          0 이하면 거부한다.
 */
@ConfigurationProperties("plan.generation")
public record PlanProperties(@DefaultValue("5m") Duration timeout, @DefaultValue("10s") Duration strategyTimeLimit) {

    public PlanProperties {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("plan.generation.timeout은 0보다 커야 합니다.");
        }
        if (strategyTimeLimit == null || strategyTimeLimit.isZero() || strategyTimeLimit.isNegative()) {
            throw new IllegalArgumentException("plan.generation.strategy-time-limit은 0보다 커야 합니다.");
        }
    }

}
