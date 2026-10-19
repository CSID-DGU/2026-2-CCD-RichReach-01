-- #67 혜택/조건 스테이징 테이블.
-- card_benefits_all.csv(4,126행, 46컬럼)와 card_conditions_all.csv(11,164행)를 컬럼 그대로 담는다.
-- 데이터는 넣지 않는다. CSV 적재와 검증은 코드(#70)가 한다.
--
-- 이 CSV는 "구조화 검토용"이며 계산 엔진에 바로 넣을 수 있는 규칙 DB가 아니다.
--   * 4,126행은 추출한 문장/표의 행 수다. 독립된 혜택 4,126개가 아니며 같은 혜택이 요약/본문에 반복될 수 있다. 행을 합산하면 안 된다.
--   * 빈칸은 미확정 또는 해당 없음이다. 0원, 무제한, 조건 없음으로 해석하지 않고 NULL로 둔다.
--   * rule_engine_ready는 현재 전부 false다. 검토와 규칙 연결 후 별도로 승인해야 한다.
--   * 여러 값이 한 컬럼에 '|'로 묶인 컬럼이 있다 (category, targets_normalized, payment_methods, condition_type). 스테이징에서는 원문 그대로 둔다.
--   * stg_card_catalog / stg_target_name / ref_common_standard와는 코드값으로 이어지며 FK는 걸지 않는다. 적재 순서에 영향이 없도록 하고, 존재 여부는 #70에서 검증한다.

-- 혜택 추출 행. benefit_id가 행 식별자이며 업데이트 간 영구 ID는 아니다.
CREATE TABLE stg_card_benefit (
    id                                BIGINT         NOT NULL AUTO_INCREMENT,
    benefit_id                        VARCHAR(80)    NOT NULL COMMENT '이번 자료에서 부여한 추출 행 ID (예: kb_09162_base). 영구 규칙 ID가 아님',
    issuer_code                       VARCHAR(20)    NOT NULL,
    card_id                           VARCHAR(40)    NOT NULL COMMENT '문자열로 다룸. 앞자리 0 유지',
    card_name                         VARCHAR(200)   NOT NULL,
    issuer                            VARCHAR(50)    NULL COMMENT '기존 5종 검토분(28행)에만 값이 있음',
    card_status                       VARCHAR(30)    NULL COMMENT '기존 5종 검토분(28행)에만 값이 있음',
    source_url                        VARCHAR(500)   NOT NULL,
    source_file                       VARCHAR(100)   NULL COMMENT '기존 5종 검토분(28행)에만 값이 있음',
    source_snapshot_date              DATE           NOT NULL,
    benefit_type                      VARCHAR(30)    NOT NULL COMMENT 'DISCOUNT, POINT, CASHBACK, MILEAGE, FEE_WAIVER, FREE_SERVICE, INTEREST_FREE, VOUCHER, REVIEW_REQUIRED',
    category                          VARCHAR(200)   NOT NULL COMMENT "업종 코드, '|' 구분. ref_common_standard(CATEGORY) 코드",
    category_name                     VARCHAR(200)   NOT NULL,
    merchants                         VARCHAR(300)   NULL COMMENT '원문에 나온 가맹점명. 대부분 비어 있음',
    target_raw                        VARCHAR(1000)  NOT NULL COMMENT '혜택 적용 대상 원문',
    targets_normalized                VARCHAR(200)   NULL COMMENT "정규화된 가맹점 코드, '|' 구분. 자동 분류 결과라 원문 확인 필요",
    payment_methods                   VARCHAR(100)   NULL COMMENT "결제수단 코드, '|' 구분 (예: KB_PAY|SAMSUNG_PAY)",
    -- 요율/금액. rate_percent 0.7은 0.7%이며 계산 시 100으로 나눈다.
    rate_percent                      DECIMAL(7,2)   NULL,
    rate_min_percent                  DECIMAL(7,2)   NULL COMMENT '한 문장에 나온 범위의 하한. 중간 요율이 모두 제공된다는 뜻은 아님',
    rate_max_percent                  DECIMAL(7,2)   NULL COMMENT '범위의 상한. 최대만 있으면 min은 NULL',
    amount                            BIGINT         NULL COMMENT '정액 혜택 금액',
    reward_unit                       VARCHAR(10)    NULL COMMENT 'KRW, POINT, MILE',
    amount_basis                      VARCHAR(30)    NULL COMMENT 'PER_LITER(리터당), PER_TRANSACTION(건당)',
    per_transaction_min_krw           BIGINT         NULL COMMENT '현재 CSV에서는 전 행이 빈칸',
    -- 실적 조건. 하한은 이상, 상한은 미만이다.
    previous_month_min_krw            BIGINT         NULL COMMENT '전월실적 하한(이상)',
    previous_month_max_exclusive_krw  BIGINT         NULL COMMENT '전월실적 상한(미만)',
    previous_month_requirement_status VARCHAR(20)    NULL COMMENT 'MINIMUM, NONE, NOT_STATED',
    annual_spend_min_krw              BIGINT         NULL,
    -- 한도. 월 한도/공유 한도/연간 한도/월 할인대상 결제금액 한도는 서로 다른 값이라 동일하게 취급하면 안 된다.
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
    -- 규칙/검토
    rule_mode                         VARCHAR(20)    NULL COMMENT 'BASE, ADDITIONAL, REPLACES_BASE, PREFER_HIGHER, ANNUAL_BONUS. 기존 5종 검토분에만 값이 있음',
    conditions                        TEXT           NULL COMMENT '혜택에 붙은 조건 원문',
    review_notes                      VARCHAR(300)   NULL,
    review_status                     VARCHAR(40)    NOT NULL COMMENT 'AUTO_EXTRACTED_NEEDS_REVIEW, SNAPSHOT_FIELDS_REVIEWED',
    rule_engine_ready                 TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '현재 전부 false. 검토 후 별도 승인 전까지 계산 엔진에 쓰지 않음',
    -- 출처
    source_scope_id                   VARCHAR(100)   NOT NULL COMMENT '원문 블록 ID. source_scopes.csv와 연결',
    source_lines                      VARCHAR(30)    NULL COMMENT '원문 줄 범위. 기존 5종 검토분에만 값이 있음',
    source_quote                      TEXT           NULL COMMENT '근거 원문. 기존 5종 검토분에만 값이 있음',
    extraction_method                 VARCHAR(30)    NOT NULL COMMENT 'PARAGRAPH, TABLE_ROW, TABLE_TIER, TABLE_TRANSPOSED, TEXT_LINE, PRIOR_REVIEWED_5_CARDS',
    loaded_at                         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stg_card_benefit_benefit_id (benefit_id),
    KEY idx_stg_card_benefit_issuer_card (issuer_code, card_id),
    KEY idx_stg_card_benefit_source_scope (source_scope_id),
    KEY idx_stg_card_benefit_type (benefit_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 전월실적/한도/제외/중복/유예/반영시점 등 원문 조건.
-- 혜택(benefit_id)이 아니라 원문 블록(source_scope_id)에 붙어 있어서, 혜택과는 source_scope_id로 이어진다.
-- 현재 CSV에서는 조건의 source_scope_id 1,047개 중 혜택 쪽에서 찾아지는 것이 613개뿐이다. 연결 누락은 #70에서 검증한다.
CREATE TABLE stg_card_condition (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    condition_id     VARCHAR(20)    NOT NULL COMMENT '조건 행 ID (예: C000001)',
    issuer_code      VARCHAR(20)    NOT NULL,
    card_id          VARCHAR(40)    NOT NULL COMMENT '문자열로 다룸. 앞자리 0 유지',
    card_name        VARCHAR(200)   NOT NULL,
    condition_type   VARCHAR(100)   NOT NULL COMMENT "'|'로 여러 유형이 묶임 (EXCLUSION, LIMIT, PERFORMANCE, STACKING, TIMING, GRACE_PERIOD, ROUNDING, CHANNEL)",
    condition_text   TEXT           NOT NULL COMMENT '조건 원문',
    source_scope_id  VARCHAR(100)   NOT NULL COMMENT '원문 블록 ID. stg_card_benefit.source_scope_id와 연결',
    scope_status     VARCHAR(40)    NOT NULL COMMENT '현재는 SCOPE_REVIEW_REQUIRED뿐',
    loaded_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stg_card_condition_condition_id (condition_id),
    KEY idx_stg_card_condition_issuer_card (issuer_code, card_id),
    KEY idx_stg_card_condition_source_scope (source_scope_id),
    KEY idx_stg_card_condition_type (condition_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
