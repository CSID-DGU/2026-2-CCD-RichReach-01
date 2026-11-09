package com.richreach.global.security;

import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code Authorization: Bearer <access 토큰>}을 확인해서 로그인한 사용자로 만든다.
 * 토큰이 없거나 잘못됐어도 여기서 응답하지 않고 비로그인 상태로 넘긴다. 로그인이 필요한 경로면 뒤에서
 * {@link RestAuthenticationEntryPoint}가 401을 내려주고, 토큰이 잘못된 이유는 요청 속성으로 전달한다.
 * 스프링 빈으로 등록하면 보안 필터 체인 밖에서도 한 번 더 실행되므로 {@link SecurityConfig}에서 직접 만든다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 토큰이 거부된 이유({@link ErrorCode})를 {@link RestAuthenticationEntryPoint}에 넘기는 요청 속성 이름 */
    static final String AUTH_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".AUTH_ERROR";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                JwtTokenProvider.TokenClaims claims = tokenProvider.parse(token, TokenType.ACCESS);
                SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    new AuthenticatedUser(claims.userId()), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            } catch (BusinessException e) {
                SecurityContextHolder.clearContext();
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, e.getErrorCode());
            }
        }
        chain.doFilter(request, response);
    }

}
