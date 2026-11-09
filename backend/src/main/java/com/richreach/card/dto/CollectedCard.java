package com.richreach.card.dto;

/**
 * 동기화로 수집한 카드 한 장. 카드사 응답을 이 모양으로 바꿔서 보유카드 저장에 넘긴다.
 * (카드사 응답의 필드 이름과 구조는 이 모양을 만드는 쪽에서만 알면 된다.)
 *
 * @param cardName     카드사가 알려준 카드명
 * @param maskedNumber 카드 번호. 마스킹되어 있어도, 전체 번호가 와도 되고, 저장 전에 마지막 4자리만 남기고 가린다. 없으면 null
 * @param cardType     카드사가 준 구분값. 카드사에 따라 신용/체크가 아니라 소유자와 브랜드({@code 본인 로컬} 등)일 수 있다.
 *                     없으면 null
 */
public record CollectedCard(String cardName, String maskedNumber, String cardType) {

}
