package com.richreach.plannedexpense.service;

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
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이번 달 예정지출의 추가·조회·수정·삭제. 추가·수정·삭제하면 플랜 쪽이 남은 기간을 다시 계산하도록 알린다. (커밋된 뒤에 처리된다)
 *
 * <ul>
 *   <li>예정지출은 <b>이번 달</b>에만 추가하고, 이번 달 것만 수정·삭제할 수 있다. 지난 달 것은 조회만 된다. 다른 달이면
 *       {@code EXPENSE-002}다.</li>
 *   <li>입력 검증을 어기면 {@code EXPENSE-002}, 없거나 다른 사용자의 예정지출이면 {@code EXPENSE-001}, 한 달에
 *       {@value #MAX_PER_MONTH}개를 넘으면 {@code EXPENSE-003}이다.</li>
 *   <li>같은 사용자의 동시 요청에서 개수 제한이 지켜지도록 사용자 행을 잠근 뒤 센다.</li>
 * </ul>
 * 이름과 금액은 소비 정보라서 로그에 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PlannedExpenseService {

    /** 한 달에 등록할 수 있는 예정지출 개수. */
    static final int MAX_PER_MONTH = 50;
    static final int MAX_NAME_LENGTH = 30;
    /** 한 건의 금액 상한(원). 비정상적으로 큰 값이 계산을 넘치게 하지 않게 막는다. */
    static final long MAX_AMOUNT = 10_000_000_000L;
    static final Set<Integer> INSTALLMENT_OPTIONS = Set.of(2, 3, 6, 12);

    /** 네 자리 연도와 두 자리 월. */
    private static final Pattern MONTH_FORMAT = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    private final PlannedExpenseRepository expenses;
    private final UserLockService userLock;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** 그 달의 예정지출 목록. 월을 생략하면 이번 달이다. 월이 {@code yyyy-MM}이 아니면 {@code COMMON-400}이다. */
    @Transactional(readOnly = true)
    public PlannedExpensesResponse list(long userId, String month) {
        YearMonth target = month == null || month.isBlank() ? YearMonth.now(clock) : parseMonth(month);
        return PlannedExpensesResponse.of(target.toString(), expenses
            .findByUserIdAndPlanMonthOrderByIdDesc(userId, target.toString()).stream()
            .map(PlannedExpenseResponse::from).toList());
    }

    @Transactional
    public PlannedExpenseResponse create(long userId, PlannedExpenseRequest request) {
        Input input = Input.parse(request);
        YearMonth month = YearMonth.now(clock);
        userLock.lock(userId);
        if (expenses.countByUserIdAndPlanMonth(userId, month.toString()) >= MAX_PER_MONTH) {
            throw new BusinessException(ErrorCode.EXPENSE_LIMIT_EXCEEDED);
        }
        PlannedExpense expense = expenses.save(PlannedExpense.create(userId, month, input.name(), input.amount(),
            input.installmentMonths(), input.category(), input.budgetMethod()));
        publishChanged(userId, month);
        return PlannedExpenseResponse.from(expense);
    }

    @Transactional
    public PlannedExpenseResponse update(long userId, long expenseId, PlannedExpenseRequest request) {
        Input input = Input.parse(request);
        PlannedExpense expense = findThisMonth(userId, expenseId);
        expense.change(input.name(), input.amount(), input.installmentMonths(), input.category(),
            input.budgetMethod());
        publishChanged(userId, YearMonth.parse(expense.getPlanMonth()));
        return PlannedExpenseResponse.from(expense);
    }

    @Transactional
    public void delete(long userId, long expenseId) {
        PlannedExpense expense = findThisMonth(userId, expenseId);
        expenses.delete(expense);
        publishChanged(userId, YearMonth.parse(expense.getPlanMonth()));
    }

    /** 플랜 재계산이 예산에 반영할 그 달의 예정지출. (이번 달 플랜을 계산할 때 쓴다) */
    @Transactional(readOnly = true)
    public List<PlannedExpenseResponse> listForBudget(long userId, YearMonth month) {
        return expenses.findByUserIdAndPlanMonthOrderByIdDesc(userId, month.toString()).stream()
            .map(PlannedExpenseResponse::from).toList();
    }

    private PlannedExpense findThisMonth(long userId, long expenseId) {
        PlannedExpense expense = expenses.findByIdAndUserId(expenseId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_NOT_FOUND));
        if (!expense.getPlanMonth().equals(YearMonth.now(clock).toString())) {
            throw new BusinessException(ErrorCode.INVALID_EXPENSE, "이번 달 예정지출만 수정하거나 삭제할 수 있습니다.");
        }
        return expense;
    }

    private void publishChanged(long userId, YearMonth month) {
        events.publishEvent(new PlannedExpensesChangedEvent(userId, month));
    }

    private static YearMonth parseMonth(String month) {
        String normalized = month.strip();
        if (!MONTH_FORMAT.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "월은 yyyy-MM 형식이어야 합니다.");
        }
        try {
            return YearMonth.parse(normalized);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "월은 yyyy-MM 형식이어야 합니다.");
        }
    }

    /** 검증을 마친 입력. */
    private record Input(String name, long amount, Integer installmentMonths, SpendingCategory category,
                         BudgetMethod budgetMethod) {

        static Input parse(PlannedExpenseRequest request) {
            if (request == null) {
                throw invalid("요청 본문이 필요합니다.");
            }
            String name = request.name() == null ? "" : request.name().strip();
            if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
                throw invalid("이름은 1자 이상 " + MAX_NAME_LENGTH + "자 이하여야 합니다.");
            }
            if (request.amount() == null || request.amount() <= 0 || request.amount() > MAX_AMOUNT) {
                throw invalid("금액은 0보다 크고 " + MAX_AMOUNT + "원 이하여야 합니다.");
            }
            Integer installment = request.installmentMonths();
            if (installment != null && !INSTALLMENT_OPTIONS.contains(installment)) {
                throw invalid("할부 개월 수는 2, 3, 6, 12 중 하나여야 합니다.");
            }
            SpendingCategory category = SpendingCategory.fromCode(request.category())
                .orElseThrow(() -> invalid("지원하지 않는 업종입니다."));
            BudgetMethod method = request.budgetMethod() == null || request.budgetMethod().isBlank()
                ? BudgetMethod.WITHIN_BUDGET
                : BudgetMethod.fromCode(request.budgetMethod())
                    .orElseThrow(() -> invalid("반영 방식은 WITHIN_BUDGET 또는 EXTRA여야 합니다."));
            return new Input(name, request.amount(), installment, category, method);
        }

        private static BusinessException invalid(String message) {
            return new BusinessException(ErrorCode.INVALID_EXPENSE, message);
        }

    }

}
