-- A per-payment discount ceiling is distinct from a monthly benefit wallet.
ALTER TABLE benefit_rules
    ADD COLUMN per_transaction_max_krw BIGINT NULL AFTER per_transaction_min_krw;
