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
import java.time.LocalDateTime;
import java.time.YearMonth;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 월간 플랜이 다시 계산되어 바뀐 기록 한 건. 바뀐 내용(업종·카드별 금액 전후)은 {@link PlanChangeItem}에 있다.
 * 소비 정보라서 {@code toString}을 만들지 않는다.
 */
@Entity
@Table(name = "plan_changes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanChange extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "plan_month", nullable = false, updatable = false, length = 7, columnDefinition = "CHAR(7)")
    private String planMonth;

    /** 바뀐 뒤의 플랜 */
    @Column(name = "plan_id", nullable = false, updatable = false)
    private Long planId;

    /** 바뀌기 전의 플랜 */
    @Column(name = "previous_plan_id", updatable = false)
    private Long previousPlanId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reason_code", nullable = false, updatable = false, length = 30)
    private PlanChangeReason reason;

    @Column(name = "reason_text", nullable = false, updatable = false, length = 300)
    private String reasonText;

    @Column(name = "previous_benefit", nullable = false, updatable = false)
    private long previousBenefit;

    @Column(name = "current_benefit", nullable = false, updatable = false)
    private long currentBenefit;

    /** 사용자가 변경 내용을 확인한 시각. 아직 확인하지 않았으면 null이다. */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    public static PlanChange create(long userId, YearMonth month, long planId, Long previousPlanId,
                                    PlanChangeReason reason, long previousBenefit, long currentBenefit) {
        PlanChange change = new PlanChange();
        change.userId = userId;
        change.planMonth = month.toString();
        change.planId = planId;
        change.previousPlanId = previousPlanId;
        change.reason = reason;
        change.reasonText = reason.text();
        change.previousBenefit = previousBenefit;
        change.currentBenefit = currentBenefit;
        return change;
    }

    /** 확인한 것으로 표시한다. 이미 확인했으면 처음 확인한 시각을 유지한다. */
    public void confirm(LocalDateTime now) {
        if (confirmedAt == null) {
            this.confirmedAt = now;
        }
    }

    public boolean isConfirmed() {
        return confirmedAt != null;
    }

}
