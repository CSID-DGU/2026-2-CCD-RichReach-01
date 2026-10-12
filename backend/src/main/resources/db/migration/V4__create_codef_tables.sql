-- CODEF 기관(카드사)별 정책. 사용자 연동과 무관하게 먼저 존재하는 기준정보.
CREATE TABLE codef_org_policies (
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    organization            VARCHAR(10)   NOT NULL COMMENT 'CODEF 기관코드',
    api_type                VARCHAR(30)   NOT NULL COMMENT 'APPROVAL, RESULT_CHECK 등',
    max_lookback_months     INT           NULL COMMENT '최대 조회 가능 개월 수',
    period_rule             VARCHAR(30)   NULL COMMENT 'PREV_OF_START, CURRENT_ONLY, PREV_AND_CURRENT',
    requires_card_auth      BOOLEAN       NOT NULL DEFAULT FALSE,
    sms_auth_risk           BOOLEAN       NOT NULL DEFAULT FALSE,
    preferred_login_type    VARCHAR(10)   NULL,
    provides_card_no        BOOLEAN       NOT NULL DEFAULT TRUE,
    provides_standard_amt   BOOLEAN       NOT NULL DEFAULT TRUE,
    provides_required_amt   BOOLEAN       NOT NULL DEFAULT TRUE,
    provides_comm_period    BOOLEAN       NOT NULL DEFAULT TRUE,
    mvp_supported           BOOLEAN       NOT NULL DEFAULT FALSE,
    note                    VARCHAR(255)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_codef_org_policies_org_api (organization, api_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 사용자의 CODEF 연동. 사용자당 1개.
CREATE TABLE codef_connections (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    connected_id    VARCHAR(100) NOT NULL COMMENT 'CODEF가 발급한 connectedId',
    env             VARCHAR(20)  NOT NULL COMMENT 'SANDBOX, DEMO, PROD',
    card_info_agreed_at            DATETIME NOT NULL COMMENT '카드정보 조회 동의 시각',
    transaction_history_agreed_at  DATETIME NOT NULL COMMENT '거래내역 조회 동의 시각',
    privacy_agreed_at              DATETIME NOT NULL COMMENT '개인정보 수집 및 이용 동의 시각',
    created_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_codef_connections_user_id (user_id),
    UNIQUE KEY uk_codef_connections_connected_id (connected_id),
    CONSTRAINT fk_codef_connections_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 연동 내에서 기관(카드사)별로 등록한 계정.
CREATE TABLE codef_org_accounts (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    connection_id          BIGINT       NOT NULL,
    policy_id              BIGINT       NULL,
    organization           VARCHAR(10)  NOT NULL COMMENT '카드사 기관코드',
    business_type          VARCHAR(10)  NOT NULL DEFAULT 'CD',
    client_type            VARCHAR(5)   NOT NULL DEFAULT 'P',
    login_type             VARCHAR(5)   NOT NULL COMMENT '0 인증서, 1 아이디',
    registration_status    VARCHAR(30)  NULL COMMENT 'resRegistrationStatus',
    status                 VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, NEEDS_CARD_AUTH, LOCKED, ERROR, DELETED',
    last_error_code        VARCHAR(20)  NULL COMMENT 'CF-12108 등',
    last_synced_at         DATETIME     NULL,
    created_at             DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_codef_org_accounts_connection
        FOREIGN KEY (connection_id) REFERENCES codef_connections (id),
    CONSTRAINT fk_codef_org_accounts_policy
        FOREIGN KEY (policy_id) REFERENCES codef_org_policies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 기관 계정별 동기화(데이터 수집) 작업 이력.
CREATE TABLE codef_sync_jobs (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    org_account_id      BIGINT       NOT NULL,
    api_type            VARCHAR(30)  NOT NULL COMMENT 'CARD_LIST, APPROVAL, RESULT_CHECK, BILLING',
    req_start_date      VARCHAR(8)   NULL COMMENT 'YYYYMMDD',
    req_end_date        VARCHAR(8)   NULL COMMENT 'YYYYMMDD',
    comm_start_date     VARCHAR(8)   NULL COMMENT 'YYYYMMDD, 카드사가 응답한 실제 반영기간 시작',
    comm_end_date       VARCHAR(8)   NULL COMMENT 'YYYYMMDD, 카드사가 응답한 실제 반영기간 종료',
    result_code         VARCHAR(20)  NULL COMMENT 'CF-00000 등',
    transaction_id      VARCHAR(100) NULL,
    fetched_count       INT          NULL,
    inserted_count      INT          NULL,
    updated_count       INT          NULL,
    status               VARCHAR(20) NOT NULL COMMENT 'SUCCESS, FAIL, NO_DATA',
    retryable            BOOLEAN     NOT NULL DEFAULT TRUE COMMENT '잠김 위험 오류는 false',
    started_at           DATETIME    NOT NULL,
    finished_at           DATETIME   NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_codef_sync_jobs_org_account
        FOREIGN KEY (org_account_id) REFERENCES codef_org_accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
