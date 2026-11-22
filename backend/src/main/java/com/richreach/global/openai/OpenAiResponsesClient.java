package com.richreach.global.openai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenAI Responses API 호출의 공통 처리. 기능별 클라이언트는 프롬프트와 스키마, 응답 검증만 맡는다.
 *
 * <ul>
 *   <li>모든 요청에 {@code store=false}를 붙여 OpenAI에 응답을 보관하지 않는다.</li>
 *   <li>키·프롬프트·응답 원문은 로그에 남기지 않는다. HTTP 상태와 예외 종류만 남긴다.</li>
 *   <li>실패는 {@link OpenAiException}으로 통일한다.</li>
 * </ul>
 */
@Slf4j
@Component
public class OpenAiResponsesClient {

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final JsonMapper jsonMapper;

    public OpenAiResponsesClient(@Qualifier("openAiRestClient") RestClient restClient,
                                 OpenAiProperties properties, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    /**
     * JSON 스키마를 강제해 모델을 호출하고, 응답 본문(output_text)을 JSON으로 파싱해 돌려준다.
     * 필드 값의 검증은 호출한 기능이 해야 한다.
     *
     * @param feature 로그에 남길 기능 이름 (예: "플랜 대화")
     */
    public JsonNode generateJson(String feature, String instructions, List<Map<String, Object>> input,
                                 String schemaName, Map<String, Object> schema, int maxOutputTokens) {
        if (!properties.configured()) {
            log.warn("OpenAI {} 호출 불가: API 키가 설정되지 않음", feature);
            throw new OpenAiException(OpenAiException.Kind.UNAVAILABLE, "OpenAI API 키가 없습니다.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("instructions", instructions);
        body.put("input", input);
        body.put("store", false);
        body.put("reasoning", Map.of("effort", "minimal"));
        body.put("max_output_tokens", maxOutputTokens);
        body.put("text", Map.of("format", Map.of(
            "type", "json_schema", "name", schemaName, "strict", true, "schema", schema)));
        String response;
        try {
            response = restClient.post().uri("/v1/responses")
                .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            log.warn("OpenAI {} 실패: HTTP {}", feature, e.getStatusCode().value());
            throw new OpenAiException(OpenAiException.Kind.UNAVAILABLE, "OpenAI HTTP 오류");
        } catch (ResourceAccessException e) {
            log.warn("OpenAI {} 연결 시간 초과", feature);
            throw new OpenAiException(OpenAiException.Kind.TIMEOUT, "OpenAI 시간 초과");
        } catch (RestClientException e) {
            log.warn("OpenAI {} 요청 실패: {}", feature, e.getClass().getSimpleName());
            throw new OpenAiException(OpenAiException.Kind.UNAVAILABLE, "OpenAI 요청 실패");
        }
        return extractJson(feature, response);
    }

    JsonNode extractJson(String feature, String response) {
        if (response == null || response.isBlank()) throw unusable(feature);
        try {
            JsonNode root = jsonMapper.readTree(response);
            if (!"completed".equals(root.path("status").asText())) throw unusable(feature);
            for (JsonNode item : root.path("output")) {
                if (!"message".equals(item.path("type").asText())) continue;
                for (JsonNode content : item.path("content")) {
                    if (!"output_text".equals(content.path("type").asText())) continue;
                    return jsonMapper.readTree(content.path("text").asText());
                }
            }
        } catch (JacksonException e) {
            log.warn("OpenAI {} 응답 형식 오류", feature);
        }
        throw unusable(feature);
    }

    private OpenAiException unusable(String feature) {
        log.warn("OpenAI {} 응답을 사용할 수 없음", feature);
        return new OpenAiException(OpenAiException.Kind.UNAVAILABLE, "OpenAI 응답을 사용할 수 없습니다.");
    }
}
