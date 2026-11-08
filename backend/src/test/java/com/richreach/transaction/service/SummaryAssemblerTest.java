package com.richreach.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.global.category.SpendingCategory;
import com.richreach.transaction.dto.SummaryPeriod;
import com.richreach.transaction.dto.TransactionSummaryResponse;
import com.richreach.transaction.dto.TransactionSummaryResponse.CategorySummary;
import com.richreach.transaction.repository.CategoryTotal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 업종별 합계를 요약 응답으로 조립하는 계산을 DB 없이 확인한다. */
class SummaryAssemblerTest {

    private static final LocalDate TO = LocalDate.of(2026, 11, 9);
    private static final LocalDate FROM = LocalDate.of(2026, 10, 9);

    private static CategoryTotal total(String category, long net, long canceled, long count) {
        return new CategoryTotal(category, net, canceled, count);
    }

    private static TransactionSummaryResponse assemble(LocalDateTime earliest, CategoryTotal... totals) {
        return SummaryAssembler.assemble(SummaryPeriod.ONE_MONTH, FROM, TO, earliest, List.of(totals));
    }

    private static CategorySummary of(TransactionSummaryResponse response, SpendingCategory category) {
        return response.categories().stream().filter(item -> item.category() == category).findFirst().orElseThrow();
    }

    // ---- 업종 ----

    @Test
    @DisplayName("거래가 없어도 6개 업종을 항상 같은 순서로 0으로 내려준다")
    void alwaysReturnsSixCategoriesInOrder() {
        TransactionSummaryResponse response = assemble(null);

        assertThat(response.categories()).extracting(CategorySummary::category).containsExactly(
            SpendingCategory.DINING, SpendingCategory.CAFE, SpendingCategory.SHOPPING, SpendingCategory.TRANSPORT,
            SpendingCategory.CONVENIENCE_STORE, SpendingCategory.ETC);
        assertThat(response.categories()).allSatisfy(item -> {
            assertThat(item.amount()).isZero();
            assertThat(item.ratio()).isZero();
            assertThat(item.transactionCount()).isZero();
        });
        assertThat(response.totalAmount()).isZero();
        assertThat(response.canceledAmount()).isZero();
        assertThat(response.transactionCount()).isZero();
    }

    @Test
    @DisplayName("분류 전(null)이거나 6개 업종이 아닌 값은 ETC로 모은다")
    void unknownCategoriesBecomeEtc() {
        TransactionSummaryResponse response = assemble(null,
            total(null, 1_000, 0, 1),
            total("TEST_UNKNOWN", 2_000, 0, 2),
            total("ETC", 500, 0, 1),
            total("cafe", 4_000, 0, 1));

        assertThat(of(response, SpendingCategory.ETC).amount()).isEqualTo(3_500);
        assertThat(of(response, SpendingCategory.ETC).transactionCount()).isEqualTo(4);
        assertThat(of(response, SpendingCategory.CAFE).amount()).isEqualTo(4_000);
        assertThat(response.totalAmount()).isEqualTo(7_500);
    }

    @Test
    @DisplayName("합계는 업종별 금액, 취소 금액, 거래 수를 모두 더한 값이다")
    void totalsAreSumsOfCategories() {
        TransactionSummaryResponse response = assemble(null,
            total("DINING", 3_000, 500, 2),
            total("CAFE", 1_000, 0, 1),
            total("SHOPPING", 6_000, 4_000, 3));

        assertThat(response.totalAmount()).isEqualTo(10_000);
        assertThat(response.canceledAmount()).isEqualTo(4_500);
        assertThat(response.transactionCount()).isEqualTo(6);
    }

    // ---- 비율 ----

    @Test
    @DisplayName("비율은 합계에서 차지하는 비중(0~1)이다")
    void ratioIsShareOfTotal() {
        TransactionSummaryResponse response = assemble(null,
            total("DINING", 3_000, 0, 1),
            total("CAFE", 1_000, 0, 1));

        assertThat(of(response, SpendingCategory.DINING).ratio()).isEqualTo(0.75);
        assertThat(of(response, SpendingCategory.CAFE).ratio()).isEqualTo(0.25);
        assertThat(of(response, SpendingCategory.SHOPPING).ratio()).isZero();
    }

    @Test
    @DisplayName("비율은 소수 넷째 자리까지 반올림한다 (1/3은 0.3333, 2/3은 0.6667)")
    void ratioIsRoundedToFourDecimals() {
        TransactionSummaryResponse thirds = assemble(null,
            total("DINING", 1, 0, 1), total("CAFE", 1, 0, 1), total("SHOPPING", 1, 0, 1));
        TransactionSummaryResponse twoThirds = assemble(null, total("DINING", 2, 0, 1), total("CAFE", 1, 0, 1));

        assertThat(of(thirds, SpendingCategory.DINING).ratio()).isEqualTo(0.3333);
        assertThat(of(twoThirds, SpendingCategory.DINING).ratio()).isEqualTo(0.6667);
        assertThat(of(twoThirds, SpendingCategory.CAFE).ratio()).isEqualTo(0.3333);
    }

    @Test
    @DisplayName("합계가 0이면 모든 비율은 0이다 (전액 취소된 경우 포함)")
    void zeroTotalHasZeroRatios() {
        TransactionSummaryResponse response = assemble(null, total("DINING", 0, 5_000, 0));

        assertThat(response.categories()).allSatisfy(item -> assertThat(item.ratio()).isZero());
        assertThat(response.canceledAmount()).isEqualTo(5_000);
    }

    // ---- 부분 데이터 ----

    @Test
    @DisplayName("수집된 가장 오래된 거래가 기간 시작일보다 늦으면 부분 데이터다")
    void partialWhenEarliestIsAfterStart() {
        TransactionSummaryResponse response = assemble(FROM.plusDays(5).atTime(12, 0));

        assertThat(response.partial()).isTrue();
        assertThat(response.collectedFrom()).isEqualTo(FROM.plusDays(5));
    }

    @Test
    @DisplayName("가장 오래된 거래가 기간 시작일이거나 더 이전이면 부분 데이터가 아니다")
    void notPartialWhenEarliestCoversTheStart() {
        assertThat(assemble(FROM.atStartOfDay()).partial()).isFalse();
        assertThat(assemble(FROM.minusDays(30).atTime(9, 0)).partial()).isFalse();
    }

    @Test
    @DisplayName("수집된 거래가 없으면 부분 데이터로 표시하지 않고 수집 시작일도 없다")
    void noTransactionsIsNotPartial() {
        TransactionSummaryResponse response = assemble(null);

        assertThat(response.partial()).isFalse();
        assertThat(response.collectedFrom()).isNull();
    }

    // ---- 응답 ----

    @Test
    @DisplayName("기간 값과 시작·종료일을 그대로 담는다")
    void echoesPeriodAndRange() {
        TransactionSummaryResponse response = SummaryAssembler.assemble(SummaryPeriod.THREE_MONTHS,
            LocalDate.of(2026, 8, 9), TO, null, List.of());

        assertThat(response.period()).isEqualTo("3M");
        assertThat(response.from()).isEqualTo(LocalDate.of(2026, 8, 9));
        assertThat(response.to()).isEqualTo(TO);
    }

}
