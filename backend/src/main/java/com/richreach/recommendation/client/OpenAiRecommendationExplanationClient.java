package com.richreach.recommendation.client;

import com.richreach.global.category.SpendingCategory;
import com.richreach.global.openai.OpenAiProperties;
import com.richreach.recommendation.service.RecommendationExplanationClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** AI 응답은 수치나 카드 선택을 바꿀 수 없다. 실패하거나 검증에 걸리면 서버 문구만 사용한다. */
@Component
public class OpenAiRecommendationExplanationClient implements RecommendationExplanationClient {

    private static final Map<String, Object> SCHEMA = Map.of(
        "type", "object",
        "properties", Map.of("reason", Map.of("type", "string")),
        "required", List.of("reason"), "additionalProperties", false);

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final JsonMapper mapper;

    public OpenAiRecommendationExplanationClient(@Qualifier("openAiRestClient") RestClient restClient,
                                                  OpenAiProperties properties, JsonMapper mapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public Optional<String> explain(SpendingCategory category, long benefit, long nextBenefit) {
        if (!properties.configured()) return Optional.empty();
        Map<String, Object> body = Map.of(
            "model", properties.model(),
            "instructions", "한국어로 카드 비교 결과를 한 문장으로 설명하세요. 제공된 사실은 선택 카드의 계산 가능한 혜택이 다른 후보 이상이라는 것뿐입니다. 카드명, 금액, 할인율, 실적, 한도, 가맹점, 확정 혜택을 언급하거나 지어내지 마세요. 45자 이내로 쓰세요.",
            "input", "업종=" + category.name() + ", 선택 카드 예상 혜택=" + benefit
                + "원, 다음 후보 예상 혜택=" + nextBenefit + "원",
            "store", false,
            "reasoning", Map.of("effort", "minimal"),
            "max_output_tokens", 100,
            "text", Map.of("format", Map.of("type", "json_schema", "name", "card_reason",
                "strict", true, "schema", SCHEMA)));
        try {
            String response = restClient.post().uri("/v1/responses")
                .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class);
            return parse(response);
        } catch (RestClientException | JacksonException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    Optional<String> parse(String response) throws JacksonException {
        if (response == null || response.isBlank()) return Optional.empty();
        JsonNode root = mapper.readTree(response);
        if (!"completed".equals(root.path("status").asText())) return Optional.empty();
        for (JsonNode item : root.path("output")) {
            if (!"message".equals(item.path("type").asText())) continue;
            for (JsonNode content : item.path("content")) {
                if (!"output_text".equals(content.path("type").asText())) continue;
                JsonNode value = mapper.readTree(content.path("text").asText());
                if (!value.path("reason").isTextual()) return Optional.empty();
                String reason = value.path("reason").asText().strip();
                if (valid(reason)) return Optional.of(reason);
            }
        }
        return Optional.empty();
    }

    static boolean valid(String reason) {
        if (reason.isBlank() || reason.length() > 60 || reason.indexOf('\uFFFD') >= 0
            || reason.contains("\n") || reason.matches(".*[0-9].*")) return false;
        for (String unsupported : List.of("확정", "보장", "반드시", "실적", "한도", "가맹점", "할인율", "최적")) {
            if (reason.contains(unsupported)) return false;
        }
        return reason.contains("혜택") && (reason.contains("높") || reason.contains("비교")
            || reason.contains("유리"));
    }
}
