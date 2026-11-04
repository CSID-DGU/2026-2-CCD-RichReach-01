package com.richreach.codef.client;

import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * CODEF와 주고받는 본문을 만들고 읽는다. 상품 API와 토큰 발급 API가 같은 규칙을 쓰므로 한 곳에 둔다.
 * <ul>
 *   <li>요청 본문: JSON 문자열을 URL 인코딩한다.</li>
 *   <li>응답 본문: JSON이면 그대로, URL 인코딩되어 오면 디코딩한 뒤 읽는다.</li>
 * </ul>
 * 본문에는 개인정보나 토큰이 들어 있을 수 있어서 어떤 오류 메시지에도 본문 내용을 담지 않는다.
 */
@Component
@RequiredArgsConstructor
class CodefJsonCodec {

    private final JsonMapper jsonMapper;

    /** 요청 본문을 JSON으로 직렬화한 뒤 URL 인코딩한다. */
    String encodeBody(Object body) {
        try {
            return URLEncoder.encode(jsonMapper.writeValueAsString(body), StandardCharsets.UTF_8);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "CODEF 요청 본문을 만들지 못했습니다.");
        }
    }

    /** 응답 본문을 JSON 트리로 읽는다. 비어 있거나 읽을 수 없으면 외부 API 오류다. */
    JsonNode readTree(String body) {
        String json = decoded(body);
        try {
            return jsonMapper.readTree(json);
        } catch (JacksonException e) {
            throw invalidFormat();
        }
    }

    /** 응답 본문을 지정한 타입으로 읽는다. 비어 있거나 읽을 수 없으면 외부 API 오류다. */
    <T> T read(String body, Class<T> type) {
        String json = decoded(body);
        try {
            return jsonMapper.readValue(json, type);
        } catch (JacksonException e) {
            throw invalidFormat();
        }
    }

    /** JSON(중괄호나 대괄호)으로 시작하지 않으면 URL 인코딩된 것으로 보고 디코딩한다. */
    private String decoded(String body) {
        if (body == null || body.isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "CODEF 응답이 비어 있습니다.");
        }
        String json = body.strip();
        if (json.startsWith("{") || json.startsWith("[")) {
            return json;
        }
        try {
            return URLDecoder.decode(json, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 잘못된 퍼센트 인코딩도 외부 서비스 문제이므로 같은 오류로 처리한다.
            throw invalidFormat();
        }
    }

    private BusinessException invalidFormat() {
        return new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "CODEF 응답 형식이 올바르지 않습니다.");
    }

}
