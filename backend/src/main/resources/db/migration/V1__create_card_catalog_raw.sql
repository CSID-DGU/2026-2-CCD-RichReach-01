-- 카드 혜택 원문 크롤링 결과를 담는 수집(staging) 테이블.
-- card_products/benefit_rules 등 서비스가 실제로 쓰는 테이블은 아직 설계 중이며,
-- 이 테이블은 "원문 → 구조화" 작업 전까지 크롤링 원문을 보관하는 중간 단계다.
-- review_status: raw_collected(수집 완료, 미구조화) -> structured(구조화 완료, 아래 FK 채워짐)
CREATE TABLE card_catalog_raw (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    issuer                 VARCHAR(50)   NOT NULL COMMENT '카드사명 (예: KB국민카드). CODEF 기관코드 매핑은 구조화 단계에서 처리',
    card_id                VARCHAR(40)   NOT NULL COMMENT '카드사 자체 상품 코드. 카드사마다 형식이 달라 숫자 전용이 아님 (예: ME4, cardDetail_kookminHappy)',
    card_name              VARCHAR(200)  NOT NULL,
    card_type              VARCHAR(30)   NOT NULL COMMENT 'credit, check, credit_or_special, business 등',
    status                 VARCHAR(30)   NOT NULL COMMENT 'listed, issuance_suspended 등. suspended는 추천 후보에서 제외해야 함',
    detail_url             VARCHAR(500)  NULL,
    content_hash           VARCHAR(64)   NULL COMMENT '원문 SHA-256 (전달 중 손상 검증용)',
    raw_text               LONGTEXT      NOT NULL COMMENT '크롤링한 원문 텍스트 전체',
    raw_text_length        INT           NULL,
    review_status          VARCHAR(20)   NOT NULL DEFAULT 'raw_collected',
    structured_product_id  BIGINT        NULL COMMENT '구조화 완료 후 card_products.id. 해당 테이블 생기기 전까지 FK 없음',
    collected_at           DATETIME      NOT NULL COMMENT '크롤링 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_card_catalog_raw_issuer_card (issuer, card_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
