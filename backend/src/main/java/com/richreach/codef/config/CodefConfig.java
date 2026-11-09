package com.richreach.codef.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({CodefProperties.class, CodefSyncProperties.class, CodefDebugProperties.class})
public class CodefConfig {

    /**
     * 카드 데이터 동기화를 실행하는 스레드 풀. 요청 스레드를 CODEF 응답이 올 때까지 붙잡지 않으려고 따로 둔다.
     * 서버를 끄는 중이어도 진행 중인 작업은 잠시 기다려 마무리한다.
     */
    @Bean(name = "codefSyncExecutor")
    public ThreadPoolTaskExecutor codefSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("codef-sync-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    /** 토큰 발급(OAuth) 서버 호출용 */
    @Bean
    public RestClient codefOauthRestClient(CodefProperties properties) {
        return buildRestClient(properties.oauthBaseUrl(), properties.connectTimeout(), properties.readTimeout());
    }

    /** 상품 API(계정 등록, 카드 조회 등) 호출용 */
    @Bean
    public RestClient codefApiRestClient(CodefProperties properties) {
        return buildRestClient(properties.apiBaseUrl(), properties.connectTimeout(), properties.apiReadTimeout());
    }

    /** 외부 호출에는 연결/응답 타임아웃을 반드시 지정한다. */
    private RestClient buildRestClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build();
    }

}
