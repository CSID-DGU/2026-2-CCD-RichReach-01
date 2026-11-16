package com.richreach.recommendation.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.recommendation.config.OpenAiProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class OpenAiQuestionInterpreterTest {

    private static final String RESPONSE = """
        {"status":"completed","output":[{"type":"message","content":[
          {"type":"output_text","text":"{\\"category\\":\\"DINING\\",\\"merchantName\\":\\"아웃백\\",\\"amount\\":100000,\\"isCardQuestion\\":true}"}
        ]}]}
        """;

    @Test
    void sendsOnlyQuestionWithSchemaAndDoesNotStoreResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.openai.com/v1/responses"))
            .andExpect(header("Authorization", "Bearer test-key"))
            .andExpect(jsonPath("$.model").value("gpt-5-nano"))
            .andExpect(jsonPath("$.input").value("아웃백에서 10만 원 결제하면 카드 뭐 써?"))
            .andExpect(jsonPath("$.store").value(false))
            .andExpect(jsonPath("$.reasoning.effort").value("minimal"))
            .andExpect(jsonPath("$.text.format.strict").value(true))
            .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        var interpreter = new OpenAiQuestionInterpreter(builder.build(), properties("test-key"),
            JsonMapper.builder().build());
        var parsed = interpreter.interpret("아웃백에서 10만 원 결제하면 카드 뭐 써?");

        assertThat(parsed.category()).isEqualTo(SpendingCategory.DINING);
        assertThat(parsed.merchantName()).isEqualTo("아웃백");
        assertThat(parsed.amount()).isEqualTo(100000);
        assertThat(parsed.cardQuestion()).isTrue();
        server.verify();
    }

    @Test
    void refusesIncompleteResponseInsteadOfInventingPaymentFacts() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.openai.com/v1/responses"))
            .andRespond(withSuccess("{\"status\":\"incomplete\",\"output\":[]}", MediaType.APPLICATION_JSON));
        var interpreter = new OpenAiQuestionInterpreter(builder.build(), properties("test-key"),
            JsonMapper.builder().build());

        assertThatThrownBy(() -> interpreter.interpret("편의점에서 카드 뭐 써?"))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.RECOMMENDATION_UNAVAILABLE);
        server.verify();
    }

    @Test
    void missingKeyFailsBeforeNetworkRequest() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var interpreter = new OpenAiQuestionInterpreter(builder.build(), properties(""),
            JsonMapper.builder().build());

        assertThatThrownBy(() -> interpreter.interpret("아웃백에서 카드 뭐 써?"))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.RECOMMENDATION_UNAVAILABLE);
        server.verify();
    }

    private static OpenAiProperties properties(String key) {
        return new OpenAiProperties(key, "gpt-5-nano", "https://api.openai.com",
            Duration.ofSeconds(3), Duration.ofSeconds(20));
    }
}
