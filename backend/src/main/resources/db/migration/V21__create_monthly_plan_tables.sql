-- 월간 카드 사용 플랜. 소비 프로필(예상 소비) -> 플랜 -> 카드별 배분 순으로 잇는다. (이슈 #155, ERD의 플랜 영역)
-- 사용자·보유카드는 다른 도메인이라 JPA 연관 대신 ID 컬럼으로만 잇는다.
--
-- 기본 설계 결정
--  * 금액은 원 단위 정수(BIGINT)다. (거래 테이블 card_transactions와 같다)
--  * ERD의 categories 테이블은 없어서, 카테고리는 거래처럼 SpendingCategory 이름을 문자열로 저장한다.
--  * 같은 사용자·월·전략의 플랜은 version으로 구분한다. 월중에 다시 계산하면 version을 올려 새 행으로 쌓는다.
--  * 사용자나 보유카드를 지우면 관련 행도 함께 지운다.
--  * 플랜 평가(plan_evaluations)는 평가 단계 구현 때 만든다.

-- 예상 소비 프로필: 최근 소비를 바탕으로 한 대상 월의 예상 소비
CREATE TABLE spending_profiles (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    target_month  CHAR(7)     NOT NULL COMMENT '예측 대상 월(yyyy-MM)',
    source_start  DATE        NOT NULL COMMENT '근거로 쓴 소비 기간 시작일(최근 2개월)',
    source_end    DATE        NOT NULL COMMENT '근거로 쓴 소비 기간 종료일',
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_spending_profiles_user_month (user_id, target_month),
    CONSTRAINT fk_spending_profiles_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 프로필의 카테고리별 예상 소비
CREATE TABLE spending_profile_items (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    profile_id       BIGINT        NOT NULL,
    category         VARCHAR(30)   NOT NULL COMMENT 'SpendingCategory 이름',
    expected_amount  BIGINT        NOT NULL COMMENT '예상 소비 금액(원). 0 이상',
    expected_count   INT           NOT NULL DEFAULT 0 COMMENT '예상 소비 횟수(빈도). 0 이상',
    occurrence_prob  DECIMAL(5, 4) NOT NULL DEFAULT 1 COMMENT '발생 확률 0~1',
    confidence       DECIMAL(5, 4) NOT NULL DEFAULT 1 COMMENT '신뢰도 0~1. 낮으면 폴백 판단에 쓴다',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_spending_profile_items_category (profile_id, category),
    CONSTRAINT fk_spending_profile_items_profile
        FOREIGN KEY (profile_id) REFERENCES spending_profiles (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 월간 플랜
CREATE TABLE monthly_plans (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    profile_id       BIGINT       NOT NULL COMMENT '플랜의 입력이 된 소비 프로필',
    plan_month       CHAR(7)      NOT NULL COMMENT '플랜 대상 월(yyyy-MM)',
    strategy         VARCHAR(20)  NOT NULL COMMENT 'HABIT(기존 습관), GREEDY, CP_SAT',
    version          INT          NOT NULL DEFAULT 1 COMMENT '월중 재계산 때 증가',
    total_spend      BIGINT       NOT NULL DEFAULT 0 COMMENT '플랜의 총 소비(원). 전략이 달라도 같아야 한다(총소비 불변 검증)',
    expected_benefit BIGINT       NOT NULL DEFAULT 0 COMMENT '플랜의 예상 혜택 합계(원)',
    solver_status    VARCHAR(20)  NULL COMMENT 'OPTIMAL, FEASIBLE, INFEASIBLE. 최적화를 쓰지 않는 전략은 NULL',
    solve_time_ms    INT          NULL COMMENT '계산 시간(ms)',
    failure_reason   VARCHAR(500) NULL COMMENT '조건 충돌 등으로 실패한 원인',
    llm_explanation  TEXT         NULL COMMENT '추천 이유 설명',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_monthly_plans_user_month_strategy_version (user_id, plan_month, strategy, version),
    KEY idx_monthly_plans_profile (profile_id),
    CONSTRAINT fk_monthly_plans_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_monthly_plans_profile
        FOREIGN KEY (profile_id) REFERENCES spending_profiles (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 플랜의 카드별·카테고리별 배분: 어느 카드로 어느 카테고리를 얼마 쓸지
CREATE TABLE plan_card_allocations (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    plan_id          BIGINT      NOT NULL,
    user_card_id     BIGINT      NOT NULL COMMENT '배분 대상 보유카드(user_cards)',
    category         VARCHAR(30) NOT NULL COMMENT 'SpendingCategory 이름',
    target_amount    BIGINT      NOT NULL COMMENT '카드별 목표 사용액(원). 0 이상',
    expected_benefit BIGINT      NOT NULL DEFAULT 0 COMMENT '이 배분의 예상 혜택(원). 0 이상',
    created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_card_allocations (plan_id, user_card_id, category),
    KEY idx_plan_card_allocations_card (user_card_id),
    CONSTRAINT fk_plan_card_allocations_plan
        FOREIGN KEY (plan_id) REFERENCES monthly_plans (id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_card_allocations_user_card
        FOREIGN KEY (user_card_id) REFERENCES user_cards (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
