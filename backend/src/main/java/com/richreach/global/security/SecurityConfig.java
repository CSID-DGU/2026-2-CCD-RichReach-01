package com.richreach.global.security;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * 로그인 토큰으로 API를 보호한다.
 * 세션과 CSRF는 쓰지 않는다. (토큰을 Authorization 헤더로 보내는 API라 쿠키 인증이 없다.)
 * CORS는 기존 {@code CorsConfig}의 설정을 그대로 쓴다.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /** 로그인 없이 쓸 수 있는 경로. 이 밖의 모든 경로는 로그인이 필요하다. */
    private static final String[] PUBLIC_PATHS = {
        "/api/auth/**",
        "/actuator/health", "/actuator/health/**",
        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
        "/error"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider tokenProvider,
                                                   RestAuthenticationEntryPoint entryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .addFilterBefore(new JwtAuthenticationFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtSigningKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey.secretKey()));
    }

    /**
     * 서명과 발급자만 확인한다. 만료는 {@link JwtTokenProvider}가 직접 확인한다.
     * (만료와 위조를 다른 에러 코드로 내려줘야 하기 때문이다.)
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtSigningKey signingKey, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey.secretKey())
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtIssuerValidator(properties.issuer())));
        return decoder;
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

}
