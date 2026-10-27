-- 회원가입: 전화번호 중복을 막고, 약관 동의 이력을 버전과 함께 남긴다.

-- 같은 전화번호로 계정을 여러 개 만들 수 없게 한다. (전화번호는 하이픈 없이 숫자만 저장한다: 01012345678)

-- 가입 API(SignupService.normalizePhone)와 같은 규칙으로 기존 번호에서 하이픈과 공백을 없앤다.
-- 이걸 하지 않으면 '010-1111-2222'로 저장된 기존 행과 '01011112222'로 가입한 새 행이 서로 다른 값이라 중복을 못 막는다.
UPDATE users SET phone = REPLACE(REPLACE(phone, '-', ''), ' ', '');

-- 정규화한 뒤에도 같은 번호가 둘 이상 있으면 아래 문장이 실패한다. 그 계정들을 먼저 정리한 뒤 다시 적용한다.
-- 적용 전에 확인한다:
--   SELECT REPLACE(REPLACE(phone, '-', ''), ' ', '') AS normalized, COUNT(*)
--   FROM users GROUP BY normalized HAVING COUNT(*) > 1;
ALTER TABLE users ADD UNIQUE KEY uk_users_phone (phone);

-- 사용자가 어떤 약관의 어떤 버전에 언제 동의했는지. users.terms_agreed_at은 가입 시각 확인용으로 그대로 둔다.
-- term_type: SERVICE_AND_PRIVACY(서비스 이용약관 및 개인정보 수집 동의). 카드 연동 동의 등은 종류만 추가한다.
CREATE TABLE user_term_agreements (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    term_type     VARCHAR(30) NOT NULL,
    term_version  VARCHAR(20) NOT NULL COMMENT '약관 버전. 예: 2026-10-01',
    agreed_at     DATETIME    NOT NULL,
    created_at    DATETIME    NOT NULL,
    updated_at    DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_term_agreements (user_id, term_type, term_version),
    CONSTRAINT fk_user_term_agreements_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
