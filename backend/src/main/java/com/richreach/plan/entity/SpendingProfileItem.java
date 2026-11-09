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
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 프로필의 카테고리별 예상 소비. 소비 정보라서 {@code toString}을 만들지 않는다. */
@Entity
@Table(name = "spending_profile_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpendingProfileItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false, updatable = false)
    private Long profileId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false, length = 30)
    private SpendingCategory category;

    /** 예상 소비 금액(원) */
    @Column(name = "expected_amount", nullable = false)
    private long expectedAmount;

    @Column(name = "expected_count", nullable = false)
    private int expectedCount;

    /** 발생 확률 0~1 */
    @Column(name = "occurrence_prob", nullable = false, precision = 5, scale = 4)
    private BigDecimal occurrenceProb;

    /** 신뢰도 0~1 */
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    public static SpendingProfileItem create(Long profileId, SpendingCategory category, long expectedAmount,
                                             int expectedCount, BigDecimal occurrenceProb, BigDecimal confidence) {
        if (expectedAmount < 0 || expectedCount < 0) {
            throw new IllegalArgumentException("예상 금액과 횟수는 0 이상이어야 합니다.");
        }
        requireRatio(occurrenceProb, "발생 확률");
        requireRatio(confidence, "신뢰도");
        SpendingProfileItem item = new SpendingProfileItem();
        item.profileId = profileId;
        item.category = category;
        item.expectedAmount = expectedAmount;
        item.expectedCount = expectedCount;
        item.occurrenceProb = occurrenceProb;
        item.confidence = confidence;
        return item;
    }

    private static void requireRatio(BigDecimal value, String name) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(name + "은(는) 0 이상 1 이하여야 합니다.");
        }
    }
}
