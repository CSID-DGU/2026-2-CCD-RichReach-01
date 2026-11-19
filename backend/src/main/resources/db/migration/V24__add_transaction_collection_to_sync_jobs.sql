-- 카드 동기화 작업에 승인내역(거래) 수집 결과를 함께 기록한다. (이슈 #232)
--
--  * 카드 목록을 저장한 뒤 같은 작업에서 최근 몇 개월의 승인내역을 수집한다. 작업은 수집이 끝나야 완료된다.
--  * tx_status: SUCCESS(전부 수집), PARTIAL(일부 월만 수집), FAILED(수집 못 함). 카드가 0장이라 수집하지 않았거나
--    수집 기능 이전의 이력이면 NULL이다. 수집이 실패해도 카드 동기화 자체는 성공으로 남는다.
--  * tx_saved_count: 새로 저장하거나 갱신한 거래 수, tx_unmatched_count: 보유카드를 정하지 못해 저장하지 않은 승인내역 수.
--  * tx_collected_from/to: 실제로 수집을 마친 기간(승인일). 일부만 수집했으면 요청한 기간보다 좁다.
ALTER TABLE codef_sync_jobs
    ADD COLUMN tx_status          VARCHAR(20) NULL COMMENT 'SUCCESS, PARTIAL, FAILED. 수집하지 않았으면 NULL',
    ADD COLUMN tx_saved_count     INT         NULL COMMENT '저장하거나 갱신한 거래 수',
    ADD COLUMN tx_unmatched_count INT         NULL COMMENT '보유카드를 정하지 못해 저장하지 않은 승인내역 수',
    ADD COLUMN tx_collected_from  DATE        NULL COMMENT '수집을 마친 기간의 시작일',
    ADD COLUMN tx_collected_to    DATE        NULL COMMENT '수집을 마친 기간의 종료일';
