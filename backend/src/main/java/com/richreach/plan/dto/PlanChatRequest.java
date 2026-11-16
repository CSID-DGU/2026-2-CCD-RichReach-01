package com.richreach.plan.dto;

import java.util.List;

/** 사용자가 확인 중인 예산 초안과 대화. 서버는 이 요청만으로 플랜을 저장하지 않는다. */
public record PlanChatRequest(String message, PlanCreateRequest currentPlan,
                              String currentPreference, List<Turn> history) {

    public record Turn(String role, String content) {
    }
}
