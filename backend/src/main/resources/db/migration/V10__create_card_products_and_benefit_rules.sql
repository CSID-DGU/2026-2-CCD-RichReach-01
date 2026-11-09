-- #71 카드 상품/혜택 규칙 최종 도메인 테이블.
-- 스테이징 테이블(stg_*, V6~V9)에서 "MVP로 선택된 카드만" 옮겨 담는다. 옮기는 일은 CardPromotionService가 한다.
-- 이 migration에는 데이터를 넣지 않는다.
--
-- 스테이징과 다른 점:
--   * 스테이징은 검토 전 CSV를 그대로 담지만, 여기는 앱이 읽는 테이블이다. FK로 카드-혜택-실적이 연결된다.
--   * 각 행에 source_*_id(스테이징의 행 ID)를 남겨서, 같은 CSV를 다시 승격해도 같은 행이 갱신되고 중복되지 않는다.
--   * 자동 추출 값은 사람이 확인하기 전까지 그대로 믿으면 안 된다. benefit_rules.rule_engine_ready가 그 표시이며,
--     기본값은 FALSE이고 승격을 다시 해도 덮어쓰지 않는다.

-- 카드 상품.
-- recommendable: 신규 추천 후보에 넣어도 되는 카드인지. 발급 중단(issuance_suspended)이거나 결제용 카드가 아니면(기프트/바우처 등) FALSE.
--   FALSE여도 삭제하지 않는다. 이미 가진 카드의 혜택 분석에는 계속 쓰기 때문이다.
CREATE TABLE card_products (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    issuer_code           VARCHAR(20)   NOT NULL COMMENT '카드사 코드 (kb, hyundai, nh, citi, woori 등)',
    issuer                VARCHAR(50)   NOT NULL COMMENT '카드사명. card_catalog_raw.issuer와 같은 표기',
    card_id               VARCHAR(40)   NOT NULL COMMENT '카드사 상품 코드. 문자열로 다룸 (앞자리 0 유지)',
    card_name             VARCHAR(200)  NOT NULL,
    card_type             VARCHAR(30)   NOT NULL COMMENT 'credit, check, credit_or_special, business',
    card_status           VARCHAR(30)   NOT NULL COMMENT 'listed, issuance_suspended',
    product_class         VARCHAR(30)   NOT NULL COMMENT 'PAYMENT_CARD, GIFT_PREPAID, SUBSIDY_PAYMENT',
    recommendable         BOOLEAN       NOT NULL COMMENT '신규 추천 후보 여부. 발급 중단/비결제 카드는 FALSE',
    source_url            VARCHAR(500)  NULL,
    source_snapshot_date  DATE          NOT NULL COMMENT '원문 저장일. 카드사의 현재 상품 정보와 다를 수 있음',
    source_text_sha256    VARCHAR(64)   NOT NULL COMMENT '원문 SHA-256. card_catalog_raw.content_hash와 대조',
    review_status         VARCHAR(40)   NOT NULL COMMENT 'AUTO_EXTRACTED_NEEDS_REVIEW, REVIEWED_SUBSET, PREPAID_NO_REWARD_RULE',
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_card_products_issuer_card (issuer_code, card_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 카드별 혜택 규칙. 스테이징 혜택 한 행이 한 규칙이 된다.
-- 같은 혜택이 요약/본문에 반복 추출될 수 있어서 행을 그대로 합산하면 안 된다. (CSV 안내문)
-- 빈칸(NULL)은 미확정 또는 해당 없음이며 0원, 무제한, 조건 없음으로 해석하지 않는다.
-- 전월실적/연간실적 조건은 performance_tiers로 분리했다.
CREATE TABLE benefit_rules (
    id                                BIGINT         NOT NULL AUTO_INCREMENT,
    card_product_id                   BIGINT         NOT NULL,
    source_benefit_id                 VARCHAR(80)    NOT NULL COMMENT '스테이징 benefit_id. 재승격 시 같은 행을 갱신하는 키. 영구 규칙 ID가 아님',
    benefit_type                      VARCHAR(30)    NOT NULL COMMENT 'DISCOUNT, POINT, CASHBACK, MILEAGE, FEE_WAIVER, FREE_SERVICE, INTEREST_FREE, VOUCHER, REVIEW_REQUIRED',
    category                          VARCHAR(200)   NOT NULL COMMENT "업종 코드, '|' 구분. ref_common_standard(CATEGORY) 코드",
    category_name                     VARCHAR(200)   NOT NULL,
    merchants                         VARCHAR(300)   NULL,
    target_raw                        VARCHAR(1000)  NOT NULL COMMENT '혜택 적용 대상 원문',
    targets_normalized                VARCHAR(200)   NULL COMMENT "정규화된 가맹점 코드, '|' 구분. 자동 분류 결과라 원문 확인 필요",
    payment_methods                   VARCHAR(100)   NULL COMMENT "결제수단 코드, '|' 구분",
    rate_percent                      DECIMAL(7,2)   NULL COMMENT '0.7은 0.7%. 계산 시 100으로 나눔',
    rate_min_percent                  DECIMAL(7,2)   NULL,
    rate_max_percent                  DECIMAL(7,2)   NULL,
    amount                            BIGINT         NULL COMMENT '정액 혜택 금액',
    reward_unit                       VARCHAR(10)    NULL COMMENT 'KRW, POINT, MILE',
    amount_basis                      VARCHAR(30)    NULL COMMENT 'PER_LITER, PER_TRANSACTION',
    monthly_limit                     BIGINT         NULL,
    monthly_limit_unit                VARCHAR(10)    NULL COMMENT 'KRW, POINT',
    monthly_limit_status              VARCHAR(30)    NOT NULL COMMENT 'NOT_STATED, NOT_EXTRACTED, LIMITED, SHARED_LIMIT, UNLIMITED 등',
    daily_limit                       BIGINT         NULL,
    shared_monthly_limit              BIGINT         NULL,
    cap_group_id                      VARCHAR(80)    NULL COMMENT '한도를 공유하는 혜택끼리 묶는 ID',
    eligible_spend_monthly_limit_krw  BIGINT         NULL COMMENT '월 할인대상 결제금액 한도. 혜택 한도와 다른 값',
    daily_count_limit                 INT            NULL,
    monthly_count_limit               INT            NULL,
    annual_count_limit                INT            NULL,
    rule_mode                         VARCHAR(20)    NULL COMMENT 'BASE, ADDITIONAL, REPLACES_BASE, PREFER_HIGHER, ANNUAL_BONUS',
    conditions                        TEXT           NULL COMMENT '혜택에 붙은 조건 원문',
    review_notes                      VARCHAR(300)   NULL,
    review_status                     VARCHAR(40)    NOT NULL COMMENT 'AUTO_EXTRACTED_NEEDS_REVIEW, SNAPSHOT_FIELDS_REVIEWED',
    rule_engine_ready                 BOOLEAN        NOT NULL DEFAULT FALSE COMMENT '사람이 원문과 대조해 확인한 뒤에만 TRUE. 승격을 다시 해도 덮어쓰지 않음',
    source_scope_id                   VARCHAR(100)   NOT NULL COMMENT '근거 원문 블록. stg_source_scope.scope_id',
    extraction_method                 VARCHAR(30)    NOT NULL,
    created_at                        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_benefit_rules_source_benefit_id (source_benefit_id),
    KEY idx_benefit_rules_card_product (card_product_id),
    CONSTRAINT fk_benefit_rules_card_product
        FOREIGN KEY (card_product_id) REFERENCES card_products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 혜택 규칙에 붙은 전월실적/연간실적 조건. 실적 조건이 있는 혜택만 행이 생긴다.
-- 하한(min)은 이상, 상한(max_exclusive)은 미만이다. 빈칸은 조건 없음이 아니라 미확정이다.
CREATE TABLE performance_tiers (
    id                                 BIGINT       NOT NULL AUTO_INCREMENT,
    benefit_rule_id                    BIGINT       NOT NULL,
    previous_month_min_krw             BIGINT       NULL COMMENT '전월실적 하한(이상)',
    previous_month_max_exclusive_krw   BIGINT       NULL COMMENT '전월실적 상한(미만)',
    previous_month_requirement_status  VARCHAR(20)  NULL COMMENT 'MINIMUM, NONE, NOT_STATED. 기존 5종 검토분에만 값이 있음',
    annual_spend_min_krw               BIGINT       NULL,
    created_at                         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_performance_tiers_benefit_rule (benefit_rule_id),
    CONSTRAINT fk_performance_tiers_benefit_rule
        FOREIGN KEY (benefit_rule_id) REFERENCES benefit_rules (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 실적/혜택에서 제외되는 대상 조건 (원문 그대로).
-- 조건은 혜택이 아니라 원문 블록(source_scope_id)에 붙어 있어서, 카드 단위로만 연결하고 혜택에는 연결하지 않는다.
-- (스테이징 조건의 source_scope_id 중 혜택에서 찾아지는 것은 약 58%뿐이라 혜택 단위 연결은 믿을 수 없다.)
-- applies_to: 전월실적 산정에서 빠지는 조건이면 PERFORMANCE, 그 외 혜택 제외 조건이면 BENEFIT.
CREATE TABLE performance_exclusions (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    card_product_id     BIGINT        NOT NULL,
    source_condition_id VARCHAR(20)   NOT NULL COMMENT '스테이징 condition_id. 재승격 시 같은 행을 갱신하는 키',
    applies_to          VARCHAR(20)   NOT NULL COMMENT 'PERFORMANCE(전월실적 산정 제외), BENEFIT(혜택 제외)',
    condition_type      VARCHAR(100)  NOT NULL COMMENT "'|'로 여러 유형이 묶임 (EXCLUSION|PERFORMANCE 등)",
    condition_text      TEXT          NOT NULL COMMENT '조건 원문',
    source_scope_id     VARCHAR(100)  NOT NULL COMMENT '근거 원문 블록. stg_source_scope.scope_id',
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_performance_exclusions_source_condition (source_condition_id),
    KEY idx_performance_exclusions_card_product (card_product_id),
    CONSTRAINT fk_performance_exclusions_card_product
        FOREIGN KEY (card_product_id) REFERENCES card_products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
