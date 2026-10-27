package com.richreach.global.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 토큰 서명/검증에 쓰는 HS256 키.
 * {@code JWT_SECRET}이 있으면 그 값을 쓰고, 없으면 실행할 때마다 임시 키를 만든다.
 * 임시 키는 서버를 다시 켜면 바뀌어서 기존 로그인이 모두 풀리므로, 운영에서는 반드시 설정해야 한다.
 */
@Slf4j
@Component
public class JwtSigningKey {

    /** HS256은 256비트(32바이트) 이상의 키가 필요하다. */
    static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;

    public JwtSigningKey(JwtProperties properties) {
        String secret = properties.secret();
        byte[] bytes;
        if (secret == null || secret.isBlank()) {
            log.warn("JWT_SECRET이 없어 임시 서명 키를 만듭니다. 서버를 다시 켜면 모든 로그인이 풀립니다. "
                + "(운영 환경에서는 JWT_SECRET을 설정하세요)");
            bytes = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(bytes);
        } else {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                    "app.jwt.secret(JWT_SECRET)은 %d바이트 이상이어야 합니다. 현재 길이: %d".formatted(MIN_SECRET_BYTES, bytes.length));
            }
        }
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
    }

    public SecretKey secretKey() {
        return key;
    }

}
