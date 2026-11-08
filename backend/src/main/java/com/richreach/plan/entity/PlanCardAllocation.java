package com.richreach.plan.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 플랜의 카드·카테고리별 목표 사용액. 소비 정보라서 {@code toString}을 만들지 않는다. */
@Entity
@Table(name = "plan_card_allocations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanCardAllocation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private Long planId;

    /** 배분 대상 보유카드(user_cards.id) */
    @Column(name = "user_card_id", nullable = false, updatable = false)
    private Long userCardId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false, length = 30)
    private SpendingCategory category;

    @Column(name = "target_amount", nullable = false)
    private long targetAmount;

    @Column(name = "expected_benefit", nullable = false)
    private long expectedBenefit;

    public static PlanCardAllocation create(Long planId, Long userCardId, SpendingCategory category,
                                            long targetAmount, long expectedBenefit) {
        if (targetAmount < 0 || expectedBenefit < 0) {
            throw new IllegalArgumentException("금액은 0 이상이어야 합니다.");
        }
        PlanCardAllocation allocation = new PlanCardAllocation();
        allocation.planId = planId;
        allocation.userCardId = userCardId;
        allocation.category = category;
        allocation.targetAmount = targetAmount;
        allocation.expectedBenefit = expectedBenefit;
        return allocation;
    }
}
