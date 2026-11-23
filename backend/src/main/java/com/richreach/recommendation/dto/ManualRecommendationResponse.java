package com.richreach.recommendation.dto;

import com.richreach.codef.entity.CardCompany;
import com.richreach.global.category.SpendingCategory;
import java.util.List;

/**
 * 결제 전 수동 입력 추천 결과. 카드, 금액, 순위는 모두 서버가 계산한 값이고 문구도 서버가 정한 고정 문구다. 금액은 원 단위
 * 정수다. 읽기 전용이라 거래나 월간 플랜을 바꾸지 않는다.
 *
 * @param type             {@code CARD}: 이번 결제에 추가 혜택이 있는 카드가 있다. {@code TEXT}: 현재 확인된 혜택 규칙으로는 추가
 *                         혜택을 확인하지 못했다. (카드에 혜택이 전혀 없다는 뜻이 아니다)
 * @param message          사용자에게 보여줄 문구
 * @param parsed           서버가 받아들인 입력
 * @param card             가장 유리한 카드. {@code TEXT}이면 {@code null}이다.
 * @param alternatives     다음으로 유리한 카드. 혜택이 양수인 카드만 최대 {@value
 *                         com.richreach.recommendation.service.ManualRecommendationService#MAX_ALTERNATIVES}장이고, 없으면 빈 목록이다.
 * @param calculationScope 계산에 포함한 혜택의 범위 안내. 화면의 안내 문구로 쓴다.
 */
public record ManualRecommendationResponse(
    Type type,
    String message,
    Parsed parsed,
    Card card,
    List<Card> alternatives,
    String calculationScope) {

    public enum Type {
        CARD, TEXT
    }

    /** 서버가 받아들인 입력. */
    public record Parsed(SpendingCategory category, long amount) {

    }

    /**
     * 추천 카드 한 장.
     *
     * @param cardId          보유카드 ID({@code GET /api/cards}의 {@code cardId})
     * @param cardProductId   카드 상품 ID. 혜택 상세 조회에 쓴다.
     * @param cardCompany     카드사
     * @param cardName        카드 이름
     * @param imageUrl        카드 이미지 주소. 모르면 {@code null}이다.
     * @param expectedBenefit 이번 결제 1건으로 늘어나는 예상 혜택(원). 월 한도와 전월 실적 조건을 반영한 값이다.
     * @param confirmedOnly   사람이 원문과 대조해 확인한 규칙만 썼는지. false이면 확인 전 규칙이 섞여 있어 참고용이다.
     * @param notes           계산에서 빼거나 단순화한 내용
     */
    public record Card(
        long cardId,
        long cardProductId,
        CardCompany cardCompany,
        String cardName,
        String imageUrl,
        long expectedBenefit,
        boolean confirmedOnly,
        List<String> notes) {

    }

}
