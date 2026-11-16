package com.richreach.plan.dto;

/** 대화 답변과 검증된 수정안. requiresConfirmation이 true여도 저장은 하지 않는다. */
public record PlanChatResponse(String reply, boolean needsClarification, boolean requiresConfirmation,
                               PlanCreateRequest proposedPlan, String proposedPreference) {
}
