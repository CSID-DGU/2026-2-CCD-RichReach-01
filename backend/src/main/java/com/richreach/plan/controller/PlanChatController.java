package com.richreach.plan.controller;

import com.richreach.global.response.ApiResponse;
import com.richreach.plan.dto.PlanChatRequest;
import com.richreach.plan.dto.PlanChatResponse;
import com.richreach.plan.dto.PlanChatConfirmRequest;
import com.richreach.plan.dto.PlanChatConfirmResponse;
import com.richreach.plan.service.PlanChatConfirmService;
import com.richreach.plan.service.PlanChatService;
import com.richreach.global.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 월 예산 초안의 대화 미리보기와 사용자 확인 뒤 재계산 요청. */
@RestController
@RequestMapping("/api/plans/chat")
@RequiredArgsConstructor
public class PlanChatController {

    private final PlanChatService service;
    private final PlanChatConfirmService confirmService;

    @PostMapping("/preview")
    public ApiResponse<PlanChatResponse> preview(@RequestBody PlanChatRequest request) {
        return ApiResponse.ok(service.preview(request));
    }

    @PostMapping("/confirm")
    public ApiResponse<PlanChatConfirmResponse> confirm(@AuthenticationPrincipal AuthenticatedUser user,
                                                        @RequestBody PlanChatConfirmRequest request) {
        return ApiResponse.ok(confirmService.confirm(user.userId(), request));
    }
}
