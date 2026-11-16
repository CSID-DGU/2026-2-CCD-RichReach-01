package com.richreach.recommendation.client;

import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.recommendation.config.OpenAiProperties;
import com.richreach.recommendation.dto.PaymentQuestion;
import java.math.BigInteger;
import java.util.ArrayList;
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

/** 질문만 외부로 보내 업종·가맹점·금액을 추출한다. 카드·거래 기록은 보내지 않는다. */
@Slf4j
@Component
public class OpenAiQuestionInterpreter {

    private static final String INSTRUCTIONS = """
        한국어 카드 결제 질문에서 명시된 결제 정보만 추출하세요. 혜택이나 카드 추천 금액을 계산하지 마세요.
        category는 DINING, CAFE, CONVENIENCE_STORE, SHOPPING, TRANSPORT, FUEL_VEHICLE,
        LIFE_HEALTH, TELECOM_SUBSCRIPTION, CULTURE_TRAVEL, EDUCATION, ETC 중 하나입니다.
        업종을 확실히 알 수 없으면 null로 두세요. merchantName은 사용자가 명시한 가맹점명만 쓰세요.
        amount는 원 단위 양의 정수이며, 명시되지 않았다면 null입니다.
        isCardQuestion은 카드 선택이나 결제 혜택 질문이면 true입니다.
        """;

    private static final Map<String, Object> SCHEMA = schema();

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final JsonMapper jsonMapper;

    public OpenAiQuestionInterpreter(@Qualifier("openAiRestClient") RestClient restClient,
                                     OpenAiProperties properties, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    public PaymentQuestion interpret(String question) {
        if (question == null || question.isBlank() || question.length() > 200) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_RECOMMENDATION_INPUT);
        }
        if (!properties.configured()) {
            log.error("OPENAI_API_KEY가 설정되지 않았습니다.");
            throw new BusinessException(ErrorCode.RECOMMENDATION_UNAVAILABLE);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("instructions", INSTRUCTIONS);
        body.put("input", question.strip());
        body.put("store", false);
        body.put("reasoning", Map.of("effort", "minimal"));
        body.put("max_output_tokens", 400);
        body.put("text", Map.of("format", Map.of(
            "type", "json_schema", "name", "payment_question", "strict", true, "schema", SCHEMA)));

        try {
            String response = restClient.post().uri("/v1/responses")
                .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(String.class);
            return parse(response);
        } catch (RestClientResponseException e) {
            log.warn("OpenAI 질문 해석 실패: HTTP {}", e.getStatusCode().value());
            throw new BusinessException(ErrorCode.RECOMMENDATION_UNAVAILABLE);
        } catch (ResourceAccessException e) {
            log.warn("OpenAI 질문 해석 시간 초과 또는 연결 실패");
            throw new BusinessException(ErrorCode.RECOMMENDATION_TIMEOUT);
        } catch (RestClientException e) {
            log.warn("OpenAI 질문 해석 요청 실패: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.RECOMMENDATION_UNAVAILABLE);
        }
    }

    PaymentQuestion parse(String response) {
        if (response == null || response.isBlank()) throw unavailable();
        try {
            JsonNode root = jsonMapper.readTree(response);
            if (!"completed".equals(root.path("status").asText())) {
                throw unavailable();
            }
            for (JsonNode item : root.path("output")) {
                if (!"message".equals(item.path("type").asText())) continue;
                for (JsonNode content : item.path("content")) {
                    if (!"output_text".equals(content.path("type").asText())) continue;
                    JsonNode result = jsonMapper.readTree(content.path("text").asText());
                    if (!result.path("isCardQuestion").isBoolean()) throw unavailable();
                    JsonNode rawCategory = result.path("category");
                    SpendingCategory category = rawCategory.isNull() ? null :
                        SpendingCategory.fromCode(rawCategory.asText()).orElseThrow(this::unavailable);
                    JsonNode rawMerchant = result.path("merchantName");
                    String merchant = rawMerchant.isNull() ? null : rawMerchant.asText().strip();
                    if (merchant != null && (merchant.isBlank() || merchant.length() > 100)) throw unavailable();
                    JsonNode rawAmount = result.path("amount");
                    if (!rawAmount.isNull() && !rawAmount.isIntegralNumber()) throw unavailable();
                    Long amount = rawAmount.isNull() ? null :
                        new BigInteger(rawAmount.asText()).longValueExact();
                    if (amount != null && amount <= 0) throw unavailable();
                    return new PaymentQuestion(category, merchant, amount,
                        result.path("isCardQuestion").asBoolean());
                }
            }
        } catch (JacksonException | IllegalArgumentException | ArithmeticException e) {
            log.warn("OpenAI 질문 해석 응답 형식 오류");
        }
        throw unavailable();
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.RECOMMENDATION_UNAVAILABLE);
    }

    private static Map<String, Object> schema() {
        List<Object> categories = new ArrayList<>();
        for (SpendingCategory category : SpendingCategory.values()) categories.add(category.name());
        categories.add(null);
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "category", Map.of("type", List.of("string", "null"), "enum", categories),
                "merchantName", Map.of("type", List.of("string", "null")),
                "amount", Map.of("type", List.of("integer", "null")),
                "isCardQuestion", Map.of("type", "boolean")),
            "required", List.of("category", "merchantName", "amount", "isCardQuestion"),
            "additionalProperties", false);
    }
}
