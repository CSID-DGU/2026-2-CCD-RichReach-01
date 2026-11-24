-- 월간 업종 합계만으로 건별 최소 결제금액을 판정할 수 없으므로 이 조건을 보존해 계산에서 제외한다.
ALTER TABLE benefit_rules
    ADD COLUMN per_transaction_min_krw BIGINT NULL AFTER amount_basis;

UPDATE benefit_rules b
JOIN card_products c ON c.id = b.card_product_id
JOIN stg_card_benefit s ON s.benefit_id = b.source_benefit_id
    AND s.issuer_code = c.issuer_code AND s.card_id = c.card_id
SET b.per_transaction_min_krw = s.per_transaction_min_krw;
