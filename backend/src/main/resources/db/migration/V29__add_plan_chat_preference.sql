-- 챗봇 확정 작업의 추천 방향은 재계산에 성공했을 때만 새 플랜에 반영한다.
ALTER TABLE plan_jobs
    ADD COLUMN requested_preference VARCHAR(20) NULL COMMENT '챗봇에서 확정한 추천 방향';

ALTER TABLE monthly_plans
    ADD COLUMN recommendation_preference VARCHAR(20) NULL COMMENT '확정된 추천 방향. NULL은 기존 MONTHLY_PLAN';
