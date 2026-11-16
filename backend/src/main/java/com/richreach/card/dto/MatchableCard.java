package com.richreach.card.dto;

import com.richreach.codef.entity.CardCompany;

/**
 * 승인내역을 어느 보유카드의 거래로 저장할지 가릴 때 쓰는 카드 정보. 화면용 {@link OwnedCardResponse}의 카드 번호는 숫자만 남긴
 * 끝자리라서 가려진 자리의 위치가 사라지지만, 여기의 {@code number}는 저장된 번호 그대로라서 가려진 자리(별표)의 위치가 남아 있다.
 * 번호 일부만 담고 있지만 로그에는 남기지 않는다.
 *
 * @param cardId      보유카드 ID
 * @param cardCompany 카드사
 * @param number      저장된 카드 번호. 마지막 4자리 숫자만 보이고 나머지 숫자는 별표이며, 카드사가 가린 자리도 별표 그대로다.
 */
public record MatchableCard(Long cardId, CardCompany cardCompany, String number) {

    @Override
    public String toString() {
        return "MatchableCard[cardId=" + cardId + "]";
    }

}
