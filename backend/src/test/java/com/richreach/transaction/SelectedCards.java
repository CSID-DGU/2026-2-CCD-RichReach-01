package com.richreach.transaction;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 거래 API 테스트용. 보유카드를 카드 상품에 매칭하고 분석 대상으로 고른다.
 * (거래 요약·목록은 기본으로 분석 대상 카드만 보는데, 동기화로 만든 테스트 카드는 상품에 매칭되지 않아 분석 대상이 아니다.)
 */
public final class SelectedCards {

    /** 테스트가 만든 카드 상품의 card_id 접두사. 정리할 때 이 접두사로 지운다. */
    public static final String PRODUCT_PREFIX = "test-txn-";

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private SelectedCards() {
    }

    public static void select(JdbcTemplate jdbc, long userCardId) {
        String productCode = PRODUCT_PREFIX + SEQUENCE.incrementAndGet();
        jdbc.update("""
            insert into card_products (issuer_code, issuer, card_id, card_name, card_type, card_status, product_class,
                recommendable, source_snapshot_date, source_text_sha256, review_status)
            values ('kb', '테스트카드사', ?, '거래 테스트 상품', 'credit', 'listed', 'PAYMENT_CARD', true, '2026-10-01', ?,
                'AUTO_EXTRACTED_NEEDS_REVIEW')
            """, productCode, "0".repeat(64));
        jdbc.update("""
            update user_cards set card_product_id = (select id from card_products where card_id = ?), selected = true
            where id = ?
            """, productCode, userCardId);
    }

    /** 테스트가 만든 카드 상품을 지운다. 보유카드를 먼저 지운 뒤에 부른다. */
    public static void cleanUp(JdbcTemplate jdbc) {
        jdbc.update("delete from card_products where card_id like ?", PRODUCT_PREFIX + "%");
    }

}
