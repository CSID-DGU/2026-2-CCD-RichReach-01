package com.richreach.plan.entity;

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
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 월간 카드 사용 플랜. 소비 정보라서 {@code toString}을 만들지 않는다.
 * 사용자는 다른 도메인이라 ID만 가진다. 같은 사용자·월·전략은 {@code version}으로 구분한다.
 */
@Entity
@DynamicUpdate
@Table(name = "monthly_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MonthlyPlan extends BaseEntity {

    /** monthly_plans.failure_reason의 컬럼 길이(V21)와 같아야 한다. */
    public static final int FAILURE_REASON_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "profile_id", nullable = false, updatable = false)
    private Long profileId;

    /** 플랜 대상 월(yyyy-MM) */
    @Column(name = "plan_month", nullable = false, updatable = false, length = 7, columnDefinition = "CHAR(7)")
    private String planMonth;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false, length = 20)
    private PlanStrategy strategy;

    @Column(nullable = false, updatable = false)
    private int version;

    /** 총 소비(원) */
    @Column(name = "total_spend", nullable = false)
    private long totalSpend;

    /** 예상 혜택 합계(원) */
    @Column(name = "expected_benefit", nullable = false)
    private long expectedBenefit;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "solver_status", length = 20)
    private SolverStatus solverStatus;

    @Column(name = "solve_time_ms")
    private Integer solveTimeMs;

    @Column(name = "failure_reason", length = FAILURE_REASON_MAX_LENGTH)
    private String failureReason;

    @Column(name = "llm_explanation", columnDefinition = "TEXT")
    private String llmExplanation;

    public static MonthlyPlan create(Long userId, Long profileId, YearMonth planMonth, PlanStrategy strategy,
                                     int version, long totalSpend, long expectedBenefit) {
        if (version < 1) {
            throw new IllegalArgumentException("version은 1 이상이어야 합니다.");
        }
        if (totalSpend < 0 || expectedBenefit < 0) {
            throw new IllegalArgumentException("금액은 0 이상이어야 합니다.");
        }
        MonthlyPlan plan = new MonthlyPlan();
        plan.userId = userId;
        plan.profileId = profileId;
        plan.planMonth = planMonth.toString();
        plan.strategy = strategy;
        plan.version = version;
        plan.totalSpend = totalSpend;
        plan.expectedBenefit = expectedBenefit;
        return plan;
    }

    /** 최적화 계산 결과를 기록한다. */
    public void recordSolve(SolverStatus status, Integer solveTimeMs) {
        this.solverStatus = status;
        this.solveTimeMs = solveTimeMs;
    }

    /** 계산 실패 원인을 기록한다. 길이 제한을 넘으면 자른다. */
    public void recordFailure(String reason) {
        this.failureReason = reason == null || reason.length() <= FAILURE_REASON_MAX_LENGTH
            ? reason : reason.substring(0, FAILURE_REASON_MAX_LENGTH);
    }

    public void explain(String explanation) {
        this.llmExplanation = explanation;
    }
}
