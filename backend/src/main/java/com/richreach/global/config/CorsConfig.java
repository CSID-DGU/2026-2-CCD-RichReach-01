package com.richreach.global.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 허용 origin은 설정({@code richreach.cors.allowed-origins}, 쉼표로 구분)으로 주입한다.
 * 값이 없으면 CORS를 열지 않는다. (웹/앱 미정이므로 최소 범위로 시작)
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${richreach.cors.allowed-origins:}") String[] allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins)
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.length == 0) {
            return;
        }
        registry.addMapping("/**")
            .allowedOrigins(allowedOrigins)
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .maxAge(3600);
    }

}
