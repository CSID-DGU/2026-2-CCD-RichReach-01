package com.richreach.recommendation.controller;

import com.richreach.global.response.ApiResponse;
import com.richreach.recommendation.client.OpenAiQuestionInterpreter;
import com.richreach.recommendation.dto.InterpretQuestionRequest;
import com.richreach.recommendation.dto.PaymentQuestion;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 결제 질문 해석 확인용 API. 추천 금액 계산과 카드 선택은 담당하지 않는다. */
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationInterpretController {

    private final OpenAiQuestionInterpreter interpreter;

    @PostMapping("/interpret")
    public ApiResponse<PaymentQuestion> interpret(@Valid @RequestBody InterpretQuestionRequest request) {
        return ApiResponse.ok(interpreter.interpret(request.question()));
    }
}
