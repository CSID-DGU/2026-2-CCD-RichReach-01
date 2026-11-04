-- 한 번의 동기화 요청(여러 카드사)을 하나로 묶는 ID. 화면이 상태를 조회할 때 쓰는 jobId다.
-- 이미 쌓인 작업 이력은 묶음이 없으므로 NULL로 둔다.
ALTER TABLE codef_sync_jobs
    ADD COLUMN batch_id VARCHAR(36) NULL COMMENT '한 번의 동기화 요청을 묶는 ID(UUID)' AFTER org_account_id,
    ADD INDEX idx_codef_sync_jobs_batch_id (batch_id);
