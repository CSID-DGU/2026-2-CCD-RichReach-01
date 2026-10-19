package com.richreach.card.entity;

import com.richreach.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 카드별 혜택 규칙. 스테이징 혜택 한 행이 한 규칙이다.
 *
 * <p>같은 혜택이 요약/본문에 반복 추출될 수 있어서 행을 그대로 합산하면 안 된다.
 * null은 미확정 또는 해당 없음이며 0원, 무제한, 조건 없음으로 해석하지 않는다.
 * {@link #ruleEngineReady}가 false인 규칙은 사람이 원문과 대조하기 전이므로 계산에 쓰지 않는다.
 */
@Entity
@Table(name = "benefit_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BenefitRule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_product_id", nullable = false)
    private CardProduct cardProduct;

    /** 스테이징 benefit_id. 재승격 시 같은 행을 갱신하는 키이며 영구 규칙 ID가 아니다. */
    @Column(name = "source_benefit_id", nullable = false, length = 80)
    private String sourceBenefitId;

    /** DISCOUNT, POINT, CASHBACK, MILEAGE, FEE_WAIVER, FREE_SERVICE, INTEREST_FREE, VOUCHER, REVIEW_REQUIRED */
    @Column(name = "benefit_type", nullable = false, length = 30)
    private String benefitType;

    /** 업종 코드. '|'로 여러 개가 묶여 있다. */
    @Column(nullable = false, length = 200)
    private String category;

    @Column(name = "category_name", nullable = false, length = 200)
    private String categoryName;

    @Column(length = 300)
    private String merchants;

    @Column(name = "target_raw", nullable = false, length = 1000)
    private String targetRaw;

    @Column(name = "targets_normalized", length = 200)
    private String targetsNormalized;

    @Column(name = "payment_methods", length = 100)
    private String paymentMethods;

    /** 0.7은 0.7%이며 계산 시 100으로 나눈다. */
    @Column(name = "rate_percent", precision = 7, scale = 2)
    private BigDecimal ratePercent;

    @Column(name = "rate_min_percent", precision = 7, scale = 2)
    private BigDecimal rateMinPercent;

    @Column(name = "rate_max_percent", precision = 7, scale = 2)
    private BigDecimal rateMaxPercent;

    @Column
    private Long amount;

    /** KRW, POINT, MILE */
    @Column(name = "reward_unit", length = 10)
    private String rewardUnit;

    /** PER_LITER, PER_TRANSACTION */
    @Column(name = "amount_basis", length = 30)
    private String amountBasis;

    @Column(name = "monthly_limit")
    private Long monthlyLimit;

    /** KRW, POINT */
    @Column(name = "monthly_limit_unit", length = 10)
    private String monthlyLimitUnit;

    @Column(name = "monthly_limit_status", nullable = false, length = 30)
    private String monthlyLimitStatus;

    @Column(name = "daily_limit")
    private Long dailyLimit;

    @Column(name = "shared_monthly_limit")
    private Long sharedMonthlyLimit;

    /** 한도를 공유하는 혜택끼리 묶는 ID */
    @Column(name = "cap_group_id", length = 80)
    private String capGroupId;

    /** 월 할인대상 결제금액 한도. 혜택 한도와 다른 값이다. */
    @Column(name = "eligible_spend_monthly_limit_krw")
    private Long eligibleSpendMonthlyLimitKrw;

    @Column(name = "daily_count_limit")
    private Integer dailyCountLimit;

    @Column(name = "monthly_count_limit")
    private Integer monthlyCountLimit;

    @Column(name = "annual_count_limit")
    private Integer annualCountLimit;

    /** BASE, ADDITIONAL, REPLACES_BASE, PREFER_HIGHER, ANNUAL_BONUS */
    @Column(name = "rule_mode", length = 20)
    private String ruleMode;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column
    private String conditions;

    @Column(name = "review_notes", length = 300)
    private String reviewNotes;

    @Column(name = "review_status", nullable = false, length = 40)
    private String reviewStatus;

    /** 사람이 원문과 대조해 확인한 뒤에만 true. 승격을 다시 해도 덮어쓰지 않는다. */
    @Column(name = "rule_engine_ready", nullable = false)
    private boolean ruleEngineReady;

    /** 근거 원문 블록 (stg_source_scope.scope_id) */
    @Column(name = "source_scope_id", nullable = false, length = 100)
    private String sourceScopeId;

    @Column(name = "extraction_method", nullable = false, length = 30)
    private String extractionMethod;

}
