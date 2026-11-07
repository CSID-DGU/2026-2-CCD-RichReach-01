package com.richreach.codef.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * CODEF 카드 조회 API 호출. 연결한 카드사의 보유 카드 목록을 가져와 {@link CodefCard}로 바꿔 돌려준다.
 * 카드 내용(카드명, 번호)은 이 클래스가 로그에 남기지 않는다.
 *
 * <p>카드 응답의 필드 이름은 CODEF 문서를 기준으로 한 <b>가정</b>이고, 아래 {@code FIELD_*} 상수 한 곳에만 있다.
 * 실제 응답과 다르면 {@link CardListResult#cardCount()}(응답에 있는 카드 수)와 {@code cards().size()}(읽어낸 카드 수)가
 * 어긋나서 호출하는 쪽이 저장하지 않고 실패로 처리한다. 실제 응답을 확인한 뒤 상수를 고친다.
 */
@Component
@RequiredArgsConstructor
public class CodefCardClient {

    static final String CARD_LIST_PATH = "/v1/kr/card/p/account/card-list";

    /** 카드 한 장의 필드 이름. 실제 응답으로 확인해서 확정한다. */
    static final String FIELD_CARD_NAME = "resCardName";
    static final String FIELD_CARD_NUMBER = "resCardNo";
    static final String FIELD_CARD_TYPE = "resCardType";

    private static final String SUCCESS_CODE = "CF-00000";

    private final CodefApiClient apiClient;

    /**
     * 카드사에 등록된 보유 카드 목록을 가져온다.
     *
     * @param connectedId  CODEF 연동 ID (로그에 남기지 않는다)
     * @param organization CODEF 기관 코드
     */
    public CardListResult fetchCardList(String connectedId, String organization) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("connectedId", connectedId);
        body.put("organization", organization);
        return parse(apiClient.post(CARD_LIST_PATH, body));
    }

    /**
     * 결과 코드가 CF-00000이면 성공이다. 카드가 여러 장이면 data가 배열이고, 한 장이면 객체로 올 수 있어서
     * 배열이면 원소마다, 비어 있지 않은 객체면 한 장으로 읽는다.
     * 카드명이 없는 항목은 카드로 읽지 못하므로 읽어낸 카드 수에서 빠진다. (응답의 카드 수는 그대로 센다.)
     */
    static CardListResult parse(JsonNode response) {
        String resultCode = response.path("result").path("code").asString("");
        if (!SUCCESS_CODE.equals(resultCode)) {
            return CardListResult.failure(resultCode);
        }
        List<JsonNode> entries = entriesOf(response.path("data"));
        List<CodefCard> cards = entries.stream().map(CodefCardClient::toCard).flatMap(Optional::stream).toList();
        return new CardListResult(true, entries.size(), resultCode, cards);
    }

    private static List<JsonNode> entriesOf(JsonNode data) {
        if (data.isArray()) {
            List<JsonNode> entries = new ArrayList<>();
            data.forEach(entries::add);
            return entries;
        }
        return data.isObject() && data.size() > 0 ? List.of(data) : List.of();
    }

    private static Optional<CodefCard> toCard(JsonNode entry) {
        String name = entry.path(FIELD_CARD_NAME).asString("");
        if (name.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new CodefCard(name, entry.path(FIELD_CARD_NUMBER).asString(""),
            entry.path(FIELD_CARD_TYPE).asString("")));
    }

    /**
     * 카드 목록 조회 결과.
     *
     * @param cardCount 응답에 들어 있던 카드 수. 읽어내지 못한 항목도 센다.
     * @param code      CODEF 결과 코드(CF-xxxxx). 성공이면 CF-00000
     * @param cards     카드로 읽어낸 목록. 실패하면 비어 있다.
     */
    public record CardListResult(boolean success, int cardCount, String code, List<CodefCard> cards) {

        public static CardListResult failure(String code) {
            return new CardListResult(false, 0, code, List.of());
        }

    }

    /**
     * CODEF가 알려준 카드 한 장. 카드명과 번호는 개인 정보라서 {@code toString}에서 가린다.
     *
     * @param number 카드사가 준 번호. 마스킹되어 있을 수도, 아닐 수도 있다. 저장하기 전에 한 번 더 가린다.
     * @param type   카드 종류(신용/체크 등). 없으면 빈 문자열
     */
    public record CodefCard(String name, String number, String type) {

        @Override
        public String toString() {
            return "CodefCard[****]";
        }

    }

}
