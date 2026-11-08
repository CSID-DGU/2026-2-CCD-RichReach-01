package com.richreach.transaction.entity;

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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 보유카드로 결제한 거래 한 건. 가맹점명과 금액은 소비 정보라서 로그에 남기지 않는다. (그래서 {@code toString}을 만들지 않는다.)
 *
 * <p>사용자와 보유카드는 다른 도메인이라 엔티티로 잇지 않고 ID만 가진다. 카드 번호는 저장하지 않는다.
 * 같은 거래는 {@code dedupeKey}로 구분해서 재동기화 때 한 행을 갱신한다.
 *
 * <p>업종 카테고리는 자동 분류 값({@code category})과 사용자가 고친 값({@code categoryOverride})을 따로 둔다.
 * 재동기화({@link #refresh})는 사용자가 고친 값을 건드리지 않는다. {@link DynamicUpdate}로 바뀐 컬럼만 UPDATE해서
 * 다른 곳에서 바뀐 값을 읽어 둔 이전 값으로 덮어쓰지 않는다.
 */
@Entity
@DynamicUpdate
@Table(name = "card_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardTransaction extends BaseEntity {

    /** card_transactions의 컬럼 길이(V18)와 같아야 한다. */
    public static final int APPROVAL_NO_MAX_LENGTH = 40;
    public static final int MERCHANT_NAME_MAX_LENGTH = 200;
    public static final int CATEGORY_MAX_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** 거래가 발생한 보유카드(user_cards.id) */
    @Column(name = "user_card_id", nullable = false, updatable = false)
    private Long userCardId;

    @Column(name = "dedupe_key", nullable = false, updatable = false, length = 64)
    private String dedupeKey;

    /** 승인번호. 없으면 빈 문자열 */
    @Column(name = "approval_no", nullable = false, updatable = false, length = APPROVAL_NO_MAX_LENGTH)
    private String approvalNo;

    @Column(name = "approved_at", nullable = false, updatable = false)
    private LocalDateTime approvedAt;

    @Column(name = "merchant_name", nullable = false, length = MERCHANT_NAME_MAX_LENGTH)
    private String merchantName;

    /** 승인 금액(원) */
    @Column(nullable = false)
    private long amount;

    /** 취소·환불된 금액(원) */
    @Column(name = "canceled_amount", nullable = false)
    private long canceledAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private TransactionStatus status;

    /** 할부 개월. 0이면 일시불 */
    @Column(name = "installment_months", nullable = false)
    private int installmentMonths;

    @Column(nullable = false)
    private boolean overseas;

    /** 자동 분류한 업종 카테고리. 분류 전이면 null */
    @Column(length = CATEGORY_MAX_LENGTH)
    private String category;

    /** 사용자가 고친 업종 카테고리. 있으면 이 값을 쓴다. */
    @Column(name = "category_override", length = CATEGORY_MAX_LENGTH)
    private String categoryOverride;

    /** 이 거래 정보를 수집(생성 또는 변경)한 시각 */
    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    /**
     * 처음 수집한 거래. 값은 호출하는 쪽에서 컬럼 길이에 맞게 다듬어 넘긴다.
     * 취소 금액은 0 이상, 승인 금액 이하로 맞춘다.
     */
    public static CardTransaction collected(long userId, long userCardId, String dedupeKey, String approvalNo,
                                            LocalDateTime approvedAt, String merchantName, long amount,
                                            long canceledAmount, int installmentMonths, boolean overseas,
                                            String category, LocalDateTime now) {
        CardTransaction transaction = new CardTransaction();
        transaction.userId = userId;
        transaction.userCardId = userCardId;
        transaction.dedupeKey = dedupeKey;
        transaction.approvalNo = approvalNo;
        transaction.approvedAt = approvedAt;
        transaction.merchantName = merchantName;
        transaction.amount = amount;
        transaction.canceledAmount = clampCanceled(amount, canceledAmount);
        transaction.status = TransactionStatus.of(amount, transaction.canceledAmount);
        transaction.installmentMonths = Math.max(installmentMonths, 0);
        transaction.overseas = overseas;
        transaction.category = category;
        transaction.collectedAt = now;
        return transaction;
    }

    /**
     * 다시 수집된 거래로 갱신한다. 바뀐 것이 있으면 수집 시각도 바꾸고 {@code true}를 돌려주고, 같으면 아무것도 바꾸지 않는다.
     * 자동 분류가 비어 있는 입력은 이미 분류된 값을 지우지 않고, 사용자가 고친 카테고리는 건드리지 않는다.
     */
    public boolean refresh(String merchantName, long amount, long canceledAmount, int installmentMonths,
                           boolean overseas, String category, LocalDateTime now) {
        long newCanceled = clampCanceled(amount, canceledAmount);
        int newInstallment = Math.max(installmentMonths, 0);
        String newCategory = category != null ? category : this.category;

        boolean changed = !this.merchantName.equals(merchantName)
            || this.amount != amount
            || this.canceledAmount != newCanceled
            || this.installmentMonths != newInstallment
            || this.overseas != overseas
            || !Objects.equals(this.category, newCategory);
        if (!changed) {
            return false;
        }
        this.merchantName = merchantName;
        this.amount = amount;
        this.canceledAmount = newCanceled;
        this.status = TransactionStatus.of(amount, newCanceled);
        this.installmentMonths = newInstallment;
        this.overseas = overseas;
        this.category = newCategory;
        this.collectedAt = now;
        return true;
    }

    /** 사용자가 업종 카테고리를 고친다. null이면 고친 값을 지우고 자동 분류 값을 쓴다. */
    public void overrideCategory(String category) {
        this.categoryOverride = category;
    }

    /** 실제로 쓰는 업종 카테고리. 사용자가 고친 값이 있으면 그 값, 없으면 자동 분류 값이다. 분류 전이면 null이다. */
    public String effectiveCategory() {
        return categoryOverride != null ? categoryOverride : category;
    }

    /** 취소·환불을 뺀 실제 결제 금액(원) */
    public long netAmount() {
        return amount - canceledAmount;
    }

    private static long clampCanceled(long amount, long canceledAmount) {
        return Math.min(Math.max(canceledAmount, 0), amount);
    }

}
