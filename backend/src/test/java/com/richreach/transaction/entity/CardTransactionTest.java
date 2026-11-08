package com.richreach.transaction.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CardTransactionTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 5, 12, 30, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 6, 9, 0, 0);
    private static final LocalDateTime LATER = NOW.plusDays(1);

    private static CardTransaction sample() {
        return CardTransaction.collected(1, 2, "key", "AP-1", AT, "테스트 상점", 10_000, 0, 0, false, "TEST_FOOD", NOW);
    }

    @Test
    @DisplayName("취소 금액으로 상태를 정한다: 없으면 승인, 일부면 부분 취소, 전부면 취소")
    void statusFollowsCanceledAmount() {
        assertThat(TransactionStatus.of(10_000, 0)).isEqualTo(TransactionStatus.APPROVED);
        assertThat(TransactionStatus.of(10_000, 1)).isEqualTo(TransactionStatus.PARTIALLY_CANCELED);
        assertThat(TransactionStatus.of(10_000, 9_999)).isEqualTo(TransactionStatus.PARTIALLY_CANCELED);
        assertThat(TransactionStatus.of(10_000, 10_000)).isEqualTo(TransactionStatus.CANCELED);
        assertThat(TransactionStatus.of(0, 0)).isEqualTo(TransactionStatus.APPROVED);
    }

    @Test
    @DisplayName("같은 값으로 갱신하면 false이고 수집 시각도 바꾸지 않는다")
    void unchangedRefreshDoesNothing() {
        CardTransaction transaction = sample();

        boolean changed = transaction.refresh("테스트 상점", 10_000, 0, 0, false, "TEST_FOOD", LATER);

        assertThat(changed).isFalse();
        assertThat(transaction.getCollectedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("바뀐 값으로 갱신하면 true이고 수집 시각과 상태가 바뀐다")
    void changedRefreshUpdatesTimeAndStatus() {
        CardTransaction transaction = sample();

        boolean changed = transaction.refresh("테스트 상점", 10_000, 10_000, 0, false, "TEST_FOOD", LATER);

        assertThat(changed).isTrue();
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.CANCELED);
        assertThat(transaction.getCollectedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("분류가 비어 있는 입력은 기존 자동 분류를 지우지 않는다")
    void nullCategoryKeepsExistingAutoCategory() {
        CardTransaction transaction = sample();

        boolean changed = transaction.refresh("테스트 상점", 10_000, 0, 0, false, null, LATER);

        assertThat(changed).isFalse();
        assertThat(transaction.getCategory()).isEqualTo("TEST_FOOD");
    }

    @Test
    @DisplayName("사용자가 고친 카테고리가 있으면 그 값을 쓰고, 지우면 자동 분류로 돌아간다")
    void overrideTakesPrecedence() {
        CardTransaction transaction = sample();

        transaction.overrideCategory("TEST_TRAVEL");
        assertThat(transaction.effectiveCategory()).isEqualTo("TEST_TRAVEL");

        transaction.overrideCategory(null);
        assertThat(transaction.effectiveCategory()).isEqualTo("TEST_FOOD");
    }

    @Test
    @DisplayName("분류하지 못했고 고치지도 않았다면 카테고리는 null이다")
    void unclassifiedHasNoCategory() {
        CardTransaction transaction = CardTransaction.collected(1, 2, "key", "", AT, "", 0, 0, 0, false, null, NOW);

        assertThat(transaction.effectiveCategory()).isNull();
    }

    @Test
    @DisplayName("할부 개월이 음수면 0(일시불)으로 본다")
    void negativeInstallmentIsLumpSum() {
        CardTransaction transaction = CardTransaction.collected(1, 2, "key", "", AT, "", 1_000, 0, -3, false, null, NOW);

        assertThat(transaction.getInstallmentMonths()).isZero();
    }

}
