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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 실적/혜택에서 제외되는 대상 조건(원문 그대로).
 * 조건은 혜택이 아니라 원문 블록에 붙어 있고 혜택과의 연결이 불완전해서, 카드 단위로만 연결한다.
 */
@Entity
@Table(name = "performance_exclusions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerformanceExclusion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_product_id", nullable = false)
    private CardProduct cardProduct;

    /** 스테이징 condition_id. 재승격 시 같은 행을 갱신하는 키다. */
    @Column(name = "source_condition_id", nullable = false, length = 20)
    private String sourceConditionId;

    /** PERFORMANCE(전월실적 산정 제외), BENEFIT(혜택 제외) */
    @Column(name = "applies_to", nullable = false, length = 20)
    private String appliesTo;

    /** '|'로 여러 유형이 묶여 있다. (예: PERFORMANCE|EXCLUSION) */
    @Column(name = "condition_type", nullable = false, length = 100)
    private String conditionType;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "condition_text", nullable = false)
    private String conditionText;

    /** 근거 원문 블록 (stg_source_scope.scope_id) */
    @Column(name = "source_scope_id", nullable = false, length = 100)
    private String sourceScopeId;

}
