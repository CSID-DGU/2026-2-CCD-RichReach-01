CREATE TABLE users (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    email             VARCHAR(255) NOT NULL,
    password_hash     VARCHAR(255) NOT NULL COMMENT '소셜 로그인 없음 - 항상 필수',
    name              VARCHAR(100) NOT NULL,
    phone             VARCHAR(20)  NOT NULL,
    user_type         VARCHAR(20)  NOT NULL DEFAULT 'REAL' COMMENT 'REAL 실사용자, PERSONA 합성 평가용',
    terms_agreed_at   DATETIME     NOT NULL COMMENT '서비스 이용약관/개인정보 수집 동의 시각',
    created_at        DATETIME     NOT NULL,
    updated_at        DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
