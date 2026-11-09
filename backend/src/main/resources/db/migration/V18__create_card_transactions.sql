-- 카드 거래(승인내역). 동기화로 수집한 거래를 보유카드(user_cards)별로 저장한다.
-- 카드 번호 같은 민감정보는 저장하지 않고 보유카드 ID로만 참조한다.
-- 사용자·보유카드는 다른 도메인이라 JPA 연관 대신 ID 컬럼으로만 잇는다. (user_id를 직접 두어 조회 때 조인하지 않는다)
--
-- 기본 설계 결정 (이슈 #161. 거래 수집 구현 때 실제 응답으로 확인하고 필요하면 바꾼다)
--  * 금액은 원 단위 정수다. amount는 승인 금액, canceled_amount는 취소·환불된 금액이고, 실제 금액은 amount - canceled_amount다.
--  * 취소·환불은 새 행을 만들지 않고 원거래의 canceled_amount와 status를 갱신한다.
--  * 같은 거래는 dedupe_key로 구분해서 재동기화 때 새로 만들지 않고 갱신한다. (키는 애플리케이션이 만든다)
--  * 업종 카테고리는 자동 분류 값(category)과 사용자가 고친 값(category_override)을 따로 둔다.
--    재동기화는 category_override를 건드리지 않는다.
--  * 보유카드나 사용자를 지우면(카드사 연결 해제, 탈퇴) 그 거래도 함께 지운다.
CREATE TABLE card_transactions (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    user_card_id        BIGINT       NOT NULL COMMENT '거래가 발생한 보유카드(user_cards)',
    dedupe_key          VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                                     COMMENT '같은 거래를 구분하는 키(SHA-256 16진수). 승인번호가 있으면 승인번호+승인일시, 없으면 일시+금액+가맹점+순번',
    approval_no         VARCHAR(40)  NOT NULL DEFAULT '' COMMENT '승인번호. 없으면 빈 문자열',
    approved_at         DATETIME     NOT NULL COMMENT '승인일시. 시각 없이 일자만 오면 00:00:00',
    merchant_name       VARCHAR(200) NOT NULL DEFAULT '' COMMENT '가맹점명',
    amount              BIGINT       NOT NULL COMMENT '승인 금액(원). 0 이상',
    canceled_amount     BIGINT       NOT NULL DEFAULT 0 COMMENT '취소·환불된 금액(원). 0 이상, amount 이하',
    status              VARCHAR(30)  NOT NULL DEFAULT 'APPROVED' COMMENT 'APPROVED, PARTIALLY_CANCELED, CANCELED',
    installment_months  INT          NOT NULL DEFAULT 0 COMMENT '할부 개월. 0이면 일시불',
    overseas            BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '해외 결제 여부',
    category            VARCHAR(30)  NULL COMMENT '자동 분류한 업종 카테고리. 분류 전이면 NULL',
    category_override   VARCHAR(30)  NULL COMMENT '사용자가 고친 업종 카테고리. 있으면 이 값을 쓴다. 재동기화로 바뀌지 않는다',
    collected_at        DATETIME     NOT NULL COMMENT '이 거래 정보를 수집(생성 또는 변경)한 시각',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_card_transactions_card_key (user_card_id, dedupe_key),
    KEY idx_card_transactions_user_approved (user_id, approved_at),
    KEY idx_card_transactions_card_approved (user_card_id, approved_at),
    CONSTRAINT fk_card_transactions_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_card_transactions_user_card
        FOREIGN KEY (user_card_id) REFERENCES user_cards (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
