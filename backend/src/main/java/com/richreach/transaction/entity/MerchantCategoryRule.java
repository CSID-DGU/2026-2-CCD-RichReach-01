package com.richreach.transaction.entity;

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

/**
 * 가맹점명을 화면용 업종으로 분류하는 규칙 한 줄. 읽기 전용 기준 데이터라서 마이그레이션으로 바꾼다.
 * 규칙은 {@code priority}가 작은 것부터, 같으면 문구가 긴 것부터 적용하고 먼저 걸린 규칙이 이긴다.
 */
@Entity
@Table(name = "merchant_category_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantCategoryRule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 가맹점명과 비교할 문구. 비교할 때 정규화한다. */
    @Column(nullable = false, length = 100)
    private String pattern;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "match_type", nullable = false, length = 20)
    private MatchType matchType;

    /** 화면용 11개 업종 코드 */
    @Column(name = "ui_category", nullable = false, length = 30)
    private String uiCategory;

    /** 작은 것부터 적용한다. */
    @Column(nullable = false)
    private int priority;

    /** false면 쓰지 않는다. */
    @Column(nullable = false)
    private boolean active;

    @Column(length = 200)
    private String note;

    /** 화면용 업종. 규칙에 11개 업종이 아닌 값이 들어 있으면 비어 있다. */
    public java.util.Optional<SpendingCategory> spendingCategory() {
        return SpendingCategory.fromCode(uiCategory);
    }

}
