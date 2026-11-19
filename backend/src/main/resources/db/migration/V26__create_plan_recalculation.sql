-- 승인 거래 반영 후 월간 플랜 재계산. (이슈 #271)
--
--  * 재계산도 플랜 생성처럼 작업(plan_jobs)으로 실행하고 같은 상태 조회(GET /api/plans/jobs/{jobId})를 쓴다. 작업 종류와
--    "한 번 더 실행" 표시, 재계산을 시작한 원인을 컬럼으로 구분한다.
--  * 재계산 중에 거래가 또 들어오면 새 작업을 만들지 않고 rerun_requested만 켠다. 작업은 끝나기 직전에 이 표시를 확인해서
--    켜져 있으면 최신 거래로 한 번 더 계산한다. (중간에 들어온 거래가 반영되지 않고 끝나는 일이 없다)
--  * 재계산 결과는 monthly_plans의 새 version(CP_SAT)으로 쌓고 이전 version은 지우지 않는다. 계산에 실패하면 이전 플랜이 그대로 남는다.
--  * 플랜이 바뀐 내용은 plan_changes(바뀐 시점, 원인, 예상 혜택 전후)와 plan_change_items(업종·카드별 금액 전후, 우선 카드 변경)에
--    남긴다. 홈의 "플랜이 변경됐어요" 안내와 변경 내용 확인창이 읽는다.
--  * 카드 ID는 기록용이라 외래키를 걸지 않는다. (카드를 지워도 변경 기록은 남는다)

ALTER TABLE plan_jobs
    ADD COLUMN job_type        VARCHAR(20) NOT NULL DEFAULT 'GENERATE' COMMENT 'GENERATE(플랜 생성), RECALCULATE(승인 거래 반영 재계산)',
    ADD COLUMN rerun_requested BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '재계산 중에 거래가 또 바뀌어 끝나기 전에 한 번 더 계산해야 하는지',
    ADD COLUMN recalc_reason   VARCHAR(30) NULL COMMENT '재계산을 시작한 원인(TRANSACTION_SYNCED, CATEGORY_EDITED)',
    ADD KEY idx_plan_jobs_user_type_status (user_id, job_type, status);

CREATE TABLE plan_changes (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    plan_month       CHAR(7)      NOT NULL COMMENT '바뀐 플랜의 대상 월(yyyy-MM)',
    plan_id          BIGINT       NOT NULL COMMENT '바뀐 뒤의 플랜(새 version)',
    previous_plan_id BIGINT       NULL COMMENT '바뀌기 전의 플랜',
    reason_code      VARCHAR(30)  NOT NULL COMMENT '변경 원인. TRANSACTION_SYNCED, CATEGORY_EDITED',
    reason_text      VARCHAR(300) NOT NULL COMMENT '사용자에게 보여줄 변경 이유(고정 문구)',
    previous_benefit BIGINT       NOT NULL COMMENT '바뀌기 전 예상 혜택(원)',
    current_benefit  BIGINT       NOT NULL COMMENT '바뀐 뒤 예상 혜택(원)',
    confirmed_at     DATETIME     NULL COMMENT '사용자가 변경 내용을 확인한 시각. NULL이면 아직 확인하지 않음',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_plan_changes_user_month (user_id, plan_month, id),
    CONSTRAINT fk_plan_changes_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_changes_plan
        FOREIGN KEY (plan_id) REFERENCES monthly_plans (id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_changes_previous_plan
        FOREIGN KEY (previous_plan_id) REFERENCES monthly_plans (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE plan_change_items (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    change_id       BIGINT      NOT NULL,
    item_type       VARCHAR(20) NOT NULL COMMENT 'AMOUNT(카드·업종별 목표 금액 변경), PRIORITY(업종의 우선 카드 변경)',
    category        VARCHAR(30) NOT NULL COMMENT 'SpendingCategory 이름',
    user_card_id    BIGINT      NULL COMMENT 'AMOUNT: 금액이 바뀐 카드, PRIORITY: 새로 우선이 된 카드',
    previous_card_id BIGINT     NULL COMMENT 'PRIORITY: 이전에 우선이던 카드',
    previous_amount BIGINT      NOT NULL DEFAULT 0 COMMENT 'AMOUNT: 이전 목표 금액(원), PRIORITY: 이전 우선 카드의 목표 금액',
    current_amount  BIGINT      NOT NULL DEFAULT 0 COMMENT 'AMOUNT: 현재 목표 금액(원), PRIORITY: 새 우선 카드의 목표 금액',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_plan_change_items_change (change_id),
    CONSTRAINT fk_plan_change_items_change
        FOREIGN KEY (change_id) REFERENCES plan_changes (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
