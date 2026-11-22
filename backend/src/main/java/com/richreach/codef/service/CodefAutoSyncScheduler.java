package com.richreach.codef.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 정해진 시각에 자동 동기화를 실행한다. {@code codef.auto-sync.enabled=true}일 때만 만들어지므로, 꺼져 있으면(기본값) 스케줄
 * 자체가 등록되지 않는다. 실행 중 어떤 오류가 나도 예외가 밖으로 나가지 않게 해서, 다음 날 실행이 멈추지 않도록 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "codef.auto-sync", name = "enabled", havingValue = "true")
public class CodefAutoSyncScheduler {

    private final CodefAutoSyncService autoSync;

    @Scheduled(cron = "${codef.auto-sync.cron:0 0 6 * * *}", zone = "Asia/Seoul")
    public void run() {
        try {
            autoSync.runOnce();
        } catch (RuntimeException e) {
            // 메시지에 사용자 정보가 섞일 수 있어서 종류만 남긴다.
            log.error("자동 동기화 실행 중 오류: type={}", e.getClass().getSimpleName());
        }
    }

}
