package com.richreach.plan.controller;

import com.richreach.global.response.ApiResponse;
import com.richreach.plan.dto.PlanChatRequest;
import com.richreach.plan.dto.PlanChatResponse;
import com.richreach.plan.service.PlanChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 월 예산 초안에 관한 대화와 수정안 미리보기. 저장·최적화는 수행하지 않는다. */
@RestController
@RequestMapping("/api/plans/chat")
@RequiredArgsConstructor
public class PlanChatController {

    private final PlanChatService service;

    @PostMapping("/preview")
    public ApiResponse<PlanChatResponse> preview(@RequestBody PlanChatRequest request) {
        return ApiResponse.ok(service.preview(request));
    }
}
