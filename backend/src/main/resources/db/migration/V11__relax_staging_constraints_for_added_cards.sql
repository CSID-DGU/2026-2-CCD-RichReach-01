-- 추가 카드 12장(csv_8_added_12)을 적재하면서 드러난 제약 불일치를 고친다.
-- 이미 적용된 V6~V10은 수정하지 않고 이 파일로 바꾼다.
--
-- 왜 필요한가:
--   * CSV 규칙은 "모르는 값은 빈칸"이다. 빈칸은 0, 무제한, 조건 없음이 아니라 "미확정"이다.
--     그런데 기존 319종에는 빈칸이 없어서, 일부 컬럼을 NOT NULL로 만들어 두었다. 추가 카드에는 빈칸이 정상적으로 있다.
--   * 새 ID와 코드 문자열이 기존보다 길다. (예: condition_id 36자, extraction_method 39자)
--
-- 스테이징은 "받은 그대로 담는 곳"이라 빈칸을 허용하고, 승격하는 최종 테이블도 같은 컬럼의 빈칸을 허용한다.
-- 빈칸을 가짜 기본값으로 채우지 않는다.

-- ───────────── 스테이징 ─────────────

ALTER TABLE stg_card_catalog
    MODIFY card_status        VARCHAR(30) NULL COMMENT 'listed, issuance_suspended. 비어 있으면 미확정. suspended는 신규 추천 후보에서 제외',
    MODIFY source_html_count  INT         NULL,
    MODIFY source_scope_count INT         NULL,
    MODIFY source_table_count INT         NULL;

ALTER TABLE stg_card_benefit
    MODIFY category_name        VARCHAR(200)  NULL,
    MODIFY target_raw           VARCHAR(1000) NULL COMMENT '혜택 적용 대상 원문. 비어 있으면 미확정',
    MODIFY monthly_limit_status VARCHAR(30)   NULL COMMENT 'NOT_STATED, NOT_EXTRACTED, LIMITED, SHARED_LIMIT, UNLIMITED 등. 비어 있으면 미확정',
    MODIFY reward_unit          VARCHAR(30)   NULL COMMENT 'KRW, POINT, MILE, USD, FREE_SERVICE 등',
    MODIFY amount_basis         VARCHAR(100)  NULL COMMENT 'PER_LITER, PER_TRANSACTION, PERCENT_OF_ELIGIBLE_SPEND 등 코드 또는 설명',
    MODIFY extraction_method    VARCHAR(80)   NOT NULL COMMENT 'PARAGRAPH, TABLE_ROW, MANUAL_OFFICIAL_GUIDE_AND_RENDERED_PAGE 등';

ALTER TABLE stg_card_condition
    MODIFY condition_id VARCHAR(80) NOT NULL COMMENT '조건 행 ID (예: C000001, mvp_kb_01664_c_...)';

ALTER TABLE stg_source_scope
    MODIFY html_line INT NULL COMMENT '원본 HTML 안의 줄 번호. 비어 있으면 미확정';

-- target_code는 UNIQUE KEY(benefit_id, target_code, target_name)에 들어 있다.
-- MySQL은 NULL이 들어간 키를 서로 다르다고 보기 때문에, NULL로 두면 같은 CSV를 다시 적재할 때 같은 줄이 중복된다.
-- 그래서 코드가 없는 연결은 빈 문자열('')로 저장한다. (적재기가 키 컬럼의 빈칸을 ''로 넣는다.)
ALTER TABLE stg_target_name
    MODIFY target_code VARCHAR(40) NOT NULL DEFAULT '' COMMENT 'ref_common_standard.code (MERCHANT 또는 PAYMENT_METHOD). 코드가 없으면 빈 문자열';

-- ───────────── 최종 테이블 ─────────────

-- card_status가 비어 있으면 미확정이다. 이때 recommendable은 FALSE로 둔다. (승격 서비스가 처리)
ALTER TABLE card_products
    MODIFY card_status VARCHAR(30) NULL COMMENT 'listed, issuance_suspended. 비어 있으면 미확정';

ALTER TABLE benefit_rules
    MODIFY category_name        VARCHAR(200)  NULL,
    MODIFY target_raw           VARCHAR(1000) NULL COMMENT '혜택 적용 대상 원문. 비어 있으면 미확정',
    MODIFY monthly_limit_status VARCHAR(30)   NULL COMMENT 'NOT_STATED, NOT_EXTRACTED, LIMITED, SHARED_LIMIT, UNLIMITED 등. 비어 있으면 미확정',
    MODIFY reward_unit          VARCHAR(30)   NULL COMMENT 'KRW, POINT, MILE, USD, FREE_SERVICE 등',
    MODIFY amount_basis         VARCHAR(100)  NULL COMMENT 'PER_LITER, PER_TRANSACTION, PERCENT_OF_ELIGIBLE_SPEND 등 코드 또는 설명',
    MODIFY extraction_method    VARCHAR(80)   NOT NULL;

ALTER TABLE performance_exclusions
    MODIFY source_condition_id VARCHAR(80) NOT NULL COMMENT '스테이징 condition_id. 재승격 시 같은 행을 갱신하는 키';
