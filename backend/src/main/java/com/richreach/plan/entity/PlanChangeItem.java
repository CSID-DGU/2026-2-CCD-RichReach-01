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

/** 플랜 변경 기록의 바뀐 내용 한 줄. 소비 정보라서 {@code toString}을 만들지 않는다. */
@Entity
@Table(name = "plan_change_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanChangeItem extends BaseEntity {

    /** 바뀐 내용의 종류. */
    public enum Type {
        /** 카드·업종별 목표 금액이 바뀌었다. */
        AMOUNT,
        /** 업종의 우선 카드(목표 금액이 가장 큰 카드)가 바뀌었다. */
        PRIORITY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "change_id", nullable = false, updatable = false)
    private Long changeId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "item_type", nullable = false, updatable = false, length = 20)
    private Type type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false, length = 30)
    private SpendingCategory category;

    /** AMOUNT: 금액이 바뀐 카드, PRIORITY: 새로 우선이 된 카드 */
    @Column(name = "user_card_id", updatable = false)
    private Long userCardId;

    /** PRIORITY: 이전에 우선이던 카드 */
    @Column(name = "previous_card_id", updatable = false)
    private Long previousCardId;

    @Column(name = "previous_amount", nullable = false, updatable = false)
    private long previousAmount;

    @Column(name = "current_amount", nullable = false, updatable = false)
    private long currentAmount;

    public static PlanChangeItem create(long changeId, Type type, SpendingCategory category, Long userCardId,
                                        Long previousCardId, long previousAmount, long currentAmount) {
        PlanChangeItem item = new PlanChangeItem();
        item.changeId = changeId;
        item.type = type;
        item.category = category;
        item.userCardId = userCardId;
        item.previousCardId = previousCardId;
        item.previousAmount = previousAmount;
        item.currentAmount = currentAmount;
        return item;
    }

}
