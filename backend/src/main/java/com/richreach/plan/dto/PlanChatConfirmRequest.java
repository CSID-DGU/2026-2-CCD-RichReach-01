package com.richreach.plan.dto;

/** 사용자가 화면에서 확인한 챗봇 수정안. 미리보기만으로는 저장하지 않는다. */
public record PlanChatConfirmRequest(long basePlanId, boolean confirmed, PlanCreateRequest proposedPlan,
                                     String proposedPreference) {
}
