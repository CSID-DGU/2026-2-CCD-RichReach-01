package com.richreach.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.richreach.global.exception.GlobalExceptionHandler;
import com.richreach.global.response.ApiResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.richreach.global.config.CorsConfig;

@WebMvcTest(controllers = SecurityConfigTest.TestController.class)
@Import({SecurityConfig.class, JwtSigningKey.class, JwtTokenProvider.class, RestAuthenticationEntryPoint.class,
    RestAccessDeniedHandler.class, GlobalExceptionHandler.class, CorsConfig.class,
    SecurityConfigTest.TestController.class})
@TestPropertySource(properties = "richreach.cors.allowed-origins=http://localhost:3000")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @Autowired
    private JwtEncoder encoder;
    @Autowired
    private JwtDecoder decoder;
    @Autowired
    private JwtProperties properties;

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    @DisplayName("로그인이 필요한 경로에 토큰 없이 오면 401과 AUTH-001을 공통 응답 형식으로 내려준다")
    void noTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/test/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("WWW-Authenticate", "Bearer"))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("AUTH-001"))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("유효한 access 토큰이면 요청한 사용자로 인증된다")
    void validTokenAuthenticates() throws Exception {
        String token = tokenProvider.createAccessToken(42L).value();

        mockMvc.perform(get("/api/test/me").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(42));
    }

    @Test
    @DisplayName("Bearer 접두사는 대소문자를 구분하지 않는다")
    void bearerPrefixIsCaseInsensitive() throws Exception {
        String token = tokenProvider.createAccessToken(7L).value();

        mockMvc.perform(get("/api/test/me").header("Authorization", "bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(7));
    }

    @Test
    @DisplayName("만료된 토큰은 401과 AUTH-004를 내려줘서 클라이언트가 재발급을 시도할 수 있다")
    void expiredTokenIsAuth004() throws Exception {
        Clock yesterday = Clock.fixed(Instant.now().minus(Duration.ofDays(1)), ZoneId.systemDefault());
        String expired = new JwtTokenProvider(encoder, decoder, properties, yesterday).createAccessToken(1L).value();

        mockMvc.perform(get("/api/test/me").header("Authorization", bearer(expired)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-004"));
    }

    @Test
    @DisplayName("위조되었거나 형식이 틀린 토큰은 401과 AUTH-005를 내려준다")
    void invalidTokenIsAuth005() throws Exception {
        mockMvc.perform(get("/api/test/me").header("Authorization", bearer("not-a-jwt")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-005"));
    }

    @Test
    @DisplayName("access 토큰 자리에 refresh 토큰을 보내면 거부한다")
    void refreshTokenCannotBeUsedAsAccessToken() throws Exception {
        String refresh = tokenProvider.createRefreshToken(1L).value();

        mockMvc.perform(get("/api/test/me").header("Authorization", bearer(refresh)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-005"));
    }

    @Test
    @DisplayName("Bearer 방식이 아닌 Authorization 헤더는 토큰이 없는 것으로 본다")
    void nonBearerSchemeIsTreatedAsNoToken() throws Exception {
        mockMvc.perform(get("/api/test/me").header("Authorization", "Basic dXNlcjpwYXNz"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-001"));
    }

    @Test
    @DisplayName("로그인/가입 경로(/api/auth/**)는 토큰이 없어도 접근할 수 있다")
    void authPathsArePublic() throws Exception {
        mockMvc.perform(get("/api/auth/ping"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value("pong"));
    }

    @Test
    @DisplayName("공개 경로는 만료되거나 잘못된 토큰이 함께 와도 막지 않는다")
    void publicPathIgnoresBadToken() throws Exception {
        mockMvc.perform(get("/api/auth/ping").header("Authorization", bearer("not-a-jwt")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("공개 경로로 지정하지 않은 경로는 존재하지 않아도 404가 아니라 401이다")
    void unknownPathRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CORS 사전 요청(OPTIONS)은 토큰 없이도 통과한다")
    void corsPreflightIsNotBlocked() throws Exception {
        mockMvc.perform(options("/api/test/me")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @RestController
    static class TestController {

        @GetMapping("/api/test/me")
        ApiResponse<Long> me(@AuthenticationPrincipal AuthenticatedUser user) {
            return ApiResponse.ok(user.userId());
        }

        @GetMapping("/api/auth/ping")
        ApiResponse<String> ping() {
            return ApiResponse.ok("pong");
        }

    }

}
