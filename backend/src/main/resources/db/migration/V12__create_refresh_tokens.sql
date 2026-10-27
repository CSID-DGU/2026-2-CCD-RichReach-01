-- 로그인 유지용 refresh 토큰. 토큰 원문은 저장하지 않고 SHA-256 해시만 저장한다.
-- 재발급할 때마다 새 토큰으로 교체하고(이전 것은 revoked_at을 채운다),
-- 이미 폐기된 토큰이 다시 오면 탈취로 보고 그 사용자의 토큰을 모두 폐기한다.
CREATE TABLE refresh_tokens (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    token_hash  VARCHAR(64) NOT NULL COMMENT 'refresh 토큰의 SHA-256(hex). 원문은 저장하지 않는다',
    expires_at  DATETIME    NOT NULL,
    revoked_at  DATETIME    NULL COMMENT 'NULL이면 사용 가능. 로그아웃/재발급/탈취 의심 시 채운다',
    created_at  DATETIME    NOT NULL,
    updated_at  DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_token_hash (token_hash),
    KEY idx_refresh_tokens_user_id (user_id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
