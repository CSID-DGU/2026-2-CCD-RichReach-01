-- 비밀번호 재설정 인증번호. 사용자당 하나만 두고, 다시 발송하면 같은 행을 새 값으로 바꾼다.
-- 인증번호 원문은 저장하지 않고 BCrypt 해시만 저장한다. 확인에 성공하거나 5번 틀리면 행을 지운다.
CREATE TABLE password_reset_codes (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    code_hash        VARCHAR(100) NOT NULL COMMENT '6자리 인증번호의 BCrypt 해시. 원문은 저장하지 않는다',
    sent_at          DATETIME     NOT NULL COMMENT '마지막으로 발송한 시각. 재전송 대기 시간 계산에 쓴다',
    expires_at       DATETIME     NOT NULL,
    failed_attempts  INT          NOT NULL DEFAULT 0 COMMENT '틀린 횟수',
    created_at       DATETIME     NOT NULL,
    updated_at       DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_password_reset_codes_user (user_id),
    CONSTRAINT fk_password_reset_codes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
