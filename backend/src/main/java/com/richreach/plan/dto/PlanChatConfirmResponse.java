package com.richreach.plan.dto;

/** 재계산을 시작했으면 jobId가 있고, 다시 확인이 필요하면 고정 안내 문구만 있다. */
public record PlanChatConfirmResponse(String reply, boolean needsClarification, String jobId) {
}
