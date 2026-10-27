package com.richreach.global.security;

import com.richreach.global.exception.ErrorCode;
import com.richreach.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 로그인이 필요한 경로에 로그인 없이 오면 401을 {@link ApiResponse} 형식으로 내려준다.
 * 토큰이 아예 없으면 {@code AUTH-001}, 만료됐으면 {@code AUTH-004}, 위조/형식 오류면 {@code AUTH-005}다.
 * 클라이언트는 {@code AUTH-004}일 때만 refresh 토큰으로 재발급을 시도하면 된다.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode errorCode = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE) instanceof ErrorCode e
            ? e : ErrorCode.UNAUTHORIZED;
        response.setStatus(errorCode.getStatus().value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(errorCode)));
    }

}
