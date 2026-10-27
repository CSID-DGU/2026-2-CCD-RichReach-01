package com.richreach.global.security;

/**
 * 인증된 요청의 사용자. 컨트롤러에서 {@code @AuthenticationPrincipal AuthenticatedUser user}로 받는다.
 */
public record AuthenticatedUser(long userId) {
}
