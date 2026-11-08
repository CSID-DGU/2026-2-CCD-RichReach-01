package com.richreach.card.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 분석 대상 카드 선택 요청. 선택한 카드 전체를 한 번에 보내고, 여기에 없는 카드는 선택이 해제된다.
 * 분석할 카드가 하나도 없으면 플랜을 만들 수 없어서 최소 한 장은 있어야 한다.
 *
 * @param cardIds 분석 대상으로 선택할 보유카드 ID 목록. 같은 ID가 여러 번 있어도 한 번으로 본다.
 */
public record SelectCardsRequest(
    @NotEmpty(message = "분석할 카드를 한 장 이상 선택해 주세요.")
    List<@NotNull(message = "카드 ID에 빈 값이 있습니다.") Long> cardIds) {

}
