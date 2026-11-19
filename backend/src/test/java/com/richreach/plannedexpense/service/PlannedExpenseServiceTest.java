package com.richreach.plannedexpense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.global.category.SpendingCategory;
import com.richreach.global.event.PlannedExpensesChangedEvent;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.plannedexpense.dto.PlannedExpenseRequest;
import com.richreach.plannedexpense.dto.PlannedExpenseResponse;
import com.richreach.plannedexpense.dto.PlannedExpensesResponse;
import com.richreach.plannedexpense.entity.BudgetMethod;
import com.richreach.plannedexpense.entity.PlannedExpense;
import com.richreach.plannedexpense.repository.PlannedExpenseRepository;
import com.richreach.user.service.UserLockService;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** 예정지출의 입력 검증, 이번 달 규칙, 개수 제한, 변경 알림을 DB 없이 확인한다. */
class PlannedExpenseServiceTest {

    private static final long USER = 1L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final YearMonth MONTH = YearMonth.of(2026, 11);

    private PlannedExpenseRepository repository;
    private UserLockService lock;
    private ApplicationEventPublisher events;
    private PlannedExpenseService service;

    @BeforeEach
    void setUp() {
        repository = mock(PlannedExpenseRepository.class);
        lock = mock(UserLockService.class);
        events = mock(ApplicationEventPublisher.class);
        service = new PlannedExpenseService(repository, lock, events, CLOCK);
        when(repository.save(any(PlannedExpense.class))).thenAnswer(call -> {
            PlannedExpense saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });
    }

    private static PlannedExpenseRequest request(String name, Long amount, Integer installment, String category,
                                                 String method) {
        return new PlannedExpenseRequest(name, amount, installment, category, method);
    }

    private static PlannedExpenseRequest valid() {
        return request("제주도 숙소", 300_000L, null, "culture_travel", "EXTRA");
    }

    private static PlannedExpense stored(String month) {
        PlannedExpense expense = PlannedExpense.create(USER, YearMonth.parse(month), "건강검진", 80_000, null,
            SpendingCategory.LIFE_HEALTH, BudgetMethod.WITHIN_BUDGET);
        ReflectionTestUtils.setField(expense, "id", 9L);
        return expense;
    }

