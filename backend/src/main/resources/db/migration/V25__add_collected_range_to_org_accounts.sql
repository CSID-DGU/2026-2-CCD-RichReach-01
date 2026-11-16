-- 카드사 계정별로 승인내역(거래)을 수집한 기간을 기록한다. (이슈 #233)
--
--  * 거래가 없던 기간과 수집하지 못한 기간을 구분하려는 것이다. 지금까지는 가장 오래된 거래의 날짜로 수집 기간을 추정했다.
--  * 수집에 성공한 연속된 구간만 기록한다. 중간에 실패해서 이어지지 않는 구간은 기록하지 않는다.
--  * tx_collected_card_ids는 기록할 때의 보유카드 구성(id를 쉼표로 이은 값)이다. 카드가 새로 생기거나 바뀌면 이미 수집한
--    기간에도 그 카드의 거래가 없을 수 있어서, 구성이 다르면 기록을 믿지 않고 전체를 다시 수집한다.
--  * 모두 NULL이면 아직 수집 기록이 없다는 뜻이다. (수집 기능 이전의 계정)
ALTER TABLE codef_org_accounts
    ADD COLUMN tx_collected_from     DATE         NULL COMMENT '거래 수집을 마친 기간의 시작일(포함)',
    ADD COLUMN tx_collected_to       DATE         NULL COMMENT '거래 수집을 마친 기간의 종료일(포함)',
    ADD COLUMN tx_collected_card_ids VARCHAR(255) NULL COMMENT '기록할 때의 보유카드 id 목록(쉼표로 구분)';
