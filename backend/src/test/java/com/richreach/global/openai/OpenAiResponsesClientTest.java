package com.richreach.global.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class OpenAiResponsesClientTest {

    private static final String OK = """
        {"status":"completed","output":[{"type":"message","content":[
          {"type":"output_text","text":"{\\"value\\":1}"}]}]}
        """;
    private static final List<Map<String, Object>> INPUT = List.of(Map.of("role", "user", "content", "hi"));
    private static final Map<String, Object> SCHEMA = Map.of("type", "object");

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void sendsStoreFalseAndStrictSchemaThenReturnsParsedJson() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
            .andExpect(header("Authorization", "Bearer test-key"))
            .andExpect(jsonPath("$.store").value(false))
            .andExpect(jsonPath("$.max_output_tokens").value(100))
            .andExpect(jsonPath("$.text.format.name").value("demo"))
            .andExpect(jsonPath("$.text.format.strict").value(true))
            .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        JsonNode result = client("test-key").generateJson("테스트", "rules", INPUT, "demo", SCHEMA, 100);

        assertThat(result.path("value").asInt()).isEqualTo(1);
        server.verify();
    }

    @Test
    void missingKeyIsUnavailableWithoutCallingOpenAi() {
        assertThatThrownBy(() -> client("").generateJson("테스트", "rules", INPUT, "demo", SCHEMA, 100))
            .isInstanceOfSatisfying(OpenAiException.class,
                e -> assertThat(e.kind()).isEqualTo(OpenAiException.Kind.UNAVAILABLE));
        server.verify();
    }

    @Test
    void httpErrorIsUnavailableAndDoesNotLeakKeyInMessage() {
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withServerError());

        assertThatThrownBy(() -> client("secret-key").generateJson("테스트", "rules", INPUT, "demo", SCHEMA, 100))
            .isInstanceOfSatisfying(OpenAiException.class, e -> {
                assertThat(e.kind()).isEqualTo(OpenAiException.Kind.UNAVAILABLE);
                assertThat(e.getMessage()).doesNotContain("secret-key");
            });
    }

    @Test
    void connectionFailureIsTimeout() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
            .andRespond(request -> {
                throw new IOException("read timed out");
            });

        assertThatThrownBy(() -> client("test-key").generateJson("테스트", "rules", INPUT, "demo", SCHEMA, 100))
            .isInstanceOfSatisfying(OpenAiException.class,
                e -> assertThat(e.kind()).isEqualTo(OpenAiException.Kind.TIMEOUT));
    }

    @Test
    void incompleteOrMalformedResponseIsUnavailable() {
        OpenAiResponsesClient client = client("test-key");
        for (String bad : List.of("", "{\"status\":\"incomplete\",\"output\":[]}", "not json",
            "{\"status\":\"completed\",\"output\":[]}",
            "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"{broken\"}]}]}")) {
            assertThatThrownBy(() -> client.extractJson("테스트", bad))
                .isInstanceOfSatisfying(OpenAiException.class,
                    e -> assertThat(e.kind()).isEqualTo(OpenAiException.Kind.UNAVAILABLE));
        }
    }

    private OpenAiResponsesClient client(String key) {
        return new OpenAiResponsesClient(builder.build(),
            new OpenAiProperties(key, "gpt-5-nano", "https://api.openai.com",
                Duration.ofSeconds(3), Duration.ofSeconds(20)),
            JsonMapper.builder().build());
    }
}
