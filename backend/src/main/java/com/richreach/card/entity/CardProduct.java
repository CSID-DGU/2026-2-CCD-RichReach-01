package com.richreach.card.entity;

import com.richreach.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카드 상품. 스테이징(stg_card_catalog)에서 MVP로 선택된 카드만 승격돼 들어온다. (CardPromotionService)
 * 행은 승격 코드가 만들기 때문에 생성자나 setter를 두지 않는다.
 */
@Entity
@Table(name = "card_products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardProduct extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 카드사 코드 (kb, hyundai, nh, citi, woori 등) */
    @Column(name = "issuer_code", nullable = false, length = 20)
    private String issuerCode;

    @Column(nullable = false, length = 50)
    private String issuer;

    /** 카드사 상품 코드. 문자열이라 앞자리 0이 유지된다. */
    @Column(name = "card_id", nullable = false, length = 40)
    private String cardId;

    @Column(name = "card_name", nullable = false, length = 200)
    private String cardName;

    /** credit, check, credit_or_special, business */
    @Column(name = "card_type", nullable = false, length = 30)
    private String cardType;

    /** listed, issuance_suspended */
    @Column(name = "card_status", nullable = false, length = 30)
    private String cardStatus;

    /** PAYMENT_CARD, GIFT_PREPAID, SUBSIDY_PAYMENT */
    @Column(name = "product_class", nullable = false, length = 30)
    private String productClass;

    /** 신규 추천 후보 여부. 발급 중단/비결제 카드는 false지만, 이미 가진 카드의 분석에는 계속 쓴다. */
    @Column(nullable = false)
    private boolean recommendable;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "source_snapshot_date", nullable = false)
    private LocalDate sourceSnapshotDate;

    @Column(name = "source_text_sha256", nullable = false, length = 64)
    private String sourceTextSha256;

    @Column(name = "review_status", nullable = false, length = 40)
    private String reviewStatus;

}