    private void assertInvalid(PlannedExpenseRequest request) {
        assertThatThrownBy(() -> service.create(USER, request)).isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_EXPENSE);
        verify(repository, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("추가하면 이번 달로 저장하고 응답을 돌려주며 변경을 알린다")
    void createStoresForThisMonth() {
        PlannedExpenseResponse response = service.create(USER, valid());

        assertThat(response.expenseId()).isEqualTo(7L);
        assertThat(response.month()).isEqualTo("2026-11");
        assertThat(response.category()).isEqualTo("CULTURE_TRAVEL");
        assertThat(response.budgetMethod()).isEqualTo("EXTRA");
        assertThat(response.monthlyAmount()).isEqualTo(300_000);
        verify(lock).lock(USER);
        verify(events).publishEvent(new PlannedExpensesChangedEvent(USER, MONTH));
    }

    @Test
    @DisplayName("반영 방식을 생략하면 기존 예산 안에서 조정이고, 이름 앞뒤 공백은 뺀다")
    void defaultsAndTrims() {
        PlannedExpenseResponse response = service.create(USER, request("  친구 생일선물  ", 130_000L, null, "SHOPPING",
            null));

        assertThat(response.name()).isEqualTo("친구 생일선물");
        assertThat(response.budgetMethod()).isEqualTo("WITHIN_BUDGET");
    }

    @Test
    @DisplayName("할부면 이번 달 반영 금액은 전체 금액을 개월 수로 나눈 값을 반올림한 금액이다")
    void installmentMonthlyAmount() {
        assertThat(service.create(USER, request("노트북", 1_000_000L, 12, "SHOPPING", null)).monthlyAmount())
            .isEqualTo(83_333);
        assertThat(service.create(USER, request("노트북", 1_000_010L, 12, "SHOPPING", null)).monthlyAmount())
            .isEqualTo(83_334);
        assertThat(service.create(USER, request("침대", 100_000L, 6, "SHOPPING", null)).monthlyAmount())
            .isEqualTo(16_667);
    }

    @Test
    @DisplayName("이름·금액·할부·업종·반영 방식이 올바르지 않으면 EXPENSE-002이다")
    void rejectsInvalidInput() {
        assertInvalid(request(null, 1000L, null, "ETC", null));
        assertInvalid(request("   ", 1000L, null, "ETC", null));
        assertInvalid(request("가".repeat(31), 1000L, null, "ETC", null));
        assertInvalid(request("이름", null, null, "ETC", null));
        assertInvalid(request("이름", 0L, null, "ETC", null));
        assertInvalid(request("이름", -5L, null, "ETC", null));
        assertInvalid(request("이름", 10_000_000_001L, null, "ETC", null));
        assertInvalid(request("이름", 1000L, 1, "ETC", null));
        assertInvalid(request("이름", 1000L, 4, "ETC", null));
        assertInvalid(request("이름", 1000L, null, "NOT_A_CATEGORY", null));
        assertInvalid(request("이름", 1000L, null, null, null));
        assertInvalid(request("이름", 1000L, null, "ETC", "SOMETIMES"));
        assertInvalid(null);
    }

    @Test
    @DisplayName("한 달에 50개를 넘으면 EXPENSE-003이다")
    void limitsTheCountPerMonth() {
        when(repository.countByUserIdAndPlanMonth(USER, "2026-11")).thenReturn(50L);

        assertThatThrownBy(() -> service.create(USER, valid())).isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.EXPENSE_LIMIT_EXCEEDED);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("수정하면 값을 바꾸고 변경을 알린다")
    void updateChangesTheValues() {
        PlannedExpense expense = stored("2026-11");
        when(repository.findByIdAndUserId(9L, USER)).thenReturn(Optional.of(expense));

        PlannedExpenseResponse response = service.update(USER, 9L, request("치과", 200_000L, 3, "LIFE_HEALTH",
            "EXTRA"));

        assertThat(response.name()).isEqualTo("치과");
        assertThat(response.amount()).isEqualTo(200_000);
        assertThat(response.installmentMonths()).isEqualTo(3);
        assertThat(response.monthlyAmount()).isEqualTo(66_667);
        assertThat(response.budgetMethod()).isEqualTo("EXTRA");
        verify(events).publishEvent(new PlannedExpensesChangedEvent(USER, MONTH));
    }

    @Test
    @DisplayName("삭제하면 지우고 변경을 알린다")
    void deleteRemoves() {
        PlannedExpense expense = stored("2026-11");
        when(repository.findByIdAndUserId(9L, USER)).thenReturn(Optional.of(expense));

        service.delete(USER, 9L);

        verify(repository).delete(expense);
        verify(events).publishEvent(new PlannedExpensesChangedEvent(USER, MONTH));
    }

    @Test
    @DisplayName("없거나 다른 사용자의 예정지출은 EXPENSE-001이다")
    void notFound() {
        when(repository.findByIdAndUserId(9L, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(USER, 9L, valid())).isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.EXPENSE_NOT_FOUND);
        assertThatThrownBy(() -> service.delete(USER, 9L)).isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.EXPENSE_NOT_FOUND);
    }

    @Test
    @DisplayName("지난 달 예정지출은 고치거나 지울 수 없다")
    void pastMonthIsReadOnly() {
        when(repository.findByIdAndUserId(9L, USER)).thenReturn(Optional.of(stored("2026-10")));

        assertThatThrownBy(() -> service.update(USER, 9L, valid())).isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_EXPENSE);
        assertThatThrownBy(() -> service.delete(USER, 9L)).isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_EXPENSE);
        verify(repository, never()).delete(any(PlannedExpense.class));
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("목록은 반영 방식별 합계를 이번 달 반영 금액(할부면 월 금액)으로 더한다")
    void listSummarizesByMethod() {
        PlannedExpense within = stored("2026-11");
        PlannedExpense extra = PlannedExpense.create(USER, MONTH, "제주도", 300_000, 3, SpendingCategory.CULTURE_TRAVEL,
            BudgetMethod.EXTRA);
        ReflectionTestUtils.setField(extra, "id", 10L);
        when(repository.findByUserIdAndPlanMonthOrderByIdDesc(USER, "2026-11")).thenReturn(List.of(extra, within));

        PlannedExpensesResponse response = service.list(USER, null);

        assertThat(response.month()).isEqualTo("2026-11");
        assertThat(response.expenses()).hasSize(2);
        assertThat(response.summary().withinBudget()).isEqualTo(80_000);
        assertThat(response.summary().extra()).isEqualTo(100_000);
        assertThat(response.summary().total()).isEqualTo(180_000);
    }

    @Test
    @DisplayName("월 형식이 yyyy-MM이 아니면 COMMON-400이다")
    void listRejectsBadMonth() {
        assertThatThrownBy(() -> service.list(USER, "2026-13")).isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> service.list(USER, "26-11")).isInstanceOf(BusinessException.class);
    }

}
