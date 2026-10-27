package com.richreach.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * 요청마다 메서드, 경로, 상태, 소요 시간을 한 줄로 남긴다.
 * 쿼리 문자열과 요청 본문은 {@link SensitiveDataMasker}로 민감한 값을 가린 뒤에만 기록하고,
 * 요청 본문은 DEBUG 레벨일 때만 남긴다. 응답 본문은 남기지 않는다.
 */
@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    /** 이보다 긴 본문은 잘려서 JSON으로 읽을 수 없으므로 통째로 가려진다. */
    private static final int MAX_BODY_BYTES = 4096;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        boolean logBody = log.isDebugEnabled();
        HttpServletRequest wrapped = logBody ? new ContentCachingRequestWrapper(request, MAX_BODY_BYTES) : request;
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(wrapped, response);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            String query = SensitiveDataMasker.maskQuery(request.getQueryString());
            log.info("{} {} -> {} ({}ms)", request.getMethod(),
                query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query,
                response.getStatus(), elapsed);
            if (logBody && wrapped instanceof ContentCachingRequestWrapper cached) {
                logBody(cached);
            }
        }
    }

    private static void logBody(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        if (content.length == 0) {
            return;
        }
        String body = new String(content, StandardCharsets.UTF_8);
        log.debug("request body: {}", SensitiveDataMasker.maskJson(body));
    }

}
