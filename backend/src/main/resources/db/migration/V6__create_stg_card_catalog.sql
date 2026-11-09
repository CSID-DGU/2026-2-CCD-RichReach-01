-- #66 카드 카탈로그 스테이징 테이블.
-- card_catalog_319.csv(319종)를 컬럼 그대로 담는 중간 테이블이다. CSV는 코드(#70)가 적재하며,
-- 이 migration에는 데이터를 넣지 않는다.
--
-- card_catalog_raw(V1)와의 차이:
--   * card_catalog_raw : 카드별 크롤링 "원문 텍스트"를 보관한다. 키는 (issuer 이름, card_id).
--   * stg_card_catalog : 원문에서 뽑은 "구조화 목록"(상태, 해시, 검토 상태 등)을 보관한다. 키는 (issuer_code, card_id).
-- 두 테이블은 (issuer, card_id)와 source_text_sha256 = card_catalog_raw.content_hash 로 맞춰볼 수 있다.
-- 최종 테이블(card_products)로 승격하는 작업은 #71에서 한다.
--
-- 값 해석 (CSV 안내문 기준):
--   * 빈칸은 미확정/해당 없음이다. 0이나 '없음'으로 바꾸지 않고 NULL로 둔다.
--   * categories, category_names, benefit_types는 '|' 또는 ' / '로 여러 값이 묶여 있다. 스테이징에서는 원문 그대로 둔다.
--   * card_status = issuance_suspended 카드도 적재한다. 신규 추천 후보에서만 제외하며, 기존 보유 카드 분석에는 쓴다.
CREATE TABLE stg_card_catalog (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    issuer_code           VARCHAR(20)   NOT NULL COMMENT '카드사 코드 (kb, hyundai, nh, citi, woori)',
    issuer                VARCHAR(50)   NOT NULL COMMENT '카드사명. card_catalog_raw.issuer와 같은 표기',
    card_id               VARCHAR(40)   NOT NULL COMMENT '카드사 상품 코드. 문자열로 다뤄야 앞자리 0이 유지됨 (예: 09828, cardDetail_standard2)',
    card_name             VARCHAR(200)  NOT NULL,
    card_type             VARCHAR(30)   NOT NULL COMMENT 'credit, check, credit_or_special, business',
    card_status           VARCHAR(30)   NOT NULL COMMENT 'listed, issuance_suspended. suspended는 신규 추천 후보에서 제외',
    source_summary        VARCHAR(500)  NULL COMMENT '카드사 상품 한 줄 소개. 없는 카드가 있음',
    source_url            VARCHAR(500)  NULL,
    source_snapshot_date  DATE          NOT NULL COMMENT '원문 저장일. 카드사의 현재 상품 정보와 다를 수 있음',
    source_text_sha256    CHAR(64)      NOT NULL COMMENT '원문 SHA-256. card_catalog_raw.content_hash와 대조',
    source_html_count     INT           NOT NULL,
    source_scope_count    INT           NOT NULL,
    source_table_count    INT           NOT NULL,
    benefit_row_count     INT           NOT NULL COMMENT '추출한 문장/표 행 수. 독립된 혜택 개수가 아니며 합산하면 안 됨',
    categories            VARCHAR(500)  NULL COMMENT "업종 코드 목록, '|' 구분 (예: PUBLIC_TRANSIT|DINING). 혜택이 없는 카드는 NULL",
    category_names        VARCHAR(500)  NULL COMMENT "업종명 목록, ' / ' 구분",
    benefit_types         VARCHAR(200)  NULL COMMENT "혜택 유형 목록, '|' 구분 (DISCOUNT, POINT, CASHBACK, MILEAGE 등)",
    review_status         VARCHAR(40)   NOT NULL COMMENT 'AUTO_EXTRACTED_NEEDS_REVIEW, REVIEWED_SUBSET, PREPAID_NO_REWARD_RULE',
    product_class         VARCHAR(30)   NOT NULL COMMENT 'PAYMENT_CARD, GIFT_PREPAID, SUBSIDY_PAYMENT. 기프트/바우처류는 혜택 계산 대상이 아님',
    sample_owner          VARCHAR(50)   NOT NULL DEFAULT 'NOT_PROVIDED' COMMENT '이 카드를 가진 팀원. #65에서 채움',
    mvp_selected          VARCHAR(30)   NOT NULL DEFAULT 'NOT_SELECTED' COMMENT 'MVP 대상 여부. #65에서 채움',
    loaded_at             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'CSV 적재 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_stg_card_catalog_issuer_card (issuer_code, card_id),
    KEY idx_stg_card_catalog_issuer_card_name (issuer, card_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
