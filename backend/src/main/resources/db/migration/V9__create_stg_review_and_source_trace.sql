-- #69 검토/추적 테이블.
-- review_queue.csv(311행), special_cases.csv(129행), source_scopes.csv(1,545행)를 컬럼 그대로 담는다.
-- 데이터는 넣지 않는다. CSV 적재와 검증은 코드(#70)가 한다.
--
-- 세 테이블은 추출 결과를 "어디서 가져왔고(source_scopes), 무엇이 남았는지(review_queue, special_cases)" 추적하는 용도다.
--   * 자동 추출한 혜택/조건(stg_card_benefit, stg_card_condition)은 source_scope_id로 source_scopes의 원문 블록과 이어진다.
--   * FK는 걸지 않는다. 적재 순서에 영향이 없도록 하고, 연결 누락은 #70에서 검증한다.
--   * 새 카드사/카드를 추가로 적재할 때도 (issuer_code, card_id) 기준으로 행만 추가하면 된다.

-- 추출에 사용한 원문 블록과 출처. 혜택/조건의 근거를 추적할 때 쓴다.
-- 카드별 전체 원문 텍스트는 card_catalog_raw.raw_text에 있고, 여기에는 추출 단위(블록)로 잘라 둔 텍스트가 있다.
CREATE TABLE stg_source_scope (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    scope_id     VARCHAR(100)  NOT NULL COMMENT '원문 블록 ID (예: kb_09828_S00001). 혜택/조건의 source_scope_id가 이 값을 가리킴',
    issuer_code  VARCHAR(20)   NOT NULL,
    card_id      VARCHAR(40)   NOT NULL COMMENT '문자열로 다룸. 앞자리 0 유지',
    card_name    VARCHAR(200)  NOT NULL,
    source_url   VARCHAR(500)  NOT NULL,
    source_html  VARCHAR(200)  NULL COMMENT '원본 HTML 파일 경로. 일부 행은 비어 있음',
    html_line    INT           NOT NULL COMMENT '원본 HTML 안의 줄 번호',
    scope_text   MEDIUMTEXT    NULL COMMENT '추출에 사용한 원문 블록. 최대 약 58KB이며 일부는 비어 있음',
    loaded_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stg_source_scope_scope_id (scope_id),
    KEY idx_stg_source_scope_issuer_card (issuer_code, card_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 자동 추출 결과 중 검토가 남은 항목 (review_queue.csv).
-- issue_type: RULE_REVIEW_PENDING(규칙 검토 대기), SOURCE_SECTION_CONFLICT(요약과 상세 본문 불일치)
-- status가 OPEN인 항목은 처리 전이다. 처리 이력 컬럼은 검토 방식이 정해지면 새 migration으로 추가한다.
CREATE TABLE stg_review_queue (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    issuer_code  VARCHAR(20)   NOT NULL,
    card_id      VARCHAR(40)   NOT NULL COMMENT '문자열로 다룸. 앞자리 0 유지',
    card_name    VARCHAR(200)  NOT NULL,
    issue_type   VARCHAR(40)   NOT NULL,
    detail       VARCHAR(500)  NOT NULL COMMENT '검토할 내용',
    status       VARCHAR(20)   NOT NULL COMMENT '현재는 OPEN뿐',
    loaded_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stg_review_queue_card_issue (issuer_code, card_id, issue_type, detail),
    KEY idx_stg_review_queue_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 일반 혜택 계산에 넣지 않고 별도로 처리하는 항목 (special_cases.csv).
-- 무이자할부, 포인트 사용, 기프트/바우처 카드, 라운지, 발급 상태 등.
-- exception_type: ADVANCE_REWARD, BRAND_SERVICE, FOREIGN_FEE_WAIVER, GIFT_PREPAID, INTEREST_FREE_INSTALLMENT,
--                 ISSUANCE_STATUS, LOUNGE, PARTNER_SERVICE, POINT_REDEMPTION, SUBSIDY_PAYMENT, WELCOME_EVENT
-- 같은 카드에 같은 유형이 여러 건 있을 수 있어서 (카드, 유형)만으로는 중복을 구분할 수 없다. 설명까지 포함해 중복을 막는다.
CREATE TABLE stg_special_case (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    issuer_code      VARCHAR(20)   NOT NULL,
    card_id          VARCHAR(40)   NOT NULL COMMENT '문자열로 다룸. 앞자리 0 유지',
    card_name        VARCHAR(200)  NOT NULL,
    exception_type   VARCHAR(40)   NOT NULL,
    description      VARCHAR(300)  NOT NULL,
    source_scope_id  VARCHAR(100)  NULL COMMENT 'stg_source_scope.scope_id. 일부 행은 비어 있음',
    source_quote     TEXT          NULL COMMENT '근거 원문. 대부분 비어 있음',
    loaded_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stg_special_case_card_type_desc (issuer_code, card_id, exception_type, description),
    KEY idx_stg_special_case_source_scope (source_scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
