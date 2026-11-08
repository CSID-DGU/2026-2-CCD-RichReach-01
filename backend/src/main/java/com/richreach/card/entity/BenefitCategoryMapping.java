package com.richreach.card.entity;

import com.richreach.global.category.SpendingCategory;
import com.richreach.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 혜택 업종 코드 하나가 화면용 6개 업종 중 무엇인지 적은 대응표의 한 행. 읽기 전용 기준 데이터라서 마이그레이션으로 바꾼다.
 * 업종이 아닌 코드(전 가맹점, 결제 수단, 해외 조건)는 화면용 업종이 없다.
 */
@Entity
@Table(name = "benefit_category_mappings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BenefitCategoryMapping extends BaseEntity {

    @Id
    @Column(name = "category_code", length = 40)
    private String categoryCode;

    /** 화면용 6개 업종 코드. 업종이 아닌 코드는 null이다. */
    @Column(name = "ui_category", length = 30)
    private String uiCategory;

    /** 업종과 상관없이 모든 가맹점에 적용되는 코드인지 */
    @Column(name = "all_merchants", nullable = false)
    private boolean allMerchants;

    @Column(length = 200)
    private String note;

    /**
     * 화면용 업종. 없으면(업종이 아닌 코드) 비어 있다. 대응표에 6개 업종이 아닌 값이 들어 있으면 조용히 넘기지 않고
     * 잘못된 데이터로 알린다.
     */
    public Optional<SpendingCategory> spendingCategory() {
        if (uiCategory == null) {
            return Optional.empty();
        }
        return Optional.of(SpendingCategory.fromCode(uiCategory).orElseThrow(() ->
            new IllegalStateException("대응표에 6개 업종이 아닌 값이 있습니다: code=" + categoryCode)));
    }

}
