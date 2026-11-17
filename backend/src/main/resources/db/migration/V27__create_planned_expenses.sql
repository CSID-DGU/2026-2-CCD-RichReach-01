-- 예정지출. (이슈 #274)
--
--  * 사용자가 이번 달에 미리 알고 있는 지출(예: 제주도 숙소 300,000원)을 월별로 저장한다.
--  * 반영 방식(budget_method): WITHIN_BUDGET(총예산은 그대로 두고 다른 업종 예산을 줄임), EXTRA(이번 달 총예산에 더함).
--  * 할부(installment_months)면 amount는 전체 금액이고, 이번 달 플랜에는 금액을 개월 수로 나눈 값(반올림)이 반영된다.
--    NULL이면 일시불이다.
--  * 이름과 금액은 소비 정보라서 로그에 남기지 않는다. 사용자를 지우면 함께 지운다.

CREATE TABLE planned_expenses (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    user_id            BIGINT       NOT NULL,
    plan_month         CHAR(7)      NOT NULL COMMENT '예정지출이 반영되는 월(yyyy-MM)',
    name               VARCHAR(50)  NOT NULL COMMENT '사용자가 적은 이름',
    amount             BIGINT       NOT NULL COMMENT '예상 금액(원). 할부면 전체 금액',
    installment_months INT          NULL COMMENT '할부 개월 수(2, 3, 6, 12). NULL이면 일시불',
    category           VARCHAR(30)  NOT NULL COMMENT 'SpendingCategory 이름',
    budget_method      VARCHAR(20)  NOT NULL COMMENT 'WITHIN_BUDGET(기존 예산 안에서 조정), EXTRA(예산 밖 추가 지출)',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_planned_expenses_user_month (user_id, plan_month, id),
    CONSTRAINT fk_planned_expenses_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
