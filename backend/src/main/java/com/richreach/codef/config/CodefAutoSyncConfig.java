package com.richreach.codef.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 자동 동기화가 켜졌을 때만 스케줄링을 켠다. ({@code codef.auto-sync.enabled=true}) 꺼져 있으면(기본값) 이 프로젝트에는
 * 스케줄 작업이 없어서 스케줄러 자체가 만들어지지 않는다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "codef.auto-sync", name = "enabled", havingValue = "true")
public class CodefAutoSyncConfig {

}
