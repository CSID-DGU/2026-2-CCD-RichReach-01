package com.richreach.card.entity;

import com.richreach.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 혜택 규칙에 붙은 전월실적/연간실적 조건. 실적 조건이 있는 혜택만 행이 생긴다.
 * 하한(min)은 이상, 상한(maxExclusive)은 미만이다. null은 조건 없음이 아니라 미확정이다.
 */
@Entity
@Table(name = "performance_tiers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerformanceTier extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "benefit_rule_id", nullable = false)
    private BenefitRule benefitRule;

    @Column(name = "previous_month_min_krw")
    private Long previousMonthMinKrw;

    @Column(name = "previous_month_max_exclusive_krw")
    private Long previousMonthMaxExclusiveKrw;

    /** MINIMUM, NONE, NOT_STATED */
    @Column(name = "previous_month_requirement_status", length = 20)
    private String previousMonthRequirementStatus;

    @Column(name = "annual_spend_min_krw")
    private Long annualSpendMinKrw;

}
