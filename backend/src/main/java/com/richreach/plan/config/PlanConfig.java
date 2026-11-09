package com.richreach.plan.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(PlanProperties.class)
public class PlanConfig {

    /**
     * 월간 플랜 생성을 실행하는 스레드 풀. 최적화 계산이 오래 걸릴 수 있어서 요청 스레드를 붙잡지 않으려고 따로 둔다.
     * 서버를 끄는 중이어도 진행 중인 작업은 잠시 기다려 마무리한다.
     */
    @Bean(name = "planJobExecutor")
    public ThreadPoolTaskExecutor planJobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("plan-job-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

}
