package com.richreach.plan.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.global.openai.OpenAiProperties;
import com.richreach.global.openai.OpenAiResponsesClient;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class OpenAiPlanChatClientTest {

    private static final String RESPONSE = """
        {"status":"completed","output":[{"type":"message","content":[
          {"type":"output_text","text":"{\\"reply\\":\\"카페 예산을 3만 원으로 바꾸는 수정안입니다.\\",\\"needsClarification\\":false,\\"edits\\":[{\\"category\\":\\"CAFE\\",\\"amount\\":30000}],\\"preference\\":null}"}
        ]}]}
        """;

    @Test
    void sendsBudgetContextAndReturnsStructuredProposalWithoutStoringResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.openai.com/v1/responses"))
            .andExpect(header("Authorization", "Bearer test-key"))
            .andExpect(jsonPath("$.model").value("gpt-5-nano"))
            .andExpect(jsonPath("$.store").value(false))
            .andExpect(jsonPath("$.input[0].role").value("user"))
            .andExpect(jsonPath("$.input[0].content").value(org.hamcrest.Matchers.containsString("CAFE")))
            .andExpect(jsonPath("$.text.format.strict").value(true))
            .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        var client = planClient(builder);
        var result = client.chat("카페 3만 원으로", "2026-12", Map.of(SpendingCategory.CAFE, 20_000L),
            "MONTHLY_PLAN", java.util.List.of());

        assertThat(result.reply()).contains("수정안");
        assertThat(result.edits()).containsExactly(new OpenAiPlanChatClient.BudgetEdit("CAFE", 30_000));
        server.verify();
    }

    @Test
    void rejectsIncompleteModelResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.openai.com/v1/responses"))
            .andRespond(withSuccess("{\"status\":\"incomplete\",\"output\":[]}", MediaType.APPLICATION_JSON));
        var client = planClient(builder);

        assertThatThrownBy(() -> client.chat("카페 3만 원으로", "2026-12",
            Map.of(SpendingCategory.CAFE, 20_000L), "MONTHLY_PLAN", java.util.List.of()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.PLAN_CHAT_UNAVAILABLE);
        server.verify();
    }

    private static OpenAiPlanChatClient planClient(RestClient.Builder builder) {
        JsonMapper mapper = JsonMapper.builder().build();
        return new OpenAiPlanChatClient(
            new OpenAiResponsesClient(builder.build(), properties("test-key"), mapper), mapper);
    }

    private static OpenAiProperties properties(String key) {
        return new OpenAiProperties(key, "gpt-5-nano", "https://api.openai.com",
            Duration.ofSeconds(3), Duration.ofSeconds(20));
    }
}
