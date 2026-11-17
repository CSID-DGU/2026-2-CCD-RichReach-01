package com.richreach.plannedexpense.entity;

import com.richreach.global.category.SpendingCategory;
import com.richreach.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.YearMonth;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 사용자가 어떤 달에 미리 알고 있는 지출 한 건. 이름과 금액은 소비 정보라서 {@code toString}을 만들지 않는다.
 */
@Entity
@Table(name = "planned_expenses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlannedExpense extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "plan_month", nullable = false, updatable = false, length = 7, columnDefinition = "CHAR(7)")
    private String planMonth;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** 예상 금액(원). 할부면 전체 금액이다. */
    @Column(name = "amount", nullable = false)
    private long amount;

    /** 할부 개월 수. 일시불이면 null이다. */
    @Column(name = "installment_months")
    private Integer installmentMonths;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "category", nullable = false, length = 30)
    private SpendingCategory category;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "budget_method", nullable = false, length = 20)
    private BudgetMethod budgetMethod;

    public static PlannedExpense create(long userId, YearMonth month, String name, long amount,
                                        Integer installmentMonths, SpendingCategory category,
                                        BudgetMethod budgetMethod) {
        PlannedExpense expense = new PlannedExpense();
        expense.userId = userId;
        expense.planMonth = month.toString();
        expense.change(name, amount, installmentMonths, category, budgetMethod);
        return expense;
    }

    /** 입력 값을 모두 바꾼다. 대상 월은 바꾸지 않는다. */
    public void change(String name, long amount, Integer installmentMonths, SpendingCategory category,
                       BudgetMethod budgetMethod) {
        this.name = name;
        this.amount = amount;
        this.installmentMonths = installmentMonths;
        this.category = category;
        this.budgetMethod = budgetMethod;
    }

    /** 이번 달 예산에 반영되는 금액. 할부면 전체 금액을 개월 수로 나눈 값(원 단위 반올림), 일시불이면 전체 금액이다. */
    public long monthlyAmount() {
        return monthlyAmount(amount, installmentMonths);
    }

    /** {@link #monthlyAmount()}의 계산. 금액은 0보다 크다고 보고 0.5는 올린다. */
    public static long monthlyAmount(long amount, Integer installmentMonths) {
        if (installmentMonths == null || installmentMonths <= 1) {
            return amount;
        }
        return (amount + installmentMonths / 2) / installmentMonths;
    }

}
