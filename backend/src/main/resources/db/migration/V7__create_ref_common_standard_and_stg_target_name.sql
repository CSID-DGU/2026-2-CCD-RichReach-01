-- #68 공통 코드 / 가맹점명 참조 테이블.
-- 데이터는 넣지 않는다. CSV 적재는 코드(#70)가 한다.

-- 업종·혜택 유형·가맹점·결제수단 공통 코드표 (common_standards.csv, 84행).
-- 서비스가 계속 쓰는 기준표라서 스테이징을 거치지 않고 바로 참조 테이블로 둔다.
-- 새 카드사/카드를 추가할 때 코드표에 없는 업종·가맹점이 나오면 행만 추가하면 된다.
-- standard_kind: CATEGORY(업종), BENEFIT_TYPE(혜택 유형), MERCHANT(가맹점), PAYMENT_METHOD(결제수단)
CREATE TABLE ref_common_standard (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    standard_kind         VARCHAR(30)   NOT NULL COMMENT 'CATEGORY, BENEFIT_TYPE, MERCHANT, PAYMENT_METHOD',
    code                  VARCHAR(40)   NOT NULL COMMENT '공통 코드 (예: DINING, COUPANG, KB_PAY). benefit/target 데이터가 이 값으로 참조함',
    name                  VARCHAR(100)  NOT NULL COMMENT '표시 이름 (예: 음식점, 쿠팡)',
    source_alias_pattern  VARCHAR(255)  NULL COMMENT '원문 문구를 이 코드로 매칭하는 정규식. 없는 코드가 있음(NULL)',
    definition            VARCHAR(200)  NULL COMMENT '범위·예외 설명',
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ref_common_standard_kind_code (standard_kind, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 원문에서 찾은 가맹점·결제수단 명칭과 공통 코드의 연결 (target_names.csv, 1,046행).
-- 이름이 비슷해서 붙인 결과(matching_status = LEXICAL_MATCH_REVIEW_SCOPE)라서,
-- 실제 혜택 적용 대상인지 제외 대상인지는 원문을 보고 확인해야 한다. 그래서 스테이징으로 둔다.
-- benefit_id는 card_benefits_all.csv의 추출 행 ID로, 업데이트 간에 영구 ID를 보장하지 않는다.
-- 적재 순서에 영향이 없도록 FK는 걸지 않는다. benefit_id/target_code 존재 여부는 #70에서 검증한다.
CREATE TABLE stg_target_name (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    benefit_id       VARCHAR(60)   NOT NULL COMMENT '혜택 추출 행 ID (예: kb_09828_A008). stg 혜택 테이블의 benefit_id와 연결',
    issuer_code      VARCHAR(20)   NOT NULL,
    card_id          VARCHAR(40)   NOT NULL COMMENT '문자열로 다룸. 앞자리 0 유지',
    target_code      VARCHAR(40)   NOT NULL COMMENT 'ref_common_standard.code (MERCHANT 또는 PAYMENT_METHOD)',
    target_name      VARCHAR(100)  NOT NULL COMMENT '원문에서 찾은 명칭',
    target_kind      VARCHAR(30)   NOT NULL COMMENT 'MERCHANT, PAYMENT_METHOD',
    matching_status  VARCHAR(40)   NOT NULL COMMENT '현재는 LEXICAL_MATCH_REVIEW_SCOPE(이름 일치, 검토 필요)뿐',
    loaded_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stg_target_name_benefit_target (benefit_id, target_code, target_name),
    KEY idx_stg_target_name_target_code (target_code),
    KEY idx_stg_target_name_issuer_card (issuer_code, card_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
