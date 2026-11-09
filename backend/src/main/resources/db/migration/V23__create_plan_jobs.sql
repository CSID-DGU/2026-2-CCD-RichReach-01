-- 월간 플랜 생성 작업(job). 플랜 생성은 오래 걸릴 수 있어서 요청을 받으면 작업을 기록하고 바로 응답하며,
-- 화면은 job_id로 진행 상태를 조회한다. (이슈 #186)
--
--  * job_id는 화면에 내려가는 UUID다. id는 내부 키다.
--  * 요청한 예산은 작업 실행에 필요해서 함께 저장한다. budget_encoded는 "업종:금액,업종:금액" 형식이고,
--    request_hash는 같은 요청인지 가리는 지문이다. (같은 요청이 다시 오면 같은 작업을 돌려준다 = 멱등성)
--  * 상태는 IN_PROGRESS, COMPLETED, FAILED다. 서버가 작업 도중 멈춰 IN_PROGRESS로 남은 기록은 plan.generation.timeout이 지나면
--    멈춘 것으로 보고 실패로 알린다. 그 기록이 새 요청을 막지 않는다.
--  * 사용자를 지우면 작업도 함께 지운다. 플랜을 지우면 plan_id만 비운다.
CREATE TABLE plan_jobs (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    job_id          CHAR(36)     NOT NULL COMMENT '화면에 내려가는 작업 ID(UUID)',
    user_id         BIGINT       NOT NULL,
    plan_month      CHAR(7)      NOT NULL COMMENT '만들 플랜의 대상 월(yyyy-MM)',
    budget_total    BIGINT       NOT NULL COMMENT '요청한 총예산(원)',
    budget_encoded  VARCHAR(600) NOT NULL COMMENT '요청한 업종별 예산. 업종:금액을 쉼표로 이은 값',
    request_hash    CHAR(64)     NOT NULL COMMENT '요청 내용의 SHA-256. 같은 요청인지 가리는 데 쓴다',
    status          VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS, COMPLETED, FAILED',
    progress        INT          NOT NULL DEFAULT 0 COMMENT '진행률 0~100',
    plan_id         BIGINT       NULL COMMENT '완료되면 사용자에게 보여줄 플랜(monthly_plans)',
    failure_code    VARCHAR(40)  NULL COMMENT '실패 구분 코드',
    failure_reason  VARCHAR(500) NULL COMMENT '실패 이유',
    started_at      DATETIME     NOT NULL,
    finished_at     DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_jobs_job_id (job_id),
    KEY idx_plan_jobs_user_status (user_id, status),
    KEY idx_plan_jobs_user_month (user_id, plan_month),
    CONSTRAINT fk_plan_jobs_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_jobs_plan
        FOREIGN KEY (plan_id) REFERENCES monthly_plans (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
