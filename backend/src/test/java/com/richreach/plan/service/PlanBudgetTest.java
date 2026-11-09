package com.richreach.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.plan.dto.PlanCreateRequest;
import com.richreach.plan.dto.PlanCreateRequest.Budget;
import com.richreach.plan.dto.PlanCreateRequest.CategoryBudget;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 플랜 생성 요청의 예산 검증과 지문, 저장 형식을 DB 없이 확인한다. */
class PlanBudgetTest {

    private static CategoryBudget item(String category, Long amount) {
        return new CategoryBudget(category, amount);
    }

    private static PlanCreateRequest request(String month, Long total, CategoryBudget... items) {
        return new PlanCreateRequest(month, new Budget(total, List.of(items)));
    }

    private static PlanCreateRequest valid() {
        return request("2026-12", 400_000L, item("DINING", 300_000L), item("CAFE", 100_000L));
    }

    private static void assertInvalidBudget(PlanCreateRequest request) {
        assertThatThrownBy(() -> PlanBudget.parse(request)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_BUDGET));
    }

    // ---- 올바른 요청 ----

    @Test
    @DisplayName("올바른 요청은 월, 총예산, 업종별 예산으로 파싱한다 (업종은 대소문자 무시)")
    void parsesValidRequest() {
        PlanBudget budget = PlanBudget.parse(request("2026-12", 400_000L, item("dining", 300_000L), item(" Cafe ", 100_000L)));

        assertThat(budget.month()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(budget.total()).isEqualTo(400_000);
        assertThat(budget.byCategory()).containsOnly(
            org.assertj.core.api.Assertions.entry(SpendingCategory.DINING, 300_000L),
            org.assertj.core.api.Assertions.entry(SpendingCategory.CAFE, 100_000L));
    }

    @Test
    @DisplayName("금액이 0인 업종은 뺀다")
    void dropsZeroAmountCategories() {
        PlanBudget budget = PlanBudget.parse(request("2026-12", 100_000L, item("DINING", 100_000L), item("CAFE", 0L)));

        assertThat(budget.byCategory()).containsOnlyKeys(SpendingCategory.DINING);
    }

    // ---- 월 ----

    @Test
    @DisplayName("월이 yyyy-MM 형식이 아니면 COMMON-400이다")
    void rejectsInvalidMonth() {
        for (String month : new String[] {null, "", " ", "2026-13", "2026/12", "202612", "12월", "2026-12-01",
            "+10000-01", "-0001-01", "10000-01", "2026-00", "2026-1", "٢٠٢٦-١٢"}) {
            assertThatThrownBy(() -> PlanBudget.parse(request(month, 400_000L, item("DINING", 400_000L))))
                .as("month=%s", month).isInstanceOfSatisfying(BusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        }
    }

    @Test
    @DisplayName("요청 본문이 없으면 COMMON-400이다")
    void rejectsMissingBody() {
        assertThatThrownBy(() -> PlanBudget.parse(null)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    // ---- 예산 ----

    @Test
    @DisplayName("예산이 없거나 비어 있으면 PLAN-003이다")
    void rejectsMissingBudget() {
        assertInvalidBudget(new PlanCreateRequest("2026-12", null));
        assertInvalidBudget(new PlanCreateRequest("2026-12", new Budget(null, List.of(item("DINING", 1L)))));
        assertInvalidBudget(new PlanCreateRequest("2026-12", new Budget(1L, null)));
        assertInvalidBudget(new PlanCreateRequest("2026-12", new Budget(1L, List.of())));
    }

    @Test
    @DisplayName("총예산은 0보다 크고 상한 이하여야 한다")
    void rejectsTotalOutOfRange() {
        assertInvalidBudget(request("2026-12", 0L, item("DINING", 0L)));
        assertInvalidBudget(request("2026-12", -1L, item("DINING", -1L)));
        assertInvalidBudget(request("2026-12", PlanBudget.MAX_TOTAL + 1, item("DINING", PlanBudget.MAX_TOTAL + 1)));
        assertThat(PlanBudget.parse(request("2026-12", PlanBudget.MAX_TOTAL, item("DINING", PlanBudget.MAX_TOTAL)))
            .total()).isEqualTo(PlanBudget.MAX_TOTAL);
    }

    @Test
    @DisplayName("알 수 없는 업종, 비어 있는 업종·금액, 음수 금액은 PLAN-003이다")
    void rejectsInvalidCategoryItems() {
        assertInvalidBudget(request("2026-12", 100L, item("UNKNOWN", 100L)));
        assertInvalidBudget(request("2026-12", 100L, item(null, 100L)));
        assertInvalidBudget(request("2026-12", 100L, item("DINING", null)));
        assertInvalidBudget(request("2026-12", 100L, item("DINING", 200L), item("CAFE", -100L)));
        assertInvalidBudget(new PlanCreateRequest("2026-12", new Budget(100L, java.util.Arrays.asList(item("DINING", 100L), null))));
    }

    @Test
    @DisplayName("같은 업종이 두 번 나오면 (대소문자가 달라도, 0원이어도) PLAN-003이다")
    void rejectsDuplicateCategories() {
        assertInvalidBudget(request("2026-12", 200L, item("DINING", 100L), item("dining", 100L)));
        assertInvalidBudget(request("2026-12", 100L, item("DINING", 100L), item("DINING", 0L)));
    }

    @Test
    @DisplayName("업종 예산의 합이 총예산과 다르면 PLAN-003이다 (모자라도, 넘쳐도)")
    void rejectsSumMismatch() {
        assertInvalidBudget(request("2026-12", 400_000L, item("DINING", 300_000L), item("CAFE", 99_999L)));
        assertInvalidBudget(request("2026-12", 400_000L, item("DINING", 300_000L), item("CAFE", 100_001L)));
    }

    // ---- 저장 형식과 지문 ----

    @Test
    @DisplayName("저장 형식으로 바꿨다가 되살리면 같은 예산이다")
    void encodeAndDecodeRoundTrip() {
        PlanBudget budget = PlanBudget.parse(valid());

        PlanBudget decoded = PlanBudget.decode(budget.month(), budget.total(), budget.encode());

        assertThat(decoded).isEqualTo(budget);
        assertThat(budget.encode()).isEqualTo("DINING:300000,CAFE:100000");
    }

    @Test
    @DisplayName("저장된 값이 깨져 있으면 되살릴 때 오류다")
    void decodeRejectsBrokenValue() {
        assertThatThrownBy(() -> PlanBudget.decode(YearMonth.of(2026, 12), 1L, "NOT_A_CATEGORY:100"))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PlanBudget.decode(YearMonth.of(2026, 12), 1L, "DINING"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("같은 요청은 지문이 같고, 업종 순서나 0원 항목은 영향을 주지 않는다")
    void sameRequestHasSameFingerprint() {
        PlanBudget first = PlanBudget.parse(valid());
        PlanBudget reordered = PlanBudget.parse(request("2026-12", 400_000L, item("CAFE", 100_000L),
            item("DINING", 300_000L), item("ETC", 0L)));

        assertThat(reordered.fingerprint()).isEqualTo(first.fingerprint());
        assertThat(first.fingerprint()).hasSize(64);
    }

    @Test
    @DisplayName("월이나 금액이 다르면 지문이 다르다")
    void differentRequestHasDifferentFingerprint() {
        String base = PlanBudget.parse(valid()).fingerprint();

        assertThat(PlanBudget.parse(request("2027-01", 400_000L, item("DINING", 300_000L), item("CAFE", 100_000L)))
            .fingerprint()).isNotEqualTo(base);
        assertThat(PlanBudget.parse(request("2026-12", 400_000L, item("DINING", 310_000L), item("CAFE", 90_000L)))
            .fingerprint()).isNotEqualTo(base);
    }

}
