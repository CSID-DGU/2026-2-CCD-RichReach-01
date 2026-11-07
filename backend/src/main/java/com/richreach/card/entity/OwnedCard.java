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
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자의 보유카드. 동기화로 수집한 카드이며, 카드 상품({@link CardProduct})에 매칭되면 그 혜택으로 분석할 수 있다.
 *
 * <p>사용자와 카드사 계정은 다른 도메인이라 엔티티로 잇지 않고 ID만 가진다. 카드 번호는 마스킹된 값만 저장한다.
 * 같은 카드는 (카드사 계정, 마스킹 번호, 카드명)으로 구분해서 재동기화 때 한 행을 갱신한다.
 */
@Entity
@Table(name = "user_cards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OwnedCard extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 이 카드를 수집한 카드사 계정(codef_org_accounts.id) */
    @Column(name = "org_account_id", nullable = false)
    private Long orgAccountId;

    /** CODEF 기관 코드. 카드사 구분용 */
    @Column(nullable = false, length = 10)
    private String organization;

    /** 매칭된 카드 상품. 매칭에 실패하면 null이고 분석할 수 없다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_product_id")
    private CardProduct cardProduct;

    @Column(name = "card_name", nullable = false, length = 200)
    private String cardName;

    /** 마스킹된 카드 번호. 없으면 빈 문자열 */
    @Column(name = "masked_number", nullable = false, length = 30)
    private String maskedNumber;

    @Column(name = "card_type", length = 30)
    private String cardType;

    /** 가장 최근 동기화에서 확인된 카드인지. 사라진 카드는 false로 남긴다. */
    @Column(nullable = false)
    private boolean active;

    /** 분석 대상 여부. 재동기화로 바뀌지 않는다. */
    @Column(nullable = false)
    private boolean selected;

    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt;

    /** 동기화에서 처음 발견한 카드. 분석 대상은 기본으로 선택하지 않는다. */
    public static OwnedCard discovered(long userId, long orgAccountId, String organization, String cardName,
                                       String maskedNumber, String cardType, CardProduct cardProduct,
                                       LocalDateTime now) {
        OwnedCard card = new OwnedCard();
        card.userId = userId;
        card.orgAccountId = orgAccountId;
        card.organization = organization;
        card.cardName = cardName;
        card.maskedNumber = maskedNumber;
        card.cardType = cardType;
        card.cardProduct = cardProduct;
        card.active = true;
        card.selected = false;
        card.lastSyncedAt = now;
        return card;
    }

    /** 다시 확인된 카드를 갱신한다. 사라졌다가 돌아온 카드는 되살리고, 사용자가 고른 선택은 건드리지 않는다. */
    public void refresh(String cardType, CardProduct cardProduct, LocalDateTime now) {
        this.cardType = cardType;
        this.cardProduct = cardProduct;
        this.active = true;
        this.lastSyncedAt = now;
    }

    /** 최근 동기화에서 보이지 않는 카드로 표시한다. 행은 지우지 않는다. */
    public void deactivate() {
        this.active = false;
    }

    /** 같은 카드를 구분하는 키. 재동기화 때 기존 행을 찾는 데 쓴다. */
    public String identityKey() {
        return identityKey(maskedNumber, cardName);
    }

    /**
     * DB의 유일 키(utf8mb4_0900_ai_ci)는 대소문자를 구분하지 않고 같은 글자의 다른 유니코드 표현도 같게 본다.
     * 같은 기준으로 키를 만들어야, 표기만 바뀐 같은 카드를 새 카드로 착각해 저장하다가 유일 키 위반으로 동기화가 실패하지 않는다.
     */
    public static String identityKey(String maskedNumber, String cardName) {
        return maskedNumber + "|" + Normalizer.normalize(cardName, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

}
