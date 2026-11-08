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
import org.hibernate.annotations.DynamicUpdate;

/**
 * 사용자의 보유카드. 동기화로 수집한 카드이며, 카드 상품({@link CardProduct})에 매칭되면 그 혜택으로 분석할 수 있다.
 *
 * <p>사용자와 카드사 계정은 다른 도메인이라 엔티티로 잇지 않고 ID만 가진다. 카드 번호는 마스킹된 값만 저장한다.
 * 같은 카드는 (카드사 계정, 마스킹 번호, 카드명)으로 구분해서 재동기화 때 한 행을 갱신한다.
 *
 * <p>{@link DynamicUpdate}로 바뀐 컬럼만 UPDATE한다. 동기화와 사용자의 선택 변경은 서로 다른 컬럼({@code selected})을
 * 바꾸므로, 한쪽이 읽은 뒤 다른 쪽이 커밋해도 읽어 둔 이전 값으로 덮어쓰지 않는다.
 */
@Entity
@DynamicUpdate
@Table(name = "user_cards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OwnedCard extends BaseEntity {

    /** user_cards.card_name, card_type 컬럼 길이(V17)와 같아야 한다. */
    public static final int CARD_NAME_MAX_LENGTH = 200;
    public static final int CARD_TYPE_MAX_LENGTH = 30;

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

    @Column(name = "card_name", nullable = false, length = CARD_NAME_MAX_LENGTH)
    private String cardName;

    /** 마스킹된 카드 번호. 없으면 빈 문자열 */
    @Column(name = "masked_number", nullable = false, length = 30)
    private String maskedNumber;

    @Column(name = "card_type", length = CARD_TYPE_MAX_LENGTH)
    private String cardType;

    /** 가장 최근 동기화에서 확인된 카드인지. 사라진 카드는 false로 남긴다. */
    @Column(nullable = false)
    private boolean active;

    /** 분석 대상 여부. 재동기화로 바뀌지 않는다. */
    @Column(nullable = false)
    private boolean selected;

    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt;

    /**
     * 동기화에서 처음 발견한 카드. 카드 상품에 매칭되어 분석할 수 있는 카드는 처음에 분석 대상으로 선택된 상태로 저장하고,
     * 매칭되지 않은 카드는 선택할 수 없으므로 선택하지 않는다. 이후 동기화는 선택을 바꾸지 않는다.
     */
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
        card.selected = cardProduct != null;
        card.lastSyncedAt = now;
        return card;
    }

    /** 카드 상품에 매칭되어 혜택으로 분석할 수 있는 카드인지. 매칭되지 않은 카드는 분석할 수 없다. */
    public boolean isAnalyzable() {
        return cardProduct != null;
    }

    /**
     * 실제로 분석 대상인지. 선택되어 있고 분석할 수 있어야 한다. 재동기화로 카드 상품 매칭이 풀린 카드는 저장된 선택을
     * 지우지 않고 남겨 두지만(다시 매칭되면 이어서 쓴다), 그동안은 분석 대상으로 보지 않는다.
     */
    public boolean isAnalysisTarget() {
        return selected && isAnalyzable();
    }

    /** 사용자가 고른 분석 대상 여부를 바꾼다. 분석할 수 없는 카드를 선택하는지는 호출하는 쪽에서 막는다. */
    public void changeSelection(boolean selected) {
        this.selected = selected;
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
     * 같은 카드를 구분하는 키. DB의 유일 키(utf8mb4_0900_ai_ci)와 같은 기준으로 만들어야 한다. 그렇지 않으면 표기만 바뀐
     * 같은 카드를 새 카드로 착각해 저장하다가 유일 키 위반으로 동기화가 실패하고, 기존 카드는 비활성이 되어 선택이 사라진다.
     * <ul>
     *   <li>카드명: 대소문자, 악센트(Café와 Cafe), 전각·반각 차이를 무시한다. (DB가 같게 보는 것들)</li>
     *   <li>카드 번호: 구분자(하이픈, 공백)와 가려진 모양이 바뀌어도 같은 카드로 보도록 마지막 4자리 숫자만 쓴다.</li>
     * </ul>
     */
    public static String identityKey(String maskedNumber, String cardName) {
        return lastFourDigits(maskedNumber) + "|" + foldForComparison(cardName);
    }

    /** 카드 번호의 마지막 4자리 숫자. 숫자가 4자리보다 적으면 있는 만큼, 없으면 빈 문자열이다. */
    public static String lastFourDigits(String maskedNumber) {
        String digits = maskedNumber == null ? "" : maskedNumber.replaceAll("\\D", "");
        return digits.length() <= 4 ? digits : digits.substring(digits.length() - 4);
    }

    private static String foldForComparison(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /**
     * 컬럼 길이(글자 수)를 넘는 값을 잘라서 저장이 실패하지 않게 한다. 같은 입력은 항상 같게 잘리므로 식별 키가 흔들리지 않는다.
     * 글자 수는 MySQL처럼 유니코드 코드 포인트로 센다. null은 그대로 돌려준다.
     */
    public static String fit(String text, int maxLength) {
        if (text == null || text.codePointCount(0, text.length()) <= maxLength) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, maxLength));
    }

}
