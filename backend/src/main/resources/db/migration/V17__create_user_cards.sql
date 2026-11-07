-- 사용자의 보유카드. 카드사 연결 후 동기화로 수집한 카드를 저장하고, 가능하면 카드 상품(card_products)과 연결한다.
-- 카드 번호는 마스킹된 값만 저장한다. (전체 번호는 저장하지 않는다)
-- 사용자·카드사 계정은 다른 도메인이라 JPA 연관 대신 ID 컬럼으로만 잇는다. (user_id를 직접 두어 조회 때 조인하지 않는다)
-- 재동기화에서 같은 카드(org_account_id, masked_number, card_name)는 한 행을 갱신한다.
CREATE TABLE user_cards (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    org_account_id   BIGINT       NOT NULL COMMENT '이 카드를 수집한 카드사 계정(codef_org_accounts)',
    organization     VARCHAR(10)  NOT NULL COMMENT 'CODEF 기관 코드. 카드사 구분용',
    card_product_id  BIGINT       NULL COMMENT '매칭된 카드 상품. 매칭에 실패하면 NULL이고 분석할 수 없다',
    card_name        VARCHAR(200) NOT NULL COMMENT '카드사가 알려준 카드명',
    masked_number    VARCHAR(30)  NOT NULL DEFAULT '' COMMENT '마스킹된 카드 번호. 없으면 빈 문자열',
    card_type        VARCHAR(30)  NULL COMMENT '카드사가 알려준 카드 종류(신용/체크 등)',
    active           BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '가장 최근 동기화에서 확인된 카드. 사라진 카드는 FALSE',
    selected         BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '분석 대상 여부. 재동기화로 바뀌지 않는다',
    last_synced_at   DATETIME     NOT NULL COMMENT '마지막으로 확인된 동기화 시각',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_cards_account_number_name (org_account_id, masked_number, card_name),
    KEY idx_user_cards_user_id (user_id),
    CONSTRAINT fk_user_cards_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_cards_org_account
        FOREIGN KEY (org_account_id) REFERENCES codef_org_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_cards_card_product
        FOREIGN KEY (card_product_id) REFERENCES card_products (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
