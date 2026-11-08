package com.richreach.card.dto;

import com.richreach.card.entity.CardProduct;
import com.richreach.card.entity.OwnedCard;
import com.richreach.codef.entity.CardCompany;
import java.util.Locale;

/**
 * 보유카드 한 장.
 *
 * @param cardId         보유카드 ID({@code user_cards.id}). 혜택 상세 조회에 쓰는 카드 상품 ID와 다르다.
 * @param cardProductId  매칭된 카드 상품 ID. 혜택 상세 조회({@code GET /api/cards/{cardId}/benefits})에는 이 값을 쓴다.
 *                       카드 상품에 매칭되지 않은 카드는 {@code null}이고 분석할 수 없다.
 * @param maskedNumber   카드 번호의 마지막 4자리. 번호를 알 수 없으면 빈 문자열이다.
 * @param cardType       신용/체크. 카드 상품이 있으면 상품의 종류를, 없으면 카드사가 알려준 종류를 쓴다.
 * @param analysisStatus 혜택으로 분석할 수 있는지. 카드 상품에 매칭된 카드만 분석할 수 있다.
 * @param selected       분석 대상인지. 선택되어 있고 분석할 수 있어야 {@code true}다. 분석할 수 없는 카드는 선택할 수 없고,
 *                       재동기화로 매칭이 풀린 카드는 저장된 선택이 남아 있어도 {@code false}로 내려간다.
 * @param imageUrl       카드 이미지. 아직 제공하지 않아 항상 {@code null}이다.
 */
public record OwnedCardResponse(
    Long cardId,
    Long cardProductId,
    CardCompany cardCompany,
    String cardName,
    String maskedNumber,
    CardKind cardType,
    AnalysisStatus analysisStatus,
    boolean selected,
    String imageUrl) {

    public enum AnalysisStatus {
        /** 카드 상품에 매칭되어 혜택으로 분석할 수 있다. */
        AVAILABLE,
        /** 카드 상품에 매칭되지 않아 분석할 수 없다. */
        UNAVAILABLE
    }

    public enum CardKind {
        CREDIT, CHECK, UNKNOWN
    }

    public static OwnedCardResponse from(OwnedCard card) {
        CardProduct product = card.getCardProduct();
        return new OwnedCardResponse(
            card.getId(),
            product == null ? null : product.getId(),
            CardCompany.fromOrganizationCode(card.getOrganization()),
            card.getCardName(),
            OwnedCard.lastFourDigits(card.getMaskedNumber()),
            kindOf(card),
            card.isAnalyzable() ? AnalysisStatus.AVAILABLE : AnalysisStatus.UNAVAILABLE,
            card.isAnalysisTarget(),
            null);
    }

    /**
     * 카드 상품의 종류(credit, check, credit_or_special, business)가 있으면 그것을, 없으면 카드사가 알려준 종류를 본다.
     * 체크로 읽히면 체크, 신용으로 읽히면 신용이고, 그 밖이거나 알 수 없으면 UNKNOWN이다.
     */
    static CardKind kindOf(OwnedCard card) {
        CardProduct product = card.getCardProduct();
        String raw = product != null ? product.getCardType() : card.getCardType();
        if (raw == null) {
            return CardKind.UNKNOWN;
        }
        String type = raw.toLowerCase(Locale.ROOT);
        if (type.contains("check") || type.contains("체크") || type.contains("debit")) {
            return CardKind.CHECK;
        }
        if (type.contains("credit") || type.contains("신용")) {
            return CardKind.CREDIT;
        }
        return CardKind.UNKNOWN;
    }

}
