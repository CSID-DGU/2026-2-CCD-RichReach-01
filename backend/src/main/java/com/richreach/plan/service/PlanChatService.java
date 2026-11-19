package com.richreach.plan.service;

import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.plan.client.OpenAiPlanChatClient;
import com.richreach.plan.client.OpenAiPlanChatClient.BudgetEdit;
import com.richreach.plan.dto.PlanChatRequest;
import com.richreach.plan.dto.PlanChatResponse;
import com.richreach.plan.dto.PlanCreateRequest;
import com.richreach.plan.dto.PlanCreateRequest.CategoryBudget;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** AI가 제안한 예산을 기존 플랜 검증 규칙으로 다시 검사하고 미리보기만 반환한다. */
@Service
@RequiredArgsConstructor
public class PlanChatService {

    private static final Set<String> PREFERENCES = Set.of("MONTHLY_PLAN", "IMMEDIATE_DISCOUNT");
    private final OpenAiPlanChatClient client;

    public PlanChatResponse preview(PlanChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()
            || request.message().length() > 500 || request.currentPlan() == null
            || request.currentPreference() == null || !PREFERENCES.contains(request.currentPreference())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        List<PlanChatRequest.Turn> history = validateHistory(request.history());
        PlanBudget current = PlanBudget.parse(request.currentPlan());
        var result = client.chat(request.message().strip(), current.month().toString(), current.byCategory(),
            request.currentPreference(), history);

        if (result.needsClarification()) {
            return new PlanChatResponse(result.reply(), true, false, null, null);
        }
        Map<SpendingCategory, Long> proposed = new EnumMap<>(SpendingCategory.class);
        proposed.putAll(current.byCategory());
        Set<SpendingCategory> seen = new HashSet<>();
        if (result.edits().size() > SpendingCategory.values().length) throw unavailable();
        for (BudgetEdit edit : result.edits()) {
            SpendingCategory category = SpendingCategory.fromCode(edit.category()).orElseThrow(this::unavailable);
            if (!seen.add(category) || edit.amount() < 0 || edit.amount() > PlanBudget.MAX_TOTAL) throw unavailable();
            if (edit.amount() == 0) proposed.remove(category);
            else proposed.put(category, edit.amount());
        }
        String preference = result.preference() == null ? request.currentPreference() : result.preference();
        if (!PREFERENCES.contains(preference)) throw unavailable();
        List<CategoryBudget> categories = new ArrayList<>();
        long total = 0;
        for (SpendingCategory category : SpendingCategory.values()) {
            long amount = proposed.getOrDefault(category, 0L);
            total = Math.addExact(total, amount);
            categories.add(new CategoryBudget(category.name(), amount));
        }
        if (total <= 0 || total > PlanBudget.MAX_TOTAL) throw unavailable();
        PlanCreateRequest proposal = new PlanCreateRequest(current.month().toString(),
            new PlanCreateRequest.Budget(total, categories));
        try {
            PlanBudget.parse(proposal);
        } catch (BusinessException e) {
            throw unavailable();
        }
        boolean changed = total != current.total() || !proposed.equals(current.byCategory())
            || !preference.equals(request.currentPreference());
        return new PlanChatResponse(result.reply(), false, changed, changed ? proposal : null,
            changed ? preference : null);
    }

    private List<PlanChatRequest.Turn> validateHistory(List<PlanChatRequest.Turn> history) {
        if (history == null) return List.of();
        if (history.size() > 10) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        for (var turn : history) {
            if (turn == null || !("USER".equals(turn.role()) || "ASSISTANT".equals(turn.role()))
                || turn.content() == null || turn.content().isBlank() || turn.content().length() > 500) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }
        return List.copyOf(history);
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.PLAN_CHAT_UNAVAILABLE);
    }
}
