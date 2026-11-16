package com.richreach.recommendation.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** OpenAI API 연결 정보. 키는 환경변수에서만 주입한다. */
@ConfigurationProperties(prefix = "richreach.openai")
public record OpenAiProperties(
    String apiKey,
    @DefaultValue("gpt-5-nano") String model,
    @DefaultValue("https://api.openai.com") String baseUrl,
    @DefaultValue("3s") Duration connectTimeout,
    @DefaultValue("20s") Duration readTimeout
) {
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String toString() {
        return "OpenAiProperties[apiKey=****, model=" + model + ", baseUrl=" + baseUrl + "]";
    }
}
