package com.richreach.transaction.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.global.category.SpendingCategory;
import com.richreach.transaction.entity.CardTransaction;
import com.richreach.transaction.entity.TransactionStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 거래를 응답으로 바꾸는 규칙(업종 표시, 금액, 상태)을 DB 없이 확인한다. */
class TransactionResponseTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 5, 12, 30, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 6, 9, 0, 0);

    private static CardTransaction transaction(long amount, long canceled, String category) {
        return CardTransaction.collected(1, 2, "key", "AP-1", AT, "테스트 상점", amount, canceled, 3, true, category, NOW);
    }

    @Test
    @DisplayName("거래의 값을 그대로 담고, 실제 결제 금액과 상태를 계산해서 담는다")
    void mapsFields() {
        TransactionResponse response = TransactionResponse.from(transaction(10_000, 3_000, "CAFE"));

        assertThat(response.cardId()).isEqualTo(2);
        assertThat(response.approvedAt()).isEqualTo(AT);
        assertThat(response.merchantName()).isEqualTo("테스트 상점");
        assertThat(response.amount()).isEqualTo(10_000);
        assertThat(response.canceledAmount()).isEqualTo(3_000);
        assertThat(response.netAmount()).isEqualTo(7_000);
        assertThat(response.status()).isEqualTo(TransactionStatus.PARTIALLY_CANCELED);
        assertThat(response.installmentMonths()).isEqualTo(3);
        assertThat(response.overseas()).isTrue();
    }

    @Test
    @DisplayName("사용자가 고치지 않았으면 자동 분류 값이 업종으로 보이고 고친 것으로 표시하지 않는다")
    void autoCategoryIsShownWhenNotEdited() {
        TransactionResponse response = TransactionResponse.from(transaction(1_000, 0, "CAFE"));

        assertThat(response.category()).isEqualTo(SpendingCategory.CAFE);
        assertThat(response.autoCategory()).isEqualTo(SpendingCategory.CAFE);
        assertThat(response.categoryEdited()).isFalse();
    }

    @Test
    @DisplayName("사용자가 고쳤으면 고친 값이 업종으로 보이고, 자동 분류 값은 따로 남는다")
    void editedCategoryWins() {
        CardTransaction transaction = transaction(1_000, 0, "CAFE");
        transaction.overrideCategory("DINING");

        TransactionResponse response = TransactionResponse.from(transaction);

        assertThat(response.category()).isEqualTo(SpendingCategory.DINING);
        assertThat(response.autoCategory()).isEqualTo(SpendingCategory.CAFE);
        assertThat(response.categoryEdited()).isTrue();
    }

    @Test
    @DisplayName("분류 전(null)이거나 6개 업종이 아닌 값은 ETC로 보인다")
    void unknownCategoryIsEtc() {
        assertThat(TransactionResponse.from(transaction(1_000, 0, null)).category()).isEqualTo(SpendingCategory.ETC);
        assertThat(TransactionResponse.from(transaction(1_000, 0, "TEST_UNKNOWN")).category())
            .isEqualTo(SpendingCategory.ETC);
        assertThat(TransactionResponse.from(transaction(1_000, 0, "TEST_UNKNOWN")).autoCategory())
            .isEqualTo(SpendingCategory.ETC);
    }

    @Test
    @DisplayName("고친 값을 지우면 자동 분류 값으로 돌아간다")
    void resetReturnsToAutoCategory() {
        CardTransaction transaction = transaction(1_000, 0, "CAFE");
        transaction.overrideCategory("DINING");
        transaction.overrideCategory(null);

        TransactionResponse response = TransactionResponse.from(transaction);

        assertThat(response.category()).isEqualTo(SpendingCategory.CAFE);
        assertThat(response.categoryEdited()).isFalse();
    }

}
