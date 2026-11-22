package com.richreach.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.plan.client.OpenAiPlanChatClient;
import com.richreach.plan.client.OpenAiPlanChatClient.BudgetEdit;
import com.richreach.plan.client.OpenAiPlanChatClient.ChatResult;
import com.richreach.plan.dto.PlanChatRequest;
import com.richreach.plan.dto.PlanCreateRequest;
import com.richreach.plan.dto.PlanCreateRequest.Budget;
import com.richreach.plan.dto.PlanCreateRequest.CategoryBudget;
import com.richreach.global.category.SpendingCategory;
import com.richreach.global.openai.OpenAiProperties;
import com.richreach.global.openai.OpenAiResponsesClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class PlanChatServiceTest {

    private final FakeClient client = new FakeClient();
    private final PlanChatService service = new PlanChatService(client);

    @Test
    void previewsOnlyExplicitBudgetEditWithoutSavingIt() {
        client.result = new ChatResult("카페 예산 수정안입니다.", false,
            List.of(new BudgetEdit("CAFE", 30_000)), null);

        var response = service.preview(request("카페 예산 3만 원으로"));

        assertThat(response.requiresConfirmation()).isTrue();
        assertThat(response.proposedPlan().budget().total()).isEqualTo(130_000);
        assertThat(response.proposedPlan().budget().byCategory())
            .contains(new CategoryBudget("DINING", 100_000L), new CategoryBudget("CAFE", 30_000L));
        assertThat(response.proposedPreference()).isEqualTo("MONTHLY_PLAN");
    }

    @Test
    void asksForClarificationWithoutApplyingModelEdits() {
        client.result = new ChatResult("금액을 알려주세요.", true,
            List.of(new BudgetEdit("CAFE", 30_000)), null);

        var response = service.preview(request("카페를 좀 늘려줘"));

        assertThat(response.needsClarification()).isTrue();
        assertThat(response.requiresConfirmation()).isFalse();
        assertThat(response.proposedPlan()).isNull();
    }

    @Test
    void clarifiesInvalidModelCategoryInsteadOfReturningProposal() {
        client.result = new ChatResult("수정안", false, List.of(new BudgetEdit("UNKNOWN", 30_000)), null);

        var response = service.preview(request("예산 바꿔줘"));
        assertThat(response.needsClarification()).isTrue();
        assertThat(response.reply()).isEqualTo(PlanChatService.CLARIFICATION_REPLY);
        assertThat(response.proposedPlan()).isNull();
    }

    @Test
    void invalidRequestNeverCallsOpenAi() {
        assertThatThrownBy(() -> service.preview(new PlanChatRequest(" ", currentPlan(),
            "MONTHLY_PLAN", List.of())))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(client.calls).isZero();
    }

    private static PlanChatRequest request(String message) {
        return new PlanChatRequest(message, currentPlan(), "MONTHLY_PLAN", List.of());
    }

    private static PlanCreateRequest currentPlan() {
        return new PlanCreateRequest("2026-12", new Budget(120_000L,
            List.of(new CategoryBudget("DINING", 100_000L), new CategoryBudget("CAFE", 20_000L))));
    }

    private static class FakeClient extends OpenAiPlanChatClient {
        ChatResult result;
        int calls;

        FakeClient() {
            super(new OpenAiResponsesClient(RestClient.create(), new OpenAiProperties("", "gpt-5-nano",
                "https://api.openai.com", Duration.ofSeconds(3), Duration.ofSeconds(20)),
                JsonMapper.builder().build()), JsonMapper.builder().build());
        }

        @Override
        public ChatResult chat(String message, String month, Map<SpendingCategory, Long> budget,
                               String preference, List<PlanChatRequest.Turn> history) {
            calls++;
            return result;
        }
    }
}
