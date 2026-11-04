package com.richreach.codef.client;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * CODEF 카드 조회 API 호출. 지금은 연결한 카드사의 보유 카드 목록 개수를 가져오는 데만 쓴다.
 * 카드 목록의 내용(카드번호 등)은 이 클래스가 읽지도, 로그에 남기지도 않는다.
 *
 * <p>요청·응답 필드는 CODEF 문서를 기준으로 한 것이다. 실제 응답은 샌드박스로 확인해서 보완한다.
 */
@Component
public class CodefCardClient {

    static final String CARD_LIST_PATH = "/v1/kr/card/p/account/card-list";

    private static final String SUCCESS_CODE = "CF-00000";

    private final CodefApiClient apiClient;

    public CodefCardClient(CodefApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 카드사에 등록된 보유 카드 개수를 가져온다.
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
     * 배열이면 길이, 비어 있지 않은 객체면 1장으로 센다.
     */
    static CardListResult parse(JsonNode response) {
        String resultCode = response.path("result").path("code").asString("");
        if (!SUCCESS_CODE.equals(resultCode)) {
            return new CardListResult(false, 0, resultCode);
        }
        JsonNode data = response.path("data");
        int count = data.isArray() ? data.size() : (data.isObject() && data.size() > 0 ? 1 : 0);
        return new CardListResult(true, count, resultCode);
    }

    /**
     * 카드 목록 조회 결과.
     *
     * @param cardCount 성공했을 때 가져온 카드 수
     * @param code      CODEF 결과 코드(CF-xxxxx). 성공이면 CF-00000
     */
    public record CardListResult(boolean success, int cardCount, String code) {

    }

}
